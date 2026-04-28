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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * {@link CommissionServiceImpl} 团队极差递减算法测试。
 *
 * 配置：commissionRates = [1, 2, 3, 4, 5]（1 星 1% / ... / 5 星 5%）
 * 实付：1000 元 = 100000 分（便于看到整数差额：1% = 1000 分）
 *
 * 用例覆盖：
 *   1. 文档第 6 节示例：5 星之上有 5 星 → 上方 5 星拿 0%（被吃掉）
 *   2. 链路：8(2星) → 7(无) → 6(3星) → 5(5星) → ... → 1
 *      期望：8 拿 2% = 2000；6 拿 (3%-2%) = 1000；5 拿 (5%-3%) = 2000；其余跳过
 *   3. 买家无星 + 链路有 1 个 5 星 → 5 星拿 5%
 *   4. 整链无星级用户 → 一分不发
 *   5. 已发后再遇相同/低于星级的人 → 跳过
 */
class CommissionServiceImplTest {

    private ShopUserStarMapper userStarMapper;
    private PromoConfigService promoConfigService;
    private ReferralService referralService;
    private PromoPointService promoPointService;
    private CommissionServiceImpl service;

    private final Map<Long, Long> awarded = new HashMap<>();
    private final Map<Long, Integer> stars = new HashMap<>();

    @BeforeEach
    void setUp() {
        userStarMapper = mock(ShopUserStarMapper.class);
        promoConfigService = mock(PromoConfigService.class);
        referralService = mock(ReferralService.class);
        promoPointService = mock(PromoPointService.class);

        service = new CommissionServiceImpl();
        ReflectionTestUtils.setField(service, "userStarMapper", userStarMapper);
        ReflectionTestUtils.setField(service, "promoConfigService", promoConfigService);
        ReflectionTestUtils.setField(service, "referralService", referralService);
        ReflectionTestUtils.setField(service, "promoPointService", promoPointService);

        awarded.clear();
        stars.clear();

        when(promoConfigService.getConfig()).thenReturn(PromoConfigDO.builder()
                .commissionRates("[1,2,3,4,5]").build());
        when(userStarMapper.selectByUserId(any())).thenAnswer(inv -> {
            Long uid = inv.getArgument(0);
            Integer s = stars.get(uid);
            if (s == null) return null;
            ShopUserStarDO d = ShopUserStarDO.builder().userId(uid).currentStar(s).build();
            return d;
        });
        when(promoPointService.addPromoPoint(any(), anyLong(), any(), any(), any())).thenAnswer(inv -> {
            Long uid = inv.getArgument(0);
            long amt = inv.getArgument(1);
            awarded.merge(uid, amt, Long::sum);
            return true;
        });
    }

    @Test
    void chain_with_decreasing_stars_distributesDifferential() {
        // 8(2星) → 7(无) → 6(3星) → 5(5星) → 4(无) → 3(无) → 2(无) → 1(无)
        stars.put(8L, 2);
        stars.put(6L, 3);
        stars.put(5L, 5);
        when(referralService.getAncestors(8L, 50)).thenReturn(Arrays.asList(7L, 6L, 5L, 4L, 3L, 2L, 1L));

        service.handleOrderPaid(8L, 100000L, 999L);

        // 8: 2% = 2000
        assertEquals(2000L, awarded.getOrDefault(8L, 0L));
        // 7 没有星级 → 0
        assertNull(awarded.get(7L));
        // 6: 3 - 2 = 1% = 1000
        assertEquals(1000L, awarded.getOrDefault(6L, 0L));
        // 5: 5 - 3 = 2% = 2000
        assertEquals(2000L, awarded.getOrDefault(5L, 0L));
        // 4..1 无星级 → 0
        assertNull(awarded.get(4L));
        assertNull(awarded.get(3L));
        assertNull(awarded.get(2L));
        assertNull(awarded.get(1L));
    }

    @Test
    void duplicate_star_above_is_eaten_zero_commission() {
        // 4(5星) → 3(5星) → 2(无) → 1(无)：3 头上的 5 星拿 0%
        stars.put(4L, 5);
        stars.put(3L, 5);
        when(referralService.getAncestors(4L, 50)).thenReturn(Arrays.asList(3L, 2L, 1L));

        service.handleOrderPaid(4L, 100000L, 1L);

        // 4: 5% = 5000
        assertEquals(5000L, awarded.getOrDefault(4L, 0L));
        // 3: 5 - 5 = 0% → 不写流水
        assertNull(awarded.get(3L), "5 星之上 5 星 → 0%，不应入账");
    }

    @Test
    void buyer_no_star_butChainHasHighStar_awardsFullRate() {
        // 8(无) → 7(无) → 6(无) → 5(4 星)
        stars.put(5L, 4);
        when(referralService.getAncestors(8L, 50)).thenReturn(Arrays.asList(7L, 6L, 5L, 4L, 3L, 2L, 1L));

        service.handleOrderPaid(8L, 100000L, 1L);

        // 5 拿 4% = 4000；其余 0
        assertEquals(4000L, awarded.getOrDefault(5L, 0L));
        assertNull(awarded.get(8L));
        assertEquals(1, awarded.size());
    }

    @Test
    void chain_without_any_star_paysNothing() {
        when(referralService.getAncestors(8L, 50)).thenReturn(Arrays.asList(7L, 6L, 5L));

        service.handleOrderPaid(8L, 100000L, 1L);

        assertTrue(awarded.isEmpty());
    }

    @Test
    void low_star_below_issued_isSkipped() {
        // 8(3星) → 7(2星) → 6(4星)：7 < 已发 3 → 跳过；6 = 4 > 3 → 4-3=1%
        stars.put(8L, 3);
        stars.put(7L, 2);
        stars.put(6L, 4);
        when(referralService.getAncestors(8L, 50)).thenReturn(Arrays.asList(7L, 6L));

        service.handleOrderPaid(8L, 100000L, 1L);

        assertEquals(3000L, awarded.getOrDefault(8L, 0L));
        assertNull(awarded.get(7L));
        assertEquals(1000L, awarded.getOrDefault(6L, 0L));
    }

    @Test
    void noop_whenAmountOrIdInvalid() {
        service.handleOrderPaid(null, 1000L, 1L);
        service.handleOrderPaid(1L, 0L, 1L);
        service.handleOrderPaid(1L, 1000L, null);
        verify(promoPointService, never()).addPromoPoint(any(), anyLong(), any(), any(), any());
    }

}
