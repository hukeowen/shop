package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppQueuePositionRespVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueueEventDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueuePositionDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopQueueEventMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopQueuePositionMapper;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import lombok.extern.slf4j.Slf4j;
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
 * 队列三机制实现。
 *
 * 一笔订单内"返谁、返多少、是否出队、是否升层"全部在一次事务内完成。
 * 防重靠 PromoPointService 内的 (sourceType, sourceId) 三元组幂等机制，
 * 这里 sourceId 用 orderId（同一订单同一 SPU 行只触发一次）。
 */
@Service
@Slf4j
public class PromoQueueServiceImpl implements PromoQueueService {

    private static final String LAYER_A = "A";
    private static final String LAYER_B = "B";
    private static final String STATUS_QUEUEING = "QUEUEING";
    private static final String STATUS_EXITED = "EXITED";

    @Resource
    private ShopQueuePositionMapper queueMapper;
    @Resource
    private ShopQueueEventMapper eventMapper;
    @Resource
    private ReferralService referralService;
    @Resource
    private PromoPointService promoPointService;
    @Resource
    private ProductPromoConfigService productPromoConfigService;
    @Resource
    private ProductSpuService productSpuService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleOrderPaid(ProductPromoConfigDO config, Long buyerUserId, Long spuId,
                                 long paidAmount, Long orderId) {
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

        Long parentId = referralService.getDirectParent(buyerUserId);
        if (parentId != null && parentId > 0) {
            // 1. 直推：上级拿奖 + 升 A 层（无 A 层则建）
            awardAtNextPosition("DIRECT", parentId, buyerUserId, spuId, paidAmount,
                    ratios, n, orderId, /* enterAsA */ true);
        } else {
            // 2/3. 自然用户路径
            ShopQueuePositionDO buyerPos = queueMapper.selectByUserAndSpu(buyerUserId, spuId);
            boolean buyerInQueueing = buyerPos != null && STATUS_QUEUEING.equals(buyerPos.getStatus());

            if (buyerInQueueing) {
                // 2. 插队（SELF_PURCHASE）：自己拿奖 + B → A
                awardAtNextPosition("SELF_PURCHASE", buyerUserId, buyerUserId, spuId,
                        paidAmount, ratios, n, orderId, /* enterAsA */ true);
            } else {
                // 3. 自然推（QUEUE）：找队首返奖；买家进 B 层尾
                ShopQueuePositionDO head = queueMapper.selectQueueHead(spuId);
                if (head != null && !head.getUserId().equals(buyerUserId)) {
                    awardAtNextPosition("QUEUE", head.getUserId(), buyerUserId, spuId,
                            paidAmount, ratios, n, orderId, /* enterAsA */ false);
                }
                // 买家进 B 层（仅在没出队过 / 没记录时新建）
                if (buyerPos == null) {
                    ShopQueuePositionDO newPos = ShopQueuePositionDO.builder()
                            .spuId(spuId)
                            .userId(buyerUserId)
                            .layer(LAYER_B)
                            .accumulatedCount(0)
                            .accumulatedAmount(0L)
                            .joinedAt(LocalDateTime.now())
                            .status(STATUS_QUEUEING)
                            .build();
                    queueMapper.insert(newPos);
                }
                // 已 EXITED 的不再回队，永久出队
            }
        }
    }

