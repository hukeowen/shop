package cn.iocoder.yudao.module.merchant.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "商户小程序 - 会员消费排行 VO")
@Data
public class AppMemberConsumptionRespVO {

    @Schema(description = "会员用户ID")
    private Long userId;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "手机号")
    private String mobile;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "累计消费金额（分）")
    private Integer totalSpent;

    @Schema(description = "消费订单数")
    private Integer orderCount;

}
