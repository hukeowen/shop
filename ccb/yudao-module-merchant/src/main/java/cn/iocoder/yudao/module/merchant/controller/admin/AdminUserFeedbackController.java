package cn.iocoder.yudao.module.merchant.controller.admin;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.merchant.dal.dataobject.UserFeedbackDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.UserFeedbackMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 用户反馈管理。
 *
 * <p>查看 / 回复 / 关闭。{@code user_feedback} 是平台级表（不继承 TenantBaseDO），
 * 默认只看 <b>当前 tenantId == 该反馈 tenantId</b> 的数据；超管 (tenantId=1) 可
 * 通过 {@code tenantId=0} 查看平台通用反馈，或 {@code allTenants=true} 跨租户查看。</p>
 */
@Tag(name = "管理后台 - 用户反馈")
@RestController
@RequestMapping("/merchant/user-feedback")
@Validated
public class AdminUserFeedbackController {

    private static final long PLATFORM_TENANT_ID = 1L;

    @Resource
    private UserFeedbackMapper userFeedbackMapper;

    @GetMapping("/page")
    @Operation(summary = "分页查询反馈列表")
    @PreAuthorize("@ss.hasPermission('merchant:user-feedback:query')")
    @TenantIgnore
    public CommonResult<PageResult<UserFeedbackDO>> page(@Valid PageParam pageParam,
                                                          @RequestParam(value = "status", required = false) Integer status,
                                                          @RequestParam(value = "category", required = false) String category,
                                                          @RequestParam(value = "userId", required = false) Long userId,
                                                          @RequestParam(value = "allTenants", required = false, defaultValue = "false") Boolean allTenants) {
        Long currentTenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId();
        LambdaQueryWrapperX<UserFeedbackDO> q = new LambdaQueryWrapperX<UserFeedbackDO>()
                .eqIfPresent(UserFeedbackDO::getStatus, status)
                .eqIfPresent(UserFeedbackDO::getCategory, category == null ? null : category.trim().toUpperCase())
                .eqIfPresent(UserFeedbackDO::getUserId, userId)
                .orderByDesc(UserFeedbackDO::getId);
        // 仅平台超管 (tenantId=1) 才能 allTenants；其他租户只能看自己 tenantId 的反馈
        boolean isPlatformAdmin = currentTenantId != null && currentTenantId == PLATFORM_TENANT_ID;
        if (!isPlatformAdmin || !Boolean.TRUE.equals(allTenants)) {
            q.eq(UserFeedbackDO::getTenantId, currentTenantId == null ? 0L : currentTenantId);
        }
        return success(userFeedbackMapper.selectPage(pageParam, q));
    }

    @PutMapping("/reply")
    @Operation(summary = "回复反馈")
    @Parameter(name = "id", description = "反馈 ID", required = true)
    @Parameter(name = "reply", description = "回复内容", required = true)
    @PreAuthorize("@ss.hasPermission('merchant:user-feedback:update')")
    @TenantIgnore
    public CommonResult<Boolean> reply(@RequestParam("id") @NotNull Long id,
                                        @RequestParam("reply") @NotNull String reply) {
        if (reply.trim().isEmpty()) {
            throw ServiceExceptionUtil.exception0(1_031_002_010, "回复内容不能为空");
        }
        if (reply.length() > 2000) {
            throw ServiceExceptionUtil.exception0(1_031_002_011, "回复内容过长（≤2000 字）");
        }
        UserFeedbackDO existing = userFeedbackMapper.selectById(id);
        if (existing == null) {
            throw ServiceExceptionUtil.exception0(1_031_002_012, "反馈不存在");
        }
        // 校验跨租户访问：除超管外只能回复自己 tenantId 的反馈
        Long currentTenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId();
        if (currentTenantId != null && currentTenantId != PLATFORM_TENANT_ID
                && !java.util.Objects.equals(existing.getTenantId(), currentTenantId)) {
            throw ServiceExceptionUtil.exception0(1_031_002_013, "无权回复其它店铺的反馈");
        }
        UserFeedbackDO upd = new UserFeedbackDO();
        upd.setId(id);
        upd.setReply(reply.trim());
        upd.setRepliedAt(LocalDateTime.now());
        // 回复后默认推进到"处理中"，已解决/已关闭维持原状态
        if (existing.getStatus() != null && existing.getStatus() == 0) {
            upd.setStatus(1);
        }
        upd.setUpdater(String.valueOf(SecurityFrameworkUtils.getLoginUserId()));
        userFeedbackMapper.updateById(upd);
        return success(true);
    }

    @PutMapping("/status")
    @Operation(summary = "推进反馈状态（0 待处理 / 1 处理中 / 2 已解决 / 3 已关闭）")
    @PreAuthorize("@ss.hasPermission('merchant:user-feedback:update')")
    @TenantIgnore
    public CommonResult<Boolean> updateStatus(@RequestParam("id") @NotNull Long id,
                                               @RequestParam("status") @NotNull Integer status) {
        if (status < 0 || status > 3) {
            throw ServiceExceptionUtil.exception0(1_031_002_014, "非法状态值");
        }
        UserFeedbackDO existing = userFeedbackMapper.selectById(id);
        if (existing == null) {
            throw ServiceExceptionUtil.exception0(1_031_002_012, "反馈不存在");
        }
        Long currentTenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId();
        if (currentTenantId != null && currentTenantId != PLATFORM_TENANT_ID
                && !java.util.Objects.equals(existing.getTenantId(), currentTenantId)) {
            throw ServiceExceptionUtil.exception0(1_031_002_013, "无权变更其它店铺的反馈");
        }
        UserFeedbackDO upd = new UserFeedbackDO();
        upd.setId(id);
        upd.setStatus(status);
        upd.setUpdater(String.valueOf(SecurityFrameworkUtils.getLoginUserId()));
        userFeedbackMapper.updateById(upd);
        return success(true);
    }

}
