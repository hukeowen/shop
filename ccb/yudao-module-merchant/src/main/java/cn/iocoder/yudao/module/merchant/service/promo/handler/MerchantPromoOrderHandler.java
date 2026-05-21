package cn.iocoder.yudao.module.merchant.service.promo.handler;

import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueueEventDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopQueueEventMapper;
import cn.iocoder.yudao.module.merchant.service.promo.CommissionService;
import cn.iocoder.yudao.module.merchant.service.promo.ProductPromoConfigService;
import cn.iocoder.yudao.module.merchant.service.promo.PromoPointService;
import cn.iocoder.yudao.module.merchant.service.promo.PromoPoolService;
import cn.iocoder.yudao.module.merchant.service.promo.PromoQueueService;
import cn.iocoder.yudao.module.merchant.service.promo.StarService;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.service.order.handler.TradeOrderHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 营销引擎订单钩子。
 *
 * 在 trade 模块支付成功后被自动调用（Spring 注入 List<TradeOrderHandler> 时按 bean 收集）。
 * 按 v6 文档第四节顺序结算：
 *   1. 消费积分入账（按商品配置）
 *   2. 直推 / 队列 / 自然推（按商品配置 tuijianEnabled）
 *   3. 团队极差递减（按商户配置 commissionRates）
 *   4. 入星级积分池（按商品 + 商户 poolEnabled）
 *   5. 星级回算（团队链路销售份数 + 上级链路）
 *
 * 每个 step 内部都做了幂等：(userId, sourceType, orderId) 唯一键。
 * 任何 step 失败都会回滚自身事务，但**不影响**订单本身的支付成功状态——
 * 订单已落库标 PAID，引擎是 best-effort 的事后流水机器。
 */
@Component
@Slf4j
public class MerchantPromoOrderHandler implements TradeOrderHandler {

    @Resource
    private ProductPromoConfigService productPromoConfigService;
    @Resource
    private PromoPointService promoPointService;
    @Resource
    private PromoQueueService promoQueueService;
    @Resource
    private CommissionService commissionService;
    @Resource
    private PromoPoolService promoPoolService;
    @Resource
    private StarService starService;
    @Resource
    private cn.iocoder.yudao.module.merchant.service.MemberShopRelService memberShopRelService;
    @Resource
    private cn.iocoder.yudao.module.merchant.service.promo.ReferralService referralService;
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper shopInfoMapper;
    @Resource
    private ShopQueueEventMapper queueEventMapper;

    @Override
    public void afterPayOrder(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        if (order == null || orderItems == null || orderItems.isEmpty()) {
            return;
        }
        Long buyerId = order.getUserId();
        Long orderId = order.getId();
        Long tenantId = order.getTenantId();
        if (buyerId == null || orderId == null) {
            return;
        }

        // 自动入店：用户首次在该店下单时建立 member_shop_rel，让会员列表 / 资产 / 邀请关系
        // 三表语义一致；referrer 沿当前推荐链头部（自然用户 / 邀请进入都兼容）
        if (tenantId != null && tenantId > 0) {
            try {
                Long parentId = referralService.getDirectParent(buyerId);
                memberShopRelService.getOrCreateWithReferrer(buyerId, tenantId, parentId == null || parentId <= 0 ? null : parentId);
            } catch (Exception e) {
                log.warn("[afterPayOrder] 自动入店失败 buyer={} tenant={}: {}", buyerId, tenantId, e.getMessage());
            }
        }

        // v8: 按 SPU 行循环触发 — 推 N 反 1 / 极差 / 升星 / 入池都按 SPU 独立
        for (TradeOrderItemDO item : orderItems) {
            try {
                processOneItem(buyerId, orderId, item);
            } catch (Exception e) {
                log.error("[afterPayOrder] order={} spu={} 引擎处理失败，跳过本行",
                        orderId, item.getSpuId(), e);
            }
        }

        // 累加店铺销量（shop_info.sales_30d）— 简化为累计销量，
        // 真"近 30 天"由后续定时 job 重算覆盖（local profile 无 quartz 时即为累计值）
        if (tenantId != null && tenantId > 0) {
            try {
                int totalQty = 0;
                for (TradeOrderItemDO item : orderItems) {
                    if (item.getCount() != null) totalQty += item.getCount();
                }
                if (totalQty > 0) {
                    shopInfoMapper.incrementSales30d(tenantId, totalQty);
                }
            } catch (Exception e) {
                log.warn("[afterPayOrder] 累加店铺销量失败 tenant={}: {}", tenantId, e.getMessage());
            }
        }
    }

