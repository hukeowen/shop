package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.saas.MerchantInviteShareCodeDO;
import cn.iocoder.yudao.module.merchant.service.MerchantService;
import cn.iocoder.yudao.module.merchant.service.saas.MerchantInviteShareCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 商户开店分享码 API（商户端 + 入驻匿名查询）。
 */
@Tag(name = "商户小程序 - 开店分享码")
@RestController
@RequestMapping("/merchant/mini/invite-share-code")
@Validated
public class AppMerchantInviteShareCodeController {

    @Resource
    private MerchantInviteShareCodeService inviteShareCodeService;
    @Resource
    private MerchantService merchantService;

    @GetMapping("/my")
    @Operation(summary = "取或生成「我的开店分享码」（商户已登录）")
    @SecurityRequirement(name = "Authorization")
    public CommonResult<MerchantInviteShareCodeDO> getOrCreateMyCode() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw new ServiceException(401, "请先登录");
        }
        MerchantDO merchant = merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            throw new ServiceException(403, "当前账号未开通商户，无法生成分享码");
        }
        return success(inviteShareCodeService.getOrCreate(userId, merchant.getTenantId()));
    }

    @GetMapping("/lookup")
    @Operation(summary = "按分享码查询邀请人信息（入驻页解析 ?invite= 用，匿名可访问）")
    @Parameter(name = "code", description = "分享码", required = true)
    public CommonResult<Map<String, Object>> lookup(@RequestParam("code") @NotBlank String code) {
        MerchantInviteShareCodeDO record = inviteShareCodeService.findByCode(code);
        Map<String, Object> resp = new HashMap<>();
        if (record == null) {
            resp.put("valid", false);
            return success(resp);
        }
        resp.put("valid", true);
        resp.put("referrerUserId", record.getReferrerUserId());
        resp.put("referrerTenantId", record.getReferrerTenantId());
        return success(resp);
    }

}
