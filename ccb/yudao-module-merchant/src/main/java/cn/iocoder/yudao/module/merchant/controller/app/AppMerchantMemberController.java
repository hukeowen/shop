package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.user.vo.MemberUserPageReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import cn.iocoder.yudao.module.trade.controller.admin.brokerage.vo.user.BrokerageUserPageReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.brokerage.BrokerageUserDO;
import cn.iocoder.yudao.module.trade.service.brokerage.BrokerageUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 商户小程序 - 会员管理（#20）+ 推广大使（#23）
 */
@Tag(name = "商户小程序 - 会员与推广管理")
@RestController
@RequestMapping("/merchant/mini/member")
@Validated
public class AppMerchantMemberController {

    @Resource
    private MemberUserService memberUserService;
    @Resource
    private BrokerageUserService brokerageUserService;

    // ==================== #20 会员列表 ====================

    @GetMapping("/page")
    @Operation(summary = "分页查询会员列表")
    public CommonResult<PageResult<MemberUserDO>> getMemberPage(@Valid MemberUserPageReqVO pageReqVO) {
        return success(memberUserService.getUserPage(pageReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获取会员详情")
    @Parameter(name = "id", description = "会员ID", required = true)
    public CommonResult<MemberUserDO> getMember(@RequestParam("id") Long id) {
        return success(memberUserService.getUser(id));
    }

    // ==================== #20 会员推广关系 ====================

    @GetMapping("/brokerage-user")
    @Operation(summary = "获取会员的推广用户信息（上下级关系）")
    @Parameter(name = "userId", description = "会员用户ID", required = true)
    public CommonResult<BrokerageUserDO> getBrokerageUser(@RequestParam("userId") Long userId) {
        return success(brokerageUserService.getBrokerageUser(userId));
    }

    // ==================== #23 推广大使列表 ====================

    @GetMapping("/brokerage-user/page")
    @Operation(summary = "分页查询推广大使列表（含业绩）")
    public CommonResult<PageResult<BrokerageUserDO>> getBrokerageUserPage(@Valid BrokerageUserPageReqVO pageReqVO) {
        return success(brokerageUserService.getBrokerageUserPage(pageReqVO));
    }

}
