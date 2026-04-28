package cn.iocoder.yudao.module.merchant.dal.mysql.promo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoPoolRoundDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShopPromoPoolRoundMapper extends BaseMapperX<ShopPromoPoolRoundDO> {

    default PageResult<ShopPromoPoolRoundDO> selectPage(PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ShopPromoPoolRoundDO>()
                .orderByDesc(ShopPromoPoolRoundDO::getSettledAt));
    }

}
