package cn.iocoder.yudao.module.merchant.controller.app.vo.aivideo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 商户小程序 - 购买套餐请求 VO（Phase 0.3.3）。
 *
 * <p>不接收 openid：后端从当前登录 MemberUser 的 {@code mini_app_open_id} 读取，
 * 防止前端伪造他人 openid 发起支付。</p>
 */
@Schema(description = "商户小程序 - 购买 AI 视频套餐 Request VO")
@Data
public class AppMerchantPackagePurchaseReqVO {

    /**
     * 支付渠道编码。Phase 0.3.3 暂只支持 {@code wx_lite}（微信小程序 JSAPI）；
     * 校验在 Service 层做白名单，非白名单直接抛错。
     */
    @Schema(description = "支付渠道", requiredMode = Schema.RequiredMode.REQUIRED, example = "wx_lite")
    @NotNull(message = "支付渠道不能为空")
    @Size(max = 16, message = "支付渠道编码过长")
    private String channelCode;

}
