package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppQueuePositionRespVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueueEventDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueuePositionDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopReferralContributionDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopQueueEventMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopQueuePositionMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopReferralContributionMapper;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * v7 推 N 反 1 状态机实现。
 *
 * <p>核心规则（详见 {@code docs/design/marketing-system-v6.md} v7 补充）：</p>
 * <ol>
 *   <li>自购首单：激活资格（state=IN_PROGRESS, cumulated=0），不返奖</li>
 *   <li>已激活（IN_PROGRESS）期：每次自购或下级首贡献都 cumulated++
 *       <ul>
 *         <li>自购：自己拿「单件 payPrice × (1/N)」</li>
 *         <li>下级首贡献：上级拿「单件 payPrice × (1/N)」</li>
 *         <li>cumulated == N → 进入 COMPLETED 永久终态</li>
 *       </ul></li>
 *   <li>已完成（COMPLETED）期：每次自购或下级首贡献按「订单中该商品行 payPrice × directCommissionRatio%」返
 *       <ul>
 *         <li>自购：自己拿</li>
 *         <li>下级首贡献：上级拿</li>
 *       </ul></li>
 *   <li>每个 (parent, child, spu) 的首贡献仅触发 1 次（DB UNIQUE 强约束）</li>
 *   <li>parent 未激活该商品 → 完全跳过 parent，不上溯，奖励吞掉</li>
 *   <li>真自然用户（无 parent）：按 promo_config.naturalPushEnabled 决定走旧 A/B 队列还是吞奖</li>
 * </ol>
 *
 * <p>所有返奖乘数 = 用户实付金额（trade_order_item.payPrice），含积分 / 余额 / 优惠抵扣后的最终值。</p>
 *
 * <p>幂等：</p>
 * <ul>
 *   <li>buyer 自购触发：promoPointService 内部 (userId, sourceType, orderId) 三元组防重</li>
 *   <li>下级首贡献触发：DB UNIQUE (parent, child, spu) + DuplicateKeyException 兜底</li>
 *   <li>同一订单同 SPU 多个 sku 行：sourceType 区分（不同 sku 视为同一订单同一 SPU 行处理）</li>
 * </ul>
 */
@Service
@Slf4j
public class PromoQueueServiceImpl implements PromoQueueService {

    /** 状态机 */
    private static final String STATE_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATE_COMPLETED = "COMPLETED";

    /** v6 兼容字段值（写入 status / layer 给老代码兼容；新代码读 state） */
    private static final String LEGACY_STATUS_QUEUEING = "QUEUEING";
    private static final String LEGACY_STATUS_EXITED = "EXITED";
    private static final String LEGACY_LAYER_A = "A";

    /** 事件类型 */
    private static final String EVT_ACTIVATE = "ACTIVATE";       // 首单激活，不返奖
    private static final String EVT_SELF_PROGRESS = "SELF_PROGRESS";   // 自购在进行期返奖
    private static final String EVT_REFERRAL_PROGRESS = "REFERRAL_PROGRESS"; // 下级首贡献在进行期返给上级
    private static final String EVT_SELF_COMMISSION = "SELF_COMMISSION";   // 自购终态返奖（间推 % 给自己）
    private static final String EVT_REFERRAL_COMMISSION = "REFERRAL_COMMISSION"; // 下级首贡献终态返给上级
    private static final String EVT_EXIT = "EXIT";              // cumulated==N 出队

