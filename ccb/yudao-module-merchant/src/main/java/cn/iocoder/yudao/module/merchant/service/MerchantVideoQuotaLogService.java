package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo.AiVideoQuotaLogPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantVideoQuotaLogDO;

/**
 * 商户 AI 视频配额流水 Service（Phase 0.3.1）。
 *
 * <p>仅提供查询与 insertLog 原子写入——<em>不</em>修改 merchant_info。
 * 调用方（{@code MerchantService#increaseVideoQuota / decreaseVideoQuota}）在同一事务内先改余量再写本表。</p>
 */
public interface MerchantVideoQuotaLogService {

    /**
     * 插入一条流水。由上层事务持有者在原子扣减/增加之后调用。
     */
    void insertLog(MerchantVideoQuotaLogDO log);

    /** Admin 分页。 */
    PageResult<MerchantVideoQuotaLogDO> getLogPage(AiVideoQuotaLogPageReqVO reqVO);

    /** App 分页：商户只能看自己的流水。 */
    PageResult<MerchantVideoQuotaLogDO> getLogPageByMerchant(Long merchantId, Integer bizType,
                                                             AiVideoQuotaLogPageReqVO pageParam);

}
