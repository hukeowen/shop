package cn.iocoder.yudao.module.merchant.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.appversion.AppVersionPageReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.appversion.AppVersionRespVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.appversion.AppVersionSaveReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.AppVersionDO;
import cn.iocoder.yudao.module.merchant.service.AppVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - App 版本管理（平台运营 → App 自动升级）
 */
@Tag(name = "管理后台 - App 版本管理")
@RestController
@RequestMapping("/merchant/app-version")
@Validated
public class AppVersionController {

    @Resource
    private AppVersionService appVersionService;

    @PostMapping("/create")
    @Operation(summary = "创建 App 版本")
    @PreAuthorize("@ss.hasPermission('merchant:app-version:create')")
    public CommonResult<Long> createAppVersion(@Valid @RequestBody AppVersionSaveReqVO reqVO) {
        return success(appVersionService.createAppVersion(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新 App 版本")
    @PreAuthorize("@ss.hasPermission('merchant:app-version:update')")
    public CommonResult<Boolean> updateAppVersion(@Valid @RequestBody AppVersionSaveReqVO reqVO) {
        appVersionService.updateAppVersion(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除 App 版本")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('merchant:app-version:delete')")
    public CommonResult<Boolean> deleteAppVersion(@RequestParam("id") Long id) {
        appVersionService.deleteAppVersion(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得 App 版本")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('merchant:app-version:query')")
    public CommonResult<AppVersionRespVO> getAppVersion(@RequestParam("id") Long id) {
        AppVersionDO version = appVersionService.getAppVersion(id);
        return success(BeanUtils.toBean(version, AppVersionRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得 App 版本分页")
    @PreAuthorize("@ss.hasPermission('merchant:app-version:query')")
    public CommonResult<PageResult<AppVersionRespVO>> getAppVersionPage(@Valid AppVersionPageReqVO reqVO) {
        PageResult<AppVersionDO> pageResult = appVersionService.getAppVersionPage(reqVO);
        return success(BeanUtils.toBean(pageResult, AppVersionRespVO.class));
    }

}
