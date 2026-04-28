package cn.iocoder.yudao.module.merchant.job.promo;

import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoPoolDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoPoolMapper;
import cn.iocoder.yudao.module.merchant.service.promo.PoolSettlementService;
import cn.iocoder.yudao.module.merchant.service.promo.PromoConfigService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link PromoPoolSettleJob} 跨租户 cron 扫描测试。
 *
 * 注意：Job 依赖 TenantUtils.execute 切换租户上下文。这里不 mock TenantUtils（静态方法），
 * 而是 mock promoConfigService.getConfig()，让它返回当前预设的 config —— 测试不关心
 * 上下文切换是否真发生，只验证哪些 tenant 触发了 settleNow。
 */
class PromoPoolSettleJobTest {

    private ShopPromoPoolMapper poolMapper;
    private PromoConfigService promoConfigService;
    private PoolSettlementService poolSettlementService;
    private PromoPoolSettleJob job;

    /** tenantId → config 映射（promoConfigService.getConfig 内的 tenant 上下文是真切的） */
    private final Map<Long, PromoConfigDO> configByTenant = new HashMap<>();
    private final Map<Long, Integer> settleCallsByTenant = new HashMap<>();

    @BeforeEach
    void setUp() {
        poolMapper = mock(ShopPromoPoolMapper.class);
        promoConfigService = mock(PromoConfigService.class);
        poolSettlementService = mock(PoolSettlementService.class);

        job = new PromoPoolSettleJob();
        ReflectionTestUtils.setField(job, "poolMapper", poolMapper);
        ReflectionTestUtils.setField(job, "promoConfigService", promoConfigService);
        ReflectionTestUtils.setField(job, "poolSettlementService", poolSettlementService);

        configByTenant.clear();
        settleCallsByTenant.clear();

        // promoConfigService 按当前 tenant 上下文返回（tenant 上下文由 TenantUtils.execute 真切）
        when(promoConfigService.getConfig()).thenAnswer(inv -> {
            Long tenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId();
            return configByTenant.get(tenantId);
        });

        // settleNow 调用计数；用当前 tenant 上下文记
        when(poolSettlementService.settleNow(any())).thenAnswer(inv -> {
            Long tenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId();
            settleCallsByTenant.merge(tenantId, 1, Integer::sum);
            return null;
        });
    }

    private ShopPromoPoolDO pool(Long tenantId, long balance, LocalDateTime lastSettled) {
        ShopPromoPoolDO p = ShopPromoPoolDO.builder()
                .balance(balance)
                .lastSettledAt(lastSettled)
                .build();
        p.setId(tenantId * 10);
        p.setTenantId(tenantId);
        return p;
    }

    private PromoConfigDO config(boolean enabled, String cron, String mode) {
        return PromoConfigDO.builder()
                .poolEnabled(enabled)
                .poolSettleCron(cron)
                .poolSettleMode(mode)
                .build();
    }

    @Test
    void firesSettle_whenCronExpired_sinceLastSettled() {
        // tenant 1: 每天 0 点 cron；上次结算 2 天前 → 应该触发
        configByTenant.put(1L, config(true, "0 0 0 * * ?", "FULL"));
        when(poolMapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.singletonList(
                        pool(1L, 10000L, LocalDateTime.now().minusDays(2))));

        String result = job.execute(null);

        assertEquals(1, settleCallsByTenant.getOrDefault(1L, 0).intValue());
        verify(poolSettlementService).settleNow("FULL");
        assertTrue(result.contains("触发 1 个"));
    }

    @Test
    void skipsSettle_whenCronNotYetDue() {
        // tenant 1: 每月 1 号 cron；上次结算 5 天前 → 还没到下个月 1 号
        configByTenant.put(1L, config(true, "0 0 0 1 * ?", "FULL"));
        when(poolMapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.singletonList(
                        pool(1L, 10000L, LocalDateTime.now().minusDays(5))));

        job.execute(null);

        assertEquals(0, settleCallsByTenant.getOrDefault(1L, 0).intValue());
        verify(poolSettlementService, never()).settleNow(any());
    }

