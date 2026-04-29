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

    /** 通联回调按 outOrderId 反查店铺（进件结果异步推送时用） */
    default ShopInfoDO selectByTlOpenOrderId(String outOrderId) {
        return selectOne(ShopInfoDO::getTlOpenOrderId, outOrderId);
    }

    /**
     * 按地理位置近距离 + 综合评分排序。
     *
     * 设计 6.7 节综合评分公式：
     *   score = sales_30d * 0.5 + avg_rating * 10 * 0.3 + isNew * 50 * 0.2
     *   isNew = (create_time > NOW() - 30 days) ? 1 : 0
     * 主排序仍按距离（用户附近偏好），次排序用综合分突出优质 / 新店。
     */
    @Select("SELECT * FROM shop_info WHERE status = 1 AND deleted = 0" +
            " AND latitude BETWEEN #{minLat} AND #{maxLat}" +
            " AND longitude BETWEEN #{minLng} AND #{maxLng}" +
            " ORDER BY ABS(latitude - #{lat}) + ABS(longitude - #{lng}) ASC," +
            " (COALESCE(sales_30d, 0) * 0.5" +
            "  + COALESCE(avg_rating, 0) * 10 * 0.3" +
            "  + (CASE WHEN create_time > DATE_SUB(NOW(), INTERVAL 30 DAY) THEN 50 ELSE 0 END) * 0.2) DESC" +
            " LIMIT #{limit}")
    List<ShopInfoDO> selectNearby(@Param("minLat") BigDecimal minLat, @Param("maxLat") BigDecimal maxLat,
                                   @Param("minLng") BigDecimal minLng, @Param("maxLng") BigDecimal maxLng,
                                   @Param("lat") BigDecimal lat, @Param("lng") BigDecimal lng,
                                   @Param("limit") int limit);

    /**
     * 按分类列出店铺，按设计 6.7 节综合评分公式排序：
     *   score = sales_30d * 0.5 + avg_rating * 10 * 0.3 + isNew * 50 * 0.2
     * isNew = 创建至今 ≤ 30 天则为 1。
     */
    @Select("SELECT * FROM shop_info WHERE status = 1 AND deleted = 0" +
            " AND category_id = #{categoryId}" +
            " ORDER BY (COALESCE(sales_30d, 0) * 0.5" +
            "  + COALESCE(avg_rating, 0) * 10 * 0.3" +
            "  + (CASE WHEN create_time > DATE_SUB(NOW(), INTERVAL 30 DAY) THEN 50 ELSE 0 END) * 0.2) DESC" +
            " LIMIT #{limit}")
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
