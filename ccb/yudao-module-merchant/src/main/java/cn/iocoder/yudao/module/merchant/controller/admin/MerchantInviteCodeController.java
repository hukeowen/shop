package cn.iocoder.yudao.module.merchant.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.MerchantInviteCodeCreateReqVO;
import cn.iocoder.yudao.module.merchant.service.MerchantInviteCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * BD 邀请码管理（管理后台）
 */
@Tag(name = "管理后台 - 商户邀请码")
@RestController
@RequestMapping("/merchant/invite-code")
@Validated
public class MerchantInviteCodeController {

    @Resource
    private MerchantInviteCodeService inviteCodeService;

    @PostMapping("/create")
    @Operation(summary = "生成邀请码")
    @PreAuthorize("@ss.hasPermission('merchant:invite-code:create')")
    public CommonResult<String> createCode(@Valid @RequestBody MerchantInviteCodeCreateReqVO reqVO) {
        Long operatorId = SecurityFrameworkUtils.getLoginUserId();
        return success(inviteCodeService.createCode(operatorId, reqVO.getUsageLimit(), reqVO.getRemark()));
    }
}
