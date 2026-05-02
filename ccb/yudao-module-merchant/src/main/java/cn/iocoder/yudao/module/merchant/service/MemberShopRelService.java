package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.module.merchant.dal.dataobject.MemberShopRelDO;

/**
 * 会员×商户关系 Service 接口
 */
public interface MemberShopRelService {

    /**
     * 获取或创建会员在指定商户的关系记录。
     * 不存在则插入默认行（firstVisitAt=now, lastVisitAt=now，余额/积分为 0）。
     */
    MemberShopRelDO getOrCreate(Long userId, Long tenantId);

    /**
     * 获取或创建会员关系记录，**仅首次创建时**带上 referrerUserId（v6 推荐链严格语义）。
     *
     * <p>语义：</p>
     * <ul>
     *   <li>rel 已存在（用户之前进过店）→ 不动 referrer_user_id，仅返回现有记录</li>
     *   <li>rel 不存在（首次进店）→ INSERT 时直接落 referrer_user_id（如非空）</li>
     * </ul>
     *
     * <p>此方法是 v6 文档"用户首次进店带 inviter 才算上下级"规则的落地点。</p>
     *
     * @param userId          会员
     * @param tenantId        商户租户
     * @param referrerUserId  推荐人（可空；仅首次创建时生效）
     * @return 关系记录
     */
    MemberShopRelDO getOrCreateWithReferrer(Long userId, Long tenantId, Long referrerUserId);

    /**
     * 原子加减余额（delta 可为负，调用方自行校验不足情况）。
     */
    void addBalance(Long userId, Long tenantId, int delta);

    /**
     * 原子加减积分（delta 可为负）。
     */
    void addPoints(Long userId, Long tenantId, int delta);

    /**
     * 绑定推荐人（仅在 referrerUserId 为 NULL 时更新，幂等）。
     */
    void bindReferrer(Long userId, Long tenantId, Long referrerUserId);

    /**
     * 查询会员在指定商户的关系记录，不存在返回 null。
     */
    MemberShopRelDO getByUserAndTenant(Long userId, Long tenantId);

    /**
     * 列出某用户访问过的所有店铺（按最近访问倒序）。承担"我的店铺收藏夹"语义。
     */
    java.util.List<MemberShopRelDO> listByUserId(Long userId);

    /**
     * 更新最近进店时间为当前时间。
     */
    void updateLastVisitAt(Long userId, Long tenantId);

    /**
     * 设置店铺收藏标记（C 端 shop-home 顶部 ♥ 按钮）。
     * @param favorite true=收藏 false=取消
     */
    void setFavorite(Long userId, Long tenantId, boolean favorite);

    /**
     * 余额单向转积分（1:1，需事务）。
     * 校验余额充足后原子扣减余额并增加积分。
     */
    void balanceToPoints(Long userId, Long tenantId, int amountFen);

    /**
     * 原子扣减余额，仅在余额充足时扣减。
     *
     * @return 受影响行数：1 成功；0 余额不足或记录不存在
     */
    int deductBalance(Long userId, Long tenantId, int amount);

}
