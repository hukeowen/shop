package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.controller.app.vo.AppQueuePositionRespVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;

import java.util.List;

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

    /**
     * v7 推荐重载：传入 unitPaid（单件实付价 = 行实付总额 / 件数）。
     *
     * <p>v7 规则：</p>
     * <ul>
     *   <li>IN_PROGRESS 期返奖按「单件实付价 × (1/N)」（按"次"不按"量"）</li>
     *   <li>COMPLETED 终态返奖按「订单中该商品行 paidAmount 总额 × directCommissionRatio%」</li>
     * </ul>
     *
     * @param paidAmount 订单中该商品行实付总额（分）
     * @param unitPaid   单件实付价（分）= paidAmount / item.count
     */
    default void handleOrderPaid(ProductPromoConfigDO config, Long buyerUserId, Long spuId,
                                 long paidAmount, long unitPaid, Long orderId) {
        // 默认实现 fallback 到旧签名（兼容历史 caller）
        handleOrderPaid(config, buyerUserId, spuId, paidAmount, orderId);
    }

    /**
     * 列出某用户当前所有 QUEUEING 状态的队列位置（"我的队列"页用）。
     * 已 EXITED 的不返；按 A 层先 / 同层内按晋升时间 / 入队时间升序。
     * 每行附上商品配置的 N（前端可显示进度 "已累计 2/3 次"）。
     *
     * <p><b>租户上下文契约（重要）</b>：本方法依赖 MyBatis Plus 的 TenantBaseDO
     * 自动按 tenant_id 过滤，因此调用方<b>必须先切到目标 tenant 上下文</b>
     * （{@code TenantUtils.execute(tenantId, ...)} 包裹），否则 SPU / config
     * 查询会查到空白结果。跨店聚合场景（如 C 端 /my-queues）应外层 for 循环
     * tenant 然后逐个调用本方法，不要把多个 tenant 的 spuId 揉一起传。</p>
     */
    List<AppQueuePositionRespVO> listMyQueueing(Long userId);

}
