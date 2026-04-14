package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.withdraw.MerchantWithdrawPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantWithdrawApplyDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MerchantWithdrawApplyMapper extends BaseMapperX<MerchantWithdrawApplyDO> {

    default PageResult<MerchantWithdrawApplyDO> selectPage(MerchantWithdrawPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MerchantWithdrawApplyDO>()
                .eqIfPresent(MerchantWithdrawApplyDO::getStatus, reqVO.getStatus())
                .eqIfPresent(MerchantWithdrawApplyDO::getTenantId, reqVO.getTenantId())
                .orderByDesc(MerchantWithdrawApplyDO::getId));
    }

}
