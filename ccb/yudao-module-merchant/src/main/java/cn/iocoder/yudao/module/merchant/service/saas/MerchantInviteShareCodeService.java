package cn.iocoder.yudao.module.merchant.service.saas;

import cn.iocoder.yudao.module.merchant.dal.dataobject.saas.MerchantInviteShareCodeDO;

/**
 * 商户开店分享码 Service。
 *
 * <p>语义：商户 A → 生成自己的分享码 → 分享给潜在商户 B → B 注册入驻时带 code
 * → 解析 code 拿到 A 的 user_id → 写 shop_user_referral 让 v8 推广引擎能找到 parent。</p>
 */
public interface MerchantInviteShareCodeService {

    /**
     * 取或生成「该商户的分享码」（每个商户最多 1 个分享码；首次调用自动生成 6 位随机 code）。
     *
     * @param referrerUserId 商户的 member_user.id
     * @param referrerTenantId 商户的 tenant_id
     */
    MerchantInviteShareCodeDO getOrCreate(Long referrerUserId, Long referrerTenantId);

    /**
     * 按 code 查邀请记录（B 注册时入口）。
     *
     * @return null 表示 code 不存在 / 已禁用
     */
    MerchantInviteShareCodeDO findByCode(String code);

    /** 原子 used_count +1（B 注册成功后调一次）。 */
    void incrementUsedCount(String code);

}
