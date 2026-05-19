package cn.iocoder.yudao.module.merchant.service.promo.handler;

import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopConsumePointDeductDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopConsumePointDeductMapper;
import cn.iocoder.yudao.module.merchant.service.promo.PromoPointService;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.service.order.handler.TradeOrderHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * V043：订单取消时退还消费积分抵扣。
 * <p>checkout submit 已在订单创建时即时扣减 shop_user_star.consume_point_balance 并写 deduct(status=COMMITTED)。
 * 若订单后续被取消，需要把这笔积分退回用户账户，并把 deduct 状态推到 CANCELED。</p>
 *
 * <p>幂等：
 * <ul>
 *   <li>deduct.status 必须 = COMMITTED 才退（CANCELED / PENDING 跳过）</li>
 *   <li>addConsumePoint 内部按 (userId, "REDEEM_REFUND", orderId) 去重，重复触发不会双倍退</li>
 * </ul></p>
 */
@Component
@Slf4j
public class ConsumePointDeductRefundHandler implements TradeOrderHandler {

    @Resource
    private ShopConsumePointDeductMapper deductMapper;
    @Resource
    private PromoPointService promoPointService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void afterCancelOrder(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        if (order == null || order.getId() == null) {
            return;
        }
        ShopConsumePointDeductDO deduct = deductMapper.selectByOrderId(order.getId());
        if (deduct == null) {
            return;
        }
        if (!ShopConsumePointDeductDO.STATUS_COMMITTED.equals(deduct.getStatus())) {
            // 已 CANCELED 或仍 PENDING（理论不该出现）— 不重复退
            return;
        }
        try {
            // 退积分（addConsumePoint 幂等：(userId, REDEEM_REFUND, orderId)）
            boolean ok = promoPointService.addConsumePoint(
                    deduct.getUserId(),
                    deduct.getPointsUsed() == null ? 0 : deduct.getPointsUsed(),
                    "REDEEM_REFUND",
                    deduct.getOrderId(),
                    "订单取消退还消费积分");
            if (!ok) {
                log.warn("[ConsumePointRefund] addConsumePoint 返 false（可能重复触发）orderId={} userId={}",
                        order.getId(), deduct.getUserId());
            }
            deduct.setStatus(ShopConsumePointDeductDO.STATUS_CANCELED);
            deduct.setCancelTime(LocalDateTime.now());
            deductMapper.updateById(deduct);
            log.info("[ConsumePointRefund] ✅ orderId={} userId={} 退回消费积分={}",
                    order.getId(), deduct.getUserId(), deduct.getPointsUsed());
        } catch (Exception e) {
            log.error("[ConsumePointRefund] 退积分失败 orderId={} userId={}",
                    order.getId(), deduct.getUserId(), e);
            throw e;  // 抛出让事务回滚，避免半态：状态未更但积分已退
        }
    }
}
