package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoWithdrawDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoWithdrawMapper;
import cn.iocoder.yudao.module.merchant.service.promo.WithdrawService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 推广积分提现 — 商户小程序入口。
 *
 * <p>包含两类操作：</p>
 * <ul>
 *   <li>用户：apply / my-list（任意 member 用户可调）</li>
 *   <li>商户审批：page / approve / reject / mark-paid（要求 user 在 merchant_info 中登记，
 *       且申请单的 tenantId 与该商户所属 tenant 一致；这避免任意 app 用户绕过审批）</li>
 * </ul>
 *
 * <p>原 admin 端镜像：{@link cn.iocoder.yudao.module.merchant.controller.admin.promo.AdminWithdrawPromoController}
 * 仍保留给后台管理员使用（由 PreAuthorize 守门）。</p>
 */
@Tag(name = "商户小程序 - 推广积分提现")
@RestController
@RequestMapping("/merchant/mini/withdraw")
@Validated
public class AppMerchantWithdrawPromoController {

    @Resource
    private WithdrawService withdrawService;
    @Resource
    private MerchantMapper merchantInfoMapper;
    @Resource
    private ShopPromoWithdrawMapper shopPromoWithdrawMapper;

    // ==================== 用户端 ====================

    @PostMapping("/apply")
    @Operation(summary = "用户申请提现")
    @Parameter(name = "amount", description = "申请金额（分）", required = true)
    @Parameter(name = "tenantId", description = "申请在哪个店（必传，决定哪家商户审批）", required = true)
    @TenantIgnore
    public CommonResult<ShopPromoWithdrawDO> apply(@RequestParam("amount") @NotNull Long amount,
                                                   @RequestParam("tenantId") @NotNull Long tenantId) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(withdrawService.apply(userId, amount, tenantId));
    }

    @GetMapping("/my-list")
    @Operation(summary = "我的提现申请（按时间倒序）")
    public CommonResult<List<ShopPromoWithdrawDO>> myList() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(withdrawService.listByUserId(userId));
    }

    @PostMapping("/confirm-received")
    @Operation(summary = "V044 用户确认已收款 — PAID → COMPLETED")
    @Parameter(name = "id", description = "提现申请 ID", required = true)
    @TenantIgnore
    public CommonResult<Boolean> confirmReceived(@RequestParam("id") @NotNull Long id) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        withdrawService.confirmReceived(id, userId);
        return success(true);
    }

    // ==================== 商户审批端（在小程序里也能审）====================

    @GetMapping("/page")
    @Operation(summary = "审批分页（仅返回当前商户 tenant 下的申请）")
    @TenantIgnore  // user token tenant=0 ≠ 申请单 tenant，要绕过 mybatis-plus 拦截器
    public CommonResult<PageResult<ShopPromoWithdrawDO>> page(
            @RequestParam(value = "status", required = false) String status,
            @Valid PageParam pageParam) {
        Long tenantId = requireMerchantTenantId();
        return success(withdrawService.pageByTenant(tenantId, status, pageParam));
    }

    @PostMapping("/approve")
    @Operation(summary = "审批通过（商户）")
    @TenantIgnore
    public CommonResult<Boolean> approve(@RequestParam("id") @NotNull Long id,
                                         @RequestParam(value = "remark", required = false) String remark) {
        Long processorId = SecurityFrameworkUtils.getLoginUserId();
        validateOwnership(id, requireMerchantTenantId());
        withdrawService.approve(id, processorId, remark);
        return success(true);
    }

    @PostMapping("/reject")
    @Operation(summary = "审批驳回（商户，自动退还推广积分）")
    @TenantIgnore
    public CommonResult<Boolean> reject(@RequestParam("id") @NotNull Long id,
                                        @RequestParam(value = "remark", required = false) String remark) {
        Long processorId = SecurityFrameworkUtils.getLoginUserId();
        validateOwnership(id, requireMerchantTenantId());
        withdrawService.reject(id, processorId, remark);
        return success(true);
    }

    @PostMapping("/mark-paid")
    @Operation(summary = "线下打款后标记已支付（商户）")
    @TenantIgnore
    public CommonResult<Boolean> markPaid(@RequestParam("id") @NotNull Long id,
                                          @RequestParam(value = "remark", required = false) String remark,
                                          @RequestParam(value = "payProofUrl", required = false) String payProofUrl) {
        Long processorId = SecurityFrameworkUtils.getLoginUserId();
        validateOwnership(id, requireMerchantTenantId());
        withdrawService.markPaid(id, processorId, remark, payProofUrl);
        return success(true);
    }

    // ==================== 内部校验 ====================

    /** 当前 login user 必须是某商户 owner，否则 403。 */
    private Long requireMerchantTenantId() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw ServiceExceptionUtil.exception0(401, "请先登录");
        }
        MerchantDO merchant = merchantInfoMapper.selectByUserId(userId);
        if (merchant == null || merchant.getTenantId() == null || merchant.getTenantId() <= 0) {
            throw ServiceExceptionUtil.exception0(403, "无商户审批权限");
        }
        return merchant.getTenantId();
    }

    /** 校验提现单属于该商户 tenant，否则 403。 */
    private void validateOwnership(Long withdrawId, Long merchantTenantId) {
        ShopPromoWithdrawDO row = shopPromoWithdrawMapper.selectById(withdrawId);
        if (row == null) {
            throw ServiceExceptionUtil.exception0(404, "申请不存在");
        }
        if (row.getTenantId() == null || !row.getTenantId().equals(merchantTenantId)) {
            throw ServiceExceptionUtil.exception0(403, "申请不属于本商户");
        }
    }

}
