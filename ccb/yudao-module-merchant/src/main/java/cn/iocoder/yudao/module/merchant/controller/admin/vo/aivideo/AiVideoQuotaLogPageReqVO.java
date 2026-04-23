package cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 视频配额流水分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AiVideoQuotaLogPageReqVO extends PageParam {

    @Schema(description = "商户 ID", example = "1024")
    private Long merchantId;

    @Schema(description = "业务类型：1=购买套餐 2=视频生成扣减 3=生成失败回补 4=平台手动调整", example = "2")
    private Integer bizType;

    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @Schema(description = "创建时间范围（开始,结束）")
    private LocalDateTime[] createTime;

}
