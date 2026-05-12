package cn.iocoder.yudao.module.member.api.user;

import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.member.convert.user.MemberUserConvert;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.USER_MOBILE_NOT_EXISTS;

/**
 * 会员用户的 API 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
public class MemberUserApiImpl implements MemberUserApi {

    @Resource
    private MemberUserService userService;

    @Override
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore
    public MemberUserRespDTO getUser(Long id) {
        // C 端用户跨店下单场景：
        //   · 用户 token tenant=用户原始租户
        //   · checkout 切到商户租户写订单
        //   · 此时 trade 计算价格调本 API 查 user，若不 ignore 则按商户租户找不到 → NPE
        // MemberUserDO 虽 extends TenantBaseDO，但 user 本质是平台级实体，跨租户查合理。
        MemberUserDO user = userService.getUser(id);
        return MemberUserConvert.INSTANCE.convert2(user);
    }

    @Override
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore
    public List<MemberUserRespDTO> getUserList(Collection<Long> ids) {
        return MemberUserConvert.INSTANCE.convertList2(userService.getUserList(ids));
    }

    @Override
    public List<MemberUserRespDTO> getUserListByNickname(String nickname) {
        return MemberUserConvert.INSTANCE.convertList2(userService.getUserListByNickname(nickname));
    }

    @Override
    public MemberUserRespDTO getUserByMobile(String mobile) {
        return MemberUserConvert.INSTANCE.convert2(userService.getUserByMobile(mobile));
    }

    @Override
    public void validateUser(Long id) {
        MemberUserDO user = userService.getUser(id);
        if (user == null) {
            throw exception(USER_MOBILE_NOT_EXISTS);
        }
    }

}
