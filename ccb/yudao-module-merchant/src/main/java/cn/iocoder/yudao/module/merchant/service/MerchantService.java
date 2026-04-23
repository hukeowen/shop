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

    // ========== AI 视频配额（Phase 0.3.1） ==========

    /**
     * 原子增加商户视频配额并写入一条流水。独立事务（REQUIRES_NEW），
     * 便于跨远程调用的错误回补场景——不要和其他大事务耦合。
     *
     * @param merchantId 商户 ID，必须存在
     * @param delta      正整数，配额增加条数
     * @param bizType    业务类型，对应
     *                   {@link cn.iocoder.yudao.module.merchant.enums.ai.VideoQuotaBizTypeEnum}
     * @param bizId      业务外键（订单号 / 任务 ID / 操作员 ID）；可空
     * @param remark     备注；可空
     * @return 配额变动结果：变动后余量 + 本次写入流水的 id（供 {@code markPaid} 回写审计字段）
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 商户不存在 / 更新失败
     */
    QuotaChangeResult increaseVideoQuota(Long merchantId, int delta, Integer bizType, String bizId, String remark);

    /**
     * 原子扣减商户视频配额并写入一条流水。独立事务（REQUIRES_NEW）。
     *
     * <p>靠 SQL {@code WHERE video_quota_remaining >= ?} 保证原子性——
     * 并发多个请求同时扣时，最多只有一个成功；其他抛
     * {@link cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants#VIDEO_QUOTA_INSUFFICIENT}。</p>
     *
     * @return 配额变动结果：变动后余量 + 本次写入流水的 id
     */
    QuotaChangeResult decreaseVideoQuota(Long merchantId, int delta, Integer bizType, String bizId, String remark);

}
