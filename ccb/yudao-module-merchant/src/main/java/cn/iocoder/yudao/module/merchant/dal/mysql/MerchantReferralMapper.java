package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantReferralDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MerchantReferralMapper extends BaseMapperX<MerchantReferralDO> {

    /** 按推荐人手机号查询所有推荐记录 */
    default List<MerchantReferralDO> selectListByReferrerMobile(String referrerMobile) {
        return selectList(new LambdaQueryWrapperX<MerchantReferralDO>()
                .eq(MerchantReferralDO::getReferrerMobile, referrerMobile));
    }

    /** 按推荐人租户ID查询 */
    default List<MerchantReferralDO> selectListByReferrerTenantId(Long referrerTenantId) {
        return selectList(new LambdaQueryWrapperX<MerchantReferralDO>()
                .eq(MerchantReferralDO::getReferrerTenantId, referrerTenantId));
    }

    /** 统计推荐人已付费的有效推荐数量 */
    default long countPaidByReferrerTenantId(Long referrerTenantId) {
        return selectCount(new LambdaQueryWrapperX<MerchantReferralDO>()
                .eq(MerchantReferralDO::getReferrerTenantId, referrerTenantId)
                .isNotNull(MerchantReferralDO::getPaidAt));
    }

    /** 查询被推荐商户的推荐记录（一对一） */
    default MerchantReferralDO selectByRefereeTenantId(Long refereeTenantId) {
        return selectOne(new LambdaQueryWrapperX<MerchantReferralDO>()
                .eq(MerchantReferralDO::getRefereeTenantId, refereeTenantId));
    }

    /**
     * 判断推荐人是否已触发过返利（只读一行，避免将整个列表加载到内存）
     */
    default boolean existsRewardedByReferrerTenantId(Long referrerTenantId) {
        return selectCount(new LambdaQueryWrapperX<MerchantReferralDO>()
                .eq(MerchantReferralDO::getReferrerTenantId, referrerTenantId)
                .eq(MerchantReferralDO::getRewarded, Boolean.TRUE)) > 0;
    }

    /**
     * 单条 SQL 原子地将推荐人的所有未奖励记录标记为已奖励。
     * 相比多次 {@code updateById} 更快，并消除了"读-改"之间的竞态窗口。
     *
     * @return 受影响行数
     */
    default int markAllRewardedByReferrerTenantId(Long referrerTenantId) {
        return update(null, new LambdaUpdateWrapper<MerchantReferralDO>()
                .set(MerchantReferralDO::getRewarded, Boolean.TRUE)
                .set(MerchantReferralDO::getRewardTime, LocalDateTime.now())
                .eq(MerchantReferralDO::getReferrerTenantId, referrerTenantId)
                .ne(MerchantReferralDO::getRewarded, Boolean.TRUE));
    }

}
