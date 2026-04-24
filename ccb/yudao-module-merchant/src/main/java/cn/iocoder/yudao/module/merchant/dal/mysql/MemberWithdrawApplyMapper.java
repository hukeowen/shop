package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MemberWithdrawApplyDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MemberWithdrawApplyMapper extends BaseMapperX<MemberWithdrawApplyDO> {

    default List<MemberWithdrawApplyDO> selectByUserIdAndTenantId(Long userId, Long tenantId) {
        return selectList(new LambdaQueryWrapper<MemberWithdrawApplyDO>()
                .eq(MemberWithdrawApplyDO::getUserId, userId)
                .eq(MemberWithdrawApplyDO::getTenantId, tenantId)
                .orderByDesc(MemberWithdrawApplyDO::getId));
    }

}
