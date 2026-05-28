package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.promo.ProductPromoConfigSaveReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.PromoConfigMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ProductPromoConfigMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserStarMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * V044 合规整改回归测试。
 *
 * 覆盖 4 项核心合规规则：
 *   1. CommissionServiceImpl.handleOrderPaidV8 永久 return — 团队极差禁用
 *   2. StarServiceImpl.attemptUpgradeV8 仅看个人 KPI — 不读 child.currentStar / teamSalesAmount
 *   3. ProductPromoConfigServiceImpl.validate
 *      a) sum(tuijianRatios) ≤ 100%
 *      b) starRatios[i] ≤ 35%
 *      c) sum + directRatio ≤ 100%（跨配置）
 *   4. PromoConfigServiceImpl.saveConfig directCommissionRatio ≤ 35%
 *
 * 这些是 V044 整改后必须永久保持的不变量。
 */
@DisplayName("V044 合规整改回归测试")
public class V044ComplianceTest {

    @Nested
    @DisplayName("1. 团队极差永久禁用（CommissionService.handleOrderPaidV8）")
    class CommissionDisabledTest {

        @Test
        @DisplayName("入口直接 return，任何参数都不产生 COMMISSION 流水")
        void shouldNeverInvokePromoPoint() {
            CommissionServiceImpl svc = new CommissionServiceImpl();
            PromoPointService mockPoint = mock(PromoPointService.class);
            ReflectionTestUtils.setField(svc, "promoPointService", mockPoint);

            ProductPromoConfigDO config = new ProductPromoConfigDO();
            config.setStarCount(3);
            config.setStarRatios("[10,20,30]");

            // 即使传入完整合法参数，也不能产生 promoPoint 入账
            svc.handleOrderPaidV8(config, 99001L, 99006L, 100_000L, 88888L);

            // V044 关键不变量：addPromoPoint 一次都不能被调用
            verifyNoInteractions(mockPoint);
        }

        @Test
        @DisplayName("null 参数 / 0 paidAmount 也不抛异常")
        void shouldSafeReturnOnEdgeCases() {
            CommissionServiceImpl svc = new CommissionServiceImpl();
            PromoPointService mockPoint = mock(PromoPointService.class);
            ReflectionTestUtils.setField(svc, "promoPointService", mockPoint);

            assertDoesNotThrow(() -> svc.handleOrderPaidV8(null, null, null, 0L, null));
            verifyNoInteractions(mockPoint);
        }
    }

    @Nested
    @DisplayName("2. 升星仅看个人 KPI（StarServiceImpl.attemptUpgradeV8）")
    class StarUpgradeIndividualKpiTest {

        private StarServiceImpl svc;
        private ShopUserStarMapper userStarMapper;

        @BeforeEach
        void setUp() {
            svc = new StarServiceImpl();
            userStarMapper = mock(ShopUserStarMapper.class);
            ReflectionTestUtils.setField(svc, "userStarMapper", userStarMapper);
        }

        private ShopUserStarDO acct(int star, int directCount, long selfPurchase, long teamSales) {
            ShopUserStarDO a = new ShopUserStarDO();
            a.setCurrentStar(star);
            a.setDirectCount(directCount);
            a.setSelfPurchaseAmount(selfPurchase);
            a.setTeamSalesAmount(teamSales);
            return a;
        }

        @Test
        @DisplayName("直推数达标即升 — 个人 KPI 分支")
        void directBranchUpgrade() {
            // 当前 0 星，直推 5 人付费 → 应升到 1 星（rules[0].requiredCount=2）
            ShopUserStarDO before = acct(0, 5, 0L, 0L);
            when(userStarMapper.selectByUserAndSpu(99001L, 99006L)).thenReturn(before);

            String rules = "[" +
                    "{\"star\":1,\"requiredCount\":2,\"selfPurchaseAmount\":50000}," +
                    "{\"star\":2,\"requiredCount\":10,\"selfPurchaseAmount\":200000}" +
                    "]";

            ReflectionTestUtils.invokeMethod(svc, "attemptUpgradeV8", 99001L, 99006L,
                    ReflectionTestUtils.invokeMethod(svc, "parseRulesV8", rules));

            verify(userStarMapper).upgradeStarIfHigherBySpu(99001L, 99006L, 1);
        }

        @Test
        @DisplayName("自购金额达标即升 — 个人 KPI 分支")
        void selfPurchaseBranchUpgrade() {
            // 当前 0 星，0 直推但自购 ¥1000（100000 分）→ 升 1 星（rules[0].selfPurchaseAmount=50000）
            ShopUserStarDO before = acct(0, 0, 100_000L, 0L);
            when(userStarMapper.selectByUserAndSpu(99001L, 99006L)).thenReturn(before);

            String rules = "[" +
                    "{\"star\":1,\"requiredCount\":2,\"selfPurchaseAmount\":50000}" +
                    "]";

            ReflectionTestUtils.invokeMethod(svc, "attemptUpgradeV8", 99001L, 99006L,
                    ReflectionTestUtils.invokeMethod(svc, "parseRulesV8", rules));

            verify(userStarMapper).upgradeStarIfHigherBySpu(99001L, 99006L, 1);
        }

