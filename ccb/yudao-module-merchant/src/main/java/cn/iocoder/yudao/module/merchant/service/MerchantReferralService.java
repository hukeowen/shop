package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.module.merchant.controller.admin.vo.referral.MerchantReferralConfigRespVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.referral.MerchantReferralRespVO;

import java.util.List;

/**
 * 平台商户推荐裂变 Service 接口
 */
public interface MerchantReferralService {

    /**
     * 获取平台推N返1配置
     */
    MerchantReferralConfigRespVO getConfig();

    /**
     * 查询某推荐人的所有推荐记录
     *
     * @param referrerTenantId 推荐人租户ID
     */
    List<MerchantReferralRespVO> getReferralList(Long referrerTenantId);

    /**
     * 审核通过时记录推荐关系（若申请时填写了 referrerMobile）
     *
     * @param referrerMobile  推荐人手机号
     * @param refereeTenantId 被推荐商户的租户ID
     */
    void recordReferral(String referrerMobile, Long refereeTenantId);

    /**
     * 被推荐商户首次续费付费后调用，标记 paid_at，并检查是否达到推N阈值触发返利
     *
     * @param refereeTenantId 完成付费的被推荐商户租户ID
     */
    void onRefereePaid(Long refereeTenantId);

}
