package cn.iocoder.yudao.module.merchant.service.allinpay;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantPackageOrderDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantPackageOrderMapper;
import cn.iocoder.yudao.module.merchant.service.MerchantPackageOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 通联订单主动查询轮询 — 异步通知漏发兜底。
 *
 * <p>触发方式：</p>
 * <ul>
 *   <li>{@link #schedulePolling(Long)}：业务 createOrder("allinpay_h5") 后立即调用，
 *       按 5s / 15s / 25s / 35s / 60s / 120s 排 6 次查询；命中 trxstatus=2000 提前结束</li>
 *   <li>{@link #scanWaitingOrders()}：定时扫所有 WAITING > 30s 的 allinpay_h5 订单，
 *       捞回服务重启时丢失的 ScheduledFuture，确保最终一致</li>
 * </ul>
 */
@Service
@Slf4j
public class AllinpayPollingService {

    /** 轮询节奏：5s / 15s / 25s / 35s / 60s / 120s */
    private static final long[] DELAYS_SEC = {5, 15, 25, 35, 60, 120};

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "allinpay-poll");
                t.setDaemon(true);
                return t;
            });

    /** 防止同一订单被多次排程 */
    private final java.util.Set<Long> inflight = ConcurrentHashMap.newKeySet();

    @Resource
    private AllinpayCashierService cashierService;

    @Resource
    private MerchantPackageOrderService packageOrderService;

    @Resource
    private MerchantPackageOrderMapper packageOrderMapper;

    /** createOrder 后立即调用：按 6 段退避排程查询。 */
    public void schedulePolling(Long orderId) {
        if (orderId == null) {
            log.warn("[allinpay/poll] schedulePolling 收到 null orderId，忽略");
            return;
        }
        if (!inflight.add(orderId)) {
            log.info("[allinpay/poll] orderId={} 已在 inflight 集合，跳过重复排程", orderId);
            return;
        }
        long elapsed = 0;
        for (int i = 0; i < DELAYS_SEC.length; i++) {
            elapsed += DELAYS_SEC[i];
            final int round = i + 1;
            final long delay = elapsed;
            scheduler.schedule(() -> tickQuery(orderId, round), delay, TimeUnit.SECONDS);
        }
        log.info("[allinpay/poll] orderId={} ✅ 已排程 6 段查询，节奏=[5s,15s,25s,35s,60s,120s]，总时长 {}s",
                orderId, java.util.Arrays.stream(DELAYS_SEC).sum());
    }

    private void tickQuery(Long orderId, int round) {
        log.info("[allinpay/poll] orderId={} round={} ▶ 开始查询", orderId, round);
        try {
            // 重新读订单：可能已被异步通知或前一轮查询标记为 PAID
            MerchantPackageOrderDO order = TenantUtils.executeIgnore(() -> packageOrderMapper.selectById(orderId));
            if (order == null) {
                inflight.remove(orderId);
                log.warn("[allinpay/poll] orderId={} round={} 订单不存在，停止轮询", orderId, round);
                return;
            }
            if (order.getPayStatus() != null
                    && order.getPayStatus() != MerchantPackageOrderDO.PAY_STATUS_WAITING) {
                inflight.remove(orderId);
                log.info("[allinpay/poll] orderId={} round={} 状态={} 已非 WAITING，停止轮询",
                        orderId, round, order.getPayStatus());
                return;
            }

            AllinpayCashierService.QueryResult r = cashierService.queryOrder(orderId);
            if (r == null) {
                log.warn("[allinpay/poll] orderId={} round={} 通联返空（通信失败），等下一轮", orderId, round);
                return;
            }
            if (!r.isSuccess()) {
                log.info("[allinpay/poll] orderId={} round={} 通联返 trxstatus={} trxamt={}（未支付），等下一轮",
                        orderId, round, r.getTrxstatus(), r.getTrxamt());
                return;
            }

            // 命中支付成功 → markPaidExternal
            log.info("[allinpay/poll] orderId={} round={} 命中 trxstatus=2000 trxamt={}，开始 markPaidExternal",
                    orderId, round, r.getTrxamt());
            packageOrderService.markPaidExternal(orderId, r.getTrxamt(), "ALLINPAY_POLL");
            inflight.remove(orderId);
            log.info("[allinpay/poll] orderId={} round={} ✅ 主动查询命中支付成功，已加配额", orderId, round);
        } catch (Exception e) {
            log.error("[allinpay/poll] orderId={} round={} 查询异常", orderId, round, e);
        }
    }

    /**
     * 兜底定时任务：扫所有 WAITING > 30s 的订单，触发轮询。
     *
     * <p>用途：服务重启后内存里 inflight 丢了，scheduler 排程也丢了；这里捞回。</p>
     * <p>每 2 分钟跑一次。</p>
     */
    @Scheduled(fixedDelay = 120_000L, initialDelay = 60_000L)
    @Async
    @TenantIgnore
    public void scanWaitingOrders() {
        log.info("[allinpay/poll/scan] ▶ 定时扫 WAITING 订单（每 2 分钟）inflight 当前 {} 单", inflight.size());
        try {
            List<MerchantPackageOrderDO> waiting = TenantUtils.executeIgnore(
                    () -> packageOrderMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MerchantPackageOrderDO>()
                            .eq(MerchantPackageOrderDO::getPayStatus, MerchantPackageOrderDO.PAY_STATUS_WAITING)
                            .lt(MerchantPackageOrderDO::getCreateTime,
                                    java.time.LocalDateTime.now().minusSeconds(30))
                            .gt(MerchantPackageOrderDO::getCreateTime,
                                    java.time.LocalDateTime.now().minusHours(2)) // 2h 之前的不再追，认为放弃支付
                    ));
            if (waiting == null || waiting.isEmpty()) {
                log.info("[allinpay/poll/scan] DB 无 WAITING 订单（30s~2h 范围）");
                return;
            }
            log.info("[allinpay/poll/scan] DB 找到 {} 个 WAITING 订单，开始检查 inflight 排程", waiting.size());
            int scheduled = 0;
            for (MerchantPackageOrderDO o : waiting) {
                if (inflight.contains(o.getId())) {
                    log.debug("[allinpay/poll/scan] orderId={} 已在 inflight，跳过", o.getId());
                    continue;
                }
                schedulePolling(o.getId());
                scheduled++;
            }
            log.info("[allinpay/poll/scan] ✅ 本轮新排程 {} 单（其余在 inflight）", scheduled);
        } catch (Exception e) {
            log.error("[allinpay/poll/scan] 扫描异常", e);
        }
    }
}
