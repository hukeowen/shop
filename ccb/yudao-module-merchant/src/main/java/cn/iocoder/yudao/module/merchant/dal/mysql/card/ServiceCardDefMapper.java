package cn.iocoder.yudao.module.merchant.dal.mysql.card;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.card.ServiceCardDefDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ServiceCardDefMapper extends BaseMapperX<ServiceCardDefDO> {

    /** 某商品的全部卡定义（按 sort 升序） */
    default List<ServiceCardDefDO> selectListBySpuId(Long spuId) {
        return selectList(new LambdaQueryWrapperX<ServiceCardDefDO>()
                .eq(ServiceCardDefDO::getSpuId, spuId)
                .orderByAsc(ServiceCardDefDO::getSort)
                .orderByAsc(ServiceCardDefDO::getId));
    }

    /** 删除某商品的全部卡定义（保存时先清后插，逻辑删除） */
    default void deleteBySpuId(Long spuId) {
        delete(new LambdaQueryWrapperX<ServiceCardDefDO>()
                .eq(ServiceCardDefDO::getSpuId, spuId));
    }

}
