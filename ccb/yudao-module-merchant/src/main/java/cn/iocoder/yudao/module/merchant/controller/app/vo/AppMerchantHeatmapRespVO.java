package cn.iocoder.yudao.module.merchant.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "商户小程序 - 时段订单热力")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppMerchantHeatmapRespVO {
    @Schema(description = "周期：day / week / month")
    private String period;
    @Schema(description = "24 个小时桶的订单计数（index = 0..23）")
    private List<Long> hourly;
    @Schema(description = "周期总订单数（=sum(hourly))")
    private Long totalOrders;
}
