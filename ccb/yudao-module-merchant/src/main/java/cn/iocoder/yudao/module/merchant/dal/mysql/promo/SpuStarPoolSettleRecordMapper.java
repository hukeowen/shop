package cn.iocoder.yudao.module.merchant.dal.mysql.promo;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolSettleRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SpuStarPoolSettleRecordMapper extends BaseMapperX<SpuStarPoolSettleRecordDO> {

    default PageResult<SpuStarPoolSettleRecordDO> selectPageBySpuId(Long spuId, int pageNo, int pageSize) {
        return selectPage(
                new cn.iocoder.yudao.framework.common.pojo.PageParam().setPageNo(pageNo).setPageSize(pageSize),
                new LambdaQueryWrapperX<SpuStarPoolSettleRecordDO>()
                        .eq(SpuStarPoolSettleRecordDO::getSpuId, spuId)
                        .orderByDesc(SpuStarPoolSettleRecordDO::getId));
    }

}
