package cn.iocoder.yudao.module.merchant.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.withdraw.MerchantWithdrawAuditReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.withdraw.MerchantWithdrawPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantWithdrawApplyDO;
import cn.iocoder.yudao.module.merchant.service.MerchantWithdrawService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商户提现审核")
@RestController
@RequestMapping("/merchant/withdraw")
@Validated
public class MerchantWithdrawController {

    @Resource
    private MerchantWithdrawService merchantWithdrawService;

    @GetMapping("/page")
    @Operation(summary = "分页查询提现申请")
    @PreAuthorize("@ss.hasPermission('merchant:withdraw:query')")
    public CommonResult<PageResult<MerchantWithdrawApplyDO>> getWithdrawPage(
            @Valid MerchantWithdrawPageReqVO pageReqVO) {
        return success(merchantWithdrawService.getWithdrawPage(pageReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获取提现申请详情")
    @Parameter(name = "id", description = "申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('merchant:withdraw:query')")
    public CommonResult<MerchantWithdrawApplyDO> getWithdraw(@RequestParam("id") Long id) {
        return success(merchantWithdrawService.getWithdraw(id));
    }

    @PostMapping("/audit")
    @Operation(summary = "审核提现申请（通过上传凭证 / 驳回填写原因）")
    @PreAuthorize("@ss.hasPermission('merchant:withdraw:audit')")
    public CommonResult<Boolean> auditWithdraw(@Valid @RequestBody MerchantWithdrawAuditReqVO auditReqVO) {
        merchantWithdrawService.auditWithdraw(auditReqVO, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

}
