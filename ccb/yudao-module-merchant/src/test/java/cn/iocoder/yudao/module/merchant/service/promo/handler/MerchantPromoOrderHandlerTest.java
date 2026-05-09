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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link MerchantPromoOrderHandler} v8 编排测试。
 *
 * 验证：每个 SPU 行独立调用一次：
 *   消费积分 / queue.previewProduced + handleOrderPaidV8 / commission.V8 / pool.V8 / star.V8
 *
 * 不验算法（已在各 ServiceImpl 测试覆盖）。
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
    void afterPayOrder_invokesAllV8Pipelines() {
        ProductPromoConfigDO config = ProductPromoConfigDO.builder()
                .spuId(100L)
                .consumePointRatio(new BigDecimal("1.00"))
                .tuijianEnabled(true).tuijianN(3).tuijianRatios("[30,30,40]")
                .poolEnabled(true)
                .build();
        when(productPromoConfigService.getBySpuId(100L)).thenReturn(config);
        // preview 返回 0（不抵扣）—— K = 0
        when(promoQueueService.previewProducedForOrder(eq(config), eq(7L), eq(100L), eq(10000), eq(2)))
                .thenReturn(0L);

        TradeOrderDO order = new TradeOrderDO().setId(999L).setUserId(7L);
        TradeOrderItemDO item = new TradeOrderItemDO()
                .setSpuId(100L).setCount(2).setPrice(10000).setPayPrice(20000);  // 单价 100 元 × 2 件 = 200 元

        handler.afterPayOrder(order, Collections.singletonList(item));

        // 1. 消费积分：200 元 × 1.00 = 200 分
        verify(promoPointService).addConsumePoint(eq(7L), eq(200L), eq("CONSUME"), eq(999L), any());
        // 2. v8 队列：先 preview 后 handleOrderPaidV8
        verify(promoQueueService).previewProducedForOrder(eq(config), eq(7L), eq(100L), eq(10000), eq(2));
        verify(promoQueueService).handleOrderPaidV8(eq(config), eq(7L), eq(100L), eq(10000), eq(2), eq(0), eq(999L));
        // 3. v8 极差（按 SPU 行 paidAmount）
        verify(commissionService).handleOrderPaidV8(eq(config), eq(7L), eq(100L), eq(20000L), eq(999L));
        // 4. v8 入池（按 SPU 行 paidAmount + spuId）
        verify(promoPoolService).depositIfEnabledV8(eq(config), eq(100L), eq(20000L), eq(999L));
        // 5. v8 升星（按 SPU + qty + paidAmount）
        verify(starService).handleOrderPaidV8(eq(config), eq(7L), eq(100L), eq(2), eq(20000L));
    }

    @Test
    void afterPayOrder_skipsQueue_whenTuijianDisabled() {
        ProductPromoConfigDO config = ProductPromoConfigDO.builder()
                .spuId(100L)
                .consumePointRatio(BigDecimal.ZERO)
                .tuijianEnabled(false)
                .poolEnabled(false)
                .build();
        when(productPromoConfigService.getBySpuId(100L)).thenReturn(config);

        TradeOrderDO order = new TradeOrderDO().setId(999L).setUserId(7L);
        TradeOrderItemDO item = new TradeOrderItemDO().setSpuId(100L).setCount(1).setPrice(10000).setPayPrice(10000);

        handler.afterPayOrder(order, Collections.singletonList(item));

        // tuijianEnabled=false → 不调 queue 和 star
        verify(promoQueueService, never()).previewProducedForOrder(any(), any(), any(), anyInt(), anyInt());
        verify(promoQueueService, never()).handleOrderPaidV8(any(), any(), any(), anyInt(), anyInt(), anyInt(), any());
        verify(starService, never()).handleOrderPaidV8(any(), any(), any(), anyInt(), anyLong());
        // 极差和入池仍调用（按商品级 starCount/poolRatio 在 service 内部判断）
        verify(commissionService).handleOrderPaidV8(eq(config), eq(7L), eq(100L), eq(10000L), eq(999L));
        verify(promoPoolService).depositIfEnabledV8(eq(config), eq(100L), eq(10000L), eq(999L));
        // 消费积分 ratio=0 不入账
        verify(promoPointService, never()).addConsumePoint(any(), anyLong(), any(), any(), any());
    }

    @Test
    void afterPayOrder_multiSpu_eachSpuIndependentlyTriggered() {
        // 同订单 2 个 SPU：A 100元 / B 250元，分别独立触发 v8 各 step
        ProductPromoConfigDO cfgA = ProductPromoConfigDO.builder().spuId(100L)
                .consumePointRatio(BigDecimal.ZERO).tuijianEnabled(false).poolEnabled(false).build();
        ProductPromoConfigDO cfgB = ProductPromoConfigDO.builder().spuId(200L)
                .consumePointRatio(BigDecimal.ZERO).tuijianEnabled(false).poolEnabled(false).build();
        when(productPromoConfigService.getBySpuId(100L)).thenReturn(cfgA);
        when(productPromoConfigService.getBySpuId(200L)).thenReturn(cfgB);

        TradeOrderDO order = new TradeOrderDO().setId(999L).setUserId(7L);
        TradeOrderItemDO i1 = new TradeOrderItemDO().setSpuId(100L).setCount(1).setPrice(10000).setPayPrice(10000);
        TradeOrderItemDO i2 = new TradeOrderItemDO().setSpuId(200L).setCount(1).setPrice(25000).setPayPrice(25000);

        handler.afterPayOrder(order, Arrays.asList(i1, i2));

        // 每个 SPU 各 1 次极差 / 入池，金额按各自行 paidAmount
        verify(commissionService).handleOrderPaidV8(eq(cfgA), eq(7L), eq(100L), eq(10000L), eq(999L));
        verify(commissionService).handleOrderPaidV8(eq(cfgB), eq(7L), eq(200L), eq(25000L), eq(999L));
        verify(promoPoolService).depositIfEnabledV8(eq(cfgA), eq(100L), eq(10000L), eq(999L));
        verify(promoPoolService).depositIfEnabledV8(eq(cfgB), eq(200L), eq(25000L), eq(999L));
    }

    @Test
    void afterPayOrder_oneItemException_doesNotBreakOthers() {
        TradeOrderItemDO item1 = new TradeOrderItemDO().setSpuId(100L).setCount(1).setPrice(10000).setPayPrice(10000);
        TradeOrderItemDO item2 = new TradeOrderItemDO().setSpuId(200L).setCount(1).setPrice(20000).setPayPrice(20000);

        when(productPromoConfigService.getBySpuId(100L))
                .thenThrow(new RuntimeException("配置查询炸了"));
        ProductPromoConfigDO config2 = ProductPromoConfigDO.builder()
                .spuId(200L).consumePointRatio(BigDecimal.ZERO)
                .tuijianEnabled(false).poolEnabled(false).build();
        when(productPromoConfigService.getBySpuId(200L)).thenReturn(config2);

        TradeOrderDO order = new TradeOrderDO().setId(999L).setUserId(7L);

        // 不应抛
        handler.afterPayOrder(order, Arrays.asList(item1, item2));

        // item2 仍被处理（极差 + 入池仍调用）
        verify(commissionService).handleOrderPaidV8(eq(config2), eq(7L), eq(200L), eq(20000L), eq(999L));
        verify(promoPoolService).depositIfEnabledV8(eq(config2), eq(200L), eq(20000L), eq(999L));
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
        TradeOrderItemDO item = new TradeOrderItemDO().setSpuId(null).setCount(1).setPrice(10000).setPayPrice(10000);
        TradeOrderDO order = new TradeOrderDO().setId(1L).setUserId(2L);

        handler.afterPayOrder(order, Collections.singletonList(item));

        verifyNoInteractions(productPromoConfigService);
        verifyNoInteractions(promoQueueService);
    }

    @Test
    void afterPayOrder_consumePoints_truncatesDownToInteger() {
        // paidAmount = 99 分；99 / 100 = 0.99 元；0.99 × 1.00 = 0.99 → 向下取整 = 0
        // 整数 0 不应入账
        ProductPromoConfigDO config = ProductPromoConfigDO.builder()
                .spuId(100L).consumePointRatio(BigDecimal.ONE)
                .tuijianEnabled(false).poolEnabled(false).build();
        when(productPromoConfigService.getBySpuId(100L)).thenReturn(config);

        TradeOrderDO order = new TradeOrderDO().setId(1L).setUserId(2L);
        TradeOrderItemDO item = new TradeOrderItemDO().setSpuId(100L).setCount(1).setPrice(99).setPayPrice(99);

        handler.afterPayOrder(order, Collections.singletonList(item));

        verify(promoPointService, never()).addConsumePoint(any(), anyLong(), any(), any(), any());
    }

    @Test
    void afterPayOrder_deductionApplied_whenPreviewExceedsUnitPrice() {
        // preview produced = 25000 分（= 单价 10000 × 2.5）→ K = floor(25000/10000) = 2 件
        // 但 totalCount=2，因此 K = min(2, 2) = 2 件全抵扣
        ProductPromoConfigDO config = ProductPromoConfigDO.builder()
                .spuId(100L)
                .consumePointRatio(BigDecimal.ZERO)
                .tuijianEnabled(true).tuijianN(3).tuijianRatios("[30,30,40]")
                .poolEnabled(false)
                .build();
        when(productPromoConfigService.getBySpuId(100L)).thenReturn(config);
        when(promoQueueService.previewProducedForOrder(eq(config), eq(7L), eq(100L), eq(10000), eq(2)))
                .thenReturn(25000L);

        TradeOrderDO order = new TradeOrderDO().setId(999L).setUserId(7L);
        TradeOrderItemDO item = new TradeOrderItemDO()
                .setSpuId(100L).setCount(2).setPrice(10000).setPayPrice(20000);

        handler.afterPayOrder(order, Collections.singletonList(item));

        // K = 2（被 totalCount cap），传给 handleOrderPaidV8
        verify(promoQueueService).handleOrderPaidV8(eq(config), eq(7L), eq(100L), eq(10000), eq(2), eq(2), eq(999L));
    }

}
