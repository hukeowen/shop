package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.MerchantAuditReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.MerchantCreateReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.MerchantPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;

/**
 * 商户 Service 接口
 */
public interface MerchantService {

    /**
     * 商户入驻申请
     */
    Long createMerchant(MerchantCreateReqVO createReqVO, Long userId);

    /**
     * 审核商户
     */
    void auditMerchant(MerchantAuditReqVO auditReqVO);

    /**
     * 获取商户信息
     */
    MerchantDO getMerchant(Long id);

    /**
     * 根据用户ID获取商户
     */
    MerchantDO getMerchantByUserId(Long userId);

    /**
     * 根据微信小程序 OpenID 获取商户
     */
    MerchantDO getMerchantByOpenId(String openId);

    /**
     * 基于会员用户快速创建商户（Phase 0.2 - 统一小程序入口）
     *
     * <p>与 {@link #createMerchant} 不同，这是简化版，只写必需字段，其他由商户后续在商户端完善。
     * 典型流程：会员在小程序内填邀请码 + 授权手机号 → 立刻获得商户身份，可进入管理面板。</p>
     *
     * @param memberUserId 会员用户 ID（同时作为 merchant.user_id）
     * @param openId       微信小程序 OpenID
     * @param unionId      微信 UnionID（可空）
     * @param phone        联系电话
     * @param inviteCodeId 邀请码 ID
     * @return 新创建的商户 ID
     */
    Long createMerchantFromMember(Long memberUserId, String openId, String unionId,
                                  String phone, Long inviteCodeId);

    /**
     * 分页查询商户
     */
    PageResult<MerchantDO> getMerchantPage(MerchantPageReqVO pageReqVO);

    /**
     * 提交微信支付进件
     */
    void submitWxPayApplyment(Long merchantId);

    /**
     * 生成商户专属小程序码
     */
    String generateMiniAppQrCode(Long merchantId);

    /**
     * 禁用商户
     */
    void disableMerchant(Long merchantId);

    /**
     * 启用商户
     */
    void enableMerchant(Long merchantId);

}
