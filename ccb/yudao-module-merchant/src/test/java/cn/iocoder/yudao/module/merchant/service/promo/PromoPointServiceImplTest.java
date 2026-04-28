package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopConsumePointRecordDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoRecordDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopConsumePointRecordMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoRecordMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserStarMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link PromoPointServiceImpl} 双积分账本服务测试（v6 修复后版本）。
 *
 * 重点：
 *   - service 不再 SELECT→改→updateById；而是 mapper 的 atomic addPromoPointBalance(/-)
 *   - 扣减失败 → mapper 返 0 行 → service 抛 IllegalStateException
 *   - 幂等键命中 → 直接返 false，不动余额
 *   - convertPromoToConsume 必须传 idempotencyKey
 */
class PromoPointServiceImplTest {

    private ShopUserStarMapper userStarMapper;
    private ShopPromoRecordMapper promoRecordMapper;
    private ShopConsumePointRecordMapper consumeRecordMapper;
    private PromoConfigService promoConfigService;
    private PromoPointServiceImpl service;

    private final AtomicLong promoBalance = new AtomicLong(100L);
    private final AtomicLong consumeBalance = new AtomicLong(0L);

    @BeforeEach
    void setUp() {
        userStarMapper = mock(ShopUserStarMapper.class);
        promoRecordMapper = mock(ShopPromoRecordMapper.class);
        consumeRecordMapper = mock(ShopConsumePointRecordMapper.class);
        promoConfigService = mock(PromoConfigService.class);

        service = new PromoPointServiceImpl();
        ReflectionTestUtils.setField(service, "userStarMapper", userStarMapper);
        ReflectionTestUtils.setField(service, "promoRecordMapper", promoRecordMapper);
        ReflectionTestUtils.setField(service, "consumeRecordMapper", consumeRecordMapper);
        ReflectionTestUtils.setField(service, "promoConfigService", promoConfigService);

        promoBalance.set(100L);
        consumeBalance.set(0L);

        // selectByUserId 永远返一个反映当前余额的 DO
        when(userStarMapper.selectByUserId(eq(1L))).thenAnswer(inv -> ShopUserStarDO.builder()
                .userId(1L).promoPointBalance(promoBalance.get()).consumePointBalance(consumeBalance.get())
                .currentStar(0).directCount(0).teamSalesCount(0).build());

        // 原子累加：仅当 balance + delta >= 0 才生效，返 1；否则返 0
        when(userStarMapper.addPromoPointBalance(eq(1L), anyLong())).thenAnswer(inv -> {
            long delta = inv.getArgument(1);
            long cur = promoBalance.get();
            if (cur + delta < 0) return 0;
            promoBalance.addAndGet(delta);
            return 1;
        });
        when(userStarMapper.addConsumePointBalance(eq(1L), anyLong())).thenAnswer(inv -> {
            long delta = inv.getArgument(1);
            long cur = consumeBalance.get();
            if (cur + delta < 0) return 0;
            consumeBalance.addAndGet(delta);
            return 1;
        });
    }

    @Test
    void addPromoPoint_credits_andWritesRecord() {
        when(promoRecordMapper.exists(1L, "DIRECT", 999L)).thenReturn(false);

        boolean ok = service.addPromoPoint(1L, 50L, "DIRECT", 999L, "推荐奖");

        assertTrue(ok);
        assertEquals(150L, promoBalance.get());
        verify(userStarMapper).addPromoPointBalance(1L, 50L);

        ArgumentCaptor<ShopPromoRecordDO> rec = ArgumentCaptor.forClass(ShopPromoRecordDO.class);
        verify(promoRecordMapper).insert(rec.capture());
        assertEquals(50L, rec.getValue().getAmount());
        assertEquals(150L, rec.getValue().getBalanceAfter());
        assertEquals("DIRECT", rec.getValue().getSourceType());
    }

    @Test
    void addPromoPoint_idempotent_whenSourceAlreadyRecorded() {
        when(promoRecordMapper.exists(1L, "DIRECT", 999L)).thenReturn(true);

        boolean ok = service.addPromoPoint(1L, 50L, "DIRECT", 999L, "重复回放");

        assertFalse(ok);
        assertEquals(100L, promoBalance.get(), "幂等命中不动余额");
        verify(userStarMapper, never()).addPromoPointBalance(any(), anyLong());
        verify(promoRecordMapper, never()).insert(any(ShopPromoRecordDO.class));
    }

