package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo.AiVideoQuotaLogPageReqVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.aivideo.AppAiVideoPackageRespVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.aivideo.AppVideoQuotaLogRespVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.aivideo.AppVideoQuotaRespVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.AiVideoPackageDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantVideoQuotaLogDO;
import cn.iocoder.yudao.module.merchant.service.AiVideoPackageService;
import cn.iocoder.yudao.module.merchant.service.MerchantService;
import cn.iocoder.yudao.module.merchant.service.MerchantVideoQuotaLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.AI_VIDEO_MERCHANT_NOT_FOUND;

/**
 * 商户小程序 - AI 视频配额 / 套餐列表 / 流水查询（Phase 0.3.2）。
 *
 * <p>所有端点以 JWT 登录态为商户边界——当前登录用户对应的 merchant。</p>
 */
@Tag(name = "商户小程序 - AI 视频配额")
@RestController
@RequestMapping("/merchant/mini/video-quota")
@Validated
public class AppMerchantVideoQuotaController {

    @Resource
    private MerchantService merchantService;

    @Resource
    private AiVideoPackageService packageService;

    @Resource
    private MerchantVideoQuotaLogService quotaLogService;

    @GetMapping("/me")
    @Operation(summary = "查询当前商户剩余视频配额")
    public CommonResult<AppVideoQuotaRespVO> me() {
        MerchantDO merchant = getMerchantOrThrow();
        AppVideoQuotaRespVO vo = new AppVideoQuotaRespVO();
        vo.setRemaining(merchant.getVideoQuotaRemaining() == null ? 0 : merchant.getVideoQuotaRemaining());
        vo.setUpdateTime(merchant.getUpdateTime());
        return success(vo);
    }

    @GetMapping("/packages")
    @Operation(summary = "查询在架 AI 视频套餐列表")
    public CommonResult<List<AppAiVideoPackageRespVO>> listPackages() {
        // 先校验商户身份（避免匿名扫库）
        getMerchantOrThrow();
        List<AiVideoPackageDO> list = packageService.listEnabledPackages();
        return success(BeanUtils.toBean(list, AppAiVideoPackageRespVO.class));
    }

    @GetMapping("/logs")
    @Operation(summary = "查询当前商户的视频配额流水")
    public CommonResult<PageResult<AppVideoQuotaLogRespVO>> logs(@Valid AiVideoQuotaLogPageReqVO reqVO) {
        MerchantDO merchant = getMerchantOrThrow();
        // 强制以当前商户 id 为过滤键，忽略 reqVO.merchantId（防越权）
        PageResult<MerchantVideoQuotaLogDO> page =
                quotaLogService.getLogPageByMerchant(merchant.getId(), reqVO.getBizType(), reqVO);
        return success(BeanUtils.toBean(page, AppVideoQuotaLogRespVO.class));
    }

    private MerchantDO getMerchantOrThrow() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        MerchantDO merchant = userId == null ? null : merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            throw exception(AI_VIDEO_MERCHANT_NOT_FOUND);
        }
        return merchant;
    }

}
