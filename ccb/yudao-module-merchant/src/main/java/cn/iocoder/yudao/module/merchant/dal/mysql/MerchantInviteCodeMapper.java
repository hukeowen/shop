package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantInviteCodeDO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MerchantInviteCodeMapper extends BaseMapperX<MerchantInviteCodeDO> {

    default MerchantInviteCodeDO selectByCode(String code) {
        return selectOne(Wrappers.<MerchantInviteCodeDO>lambdaQuery()
                .eq(MerchantInviteCodeDO::getCode, code));
    }

    /**
     * 原子自增 used_count，仅当 enabled=1 且（usage_limit=-1 或 used_count+1 &lt;= usage_limit）时成功
     *
     * @return 影响行数；0 表示不满足条件（已禁用或已用完）
     */
    @Update("UPDATE merchant_invite_code " +
            "SET used_count = used_count + 1, update_time = NOW() " +
            "WHERE id = #{id} " +
            "  AND enabled = 1 " +
            "  AND deleted = 0 " +
            "  AND (usage_limit = -1 OR used_count < usage_limit)")
    int incrementUsedCount(@Param("id") Long id);
}
