package cn.iocoder.yudao.module.merchant.controller.app.vo.promo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "用户端 - 奖池结算单（公示）响应")
@Data
public class AppSpuPoolSettleRecordRespVO {

    @Schema(description = "结算单 id")
    private Long id;

    @Schema(description = "SPU id")
    private Long spuId;

    @Schema(description = "结算前池余额（分）")
    private Long poolBalanceBefore;

    @Schema(description = "实际分配总额（分）")
    private Long totalDistributed;

    @Schema(description = "中奖人数")
    private Integer winnerCount;

    @Schema(description = "结算时间")
    private LocalDateTime createTime;

}
