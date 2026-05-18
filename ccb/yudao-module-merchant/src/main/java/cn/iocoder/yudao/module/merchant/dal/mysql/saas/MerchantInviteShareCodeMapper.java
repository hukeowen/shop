package cn.iocoder.yudao.module.merchant.dal.mysql.saas;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.saas.MerchantInviteShareCodeDO;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MerchantInviteShareCodeMapper extends BaseMapperX<MerchantInviteShareCodeDO> {

    /** 按 code 查（全局唯一，跨租户读，因为 B 注册时还不知道哪个 tenant）。 */
    default MerchantInviteShareCodeDO selectByCode(String code) {
        // 跨租户读：B 注册时没有 tenant 上下文 / 上下文 = 0；但 code 写入时是 A 的 tenant
        // 直接走全表 by code，唯一键 uk_code 兜底
        return cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.executeIgnore(() ->
                selectOne(new LambdaQueryWrapperX<MerchantInviteShareCodeDO>()
                        .eq(MerchantInviteShareCodeDO::getCode, code)));
    }

    /** 按邀请人 user_id 查（商户后台「我的分享码」入口）。 */
    default MerchantInviteShareCodeDO selectByReferrerUserId(Long userId) {
        // 同样跨租户：商户登录的 tenant 是自己的，code 也写在该 tenant；但安全起见 ignore
        return cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.executeIgnore(() ->
                selectOne(new LambdaQueryWrapperX<MerchantInviteShareCodeDO>()
                        .eq(MerchantInviteShareCodeDO::getReferrerUserId, userId)));
    }

    /** 原子 +1：成功注册后调用。 */
    @Update("UPDATE merchant_invite_share_code SET used_count = used_count + 1, update_time = NOW() "
          + "WHERE code = #{code} AND deleted = b'0' AND enabled = b'1'")
    int incrementUsedCount(@Param("code") String code);

}
