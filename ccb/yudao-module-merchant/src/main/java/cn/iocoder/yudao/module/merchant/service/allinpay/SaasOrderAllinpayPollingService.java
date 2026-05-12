package cn.iocoder.yudao.module.merchant.service.allinpay;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.saas.MerchantSubscriptionOrderDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.saas.MerchantSubscriptionOrderMapper;
import cn.iocoder.yudao.module.merchant.service.saas.SaasSubscriptionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SaaS 订阅订单通联主动查询轮询 — 异步回调验签失败 / 漏发的兜底。
 *
 * <p>问题背景：通联 SM2 回调验签 hutool 实现跟通联签名细节有差异（ZA hash userId / source 拼接等），
 * 短期内无法保证 100% 验签通过；但我们主动调 query API 时**自己签名**通联接受，
 * 通联返回的 trxstatus 直接判断（不需验通联签名），即可绕过回调验签问题。</p>
 *
 * <p>触发：</p>
 * <ul>
 *   <li>{@link #schedulePolling(Long)} — purchase 创建订单后立即排程 6 段查询（5s/15s/25s/35s/60s/120s）</li>
 *   <li>{@link #scanWaitingOrders()} — 每 2 分钟扫所有 WAITING > 30s 且 < 2h 的订阅订单兜底</li>
 * </ul>
 */
@Service
@Slf4j
public class SaasOrderAllinpayPollingService {

    private static final long[] DELAYS_SEC = {5, 15, 25, 35, 60, 120};

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "saas-allinpay-poll");
                t.setDaemon(true);
                return t;
            });

    private final Set<Long> inflight = ConcurrentHashMap.newKeySet();

    @Resource
    private AllinpayCashierService cashierService;

    @Resource
    private SaasSubscriptionService subscriptionService;

    @Resource
    private MerchantSubscriptionOrderMapper subscriptionOrderMapper;

    public void schedulePolling(Long orderId) {
        if (orderId == null) return;
        if (!inflight.add(orderId)) {
            log.info("[saas-poll] orderId={} 已 inflight，跳过重复排程", orderId);
            return;
        }
        long elapsed = 0;
        for (int i = 0; i < DELAYS_SEC.length; i++) {
            elapsed += DELAYS_SEC[i];
            final int round = i + 1;
            final long delay = elapsed;
            scheduler.schedule(() -> tickQuery(orderId, round), delay, TimeUnit.SECONDS);
        }
        log.info("[saas-poll] orderId={} ✅ 已排程 6 段查询，总时长 {}s",
                orderId, Arrays.stream(DELAYS_SEC).sum());
    }

    private void tickQuery(Long orderId, int round) {
        log.info("[saas-poll] orderId={} round={} ▶ 开始查询", orderId, round);
        try {
            MerchantSubscriptionOrderDO order = subscriptionOrderMapper.selectById(orderId);
            if (order == null) {
                inflight.remove(orderId);
                log.warn("[saas-poll] orderId={} 订单不存在，停止", orderId);
                return;
            }
            if (order.getPayStatus() != null
                    && order.getPayStatus() != MerchantSubscriptionOrderDO.PAY_STATUS_WAITING) {
                inflight.remove(orderId);
                log.info("[saas-poll] orderId={} 状态={} 已非 WAITING，停止", orderId, order.getPayStatus());
                return;
            }

            String reqsn = order.getTlReqsn();
            if (reqsn == null || reqsn.isEmpty()) {
                log.warn("[saas-poll] orderId={} tl_reqsn 为空，停止", orderId);
                inflight.remove(orderId);
                return;
            }

            // SaaS 用平台商户 (999) 的凭据
            AllinpayCashierService.TlpayCredential cred = cashierService.merchantCredentialForTenant(999L);
            AllinpayCashierService.QueryResult r = cashierService.queryByReqsn(reqsn, cred);
            if (r == null) {
                log.warn("[saas-poll] orderId={} round={} 通联返空（通信失败），等下一轮", orderId, round);
                return;
            }
            if (!r.isSuccess()) {
                log.info("[saas-poll] orderId={} round={} 通联返 trxstatus={} trxamt={}（未支付），等下一轮",
                        orderId, round, r.getTrxstatus(), r.getTrxamt());
                return;
            }

            // 命中支付成功 → markPaid（SaaSSubscriptionService.markPaid 内部 CAS 幂等）
            log.info("[saas-poll] orderId={} round={} 命中 trxstatus=2000 trxamt={}，markPaid",
                    orderId, round, r.getTrxamt());
            subscriptionService.markPaid(orderId, r.getTrxamt());
            inflight.remove(orderId);
            log.info("[saas-poll] orderId={} round={} ✅ 主动查询命中支付成功，已升级 SaaS", orderId, round);
        } catch (Exception e) {
            log.error("[saas-poll] orderId={} round={} 查询异常", orderId, round, e);
        }
    }

    /** 兜底定时：扫所有 WAITING > 30s 且 < 2h 的订阅订单，重启或漏排时捞回。每 2 分钟。 */
    @Scheduled(fixedDelay = 120_000L, initialDelay = 60_000L)
    @Async
    @TenantIgnore
    public void scanWaitingOrders() {
        log.info("[saas-poll/scan] ▶ 扫 WAITING 订阅订单 inflight={}单", inflight.size());
        try {
            List<MerchantSubscriptionOrderDO> waiting = TenantUtils.executeIgnore(
                    () -> subscriptionOrderMapper.selectList(new LambdaQueryWrapper<MerchantSubscriptionOrderDO>()
                            .eq(MerchantSubscriptionOrderDO::getPayStatus, MerchantSubscriptionOrderDO.PAY_STATUS_WAITING)
                            .lt(MerchantSubscriptionOrderDO::getCreateTime, LocalDateTime.now().minusSeconds(30))
                            .gt(MerchantSubscriptionOrderDO::getCreateTime, LocalDateTime.now().minusHours(2))
                    ));
            if (waiting == null || waiting.isEmpty()) {
                log.info("[saas-poll/scan] 无 WAITING 订阅订单（30s~2h）");
                return;
            }
            int scheduled = 0;
            for (MerchantSubscriptionOrderDO o : waiting) {
                if (inflight.contains(o.getId())) continue;
                schedulePolling(o.getId());
                scheduled++;
            }
            log.info("[saas-poll/scan] ✅ 本轮新排程 {} 单（共 {} 个 WAITING）", scheduled, waiting.size());
        } catch (Exception e) {
            log.error("[saas-poll/scan] 扫描异常", e);
        }
    }
}
