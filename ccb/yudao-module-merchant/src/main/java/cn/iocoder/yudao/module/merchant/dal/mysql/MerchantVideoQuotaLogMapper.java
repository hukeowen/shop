package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo.AiVideoQuotaLogPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantVideoQuotaLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商户 AI 视频配额流水 Mapper。按 merchant_id 过滤，不做租户过滤。
 */
@Mapper
public interface MerchantVideoQuotaLogMapper extends BaseMapperX<MerchantVideoQuotaLogDO> {

    /**
     * Admin 分页：支持 merchantId / bizType / createTime 范围筛选。
     */
    default PageResult<MerchantVideoQuotaLogDO> selectPage(AiVideoQuotaLogPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MerchantVideoQuotaLogDO>()
                .eqIfPresent(MerchantVideoQuotaLogDO::getMerchantId, reqVO.getMerchantId())
                .eqIfPresent(MerchantVideoQuotaLogDO::getBizType, reqVO.getBizType())
                .betweenIfPresent(MerchantVideoQuotaLogDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(MerchantVideoQuotaLogDO::getId));
    }

    /**
     * App 分页：限定 merchantId，商户只能看自己的流水。
     *
     * @param merchantId 商户 ID，必传；强制 eq（非 eqIfPresent）防止越权
     * @param bizType    可选筛选
     */
    default PageResult<MerchantVideoQuotaLogDO> selectPageByMerchant(Long merchantId, Integer bizType,
                                                                    AiVideoQuotaLogPageReqVO pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<MerchantVideoQuotaLogDO>()
                .eq(MerchantVideoQuotaLogDO::getMerchantId, merchantId)
                .eqIfPresent(MerchantVideoQuotaLogDO::getBizType, bizType)
                .orderByDesc(MerchantVideoQuotaLogDO::getId));
    }

}
