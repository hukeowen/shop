package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.merchant.controller.app.vo.appversion.AppVersionCheckRespVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.AppVersionDO;
import cn.iocoder.yudao.module.merchant.service.AppVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * App 端 - App 版本检查（自动升级）。
 *
 * <p>商户端 App 启动时无需登录即可拉取最新版本，故 {@link PermitAll} + {@link TenantIgnore}。</p>
 */
@Tag(name = "App 端 - App 版本检查")
@RestController
@RequestMapping("/merchant/app-version")
@Validated
public class AppAppVersionController {

    @Resource
    private AppVersionService appVersionService;

    @GetMapping("/latest")
    @Operation(summary = "获取最新 App 版本（用于比对升级）")
    @Parameter(name = "platform", description = "平台 android/ios，默认 android")
    @PermitAll
    @TenantIgnore
    public CommonResult<AppVersionCheckRespVO> getLatest(
            @RequestParam(value = "platform", defaultValue = "android") String platform) {
        AppVersionDO latest = appVersionService.getLatestPublished(platform);
        if (latest == null) {
            return success(null);
        }
        AppVersionCheckRespVO vo = new AppVersionCheckRespVO();
        vo.setPlatform(latest.getPlatform());
        vo.setVersionName(latest.getVersionName());
        vo.setVersionCode(latest.getVersionCode());
        vo.setDownloadUrl(latest.getDownloadUrl());
        vo.setUpdateLog(latest.getUpdateLog());
        vo.setForceUpdate(Boolean.TRUE.equals(latest.getForceUpdate()));
        vo.setFileSize(latest.getFileSize());
        return success(vo);
    }

}
