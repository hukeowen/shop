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
     * v6/v7 老接口：商户级共用极差。已废弃，仅保留向后兼容；新代码用 handleOrderPaidV8。
     */
    void handleOrderPaid(Long buyerUserId, long paidAmount, Long orderId);

    /**
     * v8: 商品级团队极差奖。沿 buyer 上链就近递增算法，按商品 starRatios 计算。
     *
     * @param config       商品配置（含 star_count, star_ratios）
     * @param buyerUserId  买家
     * @param spuId        商品 SPU
     * @param paidAmount   订单 spu 行实付金额（抵扣后，分）
     * @param orderId      订单 ID
     */
    void handleOrderPaidV8(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO config,
                           Long buyerUserId, Long spuId, long paidAmount, Long orderId);

}
