package cn.iocoder.yudao.module.merchant.dal.mysql.promo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoWithdrawDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ShopPromoWithdrawMapper extends BaseMapperX<ShopPromoWithdrawDO> {

    default PageResult<ShopPromoWithdrawDO> selectPageByStatus(String status, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ShopPromoWithdrawDO>()
                .eqIfPresent(ShopPromoWithdrawDO::getStatus, status)
                .orderByDesc(ShopPromoWithdrawDO::getApplyAt));
    }

    /** 用户的提现申请列表（按申请时间倒序） */
    default List<ShopPromoWithdrawDO> selectListByUserId(Long userId) {
        return selectList(new LambdaQueryWrapperX<ShopPromoWithdrawDO>()
                .eq(ShopPromoWithdrawDO::getUserId, userId)
                .orderByDesc(ShopPromoWithdrawDO::getApplyAt));
    }

    /** 是否有 PENDING/APPROVED 中的提现（用户多次申请校验） */
    default boolean existsActiveByUserId(Long userId) {
        return selectCount(new LambdaQueryWrapperX<ShopPromoWithdrawDO>()
                .eq(ShopPromoWithdrawDO::getUserId, userId)
                .in(ShopPromoWithdrawDO::getStatus, "PENDING", "APPROVED")) > 0;
    }

    /**
     * 原子状态转换：仅当当前 status = expectedFrom 才更新到 newStatus；
     * 返 1 = 成功；0 = 状态机冲突（并发 / 已转过 / 不存在）。
     *
     * 用于 approve / reject / markPaid 防止"两个 admin 同时审批"产生矛盾的中间态。
     */
    @Update("UPDATE shop_promo_withdraw "
            + "SET status = #{newStatus}, processed_at = NOW(), processor_id = #{processorId}, "
            + "    processor_remark = #{remark}, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = b'0' AND status = #{expectedFrom}")
    int transitionStatus(@Param("id") Long id,
                         @Param("expectedFrom") String expectedFrom,
                         @Param("newStatus") String newStatus,
                         @Param("processorId") Long processorId,
                         @Param("remark") String remark);

}
