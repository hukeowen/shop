package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoPoolDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueueEventDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoPoolMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopQueueEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link PromoPoolServiceImpl} 入池累积 + 幂等测试。
 */
class PromoPoolServiceImplTest {

    private ShopPromoPoolMapper poolMapper;
    private ShopQueueEventMapper eventMapper;
    private PromoConfigService promoConfigService;
    private PromoPoolServiceImpl service;

    private final AtomicLong poolBalance = new AtomicLong(0L);
    private final List<ShopQueueEventDO> events = new ArrayList<>();
    private final boolean[] poolExists = {false};

    @BeforeEach
    void setUp() {
        poolMapper = mock(ShopPromoPoolMapper.class);
        eventMapper = mock(ShopQueueEventMapper.class);
        promoConfigService = mock(PromoConfigService.class);

        service = new PromoPoolServiceImpl();
        ReflectionTestUtils.setField(service, "poolMapper", poolMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "promoConfigService", promoConfigService);

        events.clear();
        poolBalance.set(0L);
        poolExists[0] = false;

        when(poolMapper.selectCurrent()).thenAnswer(inv -> {
            if (!poolExists[0]) return null;
            return ShopPromoPoolDO.builder().balance(poolBalance.get()).build();
        });
        when(poolMapper.insert(any(ShopPromoPoolDO.class))).thenAnswer(inv -> {
            ShopPromoPoolDO p = inv.getArgument(0);
            poolBalance.set(p.getBalance());
            poolExists[0] = true;
            return 1;
        });
        when(poolMapper.updateById(any(ShopPromoPoolDO.class))).thenAnswer(inv -> {
            ShopPromoPoolDO p = inv.getArgument(0);
            poolBalance.set(p.getBalance());
            return 1;
        });
        // 原子累加：仅在 pool 已存在时被调用；模拟即可
        when(poolMapper.addBalance(any(), anyLong())).thenAnswer(inv -> {
            long delta = inv.getArgument(1);
            poolBalance.addAndGet(delta);
            return 1;
        });
        when(eventMapper.insert(any(ShopQueueEventDO.class))).thenAnswer(inv -> {
            events.add(inv.getArgument(0));
            return 1;
        });
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
    }

    @Test
    void deposit_creditsPool_andWritesMarkerEvent() {
        when(promoConfigService.getConfig()).thenReturn(PromoConfigDO.builder()
                .poolEnabled(true).poolRatio(new BigDecimal("5.00")).build());
        ProductPromoConfigDO config = ProductPromoConfigDO.builder()
                .spuId(100L).poolEnabled(true).build();

        service.depositIfEnabled(config, 10000L, 999L);

        // 5% × 10000 = 500
        assertEquals(500L, poolBalance.get());
        assertEquals(1, events.size());
        assertEquals("POOL_DEPOSIT", events.get(0).getEventType());
        assertEquals(0L, events.get(0).getBeneficiaryUserId(), "系统受益人 = 0");
    }

    @Test
    void deposit_idempotent_replaySameOrder() {
        when(promoConfigService.getConfig()).thenReturn(PromoConfigDO.builder()
                .poolEnabled(true).poolRatio(new BigDecimal("5.00")).build());
        ProductPromoConfigDO config = ProductPromoConfigDO.builder()
                .spuId(100L).poolEnabled(true).build();

        service.depositIfEnabled(config, 10000L, 999L);
        long after1st = poolBalance.get();
        service.depositIfEnabled(config, 10000L, 999L);  // 重放

        assertEquals(after1st, poolBalance.get(), "重放不应再入池");
        assertEquals(1, events.size(), "重放不应再写事件");
    }

    @Test
    void deposit_skipped_whenProductDisabled() {
        ProductPromoConfigDO config = ProductPromoConfigDO.builder()
                .spuId(100L).poolEnabled(false).build();
        service.depositIfEnabled(config, 10000L, 999L);
        assertEquals(0L, poolBalance.get());
        verifyNoInteractions(promoConfigService);
    }

    @Test
    void deposit_skipped_whenShopDisabled() {
        when(promoConfigService.getConfig()).thenReturn(PromoConfigDO.builder()
                .poolEnabled(false).build());
        ProductPromoConfigDO config = ProductPromoConfigDO.builder()
                .spuId(100L).poolEnabled(true).build();
        service.depositIfEnabled(config, 10000L, 999L);
        assertEquals(0L, poolBalance.get());
        verify(eventMapper, never()).insert(any(ShopQueueEventDO.class));
    }

    @Test
    void deposit_skipped_whenRatioIsZero() {
        when(promoConfigService.getConfig()).thenReturn(PromoConfigDO.builder()
                .poolEnabled(true).poolRatio(BigDecimal.ZERO).build());
        ProductPromoConfigDO config = ProductPromoConfigDO.builder()
                .spuId(100L).poolEnabled(true).build();
        service.depositIfEnabled(config, 10000L, 999L);
        assertEquals(0L, poolBalance.get());
    }

    @Test
    void deposit_silentReturn_onNullOrInvalidInput() {
        service.depositIfEnabled(null, 10000L, 999L);
        ProductPromoConfigDO config = ProductPromoConfigDO.builder().spuId(100L).poolEnabled(true).build();
        service.depositIfEnabled(config, 0L, 999L);
        service.depositIfEnabled(config, 10000L, null);
        verifyNoInteractions(promoConfigService);
    }

}
