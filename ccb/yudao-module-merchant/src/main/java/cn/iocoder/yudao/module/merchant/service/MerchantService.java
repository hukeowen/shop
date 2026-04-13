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
