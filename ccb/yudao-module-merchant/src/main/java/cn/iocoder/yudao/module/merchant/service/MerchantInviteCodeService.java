package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantInviteCodeDO;

/**
 * BD 商户邀请码 Service
 */
public interface MerchantInviteCodeService {

    /**
     * 校验并消费邀请码（used_count 原子 +1）
     *
     * @param code 邀请码
     * @return 校验通过的邀请码 DO（包含 id，用于关联回商户）
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException
     *         INVITE_CODE_NOT_FOUND / DISABLED / EXHAUSTED
     */
    MerchantInviteCodeDO validateAndConsume(String code);

    /**
     * 创建邀请码（BD 后台使用）
     *
     * @param operatorUserId BD 员工 ID
     * @param usageLimit     最大使用次数，-1 无限
     * @param remark         备注
     * @return 生成的邀请码字符串
     */
    String createCode(Long operatorUserId, Integer usageLimit, String remark);
}
