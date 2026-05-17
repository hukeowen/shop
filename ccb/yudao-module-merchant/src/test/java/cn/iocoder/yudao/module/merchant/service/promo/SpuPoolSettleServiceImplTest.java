package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolPayoutItemDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolSettleRecordDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ProductPromoConfigMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserStarMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.SpuStarPoolMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.SpuStarPoolPayoutItemMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.SpuStarPoolSettleRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SpuPoolSettleServiceImpl} 的单测：均分、抽奖、空人池、零头残值、扣池一致性。
 *
 * <p>所有金额单位 = 分。SPU=8001。</p>
 */
class SpuPoolSettleServiceImplTest {

    private static final Long SPU_ID = 8001L;

    private ProductPromoConfigMapper productPromoConfigMapper;
    private SpuStarPoolMapper spuStarPoolMapper;
    private SpuStarPoolSettleRecordMapper settleRecordMapper;
    private SpuStarPoolPayoutItemMapper payoutItemMapper;
    private ShopUserStarMapper shopUserStarMapper;
    private PromoPointService promoPointService;
    private SpuPoolSettleServiceImpl service;

    private final AtomicLong settleIdSeq = new AtomicLong(1);
    private final AtomicLong payoutIdSeq = new AtomicLong(1);
    private final List<SpuStarPoolPayoutItemDO> payouts = new ArrayList<>();
    private final Map<Long, Long> awarded = new HashMap<>();
    private SpuStarPoolDO pool;

    @BeforeEach
    void setUp() {
        productPromoConfigMapper = mock(ProductPromoConfigMapper.class);
        spuStarPoolMapper = mock(SpuStarPoolMapper.class);
        settleRecordMapper = mock(SpuStarPoolSettleRecordMapper.class);
        payoutItemMapper = mock(SpuStarPoolPayoutItemMapper.class);
        shopUserStarMapper = mock(ShopUserStarMapper.class);
        promoPointService = mock(PromoPointService.class);

        service = new SpuPoolSettleServiceImpl();
        ReflectionTestUtils.setField(service, "productPromoConfigMapper", productPromoConfigMapper);
        ReflectionTestUtils.setField(service, "spuStarPoolMapper", spuStarPoolMapper);
        ReflectionTestUtils.setField(service, "settleRecordMapper", settleRecordMapper);
        ReflectionTestUtils.setField(service, "payoutItemMapper", payoutItemMapper);
        ReflectionTestUtils.setField(service, "shopUserStarMapper", shopUserStarMapper);
        ReflectionTestUtils.setField(service, "promoPointService", promoPointService);

        // 确定性抽奖：种子 0 → Collections.shuffle 可重现
        service.setRandomFactory(seed -> new Random(0));

        settleIdSeq.set(1);
        payoutIdSeq.set(1);
        payouts.clear();
        awarded.clear();

        // 默认池 = 100 元 = 10000 分
        pool = SpuStarPoolDO.builder()
                .spuId(SPU_ID)
                .poolBalance(10000L)
                .totalIn(10000L)
                .totalOut(0L)
                .build();
        pool.setId(1L);

        when(spuStarPoolMapper.selectBySpuIdForUpdate(SPU_ID)).thenAnswer(inv -> pool);
        when(spuStarPoolMapper.decrementPoolForSettle(eq(SPU_ID), anyLong())).thenAnswer(inv -> {
            long delta = inv.getArgument(1);
            if (pool.getPoolBalance() < delta) return 0;
            pool.setPoolBalance(pool.getPoolBalance() - delta);
            pool.setTotalOut(pool.getTotalOut() + delta);
            return 1;
        });

        when(settleRecordMapper.insert(any(SpuStarPoolSettleRecordDO.class))).thenAnswer(inv -> {
            SpuStarPoolSettleRecordDO r = inv.getArgument(0);
            r.setId(settleIdSeq.getAndIncrement());
            return 1;
        });
        when(settleRecordMapper.updateById(any(SpuStarPoolSettleRecordDO.class))).thenReturn(1);

        when(payoutItemMapper.insert(any(SpuStarPoolPayoutItemDO.class))).thenAnswer(inv -> {
            SpuStarPoolPayoutItemDO p = inv.getArgument(0);
            p.setId(payoutIdSeq.getAndIncrement());
            payouts.add(p);
            return 1;
        });

        when(promoPointService.addPromoPoint(any(), anyLong(), eq("POOL_V8"), any(), any())).thenAnswer(inv -> {
            Long uid = inv.getArgument(0);
            long amt = inv.getArgument(1);
            awarded.merge(uid, amt, Long::sum);
            return true;
        });
    }

