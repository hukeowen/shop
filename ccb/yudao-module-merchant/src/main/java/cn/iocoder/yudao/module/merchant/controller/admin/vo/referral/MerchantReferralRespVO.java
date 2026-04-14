package cn.iocoder.yudao.module.merchant.controller.admin.vo.referral;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 平台推荐裂变记录 Response VO")
@Data
public class MerchantReferralRespVO {

    @Schema(description = "记录编号")
    private Long id;

    @Schema(description = "推荐人手机号")
    private String referrerMobile;

    @Schema(description = "推荐人租户ID")
    private Long referrerTenantId;

    @Schema(description = "被推荐商户租户ID")
    private Long refereeTenantId;

    @Schema(description = "被推荐商户名称")
    private String refereeShopName;

    @Schema(description = "被推荐商户首次付费时间")
    private LocalDateTime paidAt;

    @Schema(description = "是否已触发返利")
    private Boolean rewarded;

    @Schema(description = "返利触发时间")
    private LocalDateTime rewardTime;

    @Schema(description = "记录创建时间")
    private LocalDateTime createTime;

}
