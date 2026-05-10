package cn.iocoder.yudao.module.merchant.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Schema(description = "商户小程序 - 店铺会员行")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppMerchantMemberItemRespVO {
    @Schema(description = "user_id")
    private Long userId;
    @Schema(description = "手机号（脱敏）")
    private String mobile;
    @Schema(description = "昵称")
    private String nickname;
    @Schema(description = "加入店铺时间")
    private LocalDateTime joinedAt;
    @Schema(description = "店铺余额（分）")
    private Long balance;
    @Schema(description = "推广积分余额（分）")
    private Long promoPointBalance;
    @Schema(description = "消费积分（分）")
    private Long consumePointBalance;
    @Schema(description = "用户全局星级（spu_id=0 行）")
    private Integer currentStar;
    @Schema(description = "下单数")
    private Long orderCount;
    @Schema(description = "下单总金额（分）")
    private Long orderAmount;
    @Schema(description = "推荐人 user_id（如有）")
    private Long parentUserId;
    @Schema(description = "邀请的下级数")
    private Long invitedCount;
}
