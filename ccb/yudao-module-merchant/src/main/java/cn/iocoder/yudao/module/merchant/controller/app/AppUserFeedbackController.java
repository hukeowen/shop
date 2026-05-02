package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MemberShopRelDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.UserFeedbackDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.UserFeedbackMapper;
import cn.iocoder.yudao.module.merchant.service.MemberShopRelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * C 端 - 用户反馈与帮助：提交反馈 + 我的反馈列表。
 *
 * 平台级表（user_feedback 不继承 TenantBaseDO），跨店通用反馈 tenantId=0。
 */
@Tag(name = "C 端 - 用户反馈与帮助")
@RestController
@RequestMapping("/merchant/mini/feedback")
@Validated
public class AppUserFeedbackController {

    private static final Set<String> ALLOWED_CATEGORIES =
            new java.util.HashSet<>(Arrays.asList("BUG", "FEATURE", "PAYMENT", "ACCOUNT", "SHOP", "OTHER"));

    @Resource
    private UserFeedbackMapper userFeedbackMapper;
    @Resource
    private MemberShopRelService memberShopRelService;

    @PostMapping("/submit")
    @Operation(summary = "提交反馈")
    @TenantIgnore  // MAJ-4 修复：C 端跨店反馈，必须 ignore 拦截器的强一致校验
    @cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter(
            time = 60,
            count = 5,
            message = "提交过于频繁，请 1 分钟后再试",
            keyResolver = cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.impl.UserRateLimiterKeyResolver.class)
    public CommonResult<Long> submit(@Valid @RequestBody SubmitReqVO req) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null || userId <= 0) {
            throw ServiceExceptionUtil.exception0(1_031_002_001, "请先登录");
        }
        String category = req.getCategory() == null ? "OTHER" : req.getCategory().trim().toUpperCase();
        if (!ALLOWED_CATEGORIES.contains(category)) category = "OTHER";

        // CRIT-1 修复：scope 来自显式请求体字段，不再依赖 TenantContextHolder == null
        // 因为 TenantSecurityWebFilter 会在 controller 之前自动用 user.tenantId 兜底，
        // controller 永远拿不到 null —— 老逻辑导致 PLATFORM 模式不可达。
        String scope = req.getScope() == null ? "PLATFORM" : req.getScope().trim().toUpperCase();
        Long tenantId;
        if ("SHOP".equals(scope)) {
            // 店铺反馈 → 必须显式带 shopTenantId 且校验 rel 存在
            if (req.getShopTenantId() == null || req.getShopTenantId() <= 0) {
                throw ServiceExceptionUtil.exception0(1_031_002_007, "店铺 ID 必填");
            }
            MemberShopRelDO rel = memberShopRelService.getByUserAndTenant(userId, req.getShopTenantId());
            if (rel == null) {
                throw ServiceExceptionUtil.exception0(1_031_002_002, "请先进入该店铺再提交反馈");
            }
            tenantId = req.getShopTenantId();
        } else {
            tenantId = 0L;
        }

        UserFeedbackDO entity = UserFeedbackDO.builder()
                .userId(userId)
                .tenantId(tenantId)
                .category(category)
                .content(req.getContent().trim())
                .contact(req.getContact() == null ? null : req.getContact().trim())
                .images(sanitizeImages(req.getImages()))
                .status(0)
                .build();
        userFeedbackMapper.insert(entity);
        return success(entity.getId());
    }

    @GetMapping("/my-list")
    @Operation(summary = "我的反馈记录（按 scope 过滤）")
    @TenantIgnore  // MAJ-4 修复
    public CommonResult<PageResult<UserFeedbackDO>> myList(
            @Valid PageParam pageParam,
            @RequestParam(value = "scope", required = false, defaultValue = "PLATFORM") String scope,
            @RequestParam(value = "shopTenantId", required = false) Long shopTenantId) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null || userId <= 0) {
            return success(new PageResult<>(0L));
        }
        // CRIT-1 修复：scope 显式参数化，与 submit 的 scope 字段语义对齐
        long filterTenantId;
        String s = scope == null ? "PLATFORM" : scope.trim().toUpperCase();
        if ("SHOP".equals(s) && shopTenantId != null && shopTenantId > 0) {
            filterTenantId = shopTenantId;
        } else {
            filterTenantId = 0L;
        }
        return success(userFeedbackMapper.selectPageByUserAndTenant(userId, filterTenantId, pageParam));
    }

    /**
     * MAJ-4 修复：images 字段必须是合法 JSON 数组、最多 6 个 https URL。
     */
    private static String sanitizeImages(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String s = raw.trim();
        java.util.List<String> urls;
        try {
            urls = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseArray(s, String.class);
        } catch (Exception e) {
            throw ServiceExceptionUtil.exception0(1_031_002_003, "图片列表必须是 JSON 数组");
        }
        if (urls == null || urls.isEmpty()) return null;
        if (urls.size() > 6) {
            throw ServiceExceptionUtil.exception0(1_031_002_004, "最多 6 张图片");
        }
        for (String u : urls) {
            if (u == null || !(u.startsWith("https://") || u.startsWith("http://"))) {
                throw ServiceExceptionUtil.exception0(1_031_002_005, "图片地址必须是 http/https 链接");
            }
            if (u.length() > 500) {
                throw ServiceExceptionUtil.exception0(1_031_002_006, "单张图片地址过长");
            }
        }
        return cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(urls);
    }

    @lombok.Data
    public static class SubmitReqVO {

        /** PLATFORM = 平台通用反馈（落 tenantId=0）/ SHOP = 给特定店铺（用 shopTenantId） */
        @Size(max = 16)
        private String scope;

        /** scope=SHOP 时必填 */
        private Long shopTenantId;

        @Size(max = 32)
        private String category;

        @NotEmpty(message = "反馈内容不能为空")
        @Size(min = 5, max = 2000, message = "反馈内容 5-2000 字")
        private String content;

        @Size(max = 64)
        private String contact;

        @Size(max = 2000)
        private String images;
    }

}