    @Resource
    private ShopQueuePositionMapper queueMapper;
    @Resource
    private ShopQueueEventMapper eventMapper;
    @Resource
    private ShopReferralContributionMapper contributionMapper;
    @Resource
    private ReferralService referralService;
    @Resource
    private PromoPointService promoPointService;
    @Resource
    private ProductPromoConfigService productPromoConfigService;
    @Resource
    private PromoConfigService promoConfigService;
    @Resource
    private ProductSpuService productSpuService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleOrderPaid(ProductPromoConfigDO config, Long buyerUserId, Long spuId,
                                 long paidAmount, Long orderId) {
        // ========== 0. 前置校验 ==========
        if (config == null || !Boolean.TRUE.equals(config.getTuijianEnabled())) {
            return;
        }
        Integer n = config.getTuijianN();
        if (n == null || n <= 0) {
            return;
        }
        List<BigDecimal> ratios = parseRatios(config.getTuijianRatios(), n);
        if (ratios.isEmpty()) {
            return;
        }
        if (buyerUserId == null || buyerUserId <= 0 || paidAmount <= 0 || orderId == null) {
            return;
        }

        // count 默认为 1，按"按 1 件折算"返奖原则，单件实付价 = paidAmount / count
        // PromoQueueService 接口当前签名只传 paidAmount（已是该商品行总额，含 count）；
        // 这里从 trade item 推断 unitPaid = paidAmount / item.count；调用方 MerchantPromoOrderHandler
        // 会传 item.payPrice 作为 paidAmount，count 通过附带方法获取（见下面重载签名）。
        // 为兼容现有签名，约定 paidAmount 是「订单中该商品行实付总额」，count 默认 1，
        // 即"按行折算"——若一行包含 count 件，则单件 = paidAmount/count（caller 显式传 count）。
        long unitPaid = paidAmount;  // 默认按 1 件处理；caller 重载有传 count 的版本

        handleOrderPaidV7(config, n, ratios, buyerUserId, spuId, paidAmount, unitPaid, orderId);
    }

