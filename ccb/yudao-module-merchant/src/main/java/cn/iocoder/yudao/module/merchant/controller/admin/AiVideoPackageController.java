package cn.iocoder.yudao.module.merchant.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo.AiVideoPackagePageReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo.AiVideoPackageRespVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo.AiVideoPackageSaveReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo.AiVideoQuotaLogPageReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo.AiVideoQuotaLogRespVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.AiVideoPackageDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantVideoQuotaLogDO;
import cn.iocoder.yudao.module.merchant.service.AiVideoPackageService;
import cn.iocoder.yudao.module.merchant.service.MerchantVideoQuotaLogService;
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
 * 管理后台 - AI 视频套餐 + 配额流水（Phase 0.3.1）。
 */
@Tag(name = "管理后台 - AI 视频套餐")
@RestController
@RequestMapping("/merchant/ai-video/package")
@Validated
public class AiVideoPackageController {

    @Resource
    private AiVideoPackageService packageService;

    @Resource
    private MerchantVideoQuotaLogService quotaLogService;

    @PostMapping("/create")
    @Operation(summary = "创建套餐")
    @PreAuthorize("@ss.hasPermission('merchant:ai-video-package:create')")
    public CommonResult<Long> createPackage(@Valid @RequestBody AiVideoPackageSaveReqVO reqVO) {
        return success(packageService.createPackage(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新套餐")
    @PreAuthorize("@ss.hasPermission('merchant:ai-video-package:update')")
    public CommonResult<Boolean> updatePackage(@Valid @RequestBody AiVideoPackageSaveReqVO reqVO) {
        packageService.updatePackage(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除套餐")
    @Parameter(name = "id", description = "套餐 ID", required = true)
    @PreAuthorize("@ss.hasPermission('merchant:ai-video-package:delete')")
    public CommonResult<Boolean> deletePackage(@RequestParam("id") Long id) {
        packageService.deletePackage(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取套餐详情")
    @Parameter(name = "id", description = "套餐 ID", required = true)
    @PreAuthorize("@ss.hasPermission('merchant:ai-video-package:query')")
    public CommonResult<AiVideoPackageRespVO> getPackage(@RequestParam("id") Long id) {
        AiVideoPackageDO pkg = packageService.getPackage(id);
        return success(BeanUtils.toBean(pkg, AiVideoPackageRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获取套餐分页")
    @PreAuthorize("@ss.hasPermission('merchant:ai-video-package:query')")
    public CommonResult<PageResult<AiVideoPackageRespVO>> getPackagePage(@Valid AiVideoPackagePageReqVO reqVO) {
        PageResult<AiVideoPackageDO> page = packageService.getPackagePage(reqVO);
        return success(BeanUtils.toBean(page, AiVideoPackageRespVO.class));
    }

    // ========== 配额流水 ==========

    @GetMapping("/quota-log/page")
    @Operation(summary = "获取视频配额流水分页")
    @PreAuthorize("@ss.hasPermission('merchant:ai-video-package:query')")
    public CommonResult<PageResult<AiVideoQuotaLogRespVO>> getQuotaLogPage(
            @Valid AiVideoQuotaLogPageReqVO reqVO) {
        PageResult<MerchantVideoQuotaLogDO> page = quotaLogService.getLogPage(reqVO);
        return success(BeanUtils.toBean(page, AiVideoQuotaLogRespVO.class));
    }

}
