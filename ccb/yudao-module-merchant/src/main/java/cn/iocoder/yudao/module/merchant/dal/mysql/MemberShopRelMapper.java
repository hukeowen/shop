package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MemberShopRelDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MemberShopRelMapper extends BaseMapperX<MemberShopRelDO> {

    default MemberShopRelDO selectByUserIdAndTenantId(Long userId, Long tenantId) {
        return selectOne(MemberShopRelDO::getUserId, userId,
                MemberShopRelDO::getTenantId, tenantId);
    }

    /**
     * 原子加减余额（delta 可为负）。
     *
     * @return 受影响行数：1 成功；0 记录不存在
     */
    @Update("UPDATE member_shop_rel SET balance = balance + #{delta}" +
            " WHERE user_id = #{userId} AND tenant_id = #{tenantId} AND deleted = 0")
    int incrementBalance(@Param("userId") Long userId,
                         @Param("tenantId") Long tenantId,
                         @Param("delta") int delta);

    /**
     * 原子加减积分（delta 可为负）。
     *
     * @return 受影响行数：1 成功；0 记录不存在
     */
    @Update("UPDATE member_shop_rel SET points = points + #{delta}" +
            " WHERE user_id = #{userId} AND tenant_id = #{tenantId} AND deleted = 0")
    int incrementPoints(@Param("userId") Long userId,
                        @Param("tenantId") Long tenantId,
                        @Param("delta") int delta);

    /**
     * 仅在推荐人为 NULL 时绑定推荐人（幂等，防止覆盖已有上线）。
     *
     * @return 受影响行数：1 绑定成功；0 已有推荐人或记录不存在
     */
    @Update("UPDATE member_shop_rel SET referrer_user_id = #{referrerUserId}" +
            " WHERE user_id = #{userId} AND tenant_id = #{tenantId}" +
            " AND referrer_user_id IS NULL AND deleted = 0")
    int bindReferrer(@Param("userId") Long userId,
                     @Param("tenantId") Long tenantId,
                     @Param("referrerUserId") Long referrerUserId);

    /**
     * 更新最近进店时间。
     */
    @Update("UPDATE member_shop_rel SET last_visit_at = #{lastVisitAt}" +
            " WHERE user_id = #{userId} AND tenant_id = #{tenantId} AND deleted = 0")
    int updateLastVisitAt(@Param("userId") Long userId,
                          @Param("tenantId") Long tenantId,
                          @Param("lastVisitAt") java.time.LocalDateTime lastVisitAt);

    /**
     * 原子扣减余额，仅在余额充足（balance >= amount）时扣减。
     *
     * @return 受影响行数：1 成功；0 余额不足或记录不存在
     */
    @Update("UPDATE member_shop_rel SET balance = balance - #{amount}" +
            " WHERE user_id = #{userId} AND tenant_id = #{tenantId}" +
            " AND balance >= #{amount} AND deleted = 0")
    int deductBalance(@Param("userId") Long userId,
                      @Param("tenantId") Long tenantId,
                      @Param("amount") int amount);

}
