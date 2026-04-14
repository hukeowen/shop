package cn.iocoder.yudao.module.merchant.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 商户小程序 - 极简商品发布 Request VO
 * 只需要名称、价格、图片，其余字段自动填充默认值
 */
@Schema(description = "商户小程序 - 极简商品发布 Request VO")
@Data
public class AppSimpleSpuCreateReqVO {

    @Schema(description = "商品名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "烤面筋")
    @NotEmpty(message = "商品名称不能为空")
    private String name;

    @Schema(description = "销售价格（分）", requiredMode = Schema.RequiredMode.REQUIRED, example = "500")
    @NotNull(message = "价格不能为空")
    @Min(value = 1, message = "价格必须大于0")
    private Integer price;

    @Schema(description = "商品图片URL", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://xxx.com/a.jpg")
    @NotEmpty(message = "商品图片不能为空")
    private String picUrl;

    @Schema(description = "库存数量（默认9999）", example = "100")
    private Integer stock;

    @Schema(description = "商品分类ID（默认使用商户经营类目）", example = "1")
    private Long categoryId;

    @Schema(description = "赠送积分（默认0）", example = "10")
    private Integer giveIntegral;

    @Schema(description = "配送方式：1快递 2自提（默认[2]自提）", example = "[2]")
    private List<Integer> deliveryTypes;

    @Schema(description = "商品简介", example = "好吃的烤面筋")
    private String introduction;

}