    @Test
    void skipsSettle_whenPoolDisabled() {
        configByTenant.put(1L, config(false, "0 0 0 * * ?", "FULL"));
        when(poolMapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.singletonList(
                        pool(1L, 10000L, LocalDateTime.now().minusDays(2))));

        job.execute(null);

        verify(poolSettlementService, never()).settleNow(any());
    }

    @Test
    void skipsSettle_whenCronInvalid_logsWarning() {
        configByTenant.put(1L, config(true, "this-is-not-a-cron", "FULL"));
        when(poolMapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.singletonList(
                        pool(1L, 10000L, LocalDateTime.now().minusDays(2))));

        job.execute(null);

        verify(poolSettlementService, never()).settleNow(any());
    }

    @Test
    void honorsConfiguredMode_LOTTERY() {
        configByTenant.put(1L, config(true, "0 0 0 * * ?", "LOTTERY"));
        when(poolMapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.singletonList(
                        pool(1L, 10000L, LocalDateTime.now().minusDays(2))));

        job.execute(null);

        verify(poolSettlementService).settleNow("LOTTERY");
    }

    @Test
    void fallsBackToFULL_onIllegalMode() {
        configByTenant.put(1L, config(true, "0 0 0 * * ?", "BOGUS_MODE"));
        when(poolMapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.singletonList(
                        pool(1L, 10000L, LocalDateTime.now().minusDays(2))));

        job.execute(null);

        verify(poolSettlementService).settleNow("FULL");
    }

    @Test
    void scansMultipleTenants_perTenantIsolated() {
        configByTenant.put(1L, config(true, "0 0 0 * * ?", "FULL"));
        configByTenant.put(2L, config(true, "0 0 0 1 * ?", "FULL"));   // 月度，未到
        configByTenant.put(3L, config(false, "0 0 0 * * ?", "FULL"));  // disabled
        when(poolMapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(
                pool(1L, 10000L, LocalDateTime.now().minusDays(2)),
                pool(2L, 5000L, LocalDateTime.now().minusDays(5)),
                pool(3L, 1000L, LocalDateTime.now().minusDays(2))));

        job.execute(null);

        assertEquals(1, settleCallsByTenant.getOrDefault(1L, 0).intValue());
        assertNull(settleCallsByTenant.get(2L));
        assertNull(settleCallsByTenant.get(3L));
    }

    @Test
    void singleTenantFailure_doesNotBlockOthers() {
        // tenant 1: getConfig 时炸；tenant 2: 正常
        configByTenant.put(2L, config(true, "0 0 0 * * ?", "FULL"));
        when(poolMapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(
                pool(1L, 10000L, LocalDateTime.now().minusDays(2)),
                pool(2L, 5000L, LocalDateTime.now().minusDays(2))));
        // 给 tenant 1 的 getConfig 埋个炸弹（用 ThreadLocal context 触发）
        when(promoConfigService.getConfig()).thenAnswer(inv -> {
            Long tenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId();
            if (tenantId != null && tenantId == 1L) {
                throw new RuntimeException("炸了");
            }
            return configByTenant.get(tenantId);
        });

        String result = job.execute(null);

        assertEquals(1, settleCallsByTenant.getOrDefault(2L, 0).intValue());
        assertTrue(result.contains("失败 1 个"));
    }

    @Test
    void historicalNullLastSettled_useScanWindow() {
        // lastSettledAt = NULL → 用 now - 30 天作为 scanFrom；每天 cron 必有过去触发点 → 应该 fire
        configByTenant.put(1L, config(true, "0 0 0 * * ?", "FULL"));
        when(poolMapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.singletonList(
                        pool(1L, 10000L, null)));

        job.execute(null);

        assertEquals(1, settleCallsByTenant.getOrDefault(1L, 0).intValue());
    }

    @Test
    void emptyPools_isNoop() {
        when(poolMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        String result = job.execute(null);

        verify(poolSettlementService, never()).settleNow(any());
        assertTrue(result.contains("扫描 0 个"));
    }

}
