package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserStarMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link StarServiceImpl} 测试（v6 修复后版本）。
 *
 * 关键变化：
 *   - direct_count / team_sales_count 走 atomic addX(userId, delta)
 *   - 升星走 upgradeStarIfHigher(userId, newStar) 条件 UPDATE，只升不降
 */
class StarServiceImplTest {

    private static final String DEFAULT_RULES =
            "[{\"directCount\":2,\"teamSales\":3},"
                    + "{\"directCount\":3,\"teamSales\":9},"
                    + "{\"directCount\":5,\"teamSales\":27},"
                    + "{\"directCount\":8,\"teamSales\":81},"
                    + "{\"directCount\":12,\"teamSales\":243}]";

    private ShopUserStarMapper userStarMapper;
    private PromoConfigService promoConfigService;
    private ReferralService referralService;
    private StarServiceImpl service;

    private final Map<Long, ShopUserStarDO> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        userStarMapper = mock(ShopUserStarMapper.class);
        promoConfigService = mock(PromoConfigService.class);
        referralService = mock(ReferralService.class);

        service = new StarServiceImpl();
        ReflectionTestUtils.setField(service, "userStarMapper", userStarMapper);
        ReflectionTestUtils.setField(service, "promoConfigService", promoConfigService);
        ReflectionTestUtils.setField(service, "referralService", referralService);

        store.clear();
        when(userStarMapper.selectByUserId(any())).thenAnswer(inv -> store.get(inv.<Long>getArgument(0)));
        when(userStarMapper.insert(any(ShopUserStarDO.class))).thenAnswer(inv -> {
            ShopUserStarDO s = inv.getArgument(0);
            store.put(s.getUserId(), s);
            return 1;
        });
        // 原子累加：根据当前状态 + delta 更新内存态
        when(userStarMapper.addDirectCount(any(), anyInt())).thenAnswer(inv -> {
            Long uid = inv.getArgument(0);
            int delta = inv.getArgument(1);
            ShopUserStarDO s = store.get(uid);
            if (s == null) return 0;
            s.setDirectCount(s.getDirectCount() + delta);
            return 1;
        });
        when(userStarMapper.addTeamSalesCount(any(), anyInt())).thenAnswer(inv -> {
            Long uid = inv.getArgument(0);
            int delta = inv.getArgument(1);
            ShopUserStarDO s = store.get(uid);
            if (s == null) return 0;
            s.setTeamSalesCount(s.getTeamSalesCount() + delta);
            return 1;
        });
        // 条件升星：仅当 newStar > currentStar 才生效
        when(userStarMapper.upgradeStarIfHigher(any(), anyInt())).thenAnswer(inv -> {
            Long uid = inv.getArgument(0);
            int newStar = inv.getArgument(1);
            ShopUserStarDO s = store.get(uid);
            if (s == null) return 0;
            int cur = s.getCurrentStar() == null ? 0 : s.getCurrentStar();
            if (newStar > cur) {
                s.setCurrentStar(newStar);
                return 1;
            }
            return 0;
        });

        when(promoConfigService.getConfig()).thenReturn(PromoConfigDO.builder()
                .starUpgradeRules(DEFAULT_RULES).build());
    }

    @Test
    void handleOrderPaid_bumpsBuyerAndAllAncestors() {
        when(referralService.getAncestors(4L, 50)).thenReturn(Arrays.asList(3L, 2L, 1L));

        service.handleOrderPaid(4L, 1, true);

        assertEquals(1, store.get(4L).getTeamSalesCount());
        assertEquals(1, store.get(3L).getTeamSalesCount());
        assertEquals(1, store.get(2L).getTeamSalesCount());
        assertEquals(1, store.get(1L).getTeamSalesCount());
    }

    @Test
    void handleOrderPaid_skipsWhenNotCountable() {
        when(referralService.getAncestors(any(), anyInt())).thenReturn(Collections.emptyList());
        service.handleOrderPaid(4L, 1, false);
        assertTrue(store.isEmpty(), "不参与推 N 反 1 商品不计入");
    }

    @Test
    void handleOrderPaid_zeroQty_isNoop() {
        service.handleOrderPaid(4L, 0, true);
        assertTrue(store.isEmpty());
    }

    @Test
    void handleReferralBound_incrementsDirect_andUpgradesIfEligible() {
        store.put(1L, ShopUserStarDO.builder()
                .userId(1L).directCount(2).teamSalesCount(10).currentStar(1)
                .promoPointBalance(0L).consumePointBalance(0L).build());

        // bind 后 directCount = 3，满足 2星条件 (3, 9)
        service.handleReferralBound(1L);

        ShopUserStarDO acct = store.get(1L);
        assertEquals(3, acct.getDirectCount());
        assertEquals(2, acct.getCurrentStar(), "directCount=3 + teamSales=10 → 升 2 星");
    }

    @Test
    void recompute_upgradesMultipleLevelsAtOnce() {
        store.put(1L, ShopUserStarDO.builder()
                .userId(1L).directCount(5).teamSalesCount(27).currentStar(0)
                .promoPointBalance(0L).consumePointBalance(0L).build());

        int newStar = service.recompute(1L);

        assertEquals(3, newStar);
        assertEquals(3, store.get(1L).getCurrentStar());
    }

    @Test
    void recompute_doesNotDowngrade() {
        // 用户已是 5 星，但数据被人工调小（不应降级）— 因为 upgradeStarIfHigher 条件 UPDATE
        store.put(1L, ShopUserStarDO.builder()
                .userId(1L).directCount(0).teamSalesCount(0).currentStar(5)
                .promoPointBalance(0L).consumePointBalance(0L).build());

        int newStar = service.recompute(1L);

        assertEquals(5, newStar, "终生制：不因数据回退而降级");
        verify(userStarMapper, never()).upgradeStarIfHigher(eq(1L), anyInt());
    }

    @Test
    void recompute_capsAtMaxStar() {
        store.put(1L, ShopUserStarDO.builder()
                .userId(1L).directCount(999).teamSalesCount(999).currentStar(0)
                .promoPointBalance(0L).consumePointBalance(0L).build());

        int newStar = service.recompute(1L);

        assertEquals(5, newStar);
    }

    @Test
    void recompute_partialUpgrade() {
        store.put(1L, ShopUserStarDO.builder()
                .userId(1L).directCount(3).teamSalesCount(9).currentStar(0)
                .promoPointBalance(0L).consumePointBalance(0L).build());

        assertEquals(2, service.recompute(1L));
    }

    @Test
    void getOrCreate_createsBlank_whenAccountAbsent() {
        when(referralService.getAncestors(any(), anyInt())).thenReturn(Collections.emptyList());
        service.handleOrderPaid(99L, 1, true);

        ShopUserStarDO created = store.get(99L);
        assertNotNull(created);
        assertEquals(0, created.getDirectCount());
        assertEquals(1, created.getTeamSalesCount());
        assertEquals(0, created.getCurrentStar());
    }

}
