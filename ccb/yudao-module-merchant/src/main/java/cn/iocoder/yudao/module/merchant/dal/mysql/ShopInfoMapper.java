package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ShopInfoMapper extends BaseMapperX<ShopInfoDO> {

    default ShopInfoDO selectByTenantId(Long tenantId) {
        return selectOne(ShopInfoDO::getTenantId, tenantId);
    }

    @Select("SELECT * FROM shop_info WHERE status = 1 AND deleted = 0" +
            " AND latitude BETWEEN #{minLat} AND #{maxLat}" +
            " AND longitude BETWEEN #{minLng} AND #{maxLng}" +
            " ORDER BY ABS(latitude - #{lat}) + ABS(longitude - #{lng}) ASC" +
            " LIMIT #{limit}")
    List<ShopInfoDO> selectNearby(@Param("minLat") BigDecimal minLat, @Param("maxLat") BigDecimal maxLat,
                                   @Param("minLng") BigDecimal minLng, @Param("maxLng") BigDecimal maxLng,
                                   @Param("lat") BigDecimal lat, @Param("lng") BigDecimal lng,
                                   @Param("limit") int limit);

    @Select("SELECT * FROM shop_info WHERE status = 1 AND deleted = 0" +
            " AND category_id = #{categoryId}" +
            " ORDER BY sales_30d DESC LIMIT #{limit}")
    List<ShopInfoDO> selectByCategory(@Param("categoryId") Long categoryId, @Param("limit") int limit);

    /**
     * 原子扣减余额：仅当 {@code balance >= amount} 时才扣，避免并发提现导致负余额。
     *
     * <p>必须以 {@code tenant_id} 作为谓词以防跨租户写入（{@link ShopInfoDO} 是平台级表，
     * 未启用 MP 自动租户过滤）。</p>
     *
     * @return 受影响行数：1 表示扣减成功；0 表示余额不足或租户/店铺不匹配
     */
    @Update("UPDATE shop_info SET balance = balance - #{amount}" +
            " WHERE tenant_id = #{tenantId} AND deleted = 0" +
            " AND balance >= #{amount}")
    int decrementBalanceAtomic(@Param("tenantId") Long tenantId, @Param("amount") int amount);

    /**
     * 原子增加余额：用于驳回/退款场景。
     *
     * @return 受影响行数：1 表示成功；0 表示店铺不存在
     */
    @Update("UPDATE shop_info SET balance = balance + #{amount}" +
            " WHERE tenant_id = #{tenantId} AND deleted = 0")
    int incrementBalanceAtomic(@Param("tenantId") Long tenantId, @Param("amount") int amount);

}
