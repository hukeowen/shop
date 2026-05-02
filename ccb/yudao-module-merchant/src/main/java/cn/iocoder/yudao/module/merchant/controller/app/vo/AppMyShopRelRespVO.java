package cn.iocoder.yudao.module.merchant.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * C 端「我加入的店铺」列表 RespVO（user-home / user-me 主屏用）
 *
 * <p>结合 member_shop_rel + shop_info + shop_user_star 三表，单次返足够前端
 * 渲染卡片的所有字段：店铺名、封面、地址、星级、余额、积分、最近访问时间。</p>
 */
@Schema(description = "C 端 - 我加入的店铺 RespVO")
@Data
public class AppMyShopRelRespVO {

    @Schema(description = "店铺租户 id")
    private Long tenantId;

    @Schema(description = "店铺名（来自 shop_info.shop_name 或 merchant_info.name 兜底）")
    private String shopName;

    @Schema(description = "店铺封面 URL（shop_info.cover_url）")
    private String coverUrl;

    @Schema(description = "店铺地址")
    private String address;

    @Schema(description = "营业时间")
    private String businessHours;

    @Schema(description = "我在此店的余额（分）")
    private Integer balance;

    @Schema(description = "我在此店的推广积分")
    private Integer points;

    @Schema(description = "我在此店的当前星级（0=普通，1-5）")
    private Integer star;

    @Schema(description = "最近一次访问时间")
    private LocalDateTime lastVisitAt;

    @Schema(description = "推荐人 userId（可选，溯源用）")
    private Long referrerUserId;
}
