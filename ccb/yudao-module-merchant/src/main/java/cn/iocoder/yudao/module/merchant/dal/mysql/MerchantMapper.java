package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.MerchantPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MerchantMapper extends BaseMapperX<MerchantDO> {

    default PageResult<MerchantDO> selectPage(MerchantPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MerchantDO>()
                .likeIfPresent(MerchantDO::getName, reqVO.getName())
                .eqIfPresent(MerchantDO::getStatus, reqVO.getStatus())
                .likeIfPresent(MerchantDO::getContactPhone, reqVO.getContactPhone())
                .orderByDesc(MerchantDO::getId));
    }

    default MerchantDO selectByUserId(Long userId) {
        return selectOne(MerchantDO::getUserId, userId);
    }

    default MerchantDO selectByOpenId(String openId) {
        return selectOne(MerchantDO::getOpenId, openId);
    }

    /**
     * 原子扣减视频配额：仅在 {@code video_quota_remaining >= delta} 时才扣。
     *
     * <p>靠 SQL WHERE 判定余额，<em>禁止</em>在 Service 里先 {@code selectById} 再 {@code updateById}——
     * 并发下会超扣。参考 {@code ShopInfoMapper#decrementBalanceAtomic}。</p>
     *
     * @return 受影响行数：1 扣减成功；0 余额不足或商户不存在
     */
    @Update("UPDATE merchant_info SET video_quota_remaining = video_quota_remaining - #{delta}" +
            " WHERE id = #{merchantId} AND deleted = 0" +
            " AND video_quota_remaining >= #{delta}")
    int decrementVideoQuotaAtomic(@Param("merchantId") Long merchantId, @Param("delta") int delta);

    /**
     * 原子增加视频配额：套餐购买成功回调 / 失败回补 / 平台手动调整。
     *
     * @return 受影响行数：1 成功；0 商户不存在
     */
    @Update("UPDATE merchant_info SET video_quota_remaining = video_quota_remaining + #{delta}" +
            " WHERE id = #{merchantId} AND deleted = 0")
    int incrementVideoQuotaAtomic(@Param("merchantId") Long merchantId, @Param("delta") int delta);

}
