package cn.iocoder.yudao.module.merchant.dal.mysql.promo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopReferralContributionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShopReferralContributionMapper extends BaseMapperX<ShopReferralContributionDO> {

    /** 查 (parent, child, spu) 是否已贡献过；存在即视为「首贡献已用，永久不再触发」 */
    default boolean exists(Long parentUserId, Long childUserId, Long spuId) {
        return selectCount(new LambdaQueryWrapperX<ShopReferralContributionDO>()
                .eq(ShopReferralContributionDO::getParentUserId, parentUserId)
                .eq(ShopReferralContributionDO::getChildUserId, childUserId)
                .eq(ShopReferralContributionDO::getSpuId, spuId)) > 0;
    }
}
