package cn.iocoder.yudao.module.merchant.controller.app.vo.appversion;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "App 端 - 最新版本 Response VO")
@Data
public class AppVersionCheckRespVO {

    @Schema(description = "平台 android/ios")
    private String platform;

    @Schema(description = "版本名", example = "1.0.2")
    private String versionName;

    @Schema(description = "版本号（比对用）", example = "2")
    private Integer versionCode;

    @Schema(description = "APK 下载地址")
    private String downloadUrl;

    @Schema(description = "更新说明")
    private String updateLog;

    @Schema(description = "是否强制更新")
    private Boolean forceUpdate;

    @Schema(description = "APK 文件大小（字节）")
    private Long fileSize;

}
