package cn.iocoder.yudao.module.merchant.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 商户信息 Response VO")
@Data
public class MerchantRespVO {

    @Schema(description = "商户编号")
    private Long id;

    @Schema(description = "商户名称")
    private String name;

    @Schema(description = "商户logo")
    private String logo;

    @Schema(description = "联系人")
    private String contactName;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "营业执照号")
    private String licenseNo;

    @Schema(description = "营业执照图片URL")
    private String licenseUrl;

    @Schema(description = "经营类目")
    private String businessCategory;

    @Schema(description = "商户状态")
    private Integer status;

    @Schema(description = "审核拒绝原因")
    private String rejectReason;

    @Schema(description = "审核时间")
    private LocalDateTime auditTime;

    @Schema(description = "微信支付子商户号")
    private String wxSubMchId;

    @Schema(description = "微信支付进件状态")
    private String wxApplymentStatus;

    @Schema(description = "小程序码URL")
    private String miniAppQrCodeUrl;

    @Schema(description = "是否已绑定抖音")
    private Boolean douyinBound;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
