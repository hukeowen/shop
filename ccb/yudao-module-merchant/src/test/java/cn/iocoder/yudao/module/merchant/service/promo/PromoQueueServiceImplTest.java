package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.controller.app.vo.AppQueuePositionRespVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueueEventDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueuePositionDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopQueueEventMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopQueuePositionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link PromoQueueServiceImpl} 跑通 v6 文档第 5.4 节的 10 步样例。
 *
 * 商品配置：N=3，比例 [30, 30, 40]；每笔订单实付 100 元 = 10000 分。
 *
 * 期望终态（最关键的几个数）：
 *   - 用户 1 / 2 / 7 各累计 100% × 10000 = 10000 分推广积分（3 步出队，30+30+40）
 *   - 队列剩余：B = [3, 4, 5, 6, 10]；A = []
 *   - 用户 1 / 2 / 7 status = EXITED，其它 QUEUEING
 */
class PromoQueueServiceImplTest {

    private static final Long SPU = 100L;
    private static final long PAID = 10000L;
    private static final ProductPromoConfigDO CONFIG = ProductPromoConfigDO.builder()
            .spuId(SPU)
            .tuijianEnabled(true)
            .tuijianN(3)
            .tuijianRatios("[30,30,40]")
            .build();

    private PromoQueueServiceImpl service;
    private ShopQueuePositionMapper queueMapper;
    private ProductPromoConfigService productPromoConfigService;

    // 内存态
    private final Map<Long, ShopQueuePositionDO> queueByUserId = new HashMap<>();
    private final List<ShopQueueEventDO> events = new ArrayList<>();
    private final AtomicLong autoId = new AtomicLong(1);

    // 推荐链：parent[userId]
    private final Map<Long, Long> parentMap = new HashMap<>();

    // 收到的积分入账：userId -> total amount
    private final Map<Long, Long> rewards = new HashMap<>();

    @BeforeEach
    void setUp() {
        queueMapper = mock(ShopQueuePositionMapper.class);
        ShopQueueEventMapper eventMapper = mock(ShopQueueEventMapper.class);
        ReferralService referralService = mock(ReferralService.class);
        PromoPointService pointService = mock(PromoPointService.class);
        productPromoConfigService = mock(ProductPromoConfigService.class);

        // selectByUserAndSpu
        when(queueMapper.selectByUserAndSpu(any(), any())).thenAnswer(inv ->
                queueByUserId.get(inv.<Long>getArgument(0)));
        // selectQueueHead — 实现 A 层优先 → 按 promotedAt → 按 joinedAt 排序
        when(queueMapper.selectQueueHead(any())).thenAnswer(inv -> {
            List<ShopQueuePositionDO> queueing = queueByUserId.values().stream()
                    .filter(p -> "QUEUEING".equals(p.getStatus()))
                    .sorted(Comparator
                            .comparing(ShopQueuePositionDO::getLayer)             // A < B
                            .thenComparing(p -> p.getPromotedAt() == null
                                    ? LocalDateTime.MAX : p.getPromotedAt())
                            .thenComparing(ShopQueuePositionDO::getJoinedAt))
                    .collect(Collectors.toList());
            return queueing.isEmpty() ? null : queueing.get(0);
        });
        // insert / updateById — 直接修改内存态
        when(queueMapper.insert(any(ShopQueuePositionDO.class))).thenAnswer(inv -> {
            ShopQueuePositionDO p = inv.getArgument(0);
            p.setId(autoId.getAndIncrement());
            queueByUserId.put(p.getUserId(), p);
            return 1;
        });
        when(queueMapper.updateById(any(ShopQueuePositionDO.class))).thenAnswer(inv -> {
            ShopQueuePositionDO p = inv.getArgument(0);
            queueByUserId.put(p.getUserId(), p);
            return 1;
        });
        when(eventMapper.insert(any(ShopQueueEventDO.class))).thenAnswer(inv -> {
            events.add(inv.getArgument(0));
            return 1;
        });
        // 幂等检查：从 events 列表实时查
        when(eventMapper.existsByOrderAndBeneficiary(any(), any(), any(), any())).thenAnswer(inv -> {
            Long orderId = inv.getArgument(0);
            Long spu = inv.getArgument(1);
            Long ben = inv.getArgument(2);
            String type = inv.getArgument(3);
            return events.stream().anyMatch(e ->
                    java.util.Objects.equals(e.getSourceOrderId(), orderId)
                            && java.util.Objects.equals(e.getSpuId(), spu)
                            && java.util.Objects.equals(e.getBeneficiaryUserId(), ben)
                            && java.util.Objects.equals(e.getEventType(), type));
        });
        // referral
        when(referralService.getDirectParent(any())).thenAnswer(inv ->
                parentMap.getOrDefault(inv.<Long>getArgument(0), 0L));
        // point service — 累加 rewards
        when(pointService.addPromoPoint(any(), anyLong(), any(), any(), any())).thenAnswer(inv -> {
            Long uid = inv.getArgument(0);
            long amt = inv.getArgument(1);
            rewards.merge(uid, amt, Long::sum);
            return true;
        });

        service = new PromoQueueServiceImpl();
        ReflectionTestUtils.setField(service, "queueMapper", queueMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "referralService", referralService);
        ReflectionTestUtils.setField(service, "promoPointService", pointService);
        ReflectionTestUtils.setField(service, "productPromoConfigService", productPromoConfigService);
        ReflectionTestUtils.setField(service, "productSpuService",
                mock(cn.iocoder.yudao.module.product.service.spu.ProductSpuService.class));
    }

