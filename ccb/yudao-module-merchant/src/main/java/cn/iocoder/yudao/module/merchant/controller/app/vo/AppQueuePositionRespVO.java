package cn.iocoder.yudao.module.merchant.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户「我的队列」一行：自己在某商品队列中的当前状态。
 */
@Schema(description = "用户在某商品队列中的位置")
@Data
public class AppQueuePositionRespVO {

    @Schema(description = "店铺租户 ID")
    private Long tenantId;

    @Schema(description = "店铺名称")
    private String shopName;

    @Schema(description = "商品 SPU ID")
    private Long spuId;

    @Schema(description = "商品名称")
    private String spuName;

    @Schema(description = "商品单价(分)")
    private Integer unitPrice;

    @Schema(description = "比例文案，例：1#10%/2#20%/3#70%")
    private String ratiosText;

    @Schema(description = "层级，A = 主动层（优先返奖）/ B = 被动层")
    private String layer;

    @Schema(description = "已累计返奖次数")
    private Integer accumulatedCount;

    @Schema(description = "商品配置的 N（满 N 次出队）")
    private Integer maxN;

    @Schema(description = "已累计获得的推广积分(分)")
    private Long accumulatedAmount;

    @Schema(description = "入队时间")
    private LocalDateTime joinedAt;

    @Schema(description = "升 A 层时间（B 层为 null）")
    private LocalDateTime promotedAt;

}
