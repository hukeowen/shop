package cn.iocoder.yudao.module.merchant.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.referral.MerchantReferralConfigRespVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.referral.MerchantReferralRespVO;
import cn.iocoder.yudao.module.merchant.service.MerchantReferralService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 平台推N返1裂变")
@RestController
@RequestMapping("/merchant/referral")
@Validated
public class MerchantReferralController {

    @Resource
    private MerchantReferralService merchantReferralService;

    @GetMapping("/config")
    @Operation(summary = "获取平台推N返1配置（N值、奖励天数、开关）")
    @PreAuthorize("@ss.hasPermission('merchant:referral:query')")
    public CommonResult<MerchantReferralConfigRespVO> getConfig() {
        return success(merchantReferralService.getConfig());
    }

    @GetMapping("/list")
    @Operation(summary = "查询某推荐人的推荐记录列表")
    @Parameter(name = "referrerTenantId", description = "推荐人租户ID", required = true)
    @PreAuthorize("@ss.hasPermission('merchant:referral:query')")
    public CommonResult<List<MerchantReferralRespVO>> getReferralList(
            @RequestParam("referrerTenantId") Long referrerTenantId) {
        return success(merchantReferralService.getReferralList(referrerTenantId));
    }

}
