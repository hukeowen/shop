package cn.iocoder.yudao.module.merchant.controller.app.vo.coupon;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/** 商户新建 / 编辑券请求 */
@Schema(description = "商户端 - 优惠券保存请求（带 id 即编辑，不带即新建）")
@Data
public class AppShopCouponSaveReqVO {

    @Schema(description = "券模板 ID（编辑时必传，新建留空）")
    private Long id;

    @Schema(description = "券名（≤ 32 字）", example = "满 50 减 5")
    @NotBlank(message = "券名不能为空")
    @Size(max = 32, message = "券名最多 32 字")
    private String name;

    @Schema(description = "券面额（分）", example = "500")
    @NotNull(message = "面额不能为空")
    @Min(value = 1, message = "面额至少 1 分")
    private Integer discountAmount;

    @Schema(description = "使用门槛（分），0=无门槛", example = "5000")
    @NotNull(message = "门槛不能为空")
    @Min(value = 0, message = "门槛不能为负")
    private Integer minAmount;

    @Schema(description = "标签：NEW=新人专享 / NORMAL=通用，留空=通用", example = "NEW")
    private String tag;

    @Schema(description = "发行总量，0=不限", example = "100")
    @NotNull
    @Min(value = 0)
    private Integer totalCount;

    @Schema(description = "领取后有效天数（≥1）", example = "30")
    @NotNull
    @Min(value = 1, message = "有效天数至少 1 天")
    private Integer validDays;
}
