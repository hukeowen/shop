package cn.iocoder.yudao.module.merchant.controller.app.vo.aivideo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 商户小程序 - 购买套餐响应 VO（Phase 0.3.3）。
 *
 * <p>本接口只负责创建业务订单 + 支付单；真正调起微信支付的 JSAPI 参数，
 * 由前端拿着 {@link #payOrderId} 调用 yudao-module-pay 的
 * {@code POST /app-api/pay/order/submit} 获取。</p>
 *
 * <p>拆成两步走的好处：
 * <ul>
 *   <li>支付渠道切换（wx_lite → alipay_wap）无需改商户业务接口</li>
 *   <li>支付签名生成在 pay 模块集中，安全审计边界清晰</li>
 *   <li>pay 模块已有现成的幂等 / 风控 / 渠道扩展能力</li>
 * </ul>
 * </p>
 */
@Schema(description = "商户小程序 - 购买 AI 视频套餐 Response VO")
@Data
public class AppMerchantPackagePurchaseRespVO {

    @Schema(description = "套餐业务订单 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    private Long packageOrderId;

    @Schema(description = "支付单 ID（前端下一步提交到 /pay/order/submit）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "20001")
    private Long payOrderId;

    @Schema(description = "支付渠道编码（回显）", example = "wx_lite")
    private String channelCode;

    @Schema(description = "订单实付金额（分）", example = "990")
    private Long price;

    @Schema(description = "套餐名称（前端支付确认页展示用）", example = "体验装 5 条")
    private String packageName;

    @Schema(description = "套餐包含视频条数（前端展示用）", example = "5")
    private Integer videoCount;

}
