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

    // ============================================================
    // v8: checkout 阶段预演 + 多件循环触发
    // ============================================================

    /**
     * v8: 预演 buyer 在某 spu 上买 totalCount 件会产生多少积分（不写库）。
     *
     * <p>用于 checkout/submit 阶段计算抵扣件数：
     * <pre>
     *   K = floor(produced / unitPrice)
     *   实付 = (totalCount - K) × unitPrice
     * </pre></p>
     *
     * <p>规则（v8）：</p>
     * <ul>
     *   <li>buyer 之前没买过该 spu（buyerPos == null）→ 第 1 件 ACTIVATE 不返奖</li>
     *   <li>IN_PROGRESS 期：每件按 ratios[cumulated] × unitPrice 累加，每件 cumulated++</li>
     *   <li>cumulated 达 N → COMPLETED；后续每件按 directRate% × unitPrice</li>
     * </ul>
     *
     * @param config       商品配置（含 tuijianN, tuijianRatios, directRate）
     * @param buyerUserId  买家 userId
     * @param spuId        商品 SPU
     * @param unitPrice    单件价（分）
     * @param totalCount   订单件数
     * @return 本单 buyer 自购预计产生积分总额（分）
     */
    long previewProducedForOrder(ProductPromoConfigDO config, Long buyerUserId, Long spuId,
                                  int unitPrice, int totalCount);

    /**
     * v8: 真实触发 buyer 自购的多件循环 + parent 首贡献 + 自然推队首 + 极差奖 + 入池。
     *
     * <p>跟 v7 单件 handleOrderPaid 的区别：</p>
     * <ul>
     *   <li>buyer 自购按 totalCount 件循环推进 N 次状态机（之前一单 1 步）</li>
     *   <li>buyer 自购产生积分 P 已经在 checkout 阶段抵扣 K 件，
     *       这里只把 净到余额 = P - K×unitPrice 加到 buyer.余额</li>
     *   <li>parent 首贡献按 1 件价封顶（不论 buyer 多少件）</li>
     *   <li>极差奖按 buyer 上链就近递增 + 商品 starRatios 计算</li>
     *   <li>poolRatio 入池</li>
     * </ul>
     *
     * @param deductCount 抵扣件数 K（已在 checkout 算好）；0 = 没抵扣
     */
    void handleOrderPaidV8(ProductPromoConfigDO config, Long buyerUserId, Long spuId,
                           int unitPrice, int totalCount, int deductCount, Long orderId);

}