    @Test
    void runs_v6_example_section_5_4() {
        // === 事件 1 ~ 7：自然买，依次 user 1..7 ===
        for (long u = 1; u <= 7; u++) {
            handle(u, /* parent */ 0L, /* orderId */ u);
        }

        // 此时：
        // - user 1 累计满 3 → EXITED，奖 30+30+40 = 100% = 10000
        // - user 2 累计满 3 → EXITED，10000
        // - user 3 还未被返奖（一直在 B 但 head 排在 1/2 后）
        assertEquals(10000L, totalReward(1L), "user 1 应累计 10000");
        assertEquals(10000L, totalReward(2L), "user 2 应累计 10000");
        assertEquals(0L, totalReward(3L), "user 3 此时尚未拿奖");
        assertExited(1L);
        assertExited(2L);
        assertQueueing(3L);
        assertQueueing(4L);
        assertQueueing(5L);
        assertQueueing(6L);
        assertQueueing(7L);
        assertEquals("B", queueByUserId.get(7L).getLayer(), "用户 7 仍是 B 层");

        // === 事件 8：user 7 推 user 8（DIRECT）===
        handle(8L, /* parent */ 7L, 8L);
        assertEquals(3000L, totalReward(7L), "DIRECT 第 1 位 30%");
        ShopQueuePositionDO p7 = queueByUserId.get(7L);
        assertEquals("A", p7.getLayer(), "DIRECT 后 7 升 A 层");
        assertEquals(1, p7.getAccumulatedCount());
        assertNull(queueByUserId.get(8L), "8 是被推荐的，不入队");

        // === 事件 9：7 推 9（DIRECT）===
        handle(9L, 7L, 9L);
        assertEquals(6000L, totalReward(7L), "DIRECT 第 2 位 30%，累计 60%");

        // === 事件 10：自然用户 10 买 ===
        handle(10L, 0L, 10L);
        // head 此时是 A 层的 7，7 拿第 3 位 40% → 累计 100% → EXIT
        assertEquals(10000L, totalReward(7L), "user 7 满 3 步出队，得 100%");
        assertExited(7L);
        assertQueueing(10L);
        assertEquals("B", queueByUserId.get(10L).getLayer(), "10 进 B 层");

        // === 终态汇总 ===
        // 仍在排队的 B 层成员（按 joinedAt 升序）= [3, 4, 5, 6, 10]
        List<Long> bLayer = queueByUserId.values().stream()
                .filter(p -> "QUEUEING".equals(p.getStatus()))
                .filter(p -> "B".equals(p.getLayer()))
                .sorted(Comparator.comparing(ShopQueuePositionDO::getJoinedAt))
                .map(ShopQueuePositionDO::getUserId)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(3L, 4L, 5L, 6L, 10L), bLayer);

        // A 层应空（user 7 已 EXITED）
        boolean anyAlive = queueByUserId.values().stream()
                .anyMatch(p -> "QUEUEING".equals(p.getStatus()) && "A".equals(p.getLayer()));
        assertFalse(anyAlive, "A 层应为空");