    /**
     * v7 主流程（单件折算签名）。
     *
     * @param paidAmount 订单中该商品行的实付总额（分）
     * @param unitPaid   单件实付价（分）= paidAmount / count
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleOrderPaid(ProductPromoConfigDO config, Long buyerUserId, Long spuId,
                                 long paidAmount, long unitPaid, Long orderId) {
        if (config == null || !Boolean.TRUE.equals(config.getTuijianEnabled())) {
            return;
        }
        Integer n = config.getTuijianN();
        if (n == null || n <= 0) {
            return;
        }
        List<BigDecimal> ratios = parseRatios(config.getTuijianRatios(), n);
        if (ratios.isEmpty()) {
            return;
        }
        if (buyerUserId == null || buyerUserId <= 0 || paidAmount <= 0 || orderId == null) {
            return;
        }
        if (unitPaid <= 0) {
            unitPaid = paidAmount;
        }
        handleOrderPaidV7(config, n, ratios, buyerUserId, spuId, paidAmount, unitPaid, orderId);
    }

    private void handleOrderPaidV7(ProductPromoConfigDO config, int n, List<BigDecimal> ratios,
                                   Long buyerUserId, Long spuId,
                                   long paidAmount, long unitPaid, Long orderId) {
        // ========== 1. 取 buyer 当前状态 ==========
        ShopQueuePositionDO buyerPos = queueMapper.selectByUserAndSpu(buyerUserId, spuId);

        // ========== 2. 处理 parent（仅当 buyer 不是首单激活时；首单激活不影响 parent 触发逻辑） ==========
        // 注：v7 规则——下级首单（哪怕是 buyer 在自己状态机里的首激活）也算"对 parent 的首贡献"。
        // 所以无论 buyer 是不是首单，parent 都按下面流程判定一次。
        Long parentId = referralService.getDirectParent(buyerUserId);
        if (parentId != null && parentId > 0) {
            handleParentReward(config, n, ratios, parentId, buyerUserId, spuId, paidAmount, unitPaid, orderId);
        } else if (Boolean.TRUE.equals(loadNaturalPushEnabled())) {
            // 真自然用户（无 parent）+ 商户开了自然推开关 → 走旧 A/B 队列三机制（兼容 v6）
            handleNaturalPushLegacy(buyerPos, n, ratios, buyerUserId, spuId, paidAmount, orderId);
            // legacy 路径里已经处理了 buyer 自己进队的逻辑，下面直接 return 不再重复处理 buyer 自购
            return;
        }
        // 自然用户 + 开关 OFF：奖励吞掉（不发给任何人），buyer 自己照常激活下面继续

        // ========== 3. 处理 buyer 自己 ==========
        if (buyerPos == null) {
            // 3a. 首单：激活资格，不返奖；写 ACTIVATE 事件
            ShopQueuePositionDO created = ShopQueuePositionDO.builder()
                    .spuId(spuId)
                    .userId(buyerUserId)
                    .accumulatedCount(0)
                    .accumulatedAmount(0L)
                    .joinedAt(LocalDateTime.now())
                    .state(STATE_IN_PROGRESS)
                    // 兼容字段
                    .status(LEGACY_STATUS_QUEUEING)
                    .layer(LEGACY_LAYER_A)
                    .promotedAt(LocalDateTime.now())
                    .build();
            try {
                queueMapper.insert(created);
                writeEvent(EVT_ACTIVATE, spuId, buyerUserId, buyerUserId, orderId, 0, BigDecimal.ZERO, 0L);
            } catch (DuplicateKeyException dup) {
                // 并发首单：另一事务先写了；本次跳过，不返奖
                log.info("[handleOrderPaidV7] buyer {} spu {} 并发首单 dup，跳过", buyerUserId, spuId);
            }
            return;
        }

        if (STATE_COMPLETED.equals(buyerPos.getState())) {
            // 3c. 已完成：自购按订单实付总额 × directCommissionRatio% 返给自己
            BigDecimal commissionRatio = loadDirectCommissionRatio();
            long award = computeRatioAmount(paidAmount, commissionRatio);
            if (award > 0) {
                promoPointService.addPromoPoint(buyerUserId, award, EVT_SELF_COMMISSION, orderId,
                        "终态自购间推 spu=" + spuId);
                writeEvent(EVT_SELF_COMMISSION, spuId, buyerUserId, buyerUserId, orderId,
                        buyerPos.getAccumulatedCount(), commissionRatio, award);
            }
            return;
        }

        // 3b. IN_PROGRESS：自购累计 +1，按单件实付 × (1/N) 返给自己
        applyProgressAward(buyerPos, n, ratios, buyerUserId, spuId, unitPaid, orderId,
                EVT_SELF_PROGRESS, buyerUserId);
    }

    /**
     * 处理 parent 的首贡献奖励。
     *
     * <p>核心约束：</p>
     * <ul>
     *   <li>parent 必须已激活该商品（state ≠ null）；未激活直接吞奖（不上溯）</li>
     *   <li>(parent, child, spu) UNIQUE：仅触发一次；DuplicateKeyException 兜底</li>
     *   <li>parent.state == IN_PROGRESS：返单件 × (1/N)，cumulated++</li>
     *   <li>parent.state == COMPLETED：返订单总额 × directCommissionRatio%</li>
     * </ul>
     */
    private void handleParentReward(ProductPromoConfigDO config, int n, List<BigDecimal> ratios,
                                    Long parentId, Long childId, Long spuId,
                                    long paidAmount, long unitPaid, Long orderId) {
        ShopQueuePositionDO parentPos = queueMapper.selectByUserAndSpu(parentId, spuId);
        if (parentPos == null) {
            // parent 没买过该商品 → 没资格；吞奖
            log.debug("[handleParentReward] parent {} 未激活 spu {}，跳过", parentId, spuId);
            return;
        }

        // 先查 contribution 是否已存在（同时兜底 DuplicateKey）
        if (contributionMapper.exists(parentId, childId, spuId)) {
            log.debug("[handleParentReward] parent {} child {} spu {} 已贡献过，跳过", parentId, childId, spuId);
            return;
        }

        long award;
        String eventType;
        BigDecimal usedRatio;

        if (STATE_COMPLETED.equals(parentPos.getState())) {
            // 终态：订单实付总额 × directCommissionRatio%
            BigDecimal commissionRatio = loadDirectCommissionRatio();
            award = computeRatioAmount(paidAmount, commissionRatio);
            eventType = EVT_REFERRAL_COMMISSION;
            usedRatio = commissionRatio;
            if (award > 0) {
                promoPointService.addPromoPoint(parentId, award, eventType, orderId,
                        "终态下级首单间推 child=" + childId + " spu=" + spuId);
            }
        } else {
            // IN_PROGRESS：单件实付 × (1/N)，按 parent 当前 cumulated 取下一比例
            int nextIndex = parentPos.getAccumulatedCount() + 1;
            if (nextIndex > n) {
                log.warn("[handleParentReward] parent {} cumulated {} 越界 N={}，跳过", parentId, parentPos.getAccumulatedCount(), n);
                return;
            }
            BigDecimal ratio = ratios.get(nextIndex - 1);
            award = computeRatioAmount(unitPaid, ratio);
            eventType = EVT_REFERRAL_PROGRESS;
            usedRatio = ratio;
            if (award > 0) {
                promoPointService.addPromoPoint(parentId, award, eventType, orderId,
                        "进行期下级首贡献 child=" + childId + " spu=" + spuId + " pos=" + nextIndex);
            }
            // 累计 +1；满 N 转 COMPLETED
            parentPos.setAccumulatedCount(nextIndex);
            parentPos.setAccumulatedAmount(parentPos.getAccumulatedAmount() + award);
            if (nextIndex == n) {
                parentPos.setState(STATE_COMPLETED);
                parentPos.setStatus(LEGACY_STATUS_EXITED);
                parentPos.setExitedAt(LocalDateTime.now());
            }
            queueMapper.updateById(parentPos);
            if (nextIndex == n) {
                writeEvent(EVT_EXIT, spuId, parentId, childId, orderId, n, BigDecimal.ZERO, 0L);
            }
        }

        // 写 contribution 记录（DB UNIQUE 防同一对重复贡献）
        try {
            ShopReferralContributionDO contribution = ShopReferralContributionDO.builder()
                    .parentUserId(parentId)
                    .childUserId(childId)
                    .spuId(spuId)
                    .parentStateAt(parentPos.getState())
                    .awardAmount(award)
                    .sourceOrderId(orderId)
                    .build();
            contributionMapper.insert(contribution);
        } catch (DuplicateKeyException dup) {
            log.warn("[handleParentReward] DB UNIQUE 兜底拦截重复贡献 parent={} child={} spu={}",
                    parentId, childId, spuId);
            // 注意：此时 award 已发出 / parentPos 已 update。理论上 parent_user / parent_state 应回滚；
            // @Transactional rollbackFor=Exception 包含 DuplicateKeyException → 整个方法回滚。
            // 重新抛出让事务回滚。
            throw dup;
        }

        writeEvent(eventType, spuId, parentId, childId, orderId,
                STATE_COMPLETED.equals(parentPos.getState()) ? n : parentPos.getAccumulatedCount(),
                usedRatio, award);
    }

