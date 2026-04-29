package cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.allinpay;

import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO;

/**
 * 通联收付通微信小程序 (JSAPI) PayClient
 *
 * <p>真接入：传 openId + 商户号 + 金额 → 通联返 payment（含 timeStamp/nonceStr/package/signType/paySign）
 * → 小程序前端调 wx.requestPayment 完成支付。</p>
 */
public class AllinpayLitePayClient extends AbstractAllinpayPayClient {

    public AllinpayLitePayClient(Long channelId, AllinpayPayClientConfig config) {
        super(channelId, PayChannelEnum.ALLINPAY_LITE.getCode(), config);
    }

    @Override
    protected PayOrderRespDTO doUnifiedOrder(PayOrderUnifiedReqDTO reqDTO) throws Throwable {
        throw new UnsupportedOperationException(
                "通联小程序 JSAPI 下单待 P1-7 完善 —— 商户 mchId=" + getConfig().getMchId());
    }
}
