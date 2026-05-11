package cn.iocoder.yudao.module.merchant.controller.admin;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.merchant.dal.dataobject.saas.SaasPackageConfigDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.saas.SaasPackageConfigMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - SaaS 套餐配置 CRUD（运营调价 / 调赠送 AI 视频次数 / 上下架）
 *
 * <p>权限点：merchant:saas-package:{query,edit}</p>
 */
@Tag(name = "管理后台 - SaaS 套餐配置")
@RestController
@RequestMapping("/merchant/saas/package")
@Validated
public class SaasPackageConfigController {

    @Resource
    private SaasPackageConfigMapper mapper;

    @GetMapping("/list")
    @Operation(summary = "全量列表（含下架）")
    @PreAuthorize("@ss.hasPermission('merchant:saas-package:query')")
    @TenantIgnore
    public CommonResult<List<SaasPackageConfigDO>> list() {
        return success(mapper.selectList());
    }

    @PutMapping("/save")
    @Operation(summary = "保存套餐配置（id 存在则更新；不存在则建）")
    @PreAuthorize("@ss.hasPermission('merchant:saas-package:edit')")
    @TenantIgnore
    public CommonResult<Long> save(@Valid @RequestBody SaasPackageConfigDO reqVO) {
        if (reqVO.getLevel() == null || reqVO.getLevel().isEmpty()) {
            throw ServiceExceptionUtil.exception0(BAD_REQUEST.getCode(), "level 不能为空");
        }
        if (reqVO.getPriceFen() == null || reqVO.getPriceFen() <= 0) {
            throw ServiceExceptionUtil.exception0(BAD_REQUEST.getCode(), "价格必须 > 0");
        }
        if (reqVO.getDurationDays() == null || reqVO.getDurationDays() <= 0) {
            throw ServiceExceptionUtil.exception0(BAD_REQUEST.getCode(), "天数必须 > 0");
        }
        if (reqVO.getAiVideoGrant() == null || reqVO.getAiVideoGrant() < 0) {
            reqVO.setAiVideoGrant(0);
        }
        if (reqVO.getId() != null) {
            mapper.updateById(reqVO);
        } else {
            // level 唯一约束（V034 加了 uk_level）
            SaasPackageConfigDO existing = mapper.selectByLevel(reqVO.getLevel());
            if (existing != null) {
                throw ServiceExceptionUtil.exception0(BAD_REQUEST.getCode(),
                        "level=" + reqVO.getLevel() + " 已存在，请直接编辑");
            }
            mapper.insert(reqVO);
        }
        return success(reqVO.getId());
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "上下架")
    @PreAuthorize("@ss.hasPermission('merchant:saas-package:edit')")
    @TenantIgnore
    public CommonResult<Boolean> updateStatus(@PathVariable("id") Long id,
                                              @RequestParam("status") Integer status) {
        SaasPackageConfigDO patch = new SaasPackageConfigDO();
        patch.setId(id);
        patch.setStatus(status);
        mapper.updateById(patch);
        return success(true);
    }
}
