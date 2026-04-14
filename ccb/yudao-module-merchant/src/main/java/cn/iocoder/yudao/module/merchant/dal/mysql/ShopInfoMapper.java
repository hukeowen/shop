package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

}
