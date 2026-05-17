package cn.iocoder.yudao.module.merchant.controller.app.vo.promo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "用户端 - 奖池中奖明细 响应（脱敏）")
@Data
public class AppSpuPoolPayoutItemRespVO {

    @Schema(description = "明细 id")
    private Long id;

    @Schema(description = "结算单 id")
    private Long settleId;

    @Schema(description = "SPU id")
    private Long spuId;

    @Schema(description = "脱敏昵称，如 张** ")
    private String maskedNickname;

    @Schema(description = "用户头像 URL")
    private String avatar;

    @Schema(description = "用户当时星级")
    private Integer star;

    @Schema(description = "EQUAL=均分 / LOTTERY=抽中")
    private String mode;

    @Schema(description = "中奖金额（分）")
    private Long amount;

    @Schema(description = "中奖时间")
    private LocalDateTime createTime;

    @Schema(description = "是否当前登录用户（本人）")
    private Boolean isSelf;

}
