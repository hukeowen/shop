package cn.iocoder.yudao.module.merchant.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.*;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商户管理")
@RestController
@RequestMapping("/merchant/info")
@Validated
public class MerchantController {

    @Resource
    private MerchantService merchantService;

    @GetMapping("/page")
    @Operation(summary = "获取商户分页")
    @PreAuthorize("@ss.hasPermission('merchant:info:query')")
    public CommonResult<PageResult<MerchantRespVO>> getMerchantPage(@Valid MerchantPageReqVO pageReqVO) {
        PageResult<MerchantDO> pageResult = merchantService.getMerchantPage(pageReqVO);
        // 简单转换
        return success(new PageResult<>(pageResult.getList().stream().map(this::convert).collect(Collectors.toList()), pageResult.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "获取商户详情")
    @Parameter(name = "id", description = "商户编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('merchant:info:query')")
    public CommonResult<MerchantRespVO> getMerchant(@RequestParam("id") Long id) {
        MerchantDO merchant = merchantService.getMerchant(id);
        return success(convert(merchant));
    }

    @PostMapping("/audit")
    @Operation(summary = "审核商户")
    @PreAuthorize("@ss.hasPermission('merchant:info:audit')")
    public CommonResult<Boolean> auditMerchant(@Valid @RequestBody MerchantAuditReqVO auditReqVO) {
        merchantService.auditMerchant(auditReqVO);
        return success(true);
    }

    @PostMapping("/submit-wx-applyment")
    @Operation(summary = "提交微信支付进件")
    @Parameter(name = "merchantId", description = "商户编号", required = true)
    @PreAuthorize("@ss.hasPermission('merchant:info:wx-applyment')")
    public CommonResult<Boolean> submitWxPayApplyment(@RequestParam("merchantId") Long merchantId) {
        merchantService.submitWxPayApplyment(merchantId);
        return success(true);
    }

    @PostMapping("/generate-qrcode")
    @Operation(summary = "生成商户小程序码")
    @Parameter(name = "merchantId", description = "商户编号", required = true)
    @PreAuthorize("@ss.hasPermission('merchant:info:qrcode')")
    public CommonResult<String> generateMiniAppQrCode(@RequestParam("merchantId") Long merchantId) {
        String url = merchantService.generateMiniAppQrCode(merchantId);
        return success(url);
    }

    @PutMapping("/disable")
    @Operation(summary = "禁用商户")
    @Parameter(name = "id", description = "商户编号", required = true)
    @PreAuthorize("@ss.hasPermission('merchant:info:update')")
    public CommonResult<Boolean> disableMerchant(@RequestParam("id") Long id) {
        merchantService.disableMerchant(id);
        return success(true);
    }

    @PutMapping("/enable")
    @Operation(summary = "启用商户")
    @Parameter(name = "id", description = "商户编号", required = true)
    @PreAuthorize("@ss.hasPermission('merchant:info:update')")
    public CommonResult<Boolean> enableMerchant(@RequestParam("id") Long id) {
        merchantService.enableMerchant(id);
        return success(true);
    }

    private MerchantRespVO convert(MerchantDO merchant) {
        if (merchant == null) {
            return null;
        }
        MerchantRespVO respVO = new MerchantRespVO();
        respVO.setId(merchant.getId());
        respVO.setName(merchant.getName());
        respVO.setLogo(merchant.getLogo());
        respVO.setContactName(merchant.getContactName());
        respVO.setContactPhone(merchant.getContactPhone());
        respVO.setLicenseNo(merchant.getLicenseNo());
        respVO.setLicenseUrl(merchant.getLicenseUrl());
        respVO.setBusinessCategory(merchant.getBusinessCategory());
        respVO.setStatus(merchant.getStatus());
        respVO.setRejectReason(merchant.getRejectReason());
        respVO.setAuditTime(merchant.getAuditTime());
        respVO.setWxSubMchId(merchant.getWxSubMchId());
        respVO.setWxApplymentStatus(merchant.getWxApplymentStatus());
        respVO.setMiniAppQrCodeUrl(merchant.getMiniAppQrCodeUrl());
        respVO.setDouyinBound(merchant.getDouyinAccessToken() != null);
        respVO.setCreateTime(merchant.getCreateTime());
        return respVO;
    }

}
