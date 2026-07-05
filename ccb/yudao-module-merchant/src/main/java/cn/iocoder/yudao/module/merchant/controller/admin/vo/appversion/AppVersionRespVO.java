package cn.iocoder.yudao.module.merchant.controller.admin.vo.appversion;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - App 版本 Response VO")
@Data
public class AppVersionRespVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "平台 android/ios")
    private String platform;

    @Schema(description = "版本名")
    private String versionName;

    @Schema(description = "版本号")
    private Integer versionCode;

    @Schema(description = "APK 下载地址")
    private String downloadUrl;

    @Schema(description = "更新说明")
    private String updateLog;

    @Schema(description = "是否强制更新")
    private Boolean forceUpdate;

    @Schema(description = "APK 文件大小（字节）")
    private Long fileSize;

    @Schema(description = "状态 0发布 1停用")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
