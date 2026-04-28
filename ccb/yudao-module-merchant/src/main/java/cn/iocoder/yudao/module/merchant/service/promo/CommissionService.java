package cn.iocoder.yudao.module.merchant.service.promo;

/**
 * 团队极差递减服务（v6 文档第六节）。
 *
 * 算法（递减抽成，互不重复）：
 *   - 维护 "已发星级 / 已发比例%"
 *   - 步 1：买家自己若是 N 星 → 拿 commissionRates[N-1]% 起步，"已发 = N"
 *   - 步 2：沿推荐链向上找最近的"星级 > 已发"的用户：
 *           award = (该用户星级% - 已发%)；更新已发 = 该用户星级；继续向上找
 *   - 已发 ≥ 当前层星级 → 跳过；找不到更高星级 → 结束
 *
 * 防重幂等：调用 PromoPointService.addPromoPoint(userId, COMMISSION, orderId)，
 *           同一订单同一用户只会写一次。
 */
public interface CommissionService {

    /**
     * 处理一笔订单的团队极差递减分润。
     *
     * @param buyerUserId 买家
     * @param paidAmount  实付金额(分)
     * @param orderId     订单 ID（流水 sourceId / 防重）
     */
    void handleOrderPaid(Long buyerUserId, long paidAmount, Long orderId);

}
