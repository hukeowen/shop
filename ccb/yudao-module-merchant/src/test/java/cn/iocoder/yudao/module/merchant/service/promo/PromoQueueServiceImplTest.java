package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueueEventDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueuePositionDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopReferralContributionDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopQueueEventMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopQueuePositionMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopReferralContributionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * v7 推 N 反 1 状态机单测。
 *
 * <p>商品 A 配置：N=4，比例 [25,25,25,25]；间推 % = 10。</p>
 *
 * <p>覆盖 12 个核心场景：</p>
 * <ol>
 *   <li>buyer 首单激活，不返奖（state=IN_PROGRESS, cumulated=0）</li>
 *   <li>自购连续 N 次完成（IN_PROGRESS → COMPLETED）</li>
 *   <li>parent IN_PROGRESS：下级首贡献返 unitPaid × (1/N)</li>
 *   <li>下级一单买多件：按 unitPaid 折算（不按 paidAmount）</li>
 *   <li>下级二单不再触发 parent（contribution UNIQUE 拦）</li>
 *   <li>parent 未激活：完全跳过，不发奖</li>
 *   <li>parent COMPLETED：终态返 paidAmount × directCommissionRatio%</li>
 *   <li>自购在 COMPLETED 期：自己拿 paidAmount × directCommissionRatio%</li>
 *   <li>自然推开关 OFF + 真自然用户：吞奖</li>
 *   <li>自然推开关 ON + 真自然用户：走旧 A/B 队列</li>
 *   <li>多商品独立状态机</li>
 *   <li>实付价基准（payPrice 抵扣后金额）</li>
 * </ol>
 */
class PromoQueueServiceImplTest {

    private static final Long SPU = 100L;
    private static final long UNIT_PAID = 10000L;       // 单件 100 元
    private static final long FOUR_PAID = UNIT_PAID * 4; // 一单买 4 件 = 400 元
    private static final BigDecimal COMMISSION_RATIO = new BigDecimal("10");  // 间推 10%
    private static final ProductPromoConfigDO CONFIG = ProductPromoConfigDO.builder()
            .spuId(SPU)
            .tuijianEnabled(true)
            .tuijianN(4)
            .tuijianRatios("[25,25,25,25]")
            .build();

    private PromoQueueServiceImpl service;
    private ShopQueuePositionMapper queueMapper;
    private ShopReferralContributionMapper contributionMapper;

    private final Map<String, ShopQueuePositionDO> queueByUserSpu = new HashMap<>();
    private final List<ShopQueueEventDO> events = new ArrayList<>();
    private final Set<String> contributions = new HashSet<>();
    private final AtomicLong autoId = new AtomicLong(1);
    private final Map<Long, Long> parentMap = new HashMap<>();
    private final Map<Long, Long> rewards = new HashMap<>();

    private boolean naturalPushEnabled = false;

