package cn.iocoder.yudao.module.merchant.framework.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 商户套餐支付配置（Phase 0.3.3）。
 *
 * <p>对应 yaml：
 * <pre>
 * yudao:
 *   merchant:
 *     package:
 *       pay-app-id: 3   # admin 创建的 PayApp 主键
 *       pay-app-key: "package"  # 可选，若填则优先走 appKey
 * </pre>
 * </p>
 *
 * <p>{@link #payAppId} 为 0 时视为未配置，{@code /purchase} 接口会抛
 * {@link cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants#PAY_APP_ID_NOT_CONFIGURED}
 * 立即失败（而不是让用户走到支付渠道再失败）。</p>
 *
 * <p>yudao-module-pay 的 {@link cn.iocoder.yudao.module.pay.api.order.dto.PayOrderCreateReqDTO}
 * 要求的是 {@code appKey}（String）而非 {@code appId}，但管理后台「支付应用」一般先记主键 ID；
 * 本配置两种都支持：优先用 {@link #payAppKey}，为空时用 {@link #payAppId} 查 PayApp 拿 appKey。</p>
 */
@Component
@ConfigurationProperties(prefix = "yudao.merchant.package")
@Data
public class MerchantPackageProperties {

    /**
     * 支付应用主键（admin-api/pay/app 管理端创建 PayApp 后得到的 id）。
     * 0 或 null 表示未配置，{@code /purchase} 接口将快速失败。
     */
    private Long payAppId;

    /**
     * 支付应用 appKey（{@link cn.iocoder.yudao.module.pay.dal.dataobject.app.PayAppDO#getAppKey()}）。
     * 如果填了就直接用；否则从 {@link #payAppId} 查库反推。
     */
    private String payAppKey;

    /** 是否已配置有效的支付应用。 */
    public boolean isConfigured() {
        return (payAppKey != null && !payAppKey.isEmpty())
                || (payAppId != null && payAppId > 0);
    }

}
