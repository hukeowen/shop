package cn.iocoder.yudao.module.merchant.controller.admin.vo.appversion;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - App 版本 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AppVersionPageReqVO extends PageParam {

    @Schema(description = "平台 android/ios")
    private String platform;

    @Schema(description = "版本名")
    private String versionName;

    @Schema(description = "状态 0发布 1停用")
    private Integer status;

}
