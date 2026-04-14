package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.apply.MerchantApplyPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantApplyDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MerchantApplyMapper extends BaseMapperX<MerchantApplyDO> {

    default PageResult<MerchantApplyDO> selectPage(MerchantApplyPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MerchantApplyDO>()
                .eqIfPresent(MerchantApplyDO::getStatus, reqVO.getStatus())
                .likeIfPresent(MerchantApplyDO::getMobile, reqVO.getMobile())
                .likeIfPresent(MerchantApplyDO::getShopName, reqVO.getShopName())
                .orderByDesc(MerchantApplyDO::getId));
    }

    default MerchantApplyDO selectByMobile(String mobile) {
        return selectOne(new LambdaQueryWrapperX<MerchantApplyDO>()
                .eq(MerchantApplyDO::getMobile, mobile)
                .orderByDesc(MerchantApplyDO::getId)
                .last("LIMIT 1"));
    }

}
