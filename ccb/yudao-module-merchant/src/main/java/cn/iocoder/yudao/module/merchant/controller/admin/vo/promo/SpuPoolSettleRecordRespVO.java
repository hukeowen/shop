package cn.iocoder.yudao.module.merchant.controller.admin.vo.promo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - SPU 奖池结算单 响应")
@Data
public class SpuPoolSettleRecordRespVO {

    @Schema(description = "结算单 id")
    private Long id;

    @Schema(description = "SPU id")
    private Long spuId;

    @Schema(description = "结算前池余额（分）")
    private Long poolBalanceBefore;

    @Schema(description = "结算后池余额（分）— 通常 = 0；某星无人时残值留池")
    private Long poolBalanceAfter;

    @Schema(description = "实际分配总额（分）")
    private Long totalDistributed;

    @Schema(description = "结算时规则 JSON 快照")
    private String rulesSnapshot;

    @Schema(description = "操作人 user_id")
    private Long operatorId;

    @Schema(description = "操作人姓名")
    private String operatorName;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "结算时间")
    private LocalDateTime createTime;

}
