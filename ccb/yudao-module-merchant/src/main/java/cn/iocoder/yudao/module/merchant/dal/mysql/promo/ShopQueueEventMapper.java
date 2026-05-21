package cn.iocoder.yudao.module.merchant.dal.mysql.promo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueueEventDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ShopQueueEventMapper extends BaseMapperX<ShopQueueEventDO> {

    /** 某订单触发过的队列事件（用于判重 / 审计） */
    default List<ShopQueueEventDO> selectListByOrderId(Long orderId) {
        return selectList(new LambdaQueryWrapperX<ShopQueueEventDO>()
                .eq(ShopQueueEventDO::getSourceOrderId, orderId));
    }

    /**
     * 幂等检查：某订单 × spu × 受益人 × eventType 是否已写过事件。
     *
     * 当订单引擎被重放（例如订单 handler 重复触发、或同一 SPU 多 SKU 行）时，用这个判断
     * 避免队列位置被推进多次（即使 amount=0 / addPromoPoint 不会写流水的边缘情况也覆盖）。
     */
    default boolean existsByOrderAndBeneficiary(Long orderId, Long spuId, Long beneficiaryUserId,
                                                  String eventType) {
        return selectCount(new LambdaQueryWrapperX<ShopQueueEventDO>()
                .eq(ShopQueueEventDO::getSourceOrderId, orderId)
                .eq(ShopQueueEventDO::getSpuId, spuId)
                .eq(ShopQueueEventDO::getBeneficiaryUserId, beneficiaryUserId)
                .eq(ShopQueueEventDO::getEventType, eventType)) > 0;
    }

    /**
     * MerchantPromoOrderHandler 整单幂等键：检查某订单 × spu 是否已跑过营销引擎。
     *
     * <p>由 MerchantPromoOrderHandler.processOneItem 在结束时写入一条 type=HANDLER_DONE_V8 的
     * 事件作为 marker；同订单 + 同 SPU 第二次调用（offline-confirm 同步 + 事件监听器异步
     * 双调）时立即返回，避免极差奖 / 升星累加双倍。</p>
     */
    default boolean existsHandlerDoneV8(Long orderId, Long spuId) {
        return selectCount(new LambdaQueryWrapperX<ShopQueueEventDO>()
                .eq(ShopQueueEventDO::getSourceOrderId, orderId)
                .eq(ShopQueueEventDO::getSpuId, spuId)
                .eq(ShopQueueEventDO::getEventType, "HANDLER_DONE_V8")) > 0;
    }

}
