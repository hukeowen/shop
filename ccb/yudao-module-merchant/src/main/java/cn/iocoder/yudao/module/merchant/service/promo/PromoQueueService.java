package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;

/**
 * 商品队列服务（推 N 反 1 的核心算法，对应 v6 文档第五节）。
 *
 * 三种触发场景：
 *   1. 直推（DIRECT）        买家有上级 → 上级拿奖 + 升 A 层
 *   2. 插队（SELF_PURCHASE） 买家无上级、自己已在 B 层 → 自己拿奖 + 升 A 层
 *   3. 自然推（QUEUE）       买家无上级、自己不在队列 → 队首拿奖、自己进 B 层尾
 *
 * A/B 层定义：
 *   - A：做过主动行为（自购 / 推下级成交）；优先返奖；A 内按 promoted_at 升序
 *   - B：仅自然消费过；A 空才轮到；B 内按 joined_at 升序
 *
 * 累计满 N 次（N = 商品配置的 N）→ 出队，永不再返奖（status = EXITED）。
 */
public interface PromoQueueService {

    /**
     * 处理一笔订单某 SPU 行的队列奖励逻辑。
     *
     * @param config         商品营销配置（必须 tuijianEnabled = true 才生效）
     * @param buyerUserId    买家
     * @param spuId          商品 SPU
     * @param paidAmount     该 SPU 在该笔订单的实付金额(分)
     * @param orderId        订单 ID（用于流水 source / 幂等）
     */
    void handleOrderPaid(ProductPromoConfigDO config, Long buyerUserId, Long spuId,
                         long paidAmount, Long orderId);

}
