package cn.iocoder.yudao.module.merchant.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 商户配置商品服务卡 - 单张卡定义 Request VO
 */
@Schema(description = "商户小程序 - 服务卡定义保存项")
@Data
public class AppServiceCardDefSaveReqVO {

    @Schema(description = "卡名称", example = "洗车卡")
    private String name;

    @Schema(description = "有效天数（从付款日起算）", example = "730")
    private Integer validityDays;

    @Schema(description = "可核销次数；不传 / 0 / 负数 = 不限次数", example = "10")
    private Integer maxCount;

    @Schema(description = "卡说明/使用须知", example = "每次到店出示，洗车不限车型")
    private String description;

}
