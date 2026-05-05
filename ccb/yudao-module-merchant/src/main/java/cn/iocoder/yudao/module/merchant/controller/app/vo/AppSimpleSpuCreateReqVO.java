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

    @Schema(description = "商品分类ID（前端给具体 ID 时直接用；优先级低于 categoryName）", example = "1")
    private Long categoryId;

    /**
     * 商品分类名（推荐前端 AI 识别后传这个，比传 ID 更符合实际）：
     * - AI 识别出的分类（小吃 / 水果 / 零食 / 饮品 / 烟酒 / 烘焙 / 服装 ...）
     * - 后端 findOrCreateCategory(categoryName)：没有就自动建顶级分类
     * 优先级：categoryName 非空 > categoryId 有效 > brand 字符串 > 「通用」兜底
     */
    @Schema(description = "商品分类名（推荐 AI 识别后传中文名）", example = "小吃")
    private String categoryName;

    @Schema(description = "赠送积分（默认0）", example = "10")
    private Integer giveIntegral;

    @Schema(description = "配送方式：1快递 2自提（默认[2]自提）", example = "[2]")
    private List<Integer> deliveryTypes;

    @Schema(description = "商品简介", example = "好吃的烤面筋")
    private String introduction;

    /**
     * 品牌名（前端 AI 识别图片后自动填）：
     * - 能识别到具体品牌（例：可口可乐 / 旺仔 / 老干妈）→ 用具体品牌名
     * - 识别不到（地摊 / 自家做的 / 散装）→ 用通用类目名（例：小吃 / 水果 / 零食 / 饮品 / 通用）
     * - 完全为空 → 后端兜底创建/复用名为「通用」的品牌
     * 后端 findOrCreateBrand 自动 selectByName，没有就 insert 一个 enabled 品牌。
     */
    @Schema(description = "品牌名（AI 识别填，留空则兜底「通用」）", example = "可口可乐")
    private String brand;

}
