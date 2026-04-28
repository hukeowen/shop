package cn.iocoder.yudao.module.merchant.dal.mysql.promo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 商品营销配置 Mapper
 *
 * TenantBaseDO 自动过滤 tenant_id；查询条件内只需关心 spu_id。
 */
@Mapper
public interface ProductPromoConfigMapper extends BaseMapperX<ProductPromoConfigDO> {

    /** 取某商品的配置（无则返 null） */
    default ProductPromoConfigDO selectBySpuId(Long spuId) {
        return selectOne(new LambdaQueryWrapperX<ProductPromoConfigDO>()
                .eq(ProductPromoConfigDO::getSpuId, spuId));
    }

    /** 批量取（用于商品列表渲染时合并营销开关） */
    default List<ProductPromoConfigDO> selectListBySpuIds(List<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<ProductPromoConfigDO>()
                .in(ProductPromoConfigDO::getSpuId, spuIds));
    }

    /** 列出所有启用了推 N 反 1 的商品（后续业务计算用） */
    default List<ProductPromoConfigDO> selectListByTuijianEnabled() {
        return selectList(new LambdaQueryWrapperX<ProductPromoConfigDO>()
                .eq(ProductPromoConfigDO::getTuijianEnabled, true));
    }

    /** 列出所有参与积分池的商品（后续业务计算用） */
    default List<ProductPromoConfigDO> selectListByPoolEnabled() {
        return selectList(new LambdaQueryWrapperX<ProductPromoConfigDO>()
                .eq(ProductPromoConfigDO::getPoolEnabled, true));
    }

}
