package cn.iocoder.yudao.module.merchant.controller.admin.vo.appversion;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - App 版本 新增/修改 Request VO")
@Data
public class AppVersionSaveReqVO {

    @Schema(description = "编号（修改时必填）")
    private Long id;

    @Schema(description = "平台 android/ios", requiredMode = Schema.RequiredMode.REQUIRED, example = "android")
    @NotBlank(message = "平台不能为空")
    private String platform;

    @Schema(description = "版本名", requiredMode = Schema.RequiredMode.REQUIRED, example = "1.0.2")
    @NotBlank(message = "版本名不能为空")
    private String versionName;

    @Schema(description = "版本号（单调递增，用于比对升级）", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "版本号不能为空")
    private Integer versionCode;

    @Schema(description = "APK 下载地址", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "下载地址不能为空")
    private String downloadUrl;

    @Schema(description = "更新说明")
    private String updateLog;

    @Schema(description = "是否强制更新", example = "false")
    private Boolean forceUpdate;

    @Schema(description = "APK 文件大小（字节）")
    private Long fileSize;

    @Schema(description = "状态 0发布 1停用", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

}
