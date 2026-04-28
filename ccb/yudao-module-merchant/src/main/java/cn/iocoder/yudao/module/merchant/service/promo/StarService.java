package cn.iocoder.yudao.module.merchant.service.promo;

/**
 * 星级评定服务（v6 文档第七节）。
 *
 * 升星规则：
 *   - AND 条件：直推下级数 ≥ N，团队链路销售份数 ≥ M
 *   - 每星各一组 (N, M)，存于商户配置 PromoConfig.starUpgradeRules
 *   - 终生制：达到后不会因不活跃降级
 *   - "团队链路销售份数" = 自己 + 所有下级累计销售的"参与推 N 反 1"商品份数
 *
 * 触发点：
 *   1. 订单支付：买家 + 全部上级链路的 team_sales_count 各 +1（限参与商品）
 *   2. 推荐链绑定：上级的 direct_count +1
 *
 * 二者发生后立刻尝试升星（升满几级就升几级）；不升即止，不降级。
 */
public interface StarService {

    /**
     * 一笔订单成交后：买家 + 整条上级链团队销售份数 +qty，并尝试升星。
     * 仅当商品 tuijianEnabled = true 时才计入团队链路销售份数。
     *
     * @param buyerUserId 买家
     * @param qty         本单该商品的份数（≥ 1）
     * @param countable   是否参与推 N 反 1（=true 才累加 team_sales_count）
     */
    void handleOrderPaid(Long buyerUserId, int qty, boolean countable);

    /**
     * 推荐链绑定后：parent 的 direct_count +1，并尝试升星。
     */
    void handleReferralBound(Long parentUserId);

    /**
     * 强制重算某用户的星级（管理员后台手动修复用，按当前 direct_count / team_sales_count 重算）。
     *
     * @return 新星级
     */
    int recompute(Long userId);

}