        @Test
        @DisplayName("两项 KPI 都不达标 — 不升")
        void noUpgradeWhenBothBelow() {
            ShopUserStarDO before = acct(0, 1, 10_000L, 0L);  // 1 直推 < 2 / 100元自购 < 500元
            when(userStarMapper.selectByUserAndSpu(99001L, 99006L)).thenReturn(before);

            String rules = "[" +
                    "{\"star\":1,\"requiredCount\":2,\"selfPurchaseAmount\":50000}" +
                    "]";

            ReflectionTestUtils.invokeMethod(svc, "attemptUpgradeV8", 99001L, 99006L,
                    ReflectionTestUtils.invokeMethod(svc, "parseRulesV8", rules));

            verify(userStarMapper, never()).upgradeStarIfHigherBySpu(any(), any(), any());
        }

        @Test
        @DisplayName("仅 teamSalesAmount 高但个人 KPI 不达标 — 不升（V044 关键合规不变量）")
        void noUpgradeFromTeamSalesAlone() {
            // 直推 0、自购 0，但 teamSalesAmount 巨大（旧逻辑会升星）
            // V044 必须不升 → 证明已删除"团队业绩"判定
            ShopUserStarDO before = acct(0, 0, 0L, 9_999_999_999L);
            when(userStarMapper.selectByUserAndSpu(99001L, 99006L)).thenReturn(before);

            String rules = "[" +
                    "{\"star\":1,\"requiredCount\":2,\"selfPurchaseAmount\":50000}" +
                    "]";

            ReflectionTestUtils.invokeMethod(svc, "attemptUpgradeV8", 99001L, 99006L,
                    ReflectionTestUtils.invokeMethod(svc, "parseRulesV8", rules));

            verify(userStarMapper, never()).upgradeStarIfHigherBySpu(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("3. ProductPromoConfigServiceImpl.validate 硬约束")
    class ProductConfigValidationTest {

        private ProductPromoConfigServiceImpl svc;

        @BeforeEach
        void setUp() {
            svc = new ProductPromoConfigServiceImpl();
            ProductPromoConfigMapper mapper = mock(ProductPromoConfigMapper.class);
            PromoConfigMapper shopMapper = mock(PromoConfigMapper.class);
            ReflectionTestUtils.setField(svc, "mapper", mapper);
            ReflectionTestUtils.setField(svc, "shopPromoConfigMapper", shopMapper);

            // 默认商户级 directRatio=0，不触发跨配置校验
            PromoConfigDO shopCfg = new PromoConfigDO();
            shopCfg.setDirectCommissionRatio(BigDecimal.ZERO);
            when(shopMapper.selectCurrent()).thenReturn(shopCfg);
        }

        @Test
        @DisplayName("starRatios[i] > 35% 抛 1_031_002_012")
        void shouldRejectStarRatioAbove35() {
            ProductPromoConfigSaveReqVO req = new ProductPromoConfigSaveReqVO();
            req.setSpuId(99006L);
            req.setStarCount(3);
            req.setStarRatios("[40,30,25]");   // 第一个 40% 超 35%
            req.setStarUpgradeRules("[{\"star\":1,\"requiredCount\":1},{\"star\":2,\"requiredCount\":3},{\"star\":3,\"requiredCount\":10}]");

            ServiceException ex = assertThrows(ServiceException.class, () -> svc.save(req));
            assertTrue(ex.getMessage().contains("35%"));
            assertEquals(1_031_002_012, ex.getCode());
        }

        @Test
        @DisplayName("starRatios[i] = 35% 允许（边界）")
        void shouldAllowStarRatioAt35() {
            ProductPromoConfigSaveReqVO req = new ProductPromoConfigSaveReqVO();
            req.setSpuId(99006L);
            req.setStarCount(2);
            req.setStarRatios("[20,35]");      // 35% 边界值，允许
            req.setStarUpgradeRules("[{\"star\":1,\"requiredCount\":1},{\"star\":2,\"requiredCount\":5}]");

            assertDoesNotThrow(() -> svc.save(req));
        }

        @Test
        @DisplayName("tuijianRatios sum > 100% 抛 1_031_002_003")
        void shouldRejectTuijianSumOver100() {
            ProductPromoConfigSaveReqVO req = new ProductPromoConfigSaveReqVO();
            req.setSpuId(99006L);
            req.setTuijianEnabled(true);
            req.setTuijianN(2);
            req.setTuijianRatios("[60,60]");   // sum=120 > 100
            req.setStarCount(0);
            req.setStarRatios("[]");

            ServiceException ex = assertThrows(ServiceException.class, () -> svc.save(req));
            assertEquals(1_031_002_003, ex.getCode());
        }
    }

    @Nested
    @DisplayName("4. PromoConfigServiceImpl.saveConfig directCommissionRatio ≤ 35%")
    class PromoConfigValidationTest {

        @Test
        @DisplayName("directCommissionRatio = 36% 抛 1_031_001_001")
        void shouldRejectDirectRatioAbove35() {
            PromoConfigServiceImpl svc = new PromoConfigServiceImpl();

            cn.iocoder.yudao.module.merchant.controller.admin.vo.promo.PromoConfigSaveReqVO req =
                    new cn.iocoder.yudao.module.merchant.controller.admin.vo.promo.PromoConfigSaveReqVO();
            req.setDirectCommissionRatio(new BigDecimal("36"));

            ServiceException ex = assertThrows(ServiceException.class, () -> svc.saveConfig(req));
            assertEquals(1_031_001_001, ex.getCode());
            assertTrue(ex.getMessage().contains("35"));
        }
    }
}
