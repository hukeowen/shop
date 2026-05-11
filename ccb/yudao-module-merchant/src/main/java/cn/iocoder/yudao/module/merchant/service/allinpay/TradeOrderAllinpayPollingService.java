package cn.iocoder.yudao.module.merchant.service.allinpay;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * trade_order 通联支付轮询 — 异步通知漏发兜底。
 *
 * <h3>双兜底架构</h3>
 * <ul>
 *   <li>主路径：通联异步通知 → {@code AllinpayNotifyController.onAllinpayPayNotify}
 *       → reqsn 前缀 T 路由 → {@link TradeOrderAllinpayService#markTradeOrderPaid}</li>
 *   <li>兜底 1（短期）：checkout/submit 成功后立即 {@link #schedulePolling}，按 5/15/25/35/60/120s
 *       6 段退避主动查通联，命中 trxstatus=2000 调 markTradeOrderPaid</li>
 *   <li>兜底 2（长期）：{@link #scanWaitingOrders} 每 2 分钟扫所有 status=UNPAID 且 createTime > 30s
 *       的 trade_order，捞回服务重启时 ScheduledFuture 丢失的订单</li>
 * </ul>
 *
 * <h3>集群部署安全</h3>
 * <ul>
 *   <li><b>查通联用 Redisson 分布式锁</b>：key=tlpay:trade-poll:{orderId}，tryLock(0, 30s)
 *       拿不到立即跳过 — 其它实例正在查，避免多实例 N 倍 QPS 打通联</li>
 *   <li><b>扫描器用 Redisson 单实例锁</b>：key=tlpay:trade-poll:scan，
 *       tryLock(0, 110s) 让单 round 只有一个实例执行 scan（fixedDelay 120s 留 10s 容差）</li>
 *   <li><b>markPaid 幂等</b>：yudao trade.updateOrderPaid 内部 CAS update by status，重复回调安全</li>
 *   <li><b>inflight Set 是单实例本地</b>：仅作单机短路（同实例不重复排程）；服务重启后丢，
 *       靠 scanWaitingOrders 60s 兜底捞回</li>
 * </ul>
 */
@Service
@Slf4j
public class TradeOrderAllinpayPollingService {

    /** 轮询节奏：5s / 15s / 25s / 35s / 60s / 120s（与 package_order 对齐） */
    private static final long[] DELAYS_SEC = {5, 15, 25, 35, 60, 120};

    /** 单订单查询锁 TTL（节流防多实例重复查同一订单） */
    private static final long PER_ORDER_LOCK_TTL_SEC = 30;
    /** 扫描器锁 TTL（fixedDelay 120s + 10s 容差） */
    private static final long SCAN_LOCK_TTL_SEC = 110;

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "trade-allinpay-poll");
                t.setDaemon(true);
                return t;
            });

    /** 单实例本地：防同实例对同一订单重复排程；服务重启丢失靠 scan 兜底 */
    private final java.util.Set<Long> inflight = ConcurrentHashMap.newKeySet();

    @Resource
    private AllinpayCashierService cashierService;
    @Resource
    private TradeOrderAllinpayService tradeOrderAllinpayService;
    @Resource
    private TradeOrderMapper tradeOrderMapper;
    @Resource
    private RedissonClient redissonClient;

    /** checkout/submit 后立即排程：6 段退避主动查 */
    public void schedulePolling(Long tradeOrderId) {
        if (tradeOrderId == null) {
            log.warn("[trade-poll] schedulePolling 收到 null tradeOrderId，忽略");
            return;
        }
        if (!inflight.add(tradeOrderId)) {
            log.info("[trade-poll] tradeOrderId={} 已在 inflight，跳过重复排程", tradeOrderId);
            return;
        }
        long elapsed = 0;
        for (int i = 0; i < DELAYS_SEC.length; i++) {
            elapsed += DELAYS_SEC[i];
            final int round = i + 1;
            final long delay = elapsed;
            scheduler.schedule(() -> tickQuery(tradeOrderId, round), delay, TimeUnit.SECONDS);
        }
        log.info("[trade-poll] tradeOrderId={} ✅ 已排程 {} 段查询，节奏=[5,15,25,35,60,120]s",
                tradeOrderId, DELAYS_SEC.length);
    }

    /** 单轮查询：用 Redisson 锁防多实例重复 → 节流通联 QPS。 */
    private void tickQuery(Long tradeOrderId, int round) {
        String lockKey = "tlpay:trade-poll:" + tradeOrderId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            // 拿锁失败立即跳过（其它实例正在查或刚查完冷却期）
            acquired = lock.tryLock(0, PER_ORDER_LOCK_TTL_SEC, TimeUnit.SECONDS);
            if (!acquired) {
                log.info("[trade-poll] tradeOrderId={} round={} 锁未获取（其它实例处理中），跳过",
                        tradeOrderId, round);
                return;
            }
            log.info("[trade-poll] tradeOrderId={} round={} ▶ 开始查询", tradeOrderId, round);

            // 重读订单：可能已被异步通知或前一轮查询标已支付
            TradeOrderDO order = TenantUtils.executeIgnore(() -> tradeOrderMapper.selectById(tradeOrderId));
            if (order == null) {
                inflight.remove(tradeOrderId);
                log.warn("[trade-poll] tradeOrderId={} round={} 订单不存在，停止轮询", tradeOrderId, round);
                return;
            }
            if (!TradeOrderStatusEnum.isUnpaid(order.getStatus()) || Boolean.TRUE.equals(order.getPayStatus())) {
                inflight.remove(tradeOrderId);
                log.info("[trade-poll] tradeOrderId={} round={} status={} payStatus={} 已非待付款，停止轮询",
                        tradeOrderId, round, order.getStatus(), order.getPayStatus());
                return;
            }

            // 通联查询：reqsn 用 T 前缀区分 trade 业务（避免和 package_order 同 id 冲突）
            String reqsn = TradeOrderAllinpayService.buildTradeReqsn(tradeOrderId);
            AllinpayCashierService.QueryResult r = cashierService.queryByReqsn(reqsn);
            if (r == null) {
                log.warn("[trade-poll] tradeOrderId={} round={} 通联返空（通信失败），等下一轮",
                        tradeOrderId, round);
                return;
            }
            if (!r.isSuccess()) {
                log.info("[trade-poll] tradeOrderId={} round={} 通联返 trxstatus={} trxamt={}（未支付），等下一轮",
                        tradeOrderId, round, r.getTrxstatus(), r.getTrxamt());
                return;
            }

            log.info("[trade-poll] tradeOrderId={} round={} 命中 trxstatus=2000 trxamt={}，开始 markTradeOrderPaid",
                    tradeOrderId, round, r.getTrxamt());
            tradeOrderAllinpayService.markTradeOrderPaid(tradeOrderId, r.getTrxamt());
            inflight.remove(tradeOrderId);
            log.info("[trade-poll] tradeOrderId={} round={} ✅ 主动查询命中支付成功，已通知 yudao trade",
                    tradeOrderId, round);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("[trade-poll] tradeOrderId={} round={} 锁等待被中断", tradeOrderId, round);
        } catch (Exception e) {
            log.error("[trade-poll] tradeOrderId={} round={} 查询异常", tradeOrderId, round, e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                try { lock.unlock(); } catch (Exception ignore) {}
            }
        }
    }

    /**
     * 兜底扫描：每 2 分钟扫所有 UNPAID 且 createTime > 30s 的订单，触发轮询。
     *
     * <p>集群下用 Redisson 锁让单 round 只有一个实例真正扫 DB + 排程。
     * fixedDelay 120s，锁 TTL 110s — 留 10s 容差防过期前下一轮启动。</p>
     */
    @Scheduled(fixedDelay = 120_000L, initialDelay = 60_000L)
    @Async
    @TenantIgnore
    public void scanWaitingOrders() {
        RLock lock = redissonClient.getLock("tlpay:trade-poll:scan");
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, SCAN_LOCK_TTL_SEC, TimeUnit.SECONDS);
            if (!acquired) {
                log.debug("[trade-poll/scan] 锁未获取（其它实例正在扫），跳过本轮");
                return;
            }
            log.info("[trade-poll/scan] ▶ 扫 UNPAID 订单（每 2 分钟）inflight 当前 {} 单",
                    inflight.size());
            try {
                LocalDateTime now = LocalDateTime.now();
                List<TradeOrderDO> waiting = TenantUtils.executeIgnore(() ->
                        tradeOrderMapper.selectList(new LambdaQueryWrapper<TradeOrderDO>()
                                .eq(TradeOrderDO::getStatus, TradeOrderStatusEnum.UNPAID.getStatus())
                                .eq(TradeOrderDO::getPayStatus, false)
                                .lt(TradeOrderDO::getCreateTime, now.minusSeconds(30))
                                // 2 小时之前的不再追，认为放弃支付
                                .gt(TradeOrderDO::getCreateTime, now.minusHours(2))));
                if (waiting == null || waiting.isEmpty()) {
                    log.info("[trade-poll/scan] DB 无 UNPAID 订单（30s~2h 范围）");
                    return;
                }
                log.info("[trade-poll/scan] DB 找到 {} 个 UNPAID 订单，开始检查 inflight 排程",
                        waiting.size());
                int scheduled = 0;
                for (TradeOrderDO o : waiting) {
                    if (inflight.contains(o.getId())) continue;
                    schedulePolling(o.getId());
                    scheduled++;
                }
                log.info("[trade-poll/scan] ✅ 本轮新排程 {} 单", scheduled);
            } catch (Exception e) {
                log.error("[trade-poll/scan] 扫描异常", e);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                try { lock.unlock(); } catch (Exception ignore) {}
            }
        }
    }
}
