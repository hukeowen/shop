package cn.iocoder.yudao.module.merchant.dal.mysql.promo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShopPromoRecordMapper extends BaseMapperX<ShopPromoRecordDO> {

    /** 用户的推广积分流水分页（按时间倒序） */
    default PageResult<ShopPromoRecordDO> selectPageByUser(Long userId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ShopPromoRecordDO>()
                .eq(ShopPromoRecordDO::getUserId, userId)
                .orderByDesc(ShopPromoRecordDO::getId));
    }

    /** 判重：某来源 (sourceType, sourceId) 是否已写过该用户的流水 */
    default boolean exists(Long userId, String sourceType, Long sourceId) {
        return selectCount(new LambdaQueryWrapperX<ShopPromoRecordDO>()
                .eq(ShopPromoRecordDO::getUserId, userId)
                .eq(ShopPromoRecordDO::getSourceType, sourceType)
                .eq(ShopPromoRecordDO::getSourceId, sourceId)) > 0;
    }

    /** 用户在某店累计获得的推广积分（lifetime earned，仅正向流水之和，单位分） */
    @org.apache.ibatis.annotations.Select(
            "SELECT IFNULL(SUM(amount), 0) FROM shop_promo_record " +
            "WHERE user_id = #{userId} AND tenant_id = #{tenantId} AND amount > 0 AND deleted = 0")
    Long sumPositiveByUserAndTenant(@org.apache.ibatis.annotations.Param("userId") Long userId,
                                    @org.apache.ibatis.annotations.Param("tenantId") Long tenantId);

}
