package cn.iocoder.yudao.module.merchant.service.promo;

import java.util.List;

/**
 * 推荐链服务（每商户独立）。
 *
 * 关键约束：
 *   - 终生绑定：parent_user_id 一旦写入，不再覆盖（first-binding-wins）
 *   - 自然用户：在当前商户没有 referral 记录 或 parent_user_id = 0
 *   - 跨商户隔离：TenantBaseDO 自动注入 tenant_id
 */
public interface ReferralService {

    /**
     * 首次绑定上级。已绑定 / 自己不能绑自己 / parent 不存在均返 false。
     *
     * @return true = 新绑定 / false = 已存在或非法（不抛异常，便于在订单流程里 fire-and-forget）
     */
    boolean bindParent(Long userId, Long parentUserId, Long orderId);

    /**
     * 直接上级 ID；自然用户返 0。
     */
    Long getDirectParent(Long userId);

    /**
     * 沿推荐链向上的所有上级 ID（不含自己）；从最近到最远；遇到自然用户终止。
     * 已遍历用户做防环：若推荐链有环（理论上不应该），早终止避免死循环。
     *
     * @param maxDepth 最深向上找几层（防御性，0/负数 = 不限）
     */
    List<Long> getAncestors(Long userId, int maxDepth);

    /** 是否自然用户（没绑过 / 上级=0）。 */
    boolean isNatural(Long userId);

    /** 当前用户的直推下级数（用于 star 重算）。 */
    int countDirectChildren(Long userId);

}
