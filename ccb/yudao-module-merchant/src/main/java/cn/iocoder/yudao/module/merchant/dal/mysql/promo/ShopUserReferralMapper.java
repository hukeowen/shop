package cn.iocoder.yudao.module.merchant.dal.mysql.promo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserReferralDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ShopUserReferralMapper extends BaseMapperX<ShopUserReferralDO> {

    /** 取某用户在当前商户的推荐记录（含 parent_user_id；可能 null） */
    default ShopUserReferralDO selectByUserId(Long userId) {
        return selectOne(new LambdaQueryWrapperX<ShopUserReferralDO>()
                .eq(ShopUserReferralDO::getUserId, userId));
    }

    /** 列出某用户在当前商户直接推荐的下级（用于 direct_count 重算） */
    default List<ShopUserReferralDO> selectListByParentUserId(Long parentUserId) {
        return selectList(new LambdaQueryWrapperX<ShopUserReferralDO>()
                .eq(ShopUserReferralDO::getParentUserId, parentUserId));
    }

}
