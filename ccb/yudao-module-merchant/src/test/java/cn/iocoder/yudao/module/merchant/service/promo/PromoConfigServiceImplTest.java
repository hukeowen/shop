package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.controller.admin.vo.promo.PromoConfigSaveReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.PromoConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link PromoConfigServiceImpl} 纯 Mockito 单元测试。
 *
 * 不启动 Spring / 不连数据库，仅校验 service 业务逻辑：
 *   1. 首次进入返回内置默认值（不入库）
 *   2. 已存在配置 → 返回数据库值
 *   3. 保存：未入库 → insert；已入库 → update
 *   4. 默认值 JSON 字段格式合法
 */
class PromoConfigServiceImplTest {

    private PromoConfigMapper mapper;
    private PromoConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(PromoConfigMapper.class);
        service = new PromoConfigServiceImpl();
        ReflectionTestUtils.setField(service, "promoConfigMapper", mapper);
    }

    @Test
    void getConfig_returnsDefault_whenNotExists() {
        when(mapper.selectCurrent()).thenReturn(null);

        PromoConfigDO result = service.getConfig();

        assertNotNull(result);
        assertEquals(5, result.getStarLevelCount());
        assertEquals("[1,2,3,4,5]", result.getCommissionRates());
        assertNotNull(result.getStarUpgradeRules());
        assertTrue(result.getStarUpgradeRules().contains("directCount"));
        assertEquals(BigDecimal.ONE, result.getPointConversionRatio());
        assertEquals(10000, result.getWithdrawThreshold());
        assertFalse(result.getPoolEnabled());
        assertEquals("ALL", result.getPoolDistributeMode());
        assertEquals("FULL", result.getPoolSettleMode(), "cron 自动结算默认 FULL");
        assertNull(result.getId(), "默认值不入库，id 应为 null");
        verify(mapper, never()).insert(any(PromoConfigDO.class));
    }

    @Test
    void getConfig_returnsExisting_whenPersisted() {
        PromoConfigDO existing = PromoConfigDO.builder()
                .starLevelCount(7)
                .commissionRates("[2,3,4,5,6,7,8]")
                .build();
        existing.setId(42L);
        when(mapper.selectCurrent()).thenReturn(existing);

        PromoConfigDO result = service.getConfig();

        assertSame(existing, result);
        assertEquals(42L, result.getId());
    }

    @Test
    void saveConfig_inserts_whenNotExists() {
        when(mapper.selectCurrent()).thenReturn(null);

        PromoConfigSaveReqVO req = buildReq();
        service.saveConfig(req);

        ArgumentCaptor<PromoConfigDO> captor = ArgumentCaptor.forClass(PromoConfigDO.class);
        verify(mapper).insert(captor.capture());
        verify(mapper, never()).updateById(any(PromoConfigDO.class));
        PromoConfigDO inserted = captor.getValue();
        assertEquals(req.getStarLevelCount(), inserted.getStarLevelCount());
        assertEquals(req.getCommissionRates(), inserted.getCommissionRates());
        assertEquals(req.getPoolDistributeMode(), inserted.getPoolDistributeMode());
    }

    @Test
    void saveConfig_updates_whenExists() {
        PromoConfigDO existing = PromoConfigDO.builder().starLevelCount(3).build();
        existing.setId(7L);
        when(mapper.selectCurrent()).thenReturn(existing);

        PromoConfigSaveReqVO req = buildReq();
        service.saveConfig(req);

        verify(mapper, never()).insert(any(PromoConfigDO.class));
        ArgumentCaptor<PromoConfigDO> captor = ArgumentCaptor.forClass(PromoConfigDO.class);
        verify(mapper).updateById(captor.capture());
        PromoConfigDO updated = captor.getValue();
        assertEquals(7L, updated.getId(), "更新时必须保留 id");
        assertEquals(req.getStarLevelCount(), updated.getStarLevelCount());
    }

    private PromoConfigSaveReqVO buildReq() {
        PromoConfigSaveReqVO req = new PromoConfigSaveReqVO();
        req.setStarLevelCount(5);
        req.setCommissionRates("[1,2,3,4,5]");
        req.setStarUpgradeRules("[{\"directCount\":2,\"teamSales\":3}]");
        req.setPointConversionRatio(new BigDecimal("1.00"));
        req.setWithdrawThreshold(10000);
        req.setPoolEnabled(true);
        req.setPoolRatio(new BigDecimal("5.00"));
        req.setPoolEligibleStars("[3,4,5]");
        req.setPoolDistributeMode("STAR");
        req.setPoolSettleCron("0 0 0 1 * ?");
        req.setPoolLotteryRatio(new BigDecimal("5.00"));
        req.setPoolSettleMode("FULL");
        return req;
    }

}
