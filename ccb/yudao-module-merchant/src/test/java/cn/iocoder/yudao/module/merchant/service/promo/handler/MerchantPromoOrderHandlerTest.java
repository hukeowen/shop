package cn.iocoder.yudao.module.merchant.service.promo.handler;

import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;
import cn.iocoder.yudao.module.merchant.service.promo.CommissionService;
import cn.iocoder.yudao.module.merchant.service.promo.ProductPromoConfigService;
import cn.iocoder.yudao.module.merchant.service.promo.PromoPointService;
import cn.iocoder.yudao.module.merchant.service.promo.PromoPoolService;
import cn.iocoder.yudao.module.merchant.service.promo.PromoQueueService;
import cn.iocoder.yudao.module.merchant.service.promo.StarService;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link MerchantPromoOrderHandler} 引擎编排测试。
 *
 * 不验算法（已在各 ServiceImpl 测试覆盖），只验：
 *   - afterPayOrder 把 5 个 step 都按顺序调起来
 *   - 单 item 抛异常不会中断其它 item
 *   - null / 空 orderItems 静默返回
 *   - 消费积分入账金额按"实付分 ÷ 100 × ratio"计算
 */
class MerchantPromoOrderHandlerTest {

    private ProductPromoConfigService productPromoConfigService;
    private PromoPointService promoPointService;
    private PromoQueueService promoQueueService;
    private CommissionService commissionService;
    private PromoPoolService promoPoolService;
    private StarService starService;
    private MerchantPromoOrderHandler handler;

    @BeforeEach
    void setUp() {
        productPromoConfigService = mock(ProductPromoConfigService.class);
        promoPointService = mock(PromoPointService.class);
        promoQueueService = mock(PromoQueueService.class);
        commissionService = mock(CommissionService.class);
        promoPoolService = mock(PromoPoolService.class);
        starService = mock(StarService.class);

        handler = new MerchantPromoOrderHandler();
        ReflectionTestUtils.setField(handler, "productPromoConfigService", productPromoConfigService);
        ReflectionTestUtils.setField(handler, "promoPointService", promoPointService);
        ReflectionTestUtils.setField(handler, "promoQueueService", promoQueueService);
        ReflectionTestUtils.setField(handler, "commissionService", commissionService);
        ReflectionTestUtils.setField(handler, "promoPoolService", promoPoolService);
        ReflectionTestUtils.setField(handler, "starService", starService);
    }

    @Test
    void afterPayOrder_invokesAllFivePipelines() {
        ProductPromoConfigDO config = ProductPromoConfigDO.builder()
                .spuId(100L)
                .consumePointRatio(new BigDecimal("1.00"))
                .tuijianEnabled(true).tuijianN(3).tuijianRatios("[30,30,40]")
                .poolEnabled(true)
                .build();
        when(productPromoConfigService.getBySpuId(100L)).thenReturn(config);

        TradeOrderDO order = new TradeOrderDO().setId(999L).setUserId(7L);
        TradeOrderItemDO item = new TradeOrderItemDO()
                .setSpuId(100L).setCount(2).setPayPrice(20000);  // 200 元

        handler.afterPayOrder(order, Collections.singletonList(item));

        // 1. 消费积分：200 元 × 1.00 = 200 分
        verify(promoPointService).addConsumePoint(eq(7L), eq(200L), eq("CONSUME"), eq(999L), any());
        // 2. 队列
        verify(promoQueueService).handleOrderPaid(eq(config), eq(7L), eq(100L), eq(20000L), eq(999L));
        // 3. 极差（订单总额，单 item 总额 = 20000）
        verify(commissionService, times(1)).handleOrderPaid(eq(7L), eq(20000L), eq(999L));
        // 4. 入池
        verify(promoPoolService).depositIfEnabled(eq(config), eq(20000L), eq(999L));
        // 5. 星级（countable=true 因为 tuijianEnabled）
        verify(starService).handleOrderPaid(eq(7L), eq(2), eq(true));
    }

    @Test
    void afterPayOrder_starService_countableFalse_whenTuijianDisabled() {
        ProductPromoConfigDO config = ProductPromoConfigDO.builder()
                .spuId(100L)
                .consumePointRatio(BigDecimal.ZERO)
                .tuijianEnabled(false)
                .poolEnabled(false)
                .build();
        when(productPromoConfigService.getBySpuId(100L)).thenReturn(config);

        TradeOrderDO order = new TradeOrderDO().setId(999L).setUserId(7L);
        TradeOrderItemDO item = new TradeOrderItemDO().setSpuId(100L).setCount(1).setPayPrice(10000);

        handler.afterPayOrder(order, Collections.singletonList(item));

        verify(starService).handleOrderPaid(eq(7L), eq(1), eq(false));
        // 消费积分 ratio=0 不入账
        verify(promoPointService, never()).addConsumePoint(any(), anyLong(), any(), any(), any());
    }

