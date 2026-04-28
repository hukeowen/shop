package cn.iocoder.yudao.module.merchant.controller.admin.promo;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoWithdrawDO;
import cn.iocoder.yudao.module.merchant.service.promo.WithdrawService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 推广积分提现 — 商户后台审批端（/admin-api 受 @PreAuthorize 守护）。
 *
 * 用户申请入口在 {@link cn.iocoder.yudao.module.merchant.controller.app.AppMerchantWithdrawPromoController}。
 * 此 Controller 上的状态变更操作必须由具备 merchant:withdraw:approve 权限的管理员执行，
 * 防止 app 用户绕过审批自己审批自己的申请。
 */
@Tag(name = "管理后台 - 推广积分提现审批")
@RestController
@RequestMapping("/merchant/promo/withdraw")
@Validated
public class AdminWithdrawPromoController {

    @Resource
    private WithdrawService withdrawService;

    @GetMapping("/page")
    @Operation(summary = "提现申请分页（status 可选过滤）")
    @PreAuthorize("@ss.hasPermission('merchant:promo-withdraw:query')")
    public CommonResult<PageResult<ShopPromoWithdrawDO>> page(
            @RequestParam(value = "status", required = false) String status,
            @Valid PageParam pageParam) {
        return success(withdrawService.page(status, pageParam));
    }

    @PostMapping("/approve")
    @Operation(summary = "审批通过")
    @PreAuthorize("@ss.hasPermission('merchant:promo-withdraw:approve')")
    public CommonResult<Boolean> approve(@RequestParam("id") @NotNull Long id,
                                         @RequestParam(value = "remark", required = false) String remark) {
        Long processorId = SecurityFrameworkUtils.getLoginUserId();
        withdrawService.approve(id, processorId, remark);
        return success(true);
    }

    @PostMapping("/reject")
    @Operation(summary = "审批驳回（自动退还推广积分）")
    @PreAuthorize("@ss.hasPermission('merchant:promo-withdraw:approve')")
    public CommonResult<Boolean> reject(@RequestParam("id") @NotNull Long id,
                                        @RequestParam(value = "remark", required = false) String remark) {
        Long processorId = SecurityFrameworkUtils.getLoginUserId();
        withdrawService.reject(id, processorId, remark);
        return success(true);
    }

    @PostMapping("/mark-paid")
    @Operation(summary = "线下打款后标记已支付")
    @PreAuthorize("@ss.hasPermission('merchant:promo-withdraw:approve')")
    public CommonResult<Boolean> markPaid(@RequestParam("id") @NotNull Long id,
                                          @RequestParam(value = "remark", required = false) String remark) {
        Long processorId = SecurityFrameworkUtils.getLoginUserId();
        withdrawService.markPaid(id, processorId, remark);
        return success(true);
    }

}