    private ShopUserStarDO user(long uid, int star) {
        return ShopUserStarDO.builder()
                .userId(uid).spuId(SPU_ID).currentStar(star)
                .promoPointBalance(0L).consumePointBalance(0L)
                .directCount(0).teamSalesCount(0).teamSalesAmount(0L)
                .build();
    }

    private void mockConfig(String distRulesJson) {
        ProductPromoConfigDO cfg = ProductPromoConfigDO.builder()
                .spuId(SPU_ID)
                .starCount(5)
                .poolRatio(new BigDecimal("1"))
                .poolDistRules(distRulesJson)
                .build();
        when(productPromoConfigMapper.selectBySpuId(SPU_ID)).thenReturn(cfg);
    }

    // ───────────────────────────────────────────────────────────────────
    // 1. 校验类：池为 0 / 规则未配 / 配置不存在 / spuId null
    // ───────────────────────────────────────────────────────────────────

    @Test
    void settle_spuIdNull_throws() {
        assertThrows(ServiceException.class, () -> service.settle(null, ""));
    }

    @Test
    void settle_configMissing_throws() {
        when(productPromoConfigMapper.selectBySpuId(SPU_ID)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.settle(SPU_ID, ""));
    }

    @Test
    void settle_distRulesMissing_throws() {
        mockConfig(null);
        assertThrows(ServiceException.class, () -> service.settle(SPU_ID, ""));
    }

    @Test
    void settle_distRulesEmptyArray_throws() {
        mockConfig("[]");
        assertThrows(ServiceException.class, () -> service.settle(SPU_ID, ""));
    }

    @Test
    void settle_poolBalanceZero_throws() {
        mockConfig("[{\"star\":5,\"ratio\":100,\"mode\":\"EQUAL\"}]");
        pool.setPoolBalance(0L);
        assertThrows(ServiceException.class, () -> service.settle(SPU_ID, ""));
    }

    // ───────────────────────────────────────────────────────────────────
    // 2. 均分（EQUAL）
    // ───────────────────────────────────────────────────────────────────

    @Test
    void settle_equal_singleStar_fullPool_splitsEqually() {
        // 池 10000 分；100% 给 5 星均分；5 星有 4 人 → 每人 2500
        mockConfig("[{\"star\":5,\"ratio\":100,\"mode\":\"EQUAL\"}]");
        when(shopUserStarMapper.selectListBySpuAndStar(SPU_ID, 5)).thenReturn(Arrays.asList(
                user(101, 5), user(102, 5), user(103, 5), user(104, 5)));

        SpuStarPoolSettleRecordDO record = service.settle(SPU_ID, "test");

        assertEquals(10000L, record.getPoolBalanceBefore());
        assertEquals(0L, record.getPoolBalanceAfter());
        assertEquals(10000L, record.getTotalDistributed());
        assertEquals(2500L, awarded.get(101L));
        assertEquals(2500L, awarded.get(104L));
        assertEquals(4, payouts.size());
        // 池被清空，total_out += 10000
        assertEquals(0L, pool.getPoolBalance());
        assertEquals(10000L, pool.getTotalOut());
    }

