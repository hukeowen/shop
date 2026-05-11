package cn.iocoder.yudao.module.merchant.service.allinpay;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 通联 → trade_order 已支付适配器（mall 商品订单，区别于 package_order 套餐订单）。
 *
 * <p>跟 package_order 不同，trade_order 走 yudao pay 标准路径：
 * 提单时已建 pay_order，通联回调 / 轮询命中 trxstatus=2000 后：
 * <ol>
 *   <li>{@link TradeOrderUpdateService#updateOrderPaid(Long, Long)} 由 yudao 标准 CAS 保证幂等</li>
 *   <li>内部触发 {@code TradeOrderHandler.afterPayOrder}，连锁触发本仓 v8 推 N 反 1 / 极差 / 升星 / 入池</li>
 * </ol>
 *
 * <p>注意：{@link TradeOrderUpdateService#updateOrderPaid} 内会校验 pay_order.status=SUCCESS。
 * 该校验由 yudao pay 模块在收到通联回调时自动完成（如果 AllinpayQrPayClient.doUnifiedOrder 已实装）。
 * 当前 #153 占位期间，pay_order 状态可能仍是 WAITING，{@link #markTradeOrderPaid} 会抛
 * "校验支付订单"异常 — 这是预期：实装 PayClient 后联调通过。</p>
 */
@Service
@Slf4j
public class TradeOrderAllinpayService {

    @Resource
    private TradeOrderMapper tradeOrderMapper;

    @Resource
    private TradeOrderUpdateService tradeOrderUpdateService;

    /**
     * 通联回调 / 轮询命中支付成功 → 标 trade_order 已支付。
     *
     * <p>幂等：yudao trade 内部用 status CAS，重复回调直接 short-circuit。
     * 不需要这里再加锁。</p>
     *
     * @param tradeOrderId trade_order.id（来自 reqsn 解析）
     * @param paidAmountFen 通联回执金额（用于二次校验，传 -1 跳过）
     */
    public void markTradeOrderPaid(Long tradeOrderId, int paidAmountFen) {
        if (tradeOrderId == null) {
            log.warn("[trade-allinpay/markPaid] tradeOrderId 为空，忽略");
            return;
        }
        // 跨租户：trade_order tenant_id 是商户租户；当前 ctx 可能是 0（通联回调无 token）
        // 用 executeIgnore 跳过拦截，按 id 精确查出订单后切到该 tenant
        TradeOrderDO order = TenantUtils.executeIgnore(() -> tradeOrderMapper.selectById(tradeOrderId));
        if (order == null) {
            log.warn("[trade-allinpay/markPaid] tradeOrderId={} 不存在，忽略", tradeOrderId);
            return;
        }
        if (order.getPayOrderId() == null) {
            log.error("[trade-allinpay/markPaid] tradeOrderId={} 无 payOrderId，无法标已支付", tradeOrderId);
            return;
        }
        // 金额校验（订单 payPrice 已经经过余额/积分/优惠券抵扣，应等于通联实收）
        if (paidAmountFen >= 0 && order.getPayPrice() != null
                && !order.getPayPrice().equals(paidAmountFen)) {
            log.warn("[trade-allinpay/markPaid] 金额不一致 tradeOrderId={} expected={} actual={}",
                    tradeOrderId, order.getPayPrice(), paidAmountFen);
            // 不直接拒绝（仅 warn），让 yudao trade 内部的 validatePayOrderPaid 兜底校验决定
        }

        Long payOrderId = order.getPayOrderId();
        Long tenantId = order.getTenantId();
        log.info("[trade-allinpay/markPaid] tradeOrderId={} payOrderId={} tenantId={} payPrice={} 开始 updateOrderPaid",
                tradeOrderId, payOrderId, tenantId, order.getPayPrice());
        try {
            // 切到该订单的商户租户上下文（mybatis-plus tenant 拦截器会用）
            TenantUtils.execute(tenantId, () -> {
                tradeOrderUpdateService.updateOrderPaid(tradeOrderId, payOrderId);
                return null;
            });
            log.info("[trade-allinpay/markPaid] tradeOrderId={} ✅ 已标已支付", tradeOrderId);
        } catch (Exception e) {
            // yudao trade 内部抛"未支付"/"金额不一致"等异常时记 error 不再上抛
            // 让回调/轮询返回 success 避免无意义重试（业务异常不是 yudao pay 通信失败）
            log.error("[trade-allinpay/markPaid] tradeOrderId={} updateOrderPaid 失败：{}",
                    tradeOrderId, e.getMessage());
        }
    }

    /**
     * 给定通联 reqsn 字符串（可能带 T 前缀），解析出 tradeOrderId。
     * 不是 trade 业务订单（无 T 前缀）返 null。
     */
    public static Long parseTradeOrderId(String reqsn) {
        if (reqsn == null || reqsn.length() < 2) return null;
        if (reqsn.charAt(0) != 'T' && reqsn.charAt(0) != 't') return null;
        try {
            return Long.parseLong(reqsn.substring(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 构造 trade 业务订单的 reqsn（加 T 前缀区分 package_order）。 */
    public static String buildTradeReqsn(Long tradeOrderId) {
        return "T" + tradeOrderId;
    }
}
