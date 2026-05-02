package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.merchant.dal.dataobject.UserFeedbackDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.UserFeedbackMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @PostMapping("/submit")
    @Operation(summary = "提交反馈")
    @TenantIgnore
    public CommonResult<Long> submit(@Valid @RequestBody SubmitReqVO req) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null || userId <= 0) {
            throw ServiceExceptionUtil.exception0(1_031_002_001, "请先登录");
        }
        String category = req.getCategory() == null ? "OTHER" : req.getCategory().trim().toUpperCase();
        if (!ALLOWED_CATEGORIES.contains(category)) category = "OTHER";

        UserFeedbackDO entity = UserFeedbackDO.builder()
                .userId(userId)
                .tenantId(req.getTenantId() == null ? 0L : req.getTenantId())
                .category(category)
                .content(req.getContent().trim())
                .contact(req.getContact() == null ? null : req.getContact().trim())
                .images(req.getImages() == null ? null : req.getImages().trim())
                .status(0)
                .build();
        userFeedbackMapper.insert(entity);
        return success(entity.getId());
    }

    @GetMapping("/my-list")
    @Operation(summary = "我的反馈记录（分页）")
    @TenantIgnore
    public CommonResult<PageResult<UserFeedbackDO>> myList(@Valid PageParam pageParam) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null || userId <= 0) {
            return success(new PageResult<>(0L));
        }
        return success(userFeedbackMapper.selectPageByUser(userId, pageParam));
    }

    @lombok.Data
    public static class SubmitReqVO {
        private Long tenantId;

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
