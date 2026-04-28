package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoWithdrawDO;
import cn.iocoder.yudao.module.merchant.service.promo.WithdrawService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 推广积分提现 — 用户端入口（仅 apply / my-list）。
 *
 * 商户审批操作（approve / reject / mark-paid）已迁出到
 * {@code /admin-api/merchant/promo/withdraw/**}，由 PreAuthorize 守门，
 * 防止任意 app 用户冒充商户审批自己的申请。
 */
@Tag(name = "商户小程序 - 推广积分提现（用户端）")
@RestController
@RequestMapping("/merchant/mini/withdraw")
@Validated
public class AppMerchantWithdrawPromoController {

    @Resource
    private WithdrawService withdrawService;

    @PostMapping("/apply")
    @Operation(summary = "用户申请提现")
    @Parameter(name = "amount", description = "申请金额（分）", required = true)
    public CommonResult<ShopPromoWithdrawDO> apply(@RequestParam("amount") @NotNull Long amount) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(withdrawService.apply(userId, amount));
    }

    @GetMapping("/my-list")
    @Operation(summary = "我的提现申请（按时间倒序）")
    public CommonResult<List<ShopPromoWithdrawDO>> myList() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(withdrawService.listByUserId(userId));
    }

}
