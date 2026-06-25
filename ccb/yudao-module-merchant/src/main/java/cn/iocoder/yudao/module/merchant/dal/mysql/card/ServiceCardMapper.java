package cn.iocoder.yudao.module.merchant.dal.mysql.card;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.card.ServiceCardDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ServiceCardMapper extends BaseMapperX<ServiceCardDO> {

    /** 我的卡（跨店，按创建时间倒序）；调用方需 @TenantIgnore */
    default List<ServiceCardDO> selectListByUserId(Long userId) {
        return selectList(new LambdaQueryWrapperX<ServiceCardDO>()
                .eq(ServiceCardDO::getUserId, userId)
                .orderByDesc(ServiceCardDO::getId));
    }

    /** 按核销码 + 租户查（商户核销用，避免跨店核销）；调用方 @TenantIgnore + 显式传 tenantId */
    default ServiceCardDO selectByCardNoAndTenant(String cardNo, Long tenantId) {
        return selectOne(new LambdaQueryWrapperX<ServiceCardDO>()
                .eq(ServiceCardDO::getCardNo, cardNo)
                .eq(ServiceCardDO::getTenantId, tenantId));
    }

    /** 某订单已发的卡（发卡幂等判断用） */
    default List<ServiceCardDO> selectListByOrderId(Long orderId) {
        return selectList(new LambdaQueryWrapperX<ServiceCardDO>()
                .eq(ServiceCardDO::getOrderId, orderId));
    }

    /**
     * 原子核销：仅当卡仍可用（ACTIVE + 未过期 + 未用尽）才 used_count+1；
     * 次数达上限则同时置 USED_UP。返回 1=核销成功，0=不可核销（过期/用尽/并发/不存在）。
     *
     * <p>并发安全：所有判定都在 WHERE 里，单条 UPDATE 原子完成，杜绝两个核销员同时把同一张卡核销两次。</p>
     */
    // 注意 MySQL：单表 UPDATE 的 SET 从左到右求值，后面的赋值会读到前面已更新的值。
    // 因此 status 的 CASE 里 used_count 已是「+1 后」的新值，这里不能再 +1（否则提前一次判 USED_UP）。
    // WHERE 里的 used_count 仍是更新前的原值，故 used_count < max_count 是「本次能否核销」的正确判定。
    @Update("UPDATE shop_service_card " +
            "SET used_count = used_count + 1, " +
            "    status = CASE WHEN max_count IS NOT NULL AND used_count >= max_count THEN 'USED_UP' ELSE 'ACTIVE' END, " +
            "    update_time = NOW() " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = b'0' " +
            "  AND status = 'ACTIVE' AND expire_time > NOW() " +
            "  AND (max_count IS NULL OR used_count < max_count)")
    int redeemAtomic(@Param("id") Long id, @Param("tenantId") Long tenantId);

}
