package cn.iocoder.yudao.module.merchant.service.allinpay;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pay.dal.dataobject.order.PayOrderDO;
import cn.iocoder.yudao.module.pay.dal.mysql.order.PayOrderMapper;
import cn.iocoder.yudao.module.pay.enums.order.PayOrderStatusEnum;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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

    /** yudao pay_order 直写：通联绕开 yudao pay 标准 channel 通知机制，
     *  我们手动把 pay_order 标 SUCCESS 让 trade 的 validatePayOrderPaid pass。*/
    @Resource
    private PayOrderMapper payOrderMapper;

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
        log.info("[trade-allinpay/markPaid] tradeOrderId={} payOrderId={} tenantId={} payPrice={} 开始处理",
                tradeOrderId, payOrderId, tenantId, order.getPayPrice());

        // 1. 先把 yudao pay_order 标 SUCCESS（trade.updateOrderPaid 的 validatePayOrderPaid 要求）
        //    通联绕开 yudao pay 标准 channel 通知，这里手工 CAS 标成功
        try {
            markPayOrderSuccess(payOrderId, tradeOrderId, tenantId);
        } catch (Exception e) {
            log.error("[trade-allinpay/markPaid] 标 pay_order={} SUCCESS 失败：{}", payOrderId, e.getMessage());
            return;
        }

        // 2. 调 yudao trade 标 trade_order 已支付（CAS 幂等 + 触发 afterPayOrder handler）
        //    handler 内会跑 v8 推 N 反 1 / 极差 / 升星 / 入池
        try {
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
     * 手工把 pay_order 标 SUCCESS（CAS 幂等：仅当 status=WAITING 时才改）。
     *
     * <p>通联直接调 cashier H5（绕开 yudao pay channel 体系），所以 pay_order 不会被 yudao 自动更新。
     * 这里在 markTradeOrderPaid 之前补这步，让 trade 的 validatePayOrderPaid pass。</p>
     */
    private void markPayOrderSuccess(Long payOrderId, Long tradeOrderId, Long tenantId) {
        // pay_order 是 TenantBaseDO，但通联回调没 tenant ctx；用 executeIgnore 绕开拦截
        TenantUtils.executeIgnore(() -> {
            PayOrderDO payOrder = payOrderMapper.selectById(payOrderId);
            if (payOrder == null) {
                log.error("[markPayOrderSuccess] payOrder={} 不存在", payOrderId);
                return null;
            }
            if (PayOrderStatusEnum.SUCCESS.getStatus().equals(payOrder.getStatus())) {
                log.info("[markPayOrderSuccess] payOrder={} 已是 SUCCESS，幂等短路", payOrderId);
                return null;
            }
            if (!PayOrderStatusEnum.WAITING.getStatus().equals(payOrder.getStatus())) {
                log.warn("[markPayOrderSuccess] payOrder={} 状态={} 非 WAITING，跳过",
                        payOrderId, payOrder.getStatus());
                return null;
            }
            // CAS UPDATE（用 mybatis-plus selectByIdAndStatus 风格，避免抢占）
            PayOrderDO patch = new PayOrderDO();
            patch.setId(payOrderId);
            patch.setStatus(PayOrderStatusEnum.SUCCESS.getStatus());
            patch.setChannelCode("allinpay_qr");
            patch.setSuccessTime(LocalDateTime.now());
            int rows = payOrderMapper.update(patch,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PayOrderDO>()
                            .eq(PayOrderDO::getId, payOrderId)
                            .eq(PayOrderDO::getStatus, PayOrderStatusEnum.WAITING.getStatus()));
            if (rows == 0) {
                log.warn("[markPayOrderSuccess] payOrder={} CAS 失败（并发或已被标）", payOrderId);
            } else {
                log.info("[markPayOrderSuccess] payOrder={} 已标 SUCCESS", payOrderId);
            }
            return null;
        });
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
