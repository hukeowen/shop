package cn.iocoder.yudao.module.merchant.controller.app.vo.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "摊小二 - 登录响应 VO")
@Data
public class AppLoginRespVO {

    @Schema(description = "AccessToken")
    private String token;

    @Schema(description = "RefreshToken")
    private String refreshToken;

    @Schema(description = "Token 过期时间")
    private LocalDateTime expiresTime;

    @Schema(description = "会员 userId")
    private Long userId;

    @Schema(description = "用户拥有的角色列表")
    private List<String> roles;

    @Schema(description = "当前激活角色")
    private String activeRole;

    @Schema(description = "微信 OpenID")
    private String openid;

    @Schema(description = "绑定手机号，可能为空")
    private String phone;

    @Schema(description = "关联商户 ID，未开通商户时为 null")
    private Long merchantId;
}