    /**
     * 自购或自购触发的累计 +1（IN_PROGRESS 期）。
     *
     * @param eventType  EVT_SELF_PROGRESS（buyer 自购）
     * @param sourceUserId 来源用户（自购时 = beneficiary）
     */
    private void applyProgressAward(ShopQueuePositionDO pos, int n, List<BigDecimal> ratios,
                                    Long beneficiaryId, Long spuId, long unitPaid, Long orderId,
                                    String eventType, Long sourceUserId) {
        int nextIndex = pos.getAccumulatedCount() + 1;
        if (nextIndex > n) {
            log.warn("[applyProgressAward] beneficiary {} 已超 N={}，跳过", beneficiaryId, n);
            return;
        }
        BigDecimal ratio = ratios.get(nextIndex - 1);
        long award = computeRatioAmount(unitPaid, ratio);
        if (award > 0) {
            promoPointService.addPromoPoint(beneficiaryId, award, eventType, orderId,
                    "进行期自购返奖 spu=" + spuId + " pos=" + nextIndex);
        }
        pos.setAccumulatedCount(nextIndex);
        pos.setAccumulatedAmount(pos.getAccumulatedAmount() + award);
        if (nextIndex == n) {
            pos.setState(STATE_COMPLETED);
            pos.setStatus(LEGACY_STATUS_EXITED);
            pos.setExitedAt(LocalDateTime.now());
        }
        queueMapper.updateById(pos);
        writeEvent(eventType, spuId, beneficiaryId, sourceUserId, orderId, nextIndex, ratio, award);
        if (nextIndex == n) {
            writeEvent(EVT_EXIT, spuId, beneficiaryId, sourceUserId, orderId, n, BigDecimal.ZERO, 0L);
        }
    }

