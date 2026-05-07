package cn.iocoder.yudao.module.merchant.service.promo.handler;

import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;
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

    @Override
    public void afterPayOrder(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        if (order == null || orderItems == null || orderItems.isEmpty()) {
            return;
        }
        Long buyerId = order.getUserId();
        Long orderId = order.getId();
        if (buyerId == null || orderId == null) {
            return;
        }

        // 1~2、4~5：消费积分 / 队列三机制 / 入池 / 星级 — 都是按 SPU 行触发，因此放进循环
        long totalPaidAmount = 0L;
        for (TradeOrderItemDO item : orderItems) {
            try {
                processOneItem(buyerId, orderId, item);
                if (item.getPayPrice() != null && item.getPayPrice() > 0) {
                    totalPaidAmount += item.getPayPrice();
                }
            } catch (Exception e) {
                // 单 item 异常不阻断同单其它 item 的引擎处理；订单状态已标 PAID
                log.error("[afterPayOrder] order={} spu={} 引擎处理失败，跳过本行",
                        orderId, item.getSpuId(), e);
            }
        }

        // 3、团队极差：v6 文档明确"每笔订单触发一次"——必须用订单总额、且与 item 循环外调用，
        //    否则同一 (userId, COMMISSION, orderId) 幂等键会让后续 item 静默 skip，
        //    结果只算了第 1 个 item 的金额（已知 bug 已修）。
        if (totalPaidAmount > 0) {
            try {
                commissionService.handleOrderPaid(buyerId, totalPaidAmount, orderId);
            } catch (Exception e) {
                log.error("[afterPayOrder] order={} 团队极差结算失败", orderId, e);
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

        // 2. v7 推 N 反 1 状态机（替代 v6 三机制；自然推开关保留兜底）
        // 单件实付价 = 行总额 / 件数（按"次"不按"量"返奖原则）
        if (config != null) {
            long unitPaid = qty > 0 ? paidAmount / qty : paidAmount;
            promoQueueService.handleOrderPaid(config, buyerId, spuId, paidAmount, unitPaid, orderId);
        }

        // （3 团队极差移到 afterPayOrder 末尾按订单总额触发一次）

        // 4. 入池（商品 + 商户都 enabled 才进）
        promoPoolService.depositIfEnabled(config, paidAmount, orderId);

        // 5. 星级回算（仅参与推 N 反 1 商品份数计入团队链路销售份数）
        boolean countable = config != null && Boolean.TRUE.equals(config.getTuijianEnabled());
        starService.handleOrderPaid(buyerId, qty, countable);
    }

}
