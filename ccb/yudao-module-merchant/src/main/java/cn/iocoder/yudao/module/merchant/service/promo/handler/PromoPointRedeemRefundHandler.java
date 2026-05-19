package cn.iocoder.yudao.module.merchant.service.promo.handler;

import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoRecordDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoRecordMapper;
import cn.iocoder.yudao.module.merchant.service.promo.PromoPointService;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.service.order.handler.TradeOrderHandler;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * 订单取消时退还推广积分抵扣。
 *
 * <p>checkout submit 阶段 deductPromoPoint(REDEEM_ORDER, orderId) 已扣减用户余额并写一条负值流水；
 * 若订单后续被取消，addPromoPoint(REDEEM_REFUND, orderId) 把同等金额退回。</p>
 *
 * <p>幂等：addPromoPoint / deductPromoPoint 内部按 (userId, sourceType, sourceId) 去重；
 * 重复触发不会双倍退。</p>
 */
@Component
@Slf4j
public class PromoPointRedeemRefundHandler implements TradeOrderHandler {

    @Resource
    private ShopPromoRecordMapper promoRecordMapper;
    @Resource
    private PromoPointService promoPointService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void afterCancelOrder(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        if (order == null || order.getId() == null || order.getUserId() == null) {
            return;
        }
        Long orderId = order.getId();
        Long userId = order.getUserId();
        // 找下单时的 REDEEM_ORDER 流水，amount 为负值 = 实际抵扣金额
        ShopPromoRecordDO deduct = promoRecordMapper.selectOne(
                new LambdaQueryWrapper<ShopPromoRecordDO>()
                        .eq(ShopPromoRecordDO::getUserId, userId)
                        .eq(ShopPromoRecordDO::getSourceType, "REDEEM_ORDER")
                        .eq(ShopPromoRecordDO::getSourceId, orderId)
                        .last("LIMIT 1"));
        if (deduct == null || deduct.getAmount() == null || deduct.getAmount() >= 0) {
            return;
        }
        long refundAmount = -deduct.getAmount();  // 负值取反 = 正退还金额
        try {
            boolean ok = promoPointService.addPromoPoint(userId, refundAmount,
                    "REDEEM_REFUND", orderId, "订单取消退还推广积分");
            if (!ok) {
                log.warn("[PromoPointRedeemRefund] addPromoPoint 返 false（可能重复触发）orderId={} userId={}",
                        orderId, userId);
            }
            log.info("[PromoPointRedeemRefund] ✅ orderId={} userId={} 退回推广积分={}",
                    orderId, userId, refundAmount);
        } catch (Exception e) {
            log.error("[PromoPointRedeemRefund] 退积分失败 orderId={} userId={}", orderId, userId, e);
            throw e;
        }
    }
}