    /**
     * v6 旧 A/B 层自然推机制（仅在 naturalPushEnabled=true + 真自然用户时启用）。
     * 保留旧实现：自然推队首拿奖 + buyer 进 B 层尾。
     */
    private void handleNaturalPushLegacy(ShopQueuePositionDO buyerPos, int n, List<BigDecimal> ratios,
                                         Long buyerUserId, Long spuId, long paidAmount, Long orderId) {
        // 已 EXITED/COMPLETED 不复入
        if (buyerPos != null && (LEGACY_STATUS_EXITED.equals(buyerPos.getStatus())
                || STATE_COMPLETED.equals(buyerPos.getState()))) {
            return;
        }
        boolean buyerInQueueing = buyerPos != null && LEGACY_STATUS_QUEUEING.equals(buyerPos.getStatus());

        if (buyerInQueueing) {
            // SELF_PURCHASE 旧机制：自购升 A 层 + cumulated++
            applyProgressAward(buyerPos, n, ratios, buyerUserId, spuId, paidAmount, orderId,
                    "SELF_PURCHASE", buyerUserId);
        } else {
            // 自然推：找队首返奖；buyer 进 B 层尾
            ShopQueuePositionDO head = queueMapper.selectQueueHead(spuId);
            if (head != null && !head.getUserId().equals(buyerUserId)) {
                applyProgressAward(head, n, ratios, head.getUserId(), spuId, paidAmount, orderId,
                        "QUEUE", buyerUserId);
            }
            if (buyerPos == null) {
                ShopQueuePositionDO newPos = ShopQueuePositionDO.builder()
                        .spuId(spuId)
                        .userId(buyerUserId)
                        .accumulatedCount(0)
                        .accumulatedAmount(0L)
                        .joinedAt(LocalDateTime.now())
                        .state(STATE_IN_PROGRESS)
                        // 旧机制兼容字段
                        .status(LEGACY_STATUS_QUEUEING)
                        .layer("B")
                        .build();
                try {
                    queueMapper.insert(newPos);
                } catch (DuplicateKeyException ignored) {}
            }
        }
    }

    private long computeRatioAmount(long paidAmount, BigDecimal ratioPercent) {
        if (ratioPercent == null || ratioPercent.signum() <= 0 || paidAmount <= 0) {
            return 0L;
        }
        return BigDecimal.valueOf(paidAmount)
                .multiply(ratioPercent)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                .longValueExact();
    }

    private void writeEvent(String eventType, Long spuId, Long beneficiary, Long sourceUser,
                            Long orderId, int positionIndex, BigDecimal ratio, long amount) {
        eventMapper.insert(ShopQueueEventDO.builder()
                .spuId(spuId)
                .eventType(eventType)
                .beneficiaryUserId(beneficiary)
                .sourceUserId(sourceUser)
                .sourceOrderId(orderId)
                .positionIndex(positionIndex)
                .ratioPercent(ratio == null ? BigDecimal.ZERO : ratio)
                .amount(amount)
                .build());
    }

    private BigDecimal loadDirectCommissionRatio() {
        PromoConfigDO config = promoConfigService.getConfig();
        if (config == null || config.getDirectCommissionRatio() == null) {
            return BigDecimal.ZERO;
        }
        return config.getDirectCommissionRatio();
    }

    private Boolean loadNaturalPushEnabled() {
        PromoConfigDO config = promoConfigService.getConfig();
        return config != null && Boolean.TRUE.equals(config.getNaturalPushEnabled());
    }

