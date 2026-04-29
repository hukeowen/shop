package cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.allinpay;

import cn.iocoder.yudao.framework.common.util.validation.ValidationUtils;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.PayClientConfig;
import lombok.Data;

import javax.validation.Validator;
import javax.validation.constraints.NotBlank;

/**
 * 通联收付通的 PayClientConfig
 *
 * <p>每个商户对应一个 PayChannelDO，配置内容为该商户的通联子户参数 (tlMchId/tlMchKey)。
 * 平台级公共参数（orgId、平台 RSA 私钥、通联公钥、API base url）走 yudao-server
 * application.yaml 的 {@code merchant.allinpay.*}，由 sub-channel 客户端通过
 * Spring 上下文读到（init 阶段加载）。</p>
 */
@Data
public class AllinpayPayClientConfig implements PayClientConfig {

    /** 通联子户号 (mchntid) — 该商户在通联开户后获得 */
    @NotBlank(message = "通联子户号 mchId 不能为空")
    private String mchId;

    /** 通联子户密钥 (mchntkey) — 已加密存储；运行时由 client 解密使用 */
    @NotBlank(message = "通联子户密钥 mchKey 不能为空")
    private String mchKey;

    /** 业务子类型（聚合码 / JSAPI 等），由 sub-channel 类约定使用 */
    private String subType;

    @Override
    public void validate(Validator validator) {
        ValidationUtils.validate(validator, this);
    }
}