    /**
     * 给受益人返奖，按其当前累计计算位置和比例；
     * 若没有队列位置则新建（DIRECT / SELF_PURCHASE 直接进 A 层）。
     *
     * 幂等：通过 queue_event 上 (sourceOrderId, spuId, beneficiaryId, eventType) 联合判重。
     * 重放 / 多 SKU 同 SPU 等场景下绝不会重复推进 accumulated_count、绝不会重复返奖。
     */
    private void awardAtNextPosition(String eventType, Long beneficiaryId, Long sourceUserId,
                                      Long spuId, long paidAmount, List<BigDecimal> ratios,
                                      int n, Long orderId, boolean enterAsA) {
        if (eventMapper.existsByOrderAndBeneficiary(orderId, spuId, beneficiaryId, eventType)) {
            log.debug("[awardAtNextPosition] 幂等命中 order={} spu={} beneficiary={} type={}",
                    orderId, spuId, beneficiaryId, eventType);
            return;
        }
        ShopQueuePositionDO pos = queueMapper.selectByUserAndSpu(beneficiaryId, spuId);
        if (pos == null) {
            // 受益人初次进队（DIRECT / SELF_PURCHASE 都立即进 A）
            pos = ShopQueuePositionDO.builder()
                    .spuId(spuId)
                    .userId(beneficiaryId)
                    .layer(enterAsA ? LAYER_A : LAYER_B)
                    .accumulatedCount(0)
                    .accumulatedAmount(0L)
                    .joinedAt(LocalDateTime.now())
                    .promotedAt(enterAsA ? LocalDateTime.now() : null)
                    .status(STATUS_QUEUEING)
                    .build();
            queueMapper.insert(pos);
        } else if (STATUS_EXITED.equals(pos.getStatus())) {
            log.warn("[awardAtNextPosition] 受益人 {} spu {} 已出队，跳过", beneficiaryId, spuId);
            return;
        } else if (enterAsA && LAYER_B.equals(pos.getLayer())) {
            // 在 B 层 → 升 A
            pos.setLayer(LAYER_A);
            pos.setPromotedAt(LocalDateTime.now());
        }

        int nextIndex = pos.getAccumulatedCount() + 1;
        if (nextIndex > n) {
            // 理论上不应该（满 N 应已 EXITED）；防御性兜底
            log.warn("[awardAtNextPosition] 受益人 {} 已超过 N={}，跳过", beneficiaryId, n);
            return;
        }
        BigDecimal ratio = ratios.get(nextIndex - 1);
        long amount = BigDecimal.valueOf(paidAmount)
                .multiply(ratio)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                .longValueExact();
        if (amount > 0) {
            promoPointService.addPromoPoint(beneficiaryId, amount, eventType, orderId,
                    "队列返奖 spu=" + spuId + " pos=" + nextIndex);
        }

        pos.setAccumulatedCount(nextIndex);
        pos.setAccumulatedAmount(pos.getAccumulatedAmount() + amount);
        boolean exited = nextIndex == n;
        if (exited) {
            pos.setStatus(STATUS_EXITED);
            pos.setExitedAt(LocalDateTime.now());
        }
        queueMapper.updateById(pos);

        // 写返奖事件
        eventMapper.insert(ShopQueueEventDO.builder()
                .spuId(spuId)
                .eventType(eventType)
                .beneficiaryUserId(beneficiaryId)
                .sourceUserId(sourceUserId)
                .sourceOrderId(orderId)
                .positionIndex(nextIndex)
                .ratioPercent(ratio)
                .amount(amount)
                .build());
        // 写出队事件
        if (exited) {
            eventMapper.insert(ShopQueueEventDO.builder()
                    .spuId(spuId)
                    .eventType("EXIT")
                    .beneficiaryUserId(beneficiaryId)
                    .sourceUserId(sourceUserId)
                    .sourceOrderId(orderId)
                    .positionIndex(n)
                    .ratioPercent(BigDecimal.ZERO)
                    .amount(0L)
                    .build());
        }
    }

    @Override
    public List<AppQueuePositionRespVO> listMyQueueing(Long userId) {
        if (userId == null || userId <= 0) {
            return Collections.emptyList();
        }
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
            vo.setLayer(p.getLayer());
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
            // signum()==0 时 stripTrailingZeros 在某些 JDK 8u 早期版本会返 "0E-1"，显式短路
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
            return java.util.Collections.emptyList();
        }
        if (raw == null) raw = java.util.Collections.emptyList();
        java.util.List<BigDecimal> result = new java.util.ArrayList<>(n);
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
