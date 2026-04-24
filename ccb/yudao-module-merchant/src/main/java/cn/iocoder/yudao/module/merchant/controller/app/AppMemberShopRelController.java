package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MemberShopRelDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MemberWithdrawApplyDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MemberWithdrawApplyMapper;
import cn.iocoder.yudao.module.merchant.service.MemberShopRelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 商户小程序 - 会员×商户关系（余额/积分/推荐人）
 */
@Tag(name = "商户小程序 - 会员店铺关系")
@RestController
@RequestMapping("/merchant/mini/member-rel")
@Validated
public class AppMemberShopRelController {

    @Resource
    private MemberShopRelService memberShopRelService;
    @Resource
    private MemberWithdrawApplyMapper memberWithdrawApplyMapper;

    /**
     * 获取当前登录用户在当前 tenant 的关系记录。
     * 不存在时返回默认空对象（balance=0, points=0）。
     */
    @GetMapping("/my")
    @Operation(summary = "获取当前用户在当前店铺的余额/积分")
    @TenantIgnore
    public CommonResult<MemberShopRelDO> getMy() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        Long tenantId = TenantContextHolder.getTenantId();
        MemberShopRelDO rel = memberShopRelService.getByUserAndTenant(userId, tenantId);
        if (rel == null) {
            rel = MemberShopRelDO.builder()
                    .userId(userId)
                    .tenantId(tenantId)
                    .balance(0)
                    .points(0)
                    .build();
        }
        return success(rel);
    }

    /**
     * 记录进店：更新 lastVisitAt，首次写 firstVisitAt，可绑定推荐人。
     */
    @PostMapping("/visit")
    @Operation(summary = "记录进店（更新进店时间 + 绑定推荐人）")
    @Parameter(name = "tenantId", description = "商户租户ID", required = true)
    @Parameter(name = "referrerUserId", description = "推荐人用户ID（可选）")
    @TenantIgnore
    public CommonResult<Boolean> visit(@RequestParam("tenantId") @NotNull Long tenantId,
                                       @RequestParam(value = "referrerUserId", required = false) Long referrerUserId) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        // getOrCreate 负责写 firstVisitAt（仅首次）
        memberShopRelService.getOrCreate(userId, tenantId);
        // 绑定推荐人（幂等，已有上线不覆盖）
        if (referrerUserId != null) {
            memberShopRelService.bindReferrer(userId, tenantId, referrerUserId);
        }
        // 更新最近进店时间
        memberShopRelService.updateLastVisitAt(userId, tenantId);
        return success(true);
    }

    /**
     * 余额单向转积分（1分=1积分）。
     */
    @PostMapping("/balance-to-points")
    @Operation(summary = "余额转积分（1:1）")
    @Parameter(name = "amountFen", description = "转换金额（分）", required = true)
    @TenantIgnore
    public CommonResult<Boolean> balanceToPoints(@RequestParam("amountFen") @NotNull Integer amountFen) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        Long tenantId = TenantContextHolder.getTenantId();
        if (amountFen <= 0) {
            throw exception0(1_031_001_002, "转换金额必须大于 0");
        }
        memberShopRelService.balanceToPoints(userId, tenantId, amountFen);
        return success(true);
    }

    /**
     * 用户发起提现申请：原子扣减余额后插入申请记录。
     */
    @PostMapping("/withdraw")
    @Operation(summary = "用户发起提现申请")
    @TenantIgnore
    public CommonResult<Boolean> withdraw(
            @RequestParam("amount") @NotNull Integer amount,
            @RequestParam("withdrawType") @NotNull Integer withdrawType,
            @RequestParam(value = "accountName", required = false) String accountName,
            @RequestParam(value = "accountNo", required = false) String accountNo,
            @RequestParam(value = "bankName", required = false) String bankName) {
        if (amount <= 0) {
            throw exception0(1_031_001_003, "提现金额必须大于 0");
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        Long tenantId = TenantContextHolder.getTenantId();
        // 原子扣减余额，返回 0 说明余额不足
        int affected = memberShopRelService.deductBalance(userId, tenantId, amount);
        if (affected == 0) {
            throw exception0(1_031_001_004, "余额不足");
        }
        MemberWithdrawApplyDO apply = MemberWithdrawApplyDO.builder()
                .userId(userId)
                .tenantId(tenantId)
                .amount(amount)
                .withdrawType(withdrawType)
                .accountName(accountName)
                .accountNo(accountNo)
                .bankName(bankName)
                .status(0)
                .build();
        memberWithdrawApplyMapper.insert(apply);
        return success(true);
    }

    /**
     * 查询当前用户在当前店铺的提现记录列表。
     */
    @GetMapping("/withdraw/list")
    @Operation(summary = "查询当前用户提现记录")
    @TenantIgnore
    public CommonResult<List<MemberWithdrawApplyDO>> withdrawList() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        Long tenantId = TenantContextHolder.getTenantId();
        return success(memberWithdrawApplyMapper.selectByUserIdAndTenantId(userId, tenantId));
    }

}