        // 总返奖事件应至少 9 条 reward + 3 条 EXIT = 12 条
        // (DIRECT/QUEUE/SELF_PURCHASE 9 次 × 1 + 3 次 EXIT)
        long rewardEvents = events.stream()
                .filter(e -> !"EXIT".equals(e.getEventType())).count();
        long exitEvents = events.stream()
                .filter(e -> "EXIT".equals(e.getEventType())).count();
        assertEquals(9, rewardEvents, "reward 事件数 = 9");
        assertEquals(3, exitEvents, "EXIT 事件数 = 3 (用户 1, 2, 7)");
    }

    // 触发一笔订单
    private void handle(long buyer, long parent, long orderId) {
        if (parent > 0) {
            parentMap.put(buyer, parent);
        }
        service.handleOrderPaid(CONFIG, buyer, SPU, PAID, orderId);
    }

    private long totalReward(Long userId) {
        return rewards.getOrDefault(userId, 0L);
    }

    private void assertExited(Long userId) {
        ShopQueuePositionDO p = queueByUserId.get(userId);
        assertNotNull(p, "user " + userId + " 应有队列记录");
        assertEquals("EXITED", p.getStatus(), "user " + userId + " 应已出队");
    }

    private void assertQueueing(Long userId) {
        ShopQueuePositionDO p = queueByUserId.get(userId);
        assertNotNull(p, "user " + userId + " 应在队列");
        assertEquals("QUEUEING", p.getStatus(), "user " + userId + " 应还在排队");
    }

    @Test
    void self_purchase_promotes_b_to_a_and_rewards_self() {
        // user 1 自然买 → 进 B
        handle(1L, 0L, 1L);
        // user 2 自然买 → 1 拿 30%
        handle(2L, 0L, 2L);
        // user 1 自购：parent=0 + 已在 B → SELF_PURCHASE
        handle(1L, 0L, 3L);

        // 第 2 位是 30%，所以再加 3000，累计 6000
        // user 1 升 A 层，仍在排队
        assertEquals(6000L, totalReward(1L));
        ShopQueuePositionDO p1 = queueByUserId.get(1L);
        assertEquals("A", p1.getLayer(), "SELF_PURCHASE 后 1 升 A 层");
        assertEquals("QUEUEING", p1.getStatus());
        assertEquals(2, p1.getAccumulatedCount());
    }

    @Test
    void natural_buy_with_empty_queue_just_enters_b_no_reward() {
        handle(1L, 0L, 1L);

        assertEquals(0L, totalReward(1L), "首位自然买无人可返");
        ShopQueuePositionDO p1 = queueByUserId.get(1L);
        assertNotNull(p1);
        assertEquals("B", p1.getLayer());
        assertEquals(0, p1.getAccumulatedCount());
    }

    @Test
    void disabled_config_isNoop() {
        ProductPromoConfigDO disabled = ProductPromoConfigDO.builder()
                .spuId(SPU).tuijianEnabled(false).tuijianN(3).tuijianRatios("[30,30,40]")
                .build();
        service.handleOrderPaid(disabled, 1L, SPU, PAID, 1L);
        assertTrue(queueByUserId.isEmpty());
        assertTrue(rewards.isEmpty());
    }

    @Test
    void direct_creates_a_position_for_first_time_uplline() {
        // user 1 没有任何队列记录；user 2 推 user 3 买
        handle(3L, 1L, 1L);

        ShopQueuePositionDO p1 = queueByUserId.get(1L);
        assertNotNull(p1, "首次推下级时上级直接进 A 层");
        assertEquals("A", p1.getLayer());
        assertEquals(1, p1.getAccumulatedCount());
        assertEquals(3000L, totalReward(1L));
    }

    @Test
    void replay_sameOrder_doesNotDoubleAward_norDoubleAdvance() {
        // user 1 进 B、user 2 自然买 → 1 应拿一次 30%
        handle(1L, 0L, 1L);   // 1 进 B
        handle(2L, 0L, 2L);   // 1 拿 30% × 10000 = 3000，accumulatedCount=1

        long after1stRound = totalReward(1L);
        int after1stCount = queueByUserId.get(1L).getAccumulatedCount();
        assertEquals(3000L, after1stRound);
        assertEquals(1, after1stCount);

        // 模拟订单 handler 重放：同样的事件再触发一次（同 orderId=2、同 spu、同 buyer）
        // 因 queue_event 已有 (orderId=2, spu, beneficiary=1, QUEUE) → 必须静默跳过
        service.handleOrderPaid(CONFIG, 2L, SPU, PAID, 2L);

        assertEquals(after1stRound, totalReward(1L), "重放不应重复返奖");
        assertEquals(after1stCount, queueByUserId.get(1L).getAccumulatedCount(),
                "重放不应再推进 accumulated_count");
    }

    @Test
    void listMyQueueing_empty_userReturnsEmpty() {
        when(queueMapper.selectListByUserIdQueueing(eq(99L))).thenReturn(Collections.emptyList());
        List<AppQueuePositionRespVO> result = service.listMyQueueing(99L);
        assertTrue(result.isEmpty());
    }

    @Test
    void listMyQueueing_multipleSpus_attachesMaxN_andSkipsExited() {
        // 用户在 spu=100 是 B 层累计 1 次，在 spu=200 是 A 层累计 2 次
        ShopQueuePositionDO p1 = ShopQueuePositionDO.builder()
                .userId(7L).spuId(100L).layer("B").accumulatedCount(1).accumulatedAmount(3000L)
                .joinedAt(LocalDateTime.of(2026, 1, 1, 10, 0)).status("QUEUEING").build();
        ShopQueuePositionDO p2 = ShopQueuePositionDO.builder()
                .userId(7L).spuId(200L).layer("A").accumulatedCount(2).accumulatedAmount(7000L)
                .joinedAt(LocalDateTime.of(2026, 1, 5, 10, 0))
                .promotedAt(LocalDateTime.of(2026, 1, 6, 10, 0)).status("QUEUEING").build();
        // mapper 仅返 QUEUEING（EXITED 已被 SQL 过滤掉，无需在此构造）
        when(queueMapper.selectListByUserIdQueueing(eq(7L))).thenReturn(Arrays.asList(p1, p2));

        ProductPromoConfigDO c100 = ProductPromoConfigDO.builder().spuId(100L).tuijianN(3).build();
        ProductPromoConfigDO c200 = ProductPromoConfigDO.builder().spuId(200L).tuijianN(4).build();
        Map<Long, ProductPromoConfigDO> configMap = new HashMap<>();
        configMap.put(100L, c100);
        configMap.put(200L, c200);
        when(productPromoConfigService.mapBySpuIds(any())).thenReturn(configMap);

        List<AppQueuePositionRespVO> result = service.listMyQueueing(7L);
        assertEquals(2, result.size());
        AppQueuePositionRespVO r1 = result.get(0);
        assertEquals(100L, r1.getSpuId());
        assertEquals("B", r1.getLayer());
        assertEquals(1, r1.getAccumulatedCount());
        assertEquals(3, r1.getMaxN());
        assertEquals(3000L, r1.getAccumulatedAmount());
        AppQueuePositionRespVO r2 = result.get(1);
        assertEquals(200L, r2.getSpuId());
        assertEquals("A", r2.getLayer());
        assertEquals(4, r2.getMaxN());
        assertNotNull(r2.getPromotedAt());
    }

    @Test
    void listMyQueueing_missingProductConfig_returnsNullMaxN() {
        ShopQueuePositionDO p = ShopQueuePositionDO.builder()
                .userId(8L).spuId(300L).layer("B").accumulatedCount(0).accumulatedAmount(0L)
                .joinedAt(LocalDateTime.now()).status("QUEUEING").build();
        when(queueMapper.selectListByUserIdQueueing(eq(8L))).thenReturn(Collections.singletonList(p));
        when(productPromoConfigService.mapBySpuIds(any())).thenReturn(Collections.emptyMap());

        List<AppQueuePositionRespVO> result = service.listMyQueueing(8L);
        assertEquals(1, result.size());
        assertNull(result.get(0).getMaxN(), "商品配置缺失时 maxN 为 null，前端兜底显示 ?");
    }

    @Test
    void event_amounts_reflect_paidAmount() {
        // 改成 200 元订单
        handle(1L, 0L, 1L);  // 1 进 B
        service.handleOrderPaid(CONFIG, 2L, SPU, 20000L, 2L);  // 2 自然买，1 拿 30% × 200 = 60

        BigDecimal expected = BigDecimal.valueOf(20000)
                .multiply(BigDecimal.valueOf(30))
                .divide(BigDecimal.valueOf(100));
        assertEquals(expected.longValue(), totalReward(1L));
    }

}
