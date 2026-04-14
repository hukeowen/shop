package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.withdraw.MerchantWithdrawAuditReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.withdraw.MerchantWithdrawPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantWithdrawApplyDO;

/**
 * 商户提现申请 Service 接口
 */
public interface MerchantWithdrawService {

    /**
     * 分页查询提现申请（平台管理后台）
     */
    PageResult<MerchantWithdrawApplyDO> getWithdrawPage(MerchantWithdrawPageReqVO pageReqVO);

    /**
     * 获取提现申请详情
     */
    MerchantWithdrawApplyDO getWithdraw(Long id);

    /**
     * 审核提现申请（通过时上传凭证，驳回时填写原因）
     *
     * @param auditReqVO 审核参数
     * @param auditorId  操作人管理员ID
     */
    void auditWithdraw(MerchantWithdrawAuditReqVO auditReqVO, Long auditorId);

    /**
     * 商户端提交提现申请（由商户小程序调用）
     *
     * @param apply 提现申请内容
     */
    void createWithdraw(MerchantWithdrawApplyDO apply);

}
