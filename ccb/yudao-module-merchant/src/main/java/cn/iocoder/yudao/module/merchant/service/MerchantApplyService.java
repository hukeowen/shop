package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.apply.MerchantApplyAuditReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.apply.MerchantApplyPageReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.apply.TenantSubscriptionPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantApplyDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.TenantSubscriptionDO;

/**
 * 商户入驻申请 Service 接口
 */
public interface MerchantApplyService {

    /**
     * 分页查询申请列表（管理后台）
     */
    PageResult<MerchantApplyDO> getApplyPage(MerchantApplyPageReqVO pageReqVO);

    /**
     * 获取申请详情
     */
    MerchantApplyDO getApply(Long id);

    /**
     * 审核申请（通过 or 驳回）
     * 通过时自动：创建租户 + 初始化 shop_info + 初始化 tenant_subscription + 发送短信
     *
     * @param auditReqVO 审核参数
     * @param auditorId  审核员管理员 ID
     */
    void auditApply(MerchantApplyAuditReqVO auditReqVO, Long auditorId);

    /**
     * 获取租户订阅状态
     */
    TenantSubscriptionDO getSubscription(Long tenantId);

    /**
     * 手动续费（管理后台）
     *
     * @param tenantId 租户 ID
     * @param days     续期天数
     */
    void renewSubscription(Long tenantId, int days);

    /**
     * 分页查询订阅列表
     */
    PageResult<TenantSubscriptionDO> getSubscriptionPage(TenantSubscriptionPageReqVO pageReqVO);

    /**
     * 禁用租户订阅（封号）
     */
    void disableSubscription(Long tenantId);

    /**
     * 定时任务：将已过期的试用/正式订阅标记为过期状态
     */
    void expireOverdueSubscriptions();

}