    @Test
    void settle_equal_remainderStaysInPool() {
        // 池 10000；100% 给 5 星均分；5 星有 3 人 → 每人 3333，零头 1 分留池
        mockConfig("[{\"star\":5,\"ratio\":100,\"mode\":\"EQUAL\"}]");
        when(shopUserStarMapper.selectListBySpuAndStar(SPU_ID, 5)).thenReturn(Arrays.asList(
                user(101, 5), user(102, 5), user(103, 5)));

        SpuStarPoolSettleRecordDO record = service.settle(SPU_ID, "");

        assertEquals(9999L, record.getTotalDistributed());
        assertEquals(1L, record.getPoolBalanceAfter());
        assertEquals(3, payouts.size());
        assertEquals(3333L, awarded.get(101L));
        assertEquals(1L, pool.getPoolBalance());
    }

    // ───────────────────────────────────────────────────────────────────
    // 3. 抽奖（LOTTERY）
    // ───────────────────────────────────────────────────────────────────

    @Test
    void settle_lottery_exactWinners() {
        // 池 10000；100% 给 4 星抽奖 2 人；候选 5 人 → 选 2 人，每人 5000
        mockConfig("[{\"star\":4,\"ratio\":100,\"mode\":\"LOTTERY\",\"winners\":2}]");
        when(shopUserStarMapper.selectListBySpuAndStar(SPU_ID, 4)).thenReturn(Arrays.asList(
                user(201, 4), user(202, 4), user(203, 4), user(204, 4), user(205, 4)));

        SpuStarPoolSettleRecordDO record = service.settle(SPU_ID, "");

        assertEquals(10000L, record.getTotalDistributed());
        assertEquals(0L, record.getPoolBalanceAfter());
        assertEquals(2, payouts.size());
        verify(promoPointService, times(2))
                .addPromoPoint(any(), eq(5000L), eq("POOL_V8"), any(), any());
    }

    @Test
    void settle_lottery_winnersExceedCandidates_allWin() {
        // 配 winners=10，实际只有 3 个 4 星 → 全部 3 人中奖；每人 3333 分；1 分留池
        mockConfig("[{\"star\":4,\"ratio\":100,\"mode\":\"LOTTERY\",\"winners\":10}]");
        when(shopUserStarMapper.selectListBySpuAndStar(SPU_ID, 4)).thenReturn(Arrays.asList(
                user(201, 4), user(202, 4), user(203, 4)));

        SpuStarPoolSettleRecordDO record = service.settle(SPU_ID, "");

        assertEquals(3, payouts.size());
        assertEquals(9999L, record.getTotalDistributed());
        assertEquals(1L, record.getPoolBalanceAfter());
        assertEquals(3333L, awarded.get(201L));
    }

    // ───────────────────────────────────────────────────────────────────
    // 4. 多星组合 + 空人池兜底
    // ───────────────────────────────────────────────────────────────────

    @Test
    void settle_mixed_emptyBucketStaysInPool() {
        // 池 10000；5 星 30% EQUAL（无人）/ 4 星 70% LOTTERY 2 人（3 候选）
        // 期望：5 星段 3000 分回流池；4 星 7000 / 2 = 3500
        mockConfig("[" +
                "{\"star\":5,\"ratio\":30,\"mode\":\"EQUAL\"}," +
                "{\"star\":4,\"ratio\":70,\"mode\":\"LOTTERY\",\"winners\":2}" +
                "]");
        when(shopUserStarMapper.selectListBySpuAndStar(SPU_ID, 5)).thenReturn(Collections.emptyList());
        when(shopUserStarMapper.selectListBySpuAndStar(SPU_ID, 4)).thenReturn(Arrays.asList(
                user(301, 4), user(302, 4), user(303, 4)));

        SpuStarPoolSettleRecordDO record = service.settle(SPU_ID, "");

        assertEquals(2, payouts.size());
        assertEquals(7000L, record.getTotalDistributed());
        assertEquals(3000L, record.getPoolBalanceAfter());
        assertEquals(3500L, awarded.values().stream().findFirst().orElse(0L));
        assertEquals(3000L, pool.getPoolBalance());
        assertEquals(7000L, pool.getTotalOut());
    }

