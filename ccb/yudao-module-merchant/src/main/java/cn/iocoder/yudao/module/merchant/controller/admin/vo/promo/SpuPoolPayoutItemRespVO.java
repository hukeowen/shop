package cn.iocoder.yudao.module.merchant.controller.admin.vo.promo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - SPU 奖池结算明细 响应")
@Data
public class SpuPoolPayoutItemRespVO {

    @Schema(description = "明细 id")
    private Long id;

    @Schema(description = "结算单 id")
    private Long settleId;

    @Schema(description = "SPU id")
    private Long spuId;

    @Schema(description = "用户 id")
    private Long userId;

    @Schema(description = "用户当时星级")
    private Integer star;

    @Schema(description = "EQUAL=均分 / LOTTERY=抽中")
    private String mode;

    @Schema(description = "分到的推广积分（分）")
    private Long amount;

}
