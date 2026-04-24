package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.member.controller.admin.user.vo.MemberUserPageReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMemberConsumptionRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.brokerage.vo.user.BrokerageUserPageReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.brokerage.BrokerageUserDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.service.brokerage.BrokerageUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    @Resource
    private TradeOrderMapper tradeOrderMapper;

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

    // ==================== #20 会员消费排行（按消费倒序） ====================

    @GetMapping("/page-by-consumption")
    @Operation(summary = "会员列表（按累计消费倒序）")
    public CommonResult<PageResult<AppMemberConsumptionRespVO>> getPageByConsumption(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Long tenantId = TenantContextHolder.getTenantId();
        long total = tradeOrderMapper.countDistinctBuyers(tenantId);
        int offset = (pageNo - 1) * pageSize;
        List<Map<String, Object>> rows = tradeOrderMapper.selectConsumptionRanking(tenantId, offset, pageSize);

        List<Long> userIds = rows.stream()
                .map(r -> ((Number) r.get("user_id")).longValue())
                .collect(Collectors.toList());
        Map<Long, MemberUserDO> userMap = userIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : memberUserService.getUserList(userIds).stream()
                        .collect(Collectors.toMap(MemberUserDO::getId, u -> u, (a, b) -> a));

        List<AppMemberConsumptionRespVO> voList = rows.stream().map(r -> {
            AppMemberConsumptionRespVO vo = new AppMemberConsumptionRespVO();
            vo.setUserId(((Number) r.get("user_id")).longValue());
            vo.setTotalSpent(((Number) r.get("total_spent")).intValue());
            vo.setOrderCount(((Number) r.get("order_count")).intValue());
            MemberUserDO user = userMap.get(vo.getUserId());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setMobile(user.getMobile());
                vo.setAvatar(user.getAvatar());
            }
            return vo;
        }).collect(Collectors.toList());

        return success(new PageResult<>(voList, total));
    }

}
