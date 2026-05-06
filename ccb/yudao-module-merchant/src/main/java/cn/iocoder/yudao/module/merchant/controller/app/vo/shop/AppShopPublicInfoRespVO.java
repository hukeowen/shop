package cn.iocoder.yudao.module.merchant.controller.app.vo.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * C 端「店铺公开信息」响应 VO。
 *
 * <p>之前用 {@code Map<String,Object> + BeanUtil.beanToMap} 返 ShopInfoDO 全字段：
 * (1) 把 LocalDateTime 留在 Map 里，Jackson 序列化输出格式不可控；
 * (2) 把 mobile / tlMchKey / payApplyRejectReason 等敏感字段也带给 C 端。
 * 这里改用显式字段，仅暴露 C 端店铺详情页确实需要的展示字段。</p>
 *
 * <p>不含 createTime / updateTime（前端用不到）。</p>
 */
@Schema(description = "C 端 - 店铺公开信息响应")
@Data
public class AppShopPublicInfoRespVO {

    // ========== 来自 shop_info ==========

    @Schema(description = "店铺 ID")
    private Long id;
    @Schema(description = "租户 ID")
    private Long tenantId;
    @Schema(description = "店铺名")
    private String shopName;
    @Schema(description = "店铺分类 ID")
    private Long categoryId;
    @Schema(description = "店铺封面 URL")
    private String coverUrl;
    @Schema(description = "店铺简介")
    private String description;
    @Schema(description = "店铺公告")
    private String notice;
    @Schema(description = "店铺特色标签 CSV，如「炭火现烤,现做现卖」")
    private String featureTags;

    @Schema(description = "经度")
    private BigDecimal longitude;
    @Schema(description = "纬度")
    private BigDecimal latitude;
    @Schema(description = "详细地址")
    private String address;
    @Schema(description = "营业时间，如「09:00-22:00」")
    private String businessHours;
    @Schema(description = "营业状态：1=正常 0=暂停")
    private Integer status;

    @Schema(description = "近 30 天销量")
    private Integer sales30d;
    @Schema(description = "平均评分（一位小数）")
    private BigDecimal avgRating;

    // ========== 计算字段（按 query / 联查 promo_config） ==========

    @Schema(description = "用户↔店铺距离（米），仅传了 userLng/userLat 才有值")
    private Integer distanceMeter;

    @Schema(description = "星级折扣 JSON 数组（百分制）", example = "[100,95,92,90,88,85]")
    private String starDiscountRates;

    @Schema(description = "满减门槛（分），不启用为 null")
    private Integer fullCutThreshold;
    @Schema(description = "满减减免（分），不启用为 null")
    private Integer fullCutAmount;

    @Schema(description = "近 30 天访客数。主接口（/info）不返此字段（性能考量），由独立接口 /info/visitor 异步填充；前端拿到主信息后非阻塞补这个字段")
    private Integer visitorCount30d;
}