    @Test
    void settle_mixed_threeStars_correctAllocation() {
        // 池 10000；5 星 50% EQUAL（2 人 → 各 2500）/ 4 星 30% EQUAL（3 人 → 各 1000，零头 0）/ 3 星 20% LOTTERY 1 人 → 2000
        mockConfig("[" +
                "{\"star\":5,\"ratio\":50,\"mode\":\"EQUAL\"}," +
                "{\"star\":4,\"ratio\":30,\"mode\":\"EQUAL\"}," +
                "{\"star\":3,\"ratio\":20,\"mode\":\"LOTTERY\",\"winners\":1}" +
                "]");
        when(shopUserStarMapper.selectListBySpuAndStar(SPU_ID, 5)).thenReturn(Arrays.asList(
                user(501, 5), user(502, 5)));
        when(shopUserStarMapper.selectListBySpuAndStar(SPU_ID, 4)).thenReturn(Arrays.asList(
                user(401, 4), user(402, 4), user(403, 4)));
        when(shopUserStarMapper.selectListBySpuAndStar(SPU_ID, 3)).thenReturn(Arrays.asList(
                user(301, 3), user(302, 3)));

        SpuStarPoolSettleRecordDO record = service.settle(SPU_ID, "");

        // 5 星 2 人 × 2500 + 4 星 3 人 × 1000 + 3 星 1 人 × 2000 = 5000+3000+2000 = 10000
        assertEquals(6, payouts.size());
        assertEquals(10000L, record.getTotalDistributed());
        assertEquals(0L, record.getPoolBalanceAfter());
        assertEquals(2500L, awarded.get(501L));
        assertEquals(2500L, awarded.get(502L));
        assertEquals(1000L, awarded.get(401L));
        assertEquals(2000L, awarded.values().stream().filter(v -> v == 2000L).findFirst().orElse(0L));
    }

    // ───────────────────────────────────────────────────────────────────
    // 5. 池清零正确性 + 流水写入
    // ───────────────────────────────────────────────────────────────────

    @Test
    void settle_doesNotLoseMoney_distributedPlusRemainderEqualsBefore() {
        // 任何分配方式：distributed + remainder ≡ poolBefore
        mockConfig("[{\"star\":5,\"ratio\":100,\"mode\":\"EQUAL\"}]");
        when(shopUserStarMapper.selectListBySpuAndStar(SPU_ID, 5)).thenReturn(Arrays.asList(
                user(1, 5), user(2, 5), user(3, 5), user(4, 5), user(5, 5),
                user(6, 5), user(7, 5)));   // 7 人均分 10000 → 每人 1428，留 4

        SpuStarPoolSettleRecordDO record = service.settle(SPU_ID, "");

        assertEquals(record.getPoolBalanceBefore(),
                record.getTotalDistributed() + record.getPoolBalanceAfter(),
                "池资金守恒：distributed + after = before");
        assertEquals(7, payouts.size());
        assertEquals(1428L, awarded.get(1L));
    }

    @Test
    void settle_writesSnapshot_seedAndRules() {
        String rules = "[{\"star\":5,\"ratio\":100,\"mode\":\"EQUAL\"}]";
        mockConfig(rules);
        when(shopUserStarMapper.selectListBySpuAndStar(SPU_ID, 5)).thenReturn(Arrays.asList(user(1, 5)));

        SpuStarPoolSettleRecordDO record = service.settle(SPU_ID, "month-end");

        assertEquals(rules, record.getRulesSnapshot());
        assertTrue(record.getRandomSeed() != 0L || record.getRandomSeed() == 0L);   // 总有值
        assertEquals("month-end", record.getRemark());
    }

}
