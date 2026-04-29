package cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.allinpay;

import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundUnifiedReqDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.transfer.PayTransferRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.transfer.PayTransferUnifiedReqDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.AbstractPayClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 通联收付通 PayClient 抽象基类
 *
 * <p>真实接入时各 sub-channel（{@link AllinpayQrPayClient} 聚合扫码 /
 * {@link AllinpayLitePayClient} 小程序 JSAPI）继承本类，实现 {@code doUnifiedOrder}
 * 调通联对应支付接口。</p>
 *
 * <p>当前阶段：framework 通过 + 4 个回调/查询/退款/转账抛 UnsupportedOperationException
 * 让运行时调到立即报错，编译期一切干净；拿到通联沙箱账号后逐个填实。</p>
 */
@Slf4j
public abstract class AbstractAllinpayPayClient extends AbstractPayClient<AllinpayPayClientConfig> {

    public AbstractAllinpayPayClient(Long channelId, String channelCode, AllinpayPayClientConfig config) {
        super(channelId, channelCode, config);
    }

    @Override
    protected void doInit() {
        // 当前空实现；真接入后这里加：解密 mchKey + 加载平台 RSA 私钥引用 + 初始化 OkHttp
    }

    @Override
    protected PayRefundRespDTO doUnifiedRefund(PayRefundUnifiedReqDTO reqDTO) throws Throwable {
        throw new UnsupportedOperationException("通联退款接口待 P1-7 完善");
    }

    @Override
    protected PayRefundRespDTO doParseRefundNotify(Map<String, String> params, String body, Map<String, String> headers) throws Throwable {
        throw new UnsupportedOperationException("通联退款回调解析待 P1-7 完善");
    }

    @Override
    protected PayRefundRespDTO doGetRefund(String outTradeNo, String outRefundNo) throws Throwable {
        throw new UnsupportedOperationException("通联退款查询待 P1-7 完善");
    }

    @Override
    protected PayTransferRespDTO doUnifiedTransfer(PayTransferUnifiedReqDTO reqDTO) throws Throwable {
        throw new UnsupportedOperationException("通联转账接口待 P1-7 完善");
    }

    @Override
    protected PayTransferRespDTO doGetTransfer(String outTradeNo) throws Throwable {
        throw new UnsupportedOperationException("通联转账查询待 P1-7 完善");
    }

    @Override
    protected PayTransferRespDTO doParseTransferNotify(Map<String, String> params, String body, Map<String, String> headers) throws Throwable {
        throw new UnsupportedOperationException("通联转账回调解析待 P1-7 完善");
    }

    @Override
    protected PayOrderRespDTO doParseOrderNotify(Map<String, String> params, String body, Map<String, String> headers) throws Throwable {
        // 通联支付回调走同一个 webhook 路径 /admin-api/merchant/pay/tl-notify (在 merchant 模块)
        throw new UnsupportedOperationException(
                "通联支付回调走 /admin-api/merchant/pay/tl-notify，不应到 PayClient.parseOrderNotify");
    }

    @Override
    protected PayOrderRespDTO doGetOrder(String outTradeNo) throws Throwable {
        throw new UnsupportedOperationException("通联订单查询待 P1-7 完善");
    }
}
