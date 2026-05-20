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
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper shopInfoMapper;

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
            // 真自然用户（无 parent）+ 商户开了自然推开关 → 给队首返奖（v6 兼容）
            // 仅"奖励分配"那部分用 legacy；buyer 自己的状态机继续走下面的 v7 主流程
            handleNaturalPushLegacy(buyerPos, n, ratios, buyerUserId, spuId, paidAmount, orderId);
        }
        // 自然用户 + 开关 OFF：奖励吞掉（不发给任何人），buyer 自己照常激活下面继续
        // 自然用户 + 开关 ON：队首拿奖（上面已处理），buyer 自己也继续走 v7 状态机

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
     * v7 改造：仅处理"队首拿奖"那部分；buyer 自己进队/状态机推进交给 v7 主流程。
     */
    private void handleNaturalPushLegacy(ShopQueuePositionDO buyerPos, int n, List<BigDecimal> ratios,
                                         Long buyerUserId, Long spuId, long paidAmount, Long orderId) {
        // buyer 还不在队列（首单激活前）→ 找队首返奖；buyer 自己进队由下面 v7 主流程处理
        if (buyerPos == null) {
            ShopQueuePositionDO head = queueMapper.selectQueueHead(spuId);
            if (head != null && !head.getUserId().equals(buyerUserId)) {
                applyProgressAward(head, n, ratios, head.getUserId(), spuId, paidAmount, orderId,
                        "QUEUE", buyerUserId);
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
        // 一次拉所有 tenant 的 shopName（shop_info 是 BaseDO 跨租户表）
        List<Long> tenantIds = positions.stream().map(ShopQueuePositionDO::getTenantId).distinct().collect(Collectors.toList());
        Map<Long, String> shopNameByTenant = new HashMap<>();
        for (Long tid : tenantIds) {
            try {
                cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO si =
                        cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.executeIgnore(() -> shopInfoMapper.selectByTenantId(tid));
                if (si != null) shopNameByTenant.put(tid, si.getShopName());
            } catch (Exception ignored) { }
        }
        for (ShopQueuePositionDO p : positions) {
            AppQueuePositionRespVO vo = new AppQueuePositionRespVO();
            vo.setTenantId(p.getTenantId());
            vo.setShopName(shopNameByTenant.get(p.getTenantId()));
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

    // ============================================================
    // v8: 多件循环 + 本单抵扣 — 预演 + 真实触发
    // ============================================================

    /**
     * v8 单件状态机一步推进结果：返多少奖、是否进 COMPLETED、用了什么 ratio、什么 eventType。
     * preview / applyBuyerLoopV8 共用此函数避免算法漂移。
     */
    private static final class V8Step {
        long award;
        BigDecimal usedRatio;
        String eventType;
        boolean nextCompleted;
        int nextCumulated;
    }

    /**
     * 推进 buyer 自购状态机一件。
     * <p>state 输入：(cumulated, completed, isFirstItem)；输出 V8Step 含奖额 + 新状态。
     * 调用方负责把 V8Step.next* 写回 pos / 累加 produced。</p>
     */
    private V8Step simulateOneItem(int cumulated, boolean completed, boolean isFirstItem,
                                   int unitPrice, List<BigDecimal> ratios, BigDecimal directRate, int n) {
        V8Step step = new V8Step();
        step.nextCumulated = cumulated;
        step.nextCompleted = completed;
        if (isFirstItem) {
            // 第 1 件 ACTIVATE 不返奖
            step.award = 0L;
            step.usedRatio = BigDecimal.ZERO;
            step.eventType = "ACTIVATE";
            return step;
        }
        if (completed) {
            step.award = computeRatioAmount(unitPrice, directRate);
            step.usedRatio = directRate;
            step.eventType = "SELF_COMMISSION";
            return step;
        }
        if (cumulated >= n) {
            step.nextCompleted = true;
            step.award = computeRatioAmount(unitPrice, directRate);
            step.usedRatio = directRate;
            step.eventType = "SELF_COMMISSION";
            return step;
        }
        BigDecimal r = ratios.get(cumulated);
        step.award = computeRatioAmount(unitPrice, r);
        step.usedRatio = r;
        step.eventType = "SELF_PROGRESS";
        step.nextCumulated = cumulated + 1;
        if (step.nextCumulated >= n) {
            step.nextCompleted = true;
        }
        return step;
    }

    @Override
    public long previewProducedForOrder(ProductPromoConfigDO config, Long buyerUserId, Long spuId,
                                        int unitPrice, int totalCount) {
        if (config == null || !Boolean.TRUE.equals(config.getTuijianEnabled())) return 0L;
        if (buyerUserId == null || buyerUserId <= 0 || unitPrice <= 0 || totalCount <= 0) return 0L;
        Integer nObj = config.getTuijianN();
        if (nObj == null || nObj <= 0) return 0L;
        int n = nObj;
        List<BigDecimal> ratios = parseRatios(config.getTuijianRatios(), n);
        if (ratios.isEmpty()) return 0L;

        // 优先用商品级 directRate；缺省回退商户级（兼容老配置）
        BigDecimal directRate = config.getDirectRate();
        if (directRate == null || directRate.signum() <= 0) {
            directRate = loadDirectCommissionRatio();
        }

        // 读 buyer 当前状态机
        ShopQueuePositionDO buyerPos = queueMapper.selectByUserAndSpu(buyerUserId, spuId);
        boolean isFirstPurchase = (buyerPos == null);
        int cumulated = buyerPos == null ? 0 : (buyerPos.getAccumulatedCount() == null ? 0 : buyerPos.getAccumulatedCount());
        boolean completed = buyerPos != null && STATE_COMPLETED.equals(buyerPos.getState());

        // 精度修复：单件 round_down(unitPrice × ratio / 100) 会丢 < 1 分的余数；
        // 多件累加后差额放大（例：unitPrice=10 + ratios=[25,25,25,25]，4 件本应 100%=10 分，
        // 但每件 round_down(2.5)=2，累加 8 分，导致"推 4 反 1"要买 7 件才够抵扣 1 件）。
        // 改为：累加 ratio，最后一次 round_down(unitPrice × accRatio / 100) — 推 4 反 1 买 5 件即 1 件免单。
        BigDecimal accRatio = BigDecimal.ZERO;
        for (int i = 0; i < totalCount; i++) {
            boolean isFirst = isFirstPurchase && i == 0;
            V8Step step = simulateOneItem(cumulated, completed, isFirst, unitPrice, ratios, directRate, n);
            if (step.usedRatio != null) {
                accRatio = accRatio.add(step.usedRatio);
            }
            cumulated = step.nextCumulated;
            completed = step.nextCompleted;
        }
        long total = BigDecimal.valueOf(unitPrice)
                .multiply(accRatio)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                .longValueExact();
        return total;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleOrderPaidV8(ProductPromoConfigDO config, Long buyerUserId, Long spuId,
                                  int unitPrice, int totalCount, int deductCount, Long orderId) {
        if (config == null || !Boolean.TRUE.equals(config.getTuijianEnabled())) return;
        if (buyerUserId == null || buyerUserId <= 0 || unitPrice <= 0 || totalCount <= 0 || orderId == null) return;
        Integer nObj = config.getTuijianN();
        if (nObj == null || nObj <= 0) return;
        int n = nObj;
        List<BigDecimal> ratios = parseRatios(config.getTuijianRatios(), n);
        if (ratios.isEmpty()) return;

        // === 幂等：同 (orderId, userId, spuId) 已处理过 → 整个 handleOrderPaidV8 跳过 ===
        // 防止：重复 confirm 同一订单导致 shop_queue_position.cumulated 被多次推进、
        //       shop_queue_event 多写、但 shop_promo_record 因 (user,source_type,source_id) 三元组
        //       唯一只首次入账 → 状态机被过度推进，后续订单按错误起点结算（实战 case：
        //       order 10006 confirm 多次 → cumulated 推到 3+ → order 10007 该按 ratios[2]+directRate
        //       结算却按 directRate×2 结算，少返 80 分）。
        // 用 shop_promo_deduction_record（每订单 spu 行一条）做幂等标记 —— 哪怕 produced=0
        // 也会写 deduction record（line ~660），比 promo_record 覆盖率好。
        if (deductionRecordMapper.existsByOrderUserSpu(orderId, buyerUserId, spuId)) {
            log.info("[handleOrderPaidV8] 幂等命中 orderId={} buyer={} spu={}，跳过状态机推进", orderId, buyerUserId, spuId);
            return;
        }

        BigDecimal directRate = config.getDirectRate();
        if (directRate == null || directRate.signum() <= 0) {
            directRate = loadDirectCommissionRatio();
        }
        long unitPriceLong = unitPrice;
        long paidAmount = (long) (totalCount - deductCount) * unitPriceLong;  // 实付总额（抵扣后）

        // 1. parent 维度：buyer 首单触发一次首贡献奖（v8: 1 件价封顶）
        Long parentId = referralService.getDirectParent(buyerUserId);
        if (parentId != null && parentId > 0) {
            handleParentRewardV8(parentId, buyerUserId, spuId, unitPrice, ratios, directRate, orderId);
        } else if (Boolean.TRUE.equals(loadNaturalPushEnabled())) {
            // 自然推队首：仅在 buyer 首件触发（buyerPos 之前不存在）
            ShopQueuePositionDO existing = queueMapper.selectByUserAndSpu(buyerUserId, spuId);
            if (existing == null) {
                handleNaturalPushV8(buyerUserId, spuId, unitPrice, ratios, orderId);
            }
        }

        // 2. buyer 自购：按件循环推进状态机
        long produced = applyBuyerLoopV8(buyerUserId, spuId, unitPrice, totalCount, ratios, directRate, orderId, n);

        // 2.1 入余额：full produced（流水透明）
        if (produced > 0) {
            promoPointService.addPromoPoint(buyerUserId, produced, "SELF_BATCH", orderId,
                    "v8 多件订单产生积分 spu=" + spuId + " count=" + totalCount + " deduct=" + deductCount);
        }
        // 2.2 反向扣回：deductCount × unitPrice 部分已在 checkout 折抵给用户（少付价款），
        //     必须从余额扣回，否则用户既享了折扣又拿了积分 = 双倍。
        //     用独立 sourceType=ORDER_DEDUCT 走 deductPromoPoint，与 SELF_BATCH 同 sourceId 不冲突，
        //     流水可见正负两条 + 净余额 = produced - deductCount × unitPrice。
        long autoDeduct = Math.min((long) deductCount * unitPrice, produced);
        if (autoDeduct > 0) {
            promoPointService.deductPromoPoint(buyerUserId, autoDeduct, "ORDER_DEDUCT", orderId,
                    "v8 下单已抵扣 " + deductCount + " 件价款 spu=" + spuId);
        }

        // 3. 抵扣流水写入（审计 / 对账）
        // 注意：流水写失败不能让 buyer 状态机推进事务回滚（推进已经发奖了），
        //      只能 log error 留痕；监控告警靠 metric / 人工对账修补。
        try {
            cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoDeductionRecordDO rec =
                    cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoDeductionRecordDO.builder()
                            .orderId(orderId)
                            .orderItemId(0L)
                            .userId(buyerUserId)
                            .spuId(spuId)
                            .unitPrice(unitPrice)
                            .totalCount(totalCount)
                            .producedAmount(produced)
                            .deductCount(deductCount)
                            .actualPaid((int) paidAmount)
                            .build();
            deductionRecordMapper.insert(rec);
        } catch (Exception e) {
            log.error("[handleOrderPaidV8 流水缺失] orderId={} spuId={} userId={} produced={} deductCount={} actualPaid={}",
                    orderId, spuId, buyerUserId, produced, deductCount, paidAmount, e);
        }
    }

    /** v8: parent 首贡献按 1 件价封顶 */
    private void handleParentRewardV8(Long parentId, Long childId, Long spuId, int unitPrice,
                                       List<BigDecimal> ratios, BigDecimal directRate, Long orderId) {
        ShopQueuePositionDO parentPos = queueMapper.selectByUserAndSpu(parentId, spuId);
        if (parentPos == null) {
            log.debug("[v8 parentReward] parent {} 未激活 spu {}，跳过", parentId, spuId);
            return;
        }
        if (contributionMapper.exists(parentId, childId, spuId)) {
            log.debug("[v8 parentReward] (parent={}, child={}, spu={}) 已贡献过，跳过", parentId, childId, spuId);
            return;
        }
        long award;
        String eventType;
        BigDecimal usedRatio;
        if (STATE_COMPLETED.equals(parentPos.getState())) {
            award = computeRatioAmount(unitPrice, directRate);
            usedRatio = directRate;
            eventType = "REFERRAL_COMMISSION";
        } else {
            int idx = parentPos.getAccumulatedCount() == null ? 0 : parentPos.getAccumulatedCount();
            if (idx >= ratios.size()) idx = ratios.size() - 1;
            BigDecimal r = ratios.get(idx);
            award = computeRatioAmount(unitPrice, r);
            usedRatio = r;
            eventType = "REFERRAL_PROGRESS";
            // parent IN_PROGRESS 拿奖时也累加 cumulated（首贡献推进 parent 状态机）
            parentPos.setAccumulatedCount(idx + 1);
            parentPos.setAccumulatedAmount((parentPos.getAccumulatedAmount() == null ? 0L : parentPos.getAccumulatedAmount()) + award);
            if (parentPos.getAccumulatedCount() >= ratios.size()) {
                parentPos.setState(STATE_COMPLETED);
                parentPos.setStatus(LEGACY_STATUS_EXITED);
                parentPos.setExitedAt(LocalDateTime.now());
            }
            queueMapper.updateById(parentPos);
        }
        if (award > 0) {
            promoPointService.addPromoPoint(parentId, award, eventType, orderId,
                    "v8 下级首贡献 child=" + childId + " spu=" + spuId);
        }
        try {
            cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopReferralContributionDO contrib =
                    cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopReferralContributionDO.builder()
                            .parentUserId(parentId).childUserId(childId).spuId(spuId)
                            .parentStateAt(parentPos.getState()).awardAmount(award).sourceOrderId(orderId)
                            .build();
            contributionMapper.insert(contrib);
        } catch (org.springframework.dao.DuplicateKeyException ignored) {
            log.warn("[v8 parentReward] DB UNIQUE 兜底拦截重复贡献 parent={} child={} spu={}", parentId, childId, spuId);
        }
        writeEvent(eventType, spuId, parentId, childId, orderId,
                parentPos.getAccumulatedCount() == null ? 0 : parentPos.getAccumulatedCount(),
                usedRatio, award);
    }

    /** v8: 自然推队首拿 1 件价 × ratios[head.cumulated] */
    private void handleNaturalPushV8(Long buyerUserId, Long spuId, int unitPrice,
                                      List<BigDecimal> ratios, Long orderId) {
        // 自然队列语义：新买家奖前一个（最近一个 QUEUEING 自然用户），逐级推进。
        // 不能用 selectQueueHead（最早入队）—— 那样第一个用户永远拿奖直到累满 N，新买家从未拿过奖。
        ShopQueuePositionDO head = queueMapper.selectQueueLatest(spuId);
        if (head == null || head.getUserId().equals(buyerUserId)) return;
        int idx = head.getAccumulatedCount() == null ? 0 : head.getAccumulatedCount();
        if (idx >= ratios.size()) return;  // head 已超 N
        BigDecimal r = ratios.get(idx);
        long award = computeRatioAmount(unitPrice, r);
        if (award > 0) {
            promoPointService.addPromoPoint(head.getUserId(), award, "QUEUE", orderId,
                    "v8 自然推队首奖 spu=" + spuId + " by=" + buyerUserId);
        }
        head.setAccumulatedCount(idx + 1);
        head.setAccumulatedAmount((head.getAccumulatedAmount() == null ? 0L : head.getAccumulatedAmount()) + award);
        if (head.getAccumulatedCount() >= ratios.size()) {
            head.setState(STATE_COMPLETED);
            head.setStatus(LEGACY_STATUS_EXITED);
            head.setExitedAt(LocalDateTime.now());
        }
        queueMapper.updateById(head);
        writeEvent("QUEUE", spuId, head.getUserId(), buyerUserId, orderId, head.getAccumulatedCount(), r, award);
    }

    /** v8: buyer 自购按件循环推进状态机；返本单产生积分总数。状态机与 previewProducedForOrder 共用 simulateOneItem。 */
    private long applyBuyerLoopV8(Long buyerUserId, Long spuId, int unitPrice, int totalCount,
                                   List<BigDecimal> ratios, BigDecimal directRate, Long orderId, int n) {
        ShopQueuePositionDO pos = queueMapper.selectByUserAndSpu(buyerUserId, spuId);
        boolean isFirstPurchase = (pos == null);
        if (isFirstPurchase) {
            pos = ShopQueuePositionDO.builder()
                    .spuId(spuId).userId(buyerUserId)
                    .accumulatedCount(0).accumulatedAmount(0L)
                    .joinedAt(LocalDateTime.now())
                    .state(STATE_IN_PROGRESS)
                    .status(LEGACY_STATUS_QUEUEING)
                    .layer(LEGACY_LAYER_A)
                    .promotedAt(LocalDateTime.now())
                    .build();
            try {
                queueMapper.insert(pos);
            } catch (org.springframework.dao.DuplicateKeyException dup) {
                pos = queueMapper.selectByUserAndSpu(buyerUserId, spuId);
                isFirstPurchase = false;
            }
            writeEvent("ACTIVATE", spuId, buyerUserId, buyerUserId, orderId, 0, BigDecimal.ZERO, 0L);
        }

        long produced = 0L;
        boolean exitedThisCall = false;
        for (int i = 0; i < totalCount; i++) {
            int cumulated = pos.getAccumulatedCount() == null ? 0 : pos.getAccumulatedCount();
            boolean completed = STATE_COMPLETED.equals(pos.getState());
            boolean isFirst = isFirstPurchase && i == 0;
            V8Step step = simulateOneItem(cumulated, completed, isFirst, unitPrice, ratios, directRate, n);
            if (isFirst) {
                continue; // ACTIVATE 件已在 insert 时写过事件，不返奖也不动 cumulated
            }
            produced += step.award;
            // 仅 IN_PROGRESS 期推进 cumulated；进入 COMPLETED 后不再动
            if (step.nextCumulated != cumulated) {
                pos.setAccumulatedCount(step.nextCumulated);
                pos.setAccumulatedAmount((pos.getAccumulatedAmount() == null ? 0L : pos.getAccumulatedAmount()) + step.award);
            }
            if (step.nextCompleted && !STATE_COMPLETED.equals(pos.getState())) {
                pos.setState(STATE_COMPLETED);
                pos.setStatus(LEGACY_STATUS_EXITED);
                pos.setExitedAt(LocalDateTime.now());
                if (!exitedThisCall) {
                    writeEvent("EXIT", spuId, buyerUserId, buyerUserId, orderId, n, BigDecimal.ZERO, 0L);
                    exitedThisCall = true;
                }
            }
            writeEvent(step.eventType, spuId, buyerUserId, buyerUserId, orderId,
                    pos.getAccumulatedCount() == null ? 0 : pos.getAccumulatedCount(),
                    step.usedRatio, step.award);
        }
        // 一次 update 落库（避免每件循环都 update）
        queueMapper.updateById(pos);
        return produced;
    }

    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoDeductionRecordMapper deductionRecordMapper;

}
