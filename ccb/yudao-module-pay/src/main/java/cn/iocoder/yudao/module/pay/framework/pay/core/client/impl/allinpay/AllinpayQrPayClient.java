package cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.allinpay;

import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO;

/**
 * 通联收付通聚合扫码 PayClient
 *
 * <p>用户在商户店铺扫码 → 通联签发支付链 → 拉起微信/支付宝。资金 T+1 直达
 * 商户 (mchntid) 账户，平台不碰钱（持牌合规）。</p>
 *
 * <p>真接入流程（P1-7 完成）：</p>
 * <ol>
 *     <li>POST {@code /apiweb/scanqrpay} 带商户号 + 金额 + outTradeNo + 平台签名</li>
 *     <li>通联返回支付链 / 二维码 URL</li>
 *     <li>前端 displayMode=URL 显示二维码 / displayMode=QR_CODE 直接呈现</li>
 *     <li>用户扫码支付完成后通联推 /admin-api/merchant/pay/tl-notify</li>
 * </ol>
 *
 * <p>当前 doUnifiedOrder 抛 UnsupportedOperationException 占位，待真接入填实。</p>
 */
public class AllinpayQrPayClient extends AbstractAllinpayPayClient {

    public AllinpayQrPayClient(Long channelId, AllinpayPayClientConfig config) {
        super(channelId, PayChannelEnum.ALLINPAY_QR.getCode(), config);
    }

    @Override
    protected PayOrderRespDTO doUnifiedOrder(PayOrderUnifiedReqDTO reqDTO) throws Throwable {
        throw new UnsupportedOperationException(
                "通联聚合扫码下单待 P1-7 完善（拿到通联沙箱后填实）—— 商户 mchId=" + getConfig().getMchId());
    }
}
