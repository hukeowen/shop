package cn.iocoder.yudao.module.merchant.controller.admin.vo.referral;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Schema(description = "管理后台 - 平台推N返1配置 Response VO")
@Data
@AllArgsConstructor
public class MerchantReferralConfigRespVO {

    @Schema(description = "推荐N个付费商户触发奖励")
    private int pushN;

    @Schema(description = "奖励天数（默认365天=1年）")
    private int rewardDays;

    @Schema(description = "是否开启推N返1")
    private boolean enabled;

}
