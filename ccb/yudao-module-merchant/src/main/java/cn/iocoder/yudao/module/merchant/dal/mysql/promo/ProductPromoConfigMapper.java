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

    /**
     * 取某商品的配置（无则返 null）。
     *
     * <p>spu_id 全局唯一（product_spu.id 自增），product_promo_config 也是 1:1 跟 spu 走。
     * 跨租户读：v8 平台店铺套餐订单的 trade_order tenant 是 buyer tenant（如 1006），
     * 但套餐配置写在 tenant=999；订单 paid 时调 afterPayOrder 必须能看到 tenant=999 的配置才能
     * 触发推广引擎。普通商户的配置查询也不受影响（每个 spu_id 仍然只对应 1 条记录）。</p>
     */
    default ProductPromoConfigDO selectBySpuId(Long spuId) {
        return cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.executeIgnore(() ->
                selectOne(new LambdaQueryWrapperX<ProductPromoConfigDO>()
                        .eq(ProductPromoConfigDO::getSpuId, spuId)));
    }

    /** 批量取（用于商品列表渲染时合并营销开关）— 同样跨租户读，spu_id 全局唯一 */
    default List<ProductPromoConfigDO> selectListBySpuIds(List<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.executeIgnore(() ->
                selectList(new LambdaQueryWrapperX<ProductPromoConfigDO>()
                        .in(ProductPromoConfigDO::getSpuId, spuIds)));
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