    private void processOneItem(Long buyerId, Long orderId, TradeOrderItemDO item) {
        Long spuId = item.getSpuId();
        Integer countObj = item.getCount();
        int qty = (countObj == null) ? 1 : countObj;
        long paidAmount = item.getPayPrice() == null ? 0L : item.getPayPrice().longValue();
        if (spuId == null || paidAmount <= 0) {
            return;
        }

        // ===== 整单幂等：防止双调（offline-confirm 同步 tradeOrderHandlers + 异步 OrderPaidListener
        //                 都会跑一次 afterPayOrder，若不幂等极差/升星会累加双倍） =====
        if (queueEventMapper.existsHandlerDoneV8(orderId, spuId)) {
            log.info("[afterPayOrder] 幂等命中 order={} spu={}，跳过整个 SPU 行营销", orderId, spuId);
            return;
        }

        ProductPromoConfigDO config = productPromoConfigService.getBySpuId(spuId);

        // 1. 消费积分入账
        if (config != null && config.getConsumePointRatio() != null
                && config.getConsumePointRatio().compareTo(BigDecimal.ZERO) > 0) {
            // 实付 paidAmount 单位是分；先 ÷100 转元，再乘 ratio 得"消费积分（分）"
            long consumePoints = BigDecimal.valueOf(paidAmount)
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.DOWN)
                    .multiply(config.getConsumePointRatio())
                    .setScale(0, RoundingMode.DOWN)
                    .longValueExact();
            if (consumePoints > 0) {
                promoPointService.addConsumePoint(buyerId, consumePoints, "CONSUME",
                        orderId, "下单返消费积分 spu=" + spuId);
            }
        }

        // 2. v8 推 N 反 1 状态机（按件循环）+ parent 首贡献按 1 件价 + 自然推队首
        if (config != null && Boolean.TRUE.equals(config.getTuijianEnabled())) {
            int unitPrice = item.getPrice() == null ? 0 : item.getPrice();
            if (unitPrice > 0 && qty > 0) {
                long produced = promoQueueService.previewProducedForOrder(config, buyerId, spuId, unitPrice, qty);
                int k = (int) (produced / unitPrice);
                if (k > qty) k = qty;
                promoQueueService.handleOrderPaidV8(config, buyerId, spuId, unitPrice, qty, k, orderId);
            }
        }

        // 3. v8 团队极差奖（按 SPU 独立 + 沿链就近递增）
        if (config != null) {
            try {
                commissionService.handleOrderPaidV8(config, buyerId, spuId, paidAmount, orderId);
            } catch (Exception e) {
                log.error("[afterPayOrder v8] 极差奖失败 order={} spu={}", orderId, spuId, e);
            }
        }

        // 4. v8 入池（按商品级 pool_ratio）
        if (config != null) {
            try {
                promoPoolService.depositIfEnabledV8(config, spuId, paidAmount, orderId);
            } catch (Exception e) {
                log.error("[afterPayOrder v8] 入池失败 order={} spu={}", orderId, spuId, e);
            }
        }

        // 5. v8 升星（按 SPU 独立累加 directCount / teamSalesAmount + attemptUpgrade）
        if (config != null && Boolean.TRUE.equals(config.getTuijianEnabled())) {
            try {
                starService.handleOrderPaidV8(config, buyerId, spuId, qty, paidAmount);
            } catch (Exception e) {
                log.error("[afterPayOrder v8] 升星失败 order={} spu={}", orderId, spuId, e);
            }
        }

        // ===== 写完所有 SPU 行处理 → 留 marker，让重入调用整段 skip =====
        try {
            queueEventMapper.insert(ShopQueueEventDO.builder()
                    .spuId(spuId)
                    .eventType("HANDLER_DONE_V8")
                    .beneficiaryUserId(buyerId)
                    .sourceUserId(buyerId)
                    .sourceOrderId(orderId)
                    .positionIndex(0)
                    .ratioPercent(java.math.BigDecimal.ZERO)
                    .amount(0L)
                    .build());
        } catch (Exception e) {
            log.warn("[afterPayOrder] 写 HANDLER_DONE_V8 marker 失败 order={} spu={}: {}",
                    orderId, spuId, e.getMessage());
        }
    }

}
