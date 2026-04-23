package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.MerchantPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MerchantMapper extends BaseMapperX<MerchantDO> {

    default PageResult<MerchantDO> selectPage(MerchantPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MerchantDO>()
                .likeIfPresent(MerchantDO::getName, reqVO.getName())
                .eqIfPresent(MerchantDO::getStatus, reqVO.getStatus())
                .likeIfPresent(MerchantDO::getContactPhone, reqVO.getContactPhone())
                .orderByDesc(MerchantDO::getId));
    }

    default MerchantDO selectByUserId(Long userId) {
        return selectOne(MerchantDO::getUserId, userId);
    }

    default MerchantDO selectByOpenId(String openId) {
        return selectOne(MerchantDO::getOpenId, openId);
    }

}