    @Test
    void addPromoPoint_zeroOrNegative_isNoop() {
        assertFalse(service.addPromoPoint(1L, 0L, "DIRECT", 1L, "x"));
        assertFalse(service.addPromoPoint(1L, -1L, "DIRECT", 1L, "x"));
        verifyNoInteractions(promoRecordMapper);
        verify(userStarMapper, never()).addPromoPointBalance(any(), anyLong());
    }

    @Test
    void deductPromoPoint_throws_whenBalanceInsufficient() {
        promoBalance.set(10L);
        when(promoRecordMapper.exists(1L, "WITHDRAW", 5L)).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> service.deductPromoPoint(1L, 50L, "WITHDRAW", 5L, "提现"));
        assertEquals(10L, promoBalance.get(), "扣减失败时余额必须保持原值");
    }

    @Test
    void deductPromoPoint_succeeds_writesNegativeAmount() {
        promoBalance.set(100L);
        when(promoRecordMapper.exists(1L, "WITHDRAW", 5L)).thenReturn(false);

        boolean ok = service.deductPromoPoint(1L, 30L, "WITHDRAW", 5L, "提现");

        assertTrue(ok);
        assertEquals(70L, promoBalance.get());
        ArgumentCaptor<ShopPromoRecordDO> rec = ArgumentCaptor.forClass(ShopPromoRecordDO.class);
        verify(promoRecordMapper).insert(rec.capture());
        assertEquals(-30L, rec.getValue().getAmount());
        assertEquals(70L, rec.getValue().getBalanceAfter());
    }

    @Test
    void getOrCreateAccount_createsBlankAccount_whenAbsent() {
        when(userStarMapper.selectByUserId(42L)).thenReturn(null);

        ShopUserStarDO created = service.getOrCreateAccount(42L);

        assertEquals(42L, created.getUserId());
        assertEquals(0L, created.getPromoPointBalance());
        assertEquals(0L, created.getConsumePointBalance());
        verify(userStarMapper).insert(created);
    }

    @Test
    void convertPromoToConsume_appliesRatio_andUsesIdempotencyKey() {
        when(promoConfigService.getConfig()).thenReturn(PromoConfigDO.builder()
                .pointConversionRatio(BigDecimal.ONE).build());
        promoBalance.set(500L);
        consumeBalance.set(0L);
        when(promoRecordMapper.exists(eq(1L), eq("CONVERT"), eq(7777L))).thenReturn(false);
        when(consumeRecordMapper.exists(eq(1L), eq("CONVERT"), eq(7777L))).thenReturn(false);

        service.convertPromoToConsume(1L, 200L, 7777L);

        assertEquals(300L, promoBalance.get());
        assertEquals(200L, consumeBalance.get());
        verify(promoRecordMapper).insert(any(ShopPromoRecordDO.class));
        verify(consumeRecordMapper).insert(any(ShopConsumePointRecordDO.class));
    }

    @Test
    void convertPromoToConsume_idempotent_whenSameKeyReplayed() {
        when(promoConfigService.getConfig()).thenReturn(PromoConfigDO.builder()
                .pointConversionRatio(BigDecimal.ONE).build());
        promoBalance.set(500L);
        when(promoRecordMapper.exists(eq(1L), eq("CONVERT"), eq(7777L))).thenReturn(true);
        when(consumeRecordMapper.exists(eq(1L), eq("CONVERT"), eq(7777L))).thenReturn(true);

        service.convertPromoToConsume(1L, 200L, 7777L);

        assertEquals(500L, promoBalance.get(), "幂等命中不应再扣推广积分");
        assertEquals(0L, consumeBalance.get(), "幂等命中不应再加消费积分");
    }

    @Test
    void convertPromoToConsume_rejectsNullIdempotencyKey() {
        assertThrows(IllegalArgumentException.class,
                () -> service.convertPromoToConsume(1L, 200L, null));
    }

    @Test
    void convertPromoToConsume_appliesCustomRatio_truncatesDown() {
        when(promoConfigService.getConfig()).thenReturn(PromoConfigDO.builder()
                .pointConversionRatio(new BigDecimal("0.50")).build());
        promoBalance.set(1000L);
        consumeBalance.set(0L);
        when(promoRecordMapper.exists(eq(1L), eq("CONVERT"), any())).thenReturn(false);
        when(consumeRecordMapper.exists(eq(1L), eq("CONVERT"), any())).thenReturn(false);

        service.convertPromoToConsume(1L, 99L, 1L);

        assertEquals(901L, promoBalance.get());
        assertEquals(49L, consumeBalance.get(), "99 × 0.50 = 49.5 → 向下取整 49");
    }

}
