package cn.iocoder.yudao.module.merchant.dal.mysql.promo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopConsumePointRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShopConsumePointRecordMapper extends BaseMapperX<ShopConsumePointRecordDO> {

    default PageResult<ShopConsumePointRecordDO> selectPageByUser(Long userId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ShopConsumePointRecordDO>()
                .eq(ShopConsumePointRecordDO::getUserId, userId)
                .orderByDesc(ShopConsumePointRecordDO::getId));
    }

    default boolean exists(Long userId, String sourceType, Long sourceId) {
        return selectCount(new LambdaQueryWrapperX<ShopConsumePointRecordDO>()
                .eq(ShopConsumePointRecordDO::getUserId, userId)
                .eq(ShopConsumePointRecordDO::getSourceType, sourceType)
                .eq(ShopConsumePointRecordDO::getSourceId, sourceId)) > 0;
    }

}
