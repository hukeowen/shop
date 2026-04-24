package cn.iocoder.yudao.module.merchant.listener;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MemberShopRelDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopBrokerageConfigDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MemberShopRelMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopBrokerageConfigMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.event.OrderOfflineConfirmedEvent;
import cn.iocoder.yudao.module.merchant.service.MemberShopRelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 订单付款统一事件监听器
 *
 * <p>监听到订单付款事件后，依次执行：
 * <ol>
 *   <li>Phase 3.2 — 一级返佣：若买家有推荐人且商户开启返佣，按比例给推荐人加余额</li>
 *   <li>Phase 3.3 — 积分入账：按租户配置的 pointPerYuan 给买家加积分</li>
 *   <li>Phase 3.6 — 商户余额入账：全额计入商户余额（后续拆分平台费再处理）</li>
 * </ol>
 *
 * <p>注：搜索 yudao-module-mall/yudao-module-trade 未发现 TradeOrderPaidEvent 或类似付款完成事件，
 * 因此当前仅监听 {@link OrderOfflineConfirmedEvent}（商户手动确认到店付款）。
 * 若后续 trade 模块补充了在线付款事件，在此添加对应的 {@code @EventListener} 方法即可。</p>
 */
@Slf4j
@Component
public class OrderPaidListener {

    @Resource
    private MemberShopRelService memberShopRelService;

    @Resource
    private MemberShopRelMapper memberShopRelMapper;

    @Resource
    private ShopBrokerageConfigMapper shopBrokerageConfigMapper;

    @Resource
    private ShopInfoMapper shopInfoMapper;

    /**
     * 监听商户手动确认到店付款事件。
     */
    @Async
    @TenantIgnore
    @EventListener
    public void onOrderOfflineConfirmed(OrderOfflineConfirmedEvent event) {
        Long tenantId = event.getTenantId();
        Long buyerUserId = event.getUserId();
        int payPrice = event.getPayPrice();

        log.info("[OrderPaidListener] 收到到店付款事件 orderId={} tenantId={} buyerUserId={} payPrice={}",
                event.getOrderId(), tenantId, buyerUserId, payPrice);

        processOrderPaid(tenantId, buyerUserId, payPrice);
    }

    // ----------------------------------------------------------------
    // 核心处理逻辑（抽为私有方法，便于复用或后续接入其他事件）
    // ----------------------------------------------------------------

    private void processOrderPaid(Long tenantId, Long buyerUserId, int payPrice) {
        // 查询该租户的返佣/积分配置（selectCurrent 依赖 MP 租户过滤，需切换租户上下文）
        ShopBrokerageConfigDO config = TenantUtils.execute(tenantId,
                () -> shopBrokerageConfigMapper.selectCurrent());

        // Phase 3.2 — 一级返佣
        processFirstBrokerage(tenantId, buyerUserId, payPrice, config);

        // Phase 3.3 — 积分入账（买家）
        processPoints(tenantId, buyerUserId, payPrice, config);

        // Phase 3.6 — 商户余额入账
        processShopBalance(tenantId, payPrice);
    }

    /**
     * Phase 3.2：一级返佣。
     * 查买家的推荐人，若返佣已开启则按 firstBrokeragePercent 给推荐人加余额。
     */
    private void processFirstBrokerage(Long tenantId, Long buyerUserId, int payPrice,
                                        ShopBrokerageConfigDO config) {
        MemberShopRelDO buyerRel = memberShopRelMapper.selectByUserIdAndTenantId(buyerUserId, tenantId);
        if (buyerRel == null || buyerRel.getReferrerUserId() == null) {
            return;
        }
        if (config == null || !Boolean.TRUE.equals(config.getBrokerageEnabled())) {
            return;
        }
        if (config.getFirstBrokeragePercent() == null) {
            return;
        }

        Long referrerUserId = buyerRel.getReferrerUserId();
        // 返佣金额（分）= payPrice × firstBrokeragePercent / 100，截断取整
        int commissionFen = config.getFirstBrokeragePercent()
                .multiply(java.math.BigDecimal.valueOf(payPrice))
                .divide(java.math.BigDecimal.valueOf(100), 0, java.math.RoundingMode.DOWN)
                .intValue();

        if (commissionFen <= 0) {
            return;
        }

        memberShopRelService.addBalance(referrerUserId, tenantId, commissionFen);
        log.info("[OrderPaidListener] 一级返佣 tenantId={} buyerUserId={} referrerUserId={} payPrice={} commissionFen={}",
                tenantId, buyerUserId, referrerUserId, payPrice, commissionFen);
    }

    /**
     * Phase 3.3：积分入账（买家）。
     * 积分 = payPrice(分) / 100 * pointPerYuan（每元赠积分数）。
     */
    private void processPoints(Long tenantId, Long buyerUserId, int payPrice,
                                ShopBrokerageConfigDO config) {
        if (config == null || config.getPointPerYuan() == null || config.getPointPerYuan() <= 0) {
            return;
        }

        int points = (payPrice / 100) * config.getPointPerYuan();
        if (points <= 0) {
            return;
        }

        memberShopRelService.addPoints(buyerUserId, tenantId, points);
        log.info("[OrderPaidListener] 积分入账 tenantId={} buyerUserId={} payPrice={} points={}",
                tenantId, buyerUserId, payPrice, points);
    }

    /**
     * Phase 3.6：商户余额入账（全额，后续扣平台费再拆分）。
     */
    private void processShopBalance(Long tenantId, int payPrice) {
        shopInfoMapper.incrementBalanceAtomic(tenantId, payPrice);
        log.info("[OrderPaidListener] 商户余额入账 tenantId={} payPrice={}", tenantId, payPrice);
    }

}
