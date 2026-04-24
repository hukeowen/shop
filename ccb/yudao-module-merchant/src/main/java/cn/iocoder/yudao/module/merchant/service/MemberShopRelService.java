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
     * 更新最近进店时间为当前时间。
     */
    void updateLastVisitAt(Long userId, Long tenantId);

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
