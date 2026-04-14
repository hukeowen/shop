package cn.iocoder.yudao.module.merchant.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.apply.MerchantApplyAuditReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.apply.MerchantApplyPageReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.apply.MerchantApplyRespVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.apply.TenantSubscriptionPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantApplyDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.TenantSubscriptionDO;
import cn.iocoder.yudao.module.merchant.service.MerchantApplyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商户入驻申请")
@RestController
@RequestMapping("/merchant/apply")
@Validated
public class MerchantApplyController {

    @Resource
    private MerchantApplyService merchantApplyService;

    // ==================== 申请审核 ====================

    @GetMapping("/page")
    @Operation(summary = "分页查询申请列表")
    @PreAuthorize("@ss.hasPermission('merchant:apply:query')")
    public CommonResult<PageResult<MerchantApplyRespVO>> getApplyPage(@Valid MerchantApplyPageReqVO pageReqVO) {
        PageResult<MerchantApplyDO> pageResult = merchantApplyService.getApplyPage(pageReqVO);
        return success(new PageResult<>(
                pageResult.getList().stream().map(this::convert).collect(Collectors.toList()),
                pageResult.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "获取申请详情")
    @Parameter(name = "id", description = "申请编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('merchant:apply:query')")
    public CommonResult<MerchantApplyRespVO> getApply(@RequestParam("id") Long id) {
        return success(convert(merchantApplyService.getApply(id)));
    }

    @PostMapping("/audit")
    @Operation(summary = "审核申请（通过或驳回）")
    @PreAuthorize("@ss.hasPermission('merchant:apply:audit')")
    public CommonResult<Boolean> auditApply(@Valid @RequestBody MerchantApplyAuditReqVO auditReqVO) {
        merchantApplyService.auditApply(auditReqVO, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    // ==================== 订阅管理 ====================

    @GetMapping("/subscription/get")
    @Operation(summary = "查询租户订阅状态")
    @Parameter(name = "tenantId", description = "租户编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('merchant:apply:query')")
    public CommonResult<TenantSubscriptionDO> getSubscription(@RequestParam("tenantId") Long tenantId) {
        return success(merchantApplyService.getSubscription(tenantId));
    }

    @PostMapping("/subscription/renew")
    @Operation(summary = "手动续费（天数）")
    @PreAuthorize("@ss.hasPermission('merchant:apply:audit')")
    public CommonResult<Boolean> renewSubscription(
            @RequestParam("tenantId") @NotNull Long tenantId,
            @RequestParam("days") @Min(1) int days) {
        merchantApplyService.renewSubscription(tenantId, days);
        return success(true);
    }

    @GetMapping("/subscription/page")
    @Operation(summary = "分页查询订阅列表")
    @PreAuthorize("@ss.hasPermission('merchant:apply:query')")
    public CommonResult<PageResult<TenantSubscriptionDO>> getSubscriptionPage(
            @Valid TenantSubscriptionPageReqVO pageReqVO) {
        return success(merchantApplyService.getSubscriptionPage(pageReqVO));
    }

    @PostMapping("/subscription/disable")
    @Operation(summary = "禁用租户订阅（封号）")
    @Parameter(name = "tenantId", description = "租户ID", required = true)
    @PreAuthorize("@ss.hasPermission('merchant:apply:audit')")
    public CommonResult<Boolean> disableSubscription(@RequestParam("tenantId") @NotNull Long tenantId) {
        merchantApplyService.disableSubscription(tenantId);
        return success(true);
    }

    // ==================== 转换 ====================

    private MerchantApplyRespVO convert(MerchantApplyDO apply) {
        if (apply == null) {
            return null;
        }
        MerchantApplyRespVO vo = new MerchantApplyRespVO();
        vo.setId(apply.getId());
        vo.setShopName(apply.getShopName());
        vo.setCategoryId(apply.getCategoryId());
        vo.setMobile(apply.getMobile());
        vo.setReferrerMobile(apply.getReferrerMobile());
        vo.setLicenseUrl(apply.getLicenseUrl());
        vo.setIdCardFront(apply.getIdCardFront());
        vo.setIdCardBack(apply.getIdCardBack());
        vo.setLongitude(apply.getLongitude());
        vo.setLatitude(apply.getLatitude());
        vo.setAddress(apply.getAddress());
        vo.setWxMchType(apply.getWxMchType());
        vo.setWxMchId(apply.getWxMchId());
        vo.setStatus(apply.getStatus());
        vo.setRejectReason(apply.getRejectReason());
        vo.setAuditTime(apply.getAuditTime());
        vo.setTenantId(apply.getTenantId());
        vo.setCreateTime(apply.getCreateTime());
        return vo;
    }

}
