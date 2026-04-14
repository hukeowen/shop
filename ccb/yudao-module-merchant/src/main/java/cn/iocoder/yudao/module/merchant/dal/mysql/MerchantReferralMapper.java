package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantReferralDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MerchantReferralMapper extends BaseMapperX<MerchantReferralDO> {

    /** 按推荐人手机号查询所有推荐记录 */
    default List<MerchantReferralDO> selectListByReferrerMobile(String referrerMobile) {
        return selectList(new LambdaQueryWrapperX<MerchantReferralDO>()
                .eq(MerchantReferralDO::getReferrerMobile, referrerMobile));
    }

    /** 按推荐人租户ID查询 */
    default List<MerchantReferralDO> selectListByReferrerTenantId(Long referrerTenantId) {
        return selectList(new LambdaQueryWrapperX<MerchantReferralDO>()
                .eq(MerchantReferralDO::getReferrerTenantId, referrerTenantId));
    }

    /** 统计推荐人已付费的有效推荐数量 */
    default long countPaidByReferrerTenantId(Long referrerTenantId) {
        return selectCount(new LambdaQueryWrapperX<MerchantReferralDO>()
                .eq(MerchantReferralDO::getReferrerTenantId, referrerTenantId)
                .isNotNull(MerchantReferralDO::getPaidAt));
    }

    /** 查询被推荐商户的推荐记录（一对一） */
    default MerchantReferralDO selectByRefereeTenantId(Long refereeTenantId) {
        return selectOne(new LambdaQueryWrapperX<MerchantReferralDO>()
                .eq(MerchantReferralDO::getRefereeTenantId, refereeTenantId));
    }

}