    @Test
    void afterPayOrder_multiItem_commissionFiredOnceWithSumOfPayPrice() {
        // 同订单 2 个 item：100 元 + 250 元 → commission 应基于总额 350 元 = 35000 分
        ProductPromoConfigDO cfgA = ProductPromoConfigDO.builder().spuId(100L)
                .consumePointRatio(BigDecimal.ZERO).tuijianEnabled(false).poolEnabled(false).build();
        ProductPromoConfigDO cfgB = ProductPromoConfigDO.builder().spuId(200L)
                .consumePointRatio(BigDecimal.ZERO).tuijianEnabled(false).poolEnabled(false).build();
        when(productPromoConfigService.getBySpuId(100L)).thenReturn(cfgA);
        when(productPromoConfigService.getBySpuId(200L)).thenReturn(cfgB);

        TradeOrderDO order = new TradeOrderDO().setId(999L).setUserId(7L);
        TradeOrderItemDO i1 = new TradeOrderItemDO().setSpuId(100L).setCount(1).setPayPrice(10000);
        TradeOrderItemDO i2 = new TradeOrderItemDO().setSpuId(200L).setCount(1).setPayPrice(25000);

        handler.afterPayOrder(order, Arrays.asList(i1, i2));

        // 关键断言：commission 被调用 1 次，金额 = 35000，不是 10000，也不是 25000
        verify(commissionService, times(1)).handleOrderPaid(eq(7L), eq(35000L), eq(999L));
        verifyNoMoreInteractions(commissionService);
    }

    @Test
    void afterPayOrder_oneItemException_doesNotBreakOthers() {
        TradeOrderItemDO item1 = new TradeOrderItemDO().setSpuId(100L).setCount(1).setPayPrice(10000);
        TradeOrderItemDO item2 = new TradeOrderItemDO().setSpuId(200L).setCount(1).setPayPrice(20000);

        when(productPromoConfigService.getBySpuId(100L))
                .thenThrow(new RuntimeException("配置查询炸了"));
        ProductPromoConfigDO config2 = ProductPromoConfigDO.builder()
                .spuId(200L).consumePointRatio(BigDecimal.ZERO)
                .tuijianEnabled(false).poolEnabled(false).build();
        when(productPromoConfigService.getBySpuId(200L)).thenReturn(config2);

        TradeOrderDO order = new TradeOrderDO().setId(999L).setUserId(7L);

        // 不应抛
        handler.afterPayOrder(order, Arrays.asList(item1, item2));

        // item2 仍被处理
        verify(promoQueueService).handleOrderPaid(eq(config2), eq(7L), eq(200L), eq(20000L), eq(999L));
    }

    @Test
    void afterPayOrder_silentReturn_whenNullOrEmpty() {
        handler.afterPayOrder(null, Collections.emptyList());
        handler.afterPayOrder(new TradeOrderDO(), null);
        handler.afterPayOrder(new TradeOrderDO(), Collections.emptyList());
        verifyNoInteractions(productPromoConfigService);
    }

    @Test
    void afterPayOrder_skips_whenSpuOrPriceInvalid() {
        TradeOrderItemDO item = new TradeOrderItemDO().setSpuId(null).setCount(1).setPayPrice(10000);
        TradeOrderDO order = new TradeOrderDO().setId(1L).setUserId(2L);

        handler.afterPayOrder(order, Collections.singletonList(item));

        verifyNoInteractions(productPromoConfigService);
        verifyNoInteractions(promoQueueService);
    }

    @Test
    void afterPayOrder_consumePoints_truncatesDownToInteger() {
        // 99 分 × ratio 0.50 = 0.495 元 × 100... actually：
        // paidAmount = 99 分；99 / 100 = 0.99 元；0.99 × 1.00 = 0.99 → 向下取整 = 0
        // 整数 0 不应入账
        ProductPromoConfigDO config = ProductPromoConfigDO.builder()
                .spuId(100L).consumePointRatio(BigDecimal.ONE)
                .tuijianEnabled(false).poolEnabled(false).build();
        when(productPromoConfigService.getBySpuId(100L)).thenReturn(config);

        TradeOrderDO order = new TradeOrderDO().setId(1L).setUserId(2L);
        TradeOrderItemDO item = new TradeOrderItemDO().setSpuId(100L).setCount(1).setPayPrice(99);

        handler.afterPayOrder(order, Collections.singletonList(item));

        verify(promoPointService, never()).addConsumePoint(any(), anyLong(), any(), any(), any());
    }

}
