package cn.iocoder.yudao.module.merchant.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "商户小程序 - 推 N 反 1 漏斗")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppMerchantFunnelRespVO {
    @Schema(description = "spu_id（null 表全店聚合）")
    private Long spuId;
    @Schema(description = "已激活（IN_PROGRESS + COMPLETED）的人数")
    private Long activatedUsers;
    @Schema(description = "推进中（IN_PROGRESS）的人数")
    private Long inProgressUsers;
    @Schema(description = "已完成（COMPLETED）的人数")
    private Long completedUsers;
    @Schema(description = "首贡献已结的对数（contribution 表）")
    private Long contributionCount;
}
