package cn.iocoder.yudao.module.merchant.controller.admin.vo.apply;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 商户入驻申请详情 Response VO")
@Data
public class MerchantApplyRespVO {

    @Schema(description = "申请编号")
    private Long id;
    @Schema(description = "店铺名称")
    private String shopName;
    @Schema(description = "经营类目ID")
    private Long categoryId;
    @Schema(description = "联系手机号")
    private String mobile;
    @Schema(description = "推荐人手机号")
    private String referrerMobile;
    @Schema(description = "营业执照图片URL")
    private String licenseUrl;
    @Schema(description = "身份证正面URL")
    private String idCardFront;
    @Schema(description = "身份证背面URL")
    private String idCardBack;
    @Schema(description = "经度")
    private BigDecimal longitude;
    @Schema(description = "纬度")
    private BigDecimal latitude;
    @Schema(description = "详细地址")
    private String address;
    @Schema(description = "微信收款类型：0未配置 1已有商户号 2申请子商户")
    private Integer wxMchType;
    @Schema(description = "已有微信商户号")
    private String wxMchId;
    @Schema(description = "审核状态：0待审核 1通过 2驳回")
    private Integer status;
    @Schema(description = "驳回原因")
    private String rejectReason;
    @Schema(description = "审核时间")
    private LocalDateTime auditTime;
    @Schema(description = "关联租户ID（通过后填入）")
    private Long tenantId;
    @Schema(description = "申请时间")
    private LocalDateTime createTime;

}
