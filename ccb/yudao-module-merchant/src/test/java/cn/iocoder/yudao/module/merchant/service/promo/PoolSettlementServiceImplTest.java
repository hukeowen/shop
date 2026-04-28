package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoPoolDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoPoolRoundDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoPoolMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoPoolRoundMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserStarMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link PoolSettlementServiceImpl} 4 种模式 × 边界用例。
 *
 * 池余额 100000 分 = 1000 元；3 个 1 星 + 2 个 3 星用户。
 */
class PoolSettlementServiceImplTest {

    private PromoConfigService promoConfigService;
    private ShopPromoPoolMapper poolMapper;
    private ShopPromoPoolRoundMapper roundMapper;
    private ShopUserStarMapper userStarMapper;
    private PromoPointService promoPointService;
    private PoolSettlementServiceImpl service;

    private final AtomicLong roundIdSeq = new AtomicLong(1);
    private final List<ShopPromoPoolRoundDO> rounds = new ArrayList<>();
    private final Map<Long, Long> awarded = new HashMap<>();
    private ShopPromoPoolDO pool;

    @BeforeEach
    void setUp() {
        promoConfigService = mock(PromoConfigService.class);
        poolMapper = mock(ShopPromoPoolMapper.class);
        roundMapper = mock(ShopPromoPoolRoundMapper.class);
        userStarMapper = mock(ShopUserStarMapper.class);
        promoPointService = mock(PromoPointService.class);

        service = new PoolSettlementServiceImpl();
        ReflectionTestUtils.setField(service, "promoConfigService", promoConfigService);
        ReflectionTestUtils.setField(service, "poolMapper", poolMapper);
        ReflectionTestUtils.setField(service, "roundMapper", roundMapper);
        ReflectionTestUtils.setField(service, "userStarMapper", userStarMapper);
        ReflectionTestUtils.setField(service, "promoPointService", promoPointService);

        // 抽奖确定性：种子 0 让 shuffle 可预测，但测试只断言"刚好抽 N 个"，不依赖具体哪些
        service.setRandom(new Random(0));

        rounds.clear();
        awarded.clear();
        roundIdSeq.set(1);

        pool = ShopPromoPoolDO.builder().balance(100000L).build();
        pool.setId(1L);
        when(poolMapper.selectCurrent()).thenReturn(pool);
        when(poolMapper.updateById(any(ShopPromoPoolDO.class))).thenAnswer(inv -> {
            pool = inv.getArgument(0);
            return 1;
        });
        // 条件清池：仅当余额 = expectedBalance 才清零
        when(poolMapper.clearIfBalanceEquals(any(), anyLong())).thenAnswer(inv -> {
            long expected = inv.getArgument(1);
            if (pool.getBalance() != expected) return 0;
            pool.setBalance(0L);
            return 1;
        });
        when(roundMapper.insert(any(ShopPromoPoolRoundDO.class))).thenAnswer(inv -> {
            ShopPromoPoolRoundDO r = inv.getArgument(0);
            r.setId(roundIdSeq.getAndIncrement());
            rounds.add(r);
            return 1;
        });
        when(roundMapper.updateById(any(ShopPromoPoolRoundDO.class))).thenReturn(1);
        when(promoPointService.addPromoPoint(any(), anyLong(), eq("POOL"), any(), any())).thenAnswer(inv -> {
            Long uid = inv.getArgument(0);
            long amt = inv.getArgument(1);
            awarded.merge(uid, amt, Long::sum);
            return true;
        });
    }

    private List<ShopUserStarDO> defaultParticipants() {
        return Arrays.asList(
                user(1L, 1), user(2L, 1), user(3L, 1),
                user(4L, 3), user(5L, 3));
    }

    private ShopUserStarDO user(Long id, int star) {
        return ShopUserStarDO.builder().userId(id).currentStar(star).build();
    }

    private PromoConfigDO config(String distributeMode, BigDecimal lotteryRatio, String eligibleStars) {
        return PromoConfigDO.builder()
                .poolEnabled(true)
                .poolEligibleStars(eligibleStars)
                .poolDistributeMode(distributeMode)
                .poolLotteryRatio(lotteryRatio)
                .build();
    }

