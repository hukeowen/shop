package cn.iocoder.yudao.module.merchant.controller.admin.vo.tlpay;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 通联支付配置分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class TlpayConfigPageReqVO extends PageParam {

    @Schema(description = "店铺名（模糊匹配）")
    private String shopName;

    @Schema(description = "是否启用 0=关 1=开")
    private Boolean enabled;

}