    @BeforeEach
    void setUp() {
        queueMapper = mock(ShopQueuePositionMapper.class);
        ShopQueueEventMapper eventMapper = mock(ShopQueueEventMapper.class);
        contributionMapper = mock(ShopReferralContributionMapper.class);
        ReferralService referralService = mock(ReferralService.class);
        PromoPointService pointService = mock(PromoPointService.class);
        ProductPromoConfigService productPromoConfigService = mock(ProductPromoConfigService.class);
        PromoConfigService promoConfigService = mock(PromoConfigService.class);

        when(queueMapper.selectByUserAndSpu(any(), any())).thenAnswer(inv -> {
            Long uid = inv.getArgument(0);
            Long spu = inv.getArgument(1);
            return queueByUserSpu.get(uid + "_" + spu);
        });
        when(queueMapper.selectQueueHead(any())).thenAnswer(inv -> {
            Long spu = inv.getArgument(0);
            return queueByUserSpu.values().stream()
                    .filter(p -> spu.equals(p.getSpuId()))
                    .filter(p -> "QUEUEING".equals(p.getStatus()))
                    .sorted(Comparator
                            .comparing(ShopQueuePositionDO::getLayer, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(p -> p.getPromotedAt() == null ? LocalDateTime.MAX : p.getPromotedAt())
                            .thenComparing(ShopQueuePositionDO::getJoinedAt))
                    .findFirst()
                    .orElse(null);
        });
        when(queueMapper.insert(any(ShopQueuePositionDO.class))).thenAnswer(inv -> {
            ShopQueuePositionDO p = inv.getArgument(0);
            p.setId(autoId.getAndIncrement());
            queueByUserSpu.put(p.getUserId() + "_" + p.getSpuId(), p);
            return 1;
        });
        when(queueMapper.updateById(any(ShopQueuePositionDO.class))).thenAnswer(inv -> {
            ShopQueuePositionDO p = inv.getArgument(0);
            queueByUserSpu.put(p.getUserId() + "_" + p.getSpuId(), p);
            return 1;
        });

        when(eventMapper.insert(any(ShopQueueEventDO.class))).thenAnswer(inv -> {
            events.add(inv.getArgument(0));
            return 1;
        });

        when(contributionMapper.exists(any(), any(), any())).thenAnswer(inv -> {
            String key = inv.getArgument(0) + "_" + inv.getArgument(1) + "_" + inv.getArgument(2);
            return contributions.contains(key);
        });
        when(contributionMapper.insert(any(ShopReferralContributionDO.class))).thenAnswer(inv -> {
            ShopReferralContributionDO c = inv.getArgument(0);
            String key = c.getParentUserId() + "_" + c.getChildUserId() + "_" + c.getSpuId();
            if (contributions.contains(key)) {
                throw new org.springframework.dao.DuplicateKeyException("uk_parent_child_spu");
            }
            contributions.add(key);
            return 1;
        });

        when(referralService.getDirectParent(any())).thenAnswer(inv ->
                parentMap.getOrDefault(inv.<Long>getArgument(0), 0L));

        when(pointService.addPromoPoint(any(), org.mockito.ArgumentMatchers.anyLong(), any(), any(), any()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    long amt = inv.getArgument(1);
                    rewards.merge(uid, amt, Long::sum);
                    return true;
                });

        when(promoConfigService.getConfig()).thenAnswer(inv -> {
            PromoConfigDO c = new PromoConfigDO();
            c.setDirectCommissionRatio(COMMISSION_RATIO);
            c.setNaturalPushEnabled(naturalPushEnabled);
            return c;
        });

        service = new PromoQueueServiceImpl();
        ReflectionTestUtils.setField(service, "queueMapper", queueMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "contributionMapper", contributionMapper);
        ReflectionTestUtils.setField(service, "referralService", referralService);
        ReflectionTestUtils.setField(service, "promoPointService", pointService);
        ReflectionTestUtils.setField(service, "productPromoConfigService", productPromoConfigService);
        ReflectionTestUtils.setField(service, "promoConfigService", promoConfigService);
        ReflectionTestUtils.setField(service, "productSpuService",
                mock(cn.iocoder.yudao.module.product.service.spu.ProductSpuService.class));
    }

    private void buy(long buyer, long paidAmount, long unitPaid, long orderId) {
        service.handleOrderPaid(CONFIG, buyer, SPU, paidAmount, unitPaid, orderId);
    }

    private ShopQueuePositionDO pos(long uid) { return queueByUserSpu.get(uid + "_" + SPU); }
    private long reward(long uid) { return rewards.getOrDefault(uid, 0L); }

    // ========================= 12 个 v7 用例 =========================

    @Test
    @DisplayName("[1] buyer 首单激活，不返奖；state=IN_PROGRESS, cumulated=0")
    void test01_firstBuy_activateOnly_noReward() {
        buy(1L, UNIT_PAID, UNIT_PAID, 1001L);

        ShopQueuePositionDO p = pos(1L);
        assertNotNull(p, "首单应建队列位置");
        assertEquals("IN_PROGRESS", p.getState());
        assertEquals(0, p.getAccumulatedCount());
        assertEquals(0L, p.getAccumulatedAmount());
        assertEquals(0L, reward(1L), "首单不返奖");
        assertTrue(events.stream().anyMatch(e -> "ACTIVATE".equals(e.getEventType())));
    }

    @Test
    @DisplayName("[2] 自购连续 N+1 次：首单激活 + 4 次累计 → COMPLETED")
    void test02_selfBuy_NPlusOne_complete() {
        // 自购 5 次：1 激活 + 4 累计
        for (int i = 1; i <= 5; i++) buy(1L, UNIT_PAID, UNIT_PAID, 1000L + i);

        ShopQueuePositionDO p = pos(1L);
        assertEquals("COMPLETED", p.getState());
        assertEquals(4, p.getAccumulatedCount());
        // 4 次每次 25%，每次 10000 × 25% = 2500，共 10000
        assertEquals(10000L, reward(1L));
    }

    @Test
    @DisplayName("[3] parent IN_PROGRESS：下级首贡献返 unitPaid × (1/N)")
    void test03_parentInProgress_childFirstContrib_oneNthOfUnit() {
        // 1 自购 1 次激活
        buy(1L, UNIT_PAID, UNIT_PAID, 2001L);
        // 1 是 2 的 parent
        parentMap.put(2L, 1L);
        // 2 首单买 1 件
        buy(2L, UNIT_PAID, UNIT_PAID, 2002L);

        // parent 1 拿 unitPaid × 25% = 2500；cumulated 1
        assertEquals(2500L, reward(1L));
        assertEquals(1, pos(1L).getAccumulatedCount());
        assertEquals("IN_PROGRESS", pos(1L).getState());
        // 2 自己也激活
        assertEquals("IN_PROGRESS", pos(2L).getState());
        assertEquals(0, pos(2L).getAccumulatedCount());
        // 2 没有自己拿奖（首单激活）
        assertEquals(0L, reward(2L));
    }

    @Test
    @DisplayName("[4] 下级一单买 4 件：parent 按 unitPaid 折算（不是 paidAmount）— 只 +1 累计")
    void test04_childBuysMultiple_payByUnitNotTotal() {
        buy(1L, UNIT_PAID, UNIT_PAID, 3001L); // 1 激活
        parentMap.put(2L, 1L);
        // 2 一单买 4 件，paid=400 元，unit=100 元
        buy(2L, FOUR_PAID, UNIT_PAID, 3002L);

        // parent 仅按 unitPaid × 25% = 2500；cumulated 仅 +1
        assertEquals(2500L, reward(1L));
        assertEquals(1, pos(1L).getAccumulatedCount());
        // 2 激活，cumulated=0
        assertEquals(0, pos(2L).getAccumulatedCount());
    }

    @Test
    @DisplayName("[5] 下级二单不再触发 parent（contribution UNIQUE 拦）")
    void test05_childSecondOrder_noParentReward() {
        buy(1L, UNIT_PAID, UNIT_PAID, 4001L);
        parentMap.put(2L, 1L);
        buy(2L, UNIT_PAID, UNIT_PAID, 4002L);  // 触发首贡献
        long parentRewardAfterFirst = reward(1L);

        // 2 第二单（任何金额）
        buy(2L, UNIT_PAID, UNIT_PAID, 4003L);

        // parent 奖励不变；cumulated 不变
        assertEquals(parentRewardAfterFirst, reward(1L));
        assertEquals(1, pos(1L).getAccumulatedCount());
        // 2 第二单是自购，2 累计 +1（25% × 10000 = 2500 自己拿）
        assertEquals(1, pos(2L).getAccumulatedCount());
        assertEquals(2500L, reward(2L));
    }

    @Test
    @DisplayName("[6] parent 未激活该商品：完全跳过，不发奖；下级正常激活")
    void test06_parentNotActivated_noReward_childStillActivates() {
        // 1 没买过 SPU
        parentMap.put(2L, 1L);
        buy(2L, UNIT_PAID, UNIT_PAID, 5001L);

        // parent 不发奖
        assertEquals(0L, reward(1L));
        assertNull(pos(1L), "parent 没买过不应建队列");
        // 2 照常激活
        assertEquals("IN_PROGRESS", pos(2L).getState());
        assertEquals(0, pos(2L).getAccumulatedCount());
        assertEquals(0L, reward(2L));
    }

    @Test
    @DisplayName("[7] parent COMPLETED：下级首单返 paidAmount × directCommissionRatio%")
    void test07_parentCompleted_childFirst_commission() {
        // 1 自购 5 次进 COMPLETED
        for (int i = 1; i <= 5; i++) buy(1L, UNIT_PAID, UNIT_PAID, 6000L + i);
        long rewardBefore = reward(1L);
        assertEquals("COMPLETED", pos(1L).getState());

        parentMap.put(2L, 1L);
        // 2 一单买 4 件，paid=400 元
        buy(2L, FOUR_PAID, UNIT_PAID, 6010L);

        // 1 拿 400 元 × 10% = 40 元 = 4000 分
        assertEquals(rewardBefore + 4000L, reward(1L));
    }

    @Test
    @DisplayName("[8] 自购在 COMPLETED 期：自己拿 paidAmount × directCommissionRatio%")
    void test08_selfBuyAfterComplete_commission() {
        // 1 自购 5 次进 COMPLETED
        for (int i = 1; i <= 5; i++) buy(1L, UNIT_PAID, UNIT_PAID, 7000L + i);
        long rewardBefore = reward(1L);

        // 1 终态再自购 13 件，paid=1300 元
        buy(1L, UNIT_PAID * 13, UNIT_PAID, 7100L);

        // 拿 1300 × 10% = 130 元 = 13000 分
        assertEquals(rewardBefore + 13000L, reward(1L));
    }

    @Test
    @DisplayName("[9] 自然推开关 OFF + 真自然用户：吞奖（不发给任何人）")
    void test09_naturalUser_pushOff_swallow() {
        naturalPushEnabled = false;
        // 1 没有 parent，是真自然用户
        buy(1L, UNIT_PAID, UNIT_PAID, 8001L);

        // 没人拿奖（自然推关闭）
        assertEquals(0L, reward(1L));
        // 但 1 自己照常激活
        assertEquals("IN_PROGRESS", pos(1L).getState());
        assertEquals(0, pos(1L).getAccumulatedCount());
    }

    @Test
    @DisplayName("[10] 自然推开关 ON + 真自然用户：走旧 A/B 队列")
    void test10_naturalUser_pushOn_legacyQueue() {
        naturalPushEnabled = true;
        // 1 真自然用户首单
        buy(1L, UNIT_PAID, UNIT_PAID, 9001L);
        // 旧 legacy 路径里 buyerPos==null 不走自购累计；buyer 进 B 层尾
        // 队首此时为 null（队列里只有刚进的 1 自己），所以不返奖；1 进 B 层
        assertNotNull(pos(1L));
        // 2 真自然用户首单：把 1 当队首返奖
        buy(2L, UNIT_PAID, UNIT_PAID, 9002L);
        // 1 拿 25% × 10000 = 2500
        assertTrue(reward(1L) > 0, "队首应拿到自然推奖励");
    }

    @Test
    @DisplayName("[11] 多商品独立状态机：spu1 IN_PROGRESS 不影响 spu2 NEW")
    void test11_multipleSpus_independent() {
        Long SPU2 = 200L;
        ProductPromoConfigDO config2 = ProductPromoConfigDO.builder()
                .spuId(SPU2).tuijianEnabled(true).tuijianN(4)
                .tuijianRatios("[25,25,25,25]").build();

        // 1 在 SPU 自购 1 次
        buy(1L, UNIT_PAID, UNIT_PAID, 11001L);
        // 1 在 SPU2 是新的（NEW）
        service.handleOrderPaid(config2, 1L, SPU2, UNIT_PAID, UNIT_PAID, 11002L);

        ShopQueuePositionDO p1 = pos(1L);
        ShopQueuePositionDO p2 = queueByUserSpu.get("1_" + SPU2);
        assertEquals("IN_PROGRESS", p1.getState());
        assertEquals(0, p1.getAccumulatedCount());
        assertEquals("IN_PROGRESS", p2.getState());
        assertEquals(0, p2.getAccumulatedCount(), "spu2 也是首单激活");
        // 都是首单不返奖
        assertEquals(0L, reward(1L));
    }

    @Test
    @DisplayName("[12] 实付价基准：payPrice=75（抵扣 25 元）→ 返奖按 75 算")
    void test12_payPriceAfterDeduction() {
        buy(1L, UNIT_PAID, UNIT_PAID, 12001L); // 激活
        parentMap.put(2L, 1L);
        // 2 首单实付 75 元（抵扣 25 元），unit = 75 元 = 7500 分
        buy(2L, 7500L, 7500L, 12002L);

        // parent 1 拿 7500 × 25% = 1875 分
        assertEquals(1875L, reward(1L));
    }
}