    @Test
    void full_all_evenSplit_amongAllParticipants() {
        when(promoConfigService.getConfig()).thenReturn(config("ALL", BigDecimal.ZERO, "[1,2,3,4,5]"));
        when(userStarMapper.selectListByCurrentStarIn(any())).thenReturn(defaultParticipants());

        ShopPromoPoolRoundDO round = service.settleNow("FULL");

        // 100000 / 5 = 20000 每人
        assertNotNull(round);
        assertEquals(5, round.getParticipantCount());
        assertEquals(5, round.getWinnerCount());
        assertEquals(0L, pool.getBalance(), "池清零");
        for (long uid : new long[]{1L, 2L, 3L, 4L, 5L}) {
            assertEquals(20000L, awarded.getOrDefault(uid, 0L), "user " + uid);
        }
    }

    @Test
    void full_star_bucketSplit_byStar() {
        when(promoConfigService.getConfig()).thenReturn(config("STAR", BigDecimal.ZERO, "[1,2,3,4,5]"));
        when(userStarMapper.selectListByCurrentStarIn(any())).thenReturn(defaultParticipants());

        service.settleNow("FULL");

        // 池 100000 分 / 2 桶（1星 + 3星）= 50000/桶
        // 1 星 3 人 → 50000/3 = 16666 每人
        // 3 星 2 人 → 50000/2 = 25000 每人
        assertEquals(16666L, awarded.getOrDefault(1L, 0L));
        assertEquals(16666L, awarded.getOrDefault(2L, 0L));
        assertEquals(16666L, awarded.getOrDefault(3L, 0L));
        assertEquals(25000L, awarded.getOrDefault(4L, 0L));
        assertEquals(25000L, awarded.getOrDefault(5L, 0L));
    }

    @Test
    void lottery_all_picksFractionAndEvenSplits() {
        // lotteryRatio = 40% → 5 × 40% = 2 人中奖
        when(promoConfigService.getConfig()).thenReturn(config("ALL",
                new BigDecimal("40.00"), "[1,2,3,4,5]"));
        when(userStarMapper.selectListByCurrentStarIn(any())).thenReturn(defaultParticipants());

        ShopPromoPoolRoundDO round = service.settleNow("LOTTERY");

        assertEquals(5, round.getParticipantCount());
        assertEquals(2, round.getWinnerCount());
        // 池 100000 / 2 = 50000 每个中奖人
        long totalAwarded = awarded.values().stream().mapToLong(Long::longValue).sum();
        assertEquals(100000L, totalAwarded);
        assertEquals(2, awarded.size(), "应只有 2 个中奖人");
        for (Long amt : awarded.values()) {
            assertEquals(50000L, amt);
        }
    }

    @Test
    void lottery_winnerCount_zero_isNoop() {
        when(promoConfigService.getConfig()).thenReturn(config("ALL",
                new BigDecimal("10.00"), "[1,2,3,4,5]"));
        // 5 人 × 10% = 0.5 → 向下取整 = 0
        when(userStarMapper.selectListByCurrentStarIn(any())).thenReturn(defaultParticipants());

        assertNull(service.settleNow("LOTTERY"));
        assertTrue(awarded.isEmpty());
        assertEquals(100000L, pool.getBalance(), "池子未结算");
    }

    @Test
    void emptyPool_isNoop() {
        pool.setBalance(0L);
        when(promoConfigService.getConfig()).thenReturn(config("ALL", BigDecimal.ZERO, "[1,2,3,4,5]"));

        assertNull(service.settleNow("FULL"));
        assertTrue(awarded.isEmpty());
        verify(roundMapper, never()).insert(any(ShopPromoPoolRoundDO.class));
    }

    @Test
    void noEligibleStars_isNoop() {
        when(promoConfigService.getConfig()).thenReturn(config("ALL", BigDecimal.ZERO, "[]"));

        assertNull(service.settleNow("FULL"));
        assertTrue(awarded.isEmpty());
    }

    @Test
    void noParticipants_isNoop() {
        when(promoConfigService.getConfig()).thenReturn(config("ALL", BigDecimal.ZERO, "[1,2,3]"));
        when(userStarMapper.selectListByCurrentStarIn(any())).thenReturn(Collections.emptyList());

        assertNull(service.settleNow("FULL"));
        assertTrue(awarded.isEmpty());
    }

    @Test
    void invalidMode_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.settleNow("RANDOM"));
        assertThrows(IllegalArgumentException.class, () -> service.settleNow(null));
    }

    @Test
    void poolDisabled_isNoop() {
        when(promoConfigService.getConfig()).thenReturn(PromoConfigDO.builder().poolEnabled(false).build());
        assertNull(service.settleNow("FULL"));
    }

}
