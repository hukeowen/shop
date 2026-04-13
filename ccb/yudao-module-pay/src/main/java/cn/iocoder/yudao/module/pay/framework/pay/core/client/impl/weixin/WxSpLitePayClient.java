package cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.weixin;

import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import lombok.extern.slf4j.Slf4j;

/**
 * 微信支付【小程序】（服务商模式）的 PayClient 实现类
 *
 * 服务商模式下，使用 sp_mchid + sub_mchid 进行支付
 */
@Slf4j
public class WxSpLitePayClient extends WxPubPayClient {

    public WxSpLitePayClient(Long channelId, WxPayClientConfig config) {
        super(channelId, PayChannelEnum.WX_SP_LITE.getCode(), config);
        // 强制设置为服务商模式
        config.setServiceProviderMode(true);
    }

}