    @Override
    public List<AppQueuePositionRespVO> listMyQueueing(Long userId) {
        if (userId == null || userId <= 0) {
            return Collections.emptyList();
        }
        // v7：列出 IN_PROGRESS 的所有商品队列；COMPLETED 的不展示
        // 兼容旧数据：state 为 null 时按 status='QUEUEING' fallback
        List<ShopQueuePositionDO> positions = queueMapper.selectListByUserIdQueueing(userId);
        if (positions.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> spuIds = positions.stream()
                .map(ShopQueuePositionDO::getSpuId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, ProductPromoConfigDO> configBySpu = productPromoConfigService.mapBySpuIds(spuIds);
        Map<Long, ProductSpuDO> spuMap = new HashMap<>();
        try {
            List<ProductSpuDO> spus = productSpuService.getSpuList(spuIds);
            if (spus != null) {
                for (ProductSpuDO s : spus) {
                    if (s != null) spuMap.put(s.getId(), s);
                }
            }
        } catch (Exception e) {
            log.warn("[listMyQueueing] 加载 SPU 列表失败 spuIds={}: {}", spuIds, e.getMessage());
        }
        List<AppQueuePositionRespVO> result = new ArrayList<>(positions.size());
        for (ShopQueuePositionDO p : positions) {
            AppQueuePositionRespVO vo = new AppQueuePositionRespVO();
            vo.setTenantId(p.getTenantId());
            vo.setSpuId(p.getSpuId());
            // v7：layer 字段直接传 state 给前端展示进度（前端逻辑改为按 state 渲染）
            vo.setLayer(p.getState() != null ? p.getState() : p.getLayer());
            vo.setAccumulatedCount(p.getAccumulatedCount());
            vo.setAccumulatedAmount(p.getAccumulatedAmount());
            vo.setJoinedAt(p.getJoinedAt());
            vo.setPromotedAt(p.getPromotedAt());
            ProductPromoConfigDO config = configBySpu == null ? null : configBySpu.get(p.getSpuId());
            vo.setMaxN(config == null ? null : config.getTuijianN());
            vo.setRatiosText(config == null ? null : formatRatiosText(config.getTuijianRatios(), config.getTuijianN()));
            ProductSpuDO spu = spuMap.get(p.getSpuId());
            if (spu != null) {
                vo.setSpuName(spu.getName());
                vo.setUnitPrice(spu.getPrice());
            }
            result.add(vo);
        }
        return result;
    }

    /** 把 ratios JSON（"[10,20,70]"）格式化成 "1#10%/2#20%/3#70%" 给前端透传显示。 */
    private String formatRatiosText(String json, Integer n) {
        if (json == null || json.isEmpty() || n == null || n <= 0) return null;
        List<BigDecimal> ratios = parseRatios(json, n);
        if (ratios.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ratios.size(); i++) {
            if (i > 0) sb.append('/');
            BigDecimal r = ratios.get(i);
            String num = (r == null || r.signum() == 0) ? "0" : r.stripTrailingZeros().toPlainString();
            sb.append(i + 1).append('#').append(num).append('%');
        }
        return sb.toString();
    }

    /** 解析 "[25,25,25,25]" → [25,25,25,25]；长度对齐到 n（不足补 0，超出截断） */
    private List<BigDecimal> parseRatios(String json, int n) {
        List<Number> raw;
        try {
            raw = JsonUtils.parseArray(json, Number.class);
        } catch (Exception e) {
            log.warn("[parseRatios] 解析失败 {}: {}", json, e.getMessage());
            return Collections.emptyList();
        }
        if (raw == null) raw = Collections.emptyList();
        List<BigDecimal> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (i < raw.size()) {
                Number num = raw.get(i);
                result.add(num == null ? BigDecimal.ZERO : new BigDecimal(num.toString()));
            } else {
                result.add(BigDecimal.ZERO);
            }
        }
        return result;
    }

}
