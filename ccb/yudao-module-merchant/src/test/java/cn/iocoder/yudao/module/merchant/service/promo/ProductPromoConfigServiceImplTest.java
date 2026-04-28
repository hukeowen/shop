package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.controller.admin.vo.promo.ProductPromoConfigSaveReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ProductPromoConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link ProductPromoConfigServiceImpl} 纯 Mockito 单元测试。
 *
 * 校验：
 *   1. 未配置 → 返"全关"默认值（不入库）
 *   2. 已配置 → 返库内值
 *   3. upsert：未存在→insert, 已存在→update（保留 id）
 *   4. mapBySpuIds：空入参返空 map；正常返 spuId→DO
 */
class ProductPromoConfigServiceImplTest {

    private ProductPromoConfigMapper mapper;
    private ProductPromoConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProductPromoConfigMapper.class);
        service = new ProductPromoConfigServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
    }

    @Test
    void getBySpuId_returnsDefault_whenNotExists() {
        when(mapper.selectBySpuId(99L)).thenReturn(null);

        ProductPromoConfigDO result = service.getBySpuId(99L);

        assertNotNull(result);
        assertEquals(99L, result.getSpuId());
        assertEquals(BigDecimal.ZERO, result.getConsumePointRatio());
        assertFalse(result.getTuijianEnabled());
        assertEquals(0, result.getTuijianN());
        assertEquals("[]", result.getTuijianRatios());
        assertFalse(result.getPoolEnabled());
        assertNull(result.getId(), "默认值不入库");
        verify(mapper, never()).insert(any(ProductPromoConfigDO.class));
    }

    @Test
    void getBySpuId_returnsExisting_whenPersisted() {
        ProductPromoConfigDO existing = ProductPromoConfigDO.builder()
                .spuId(123L)
                .consumePointRatio(new BigDecimal("2.00"))
                .tuijianEnabled(true)
                .tuijianN(4)
                .tuijianRatios("[25,25,25,25]")
                .poolEnabled(true)
                .build();
        existing.setId(11L);
        when(mapper.selectBySpuId(123L)).thenReturn(existing);

        ProductPromoConfigDO result = service.getBySpuId(123L);

        assertSame(existing, result);
        assertEquals(11L, result.getId());
    }

    @Test
    void save_inserts_whenNotExists() {
        when(mapper.selectBySpuId(7L)).thenReturn(null);

        ProductPromoConfigSaveReqVO req = buildReq(7L);
        service.save(req);

        ArgumentCaptor<ProductPromoConfigDO> captor = ArgumentCaptor.forClass(ProductPromoConfigDO.class);
        verify(mapper).insert(captor.capture());
        verify(mapper, never()).updateById(any(ProductPromoConfigDO.class));
        ProductPromoConfigDO inserted = captor.getValue();
        assertEquals(7L, inserted.getSpuId());
        assertTrue(inserted.getTuijianEnabled());
        assertEquals(4, inserted.getTuijianN());
        assertEquals("[25,25,25,25]", inserted.getTuijianRatios());
    }

    @Test
    void save_updates_whenExists() {
        ProductPromoConfigDO existing = ProductPromoConfigDO.builder().spuId(7L).build();
        existing.setId(33L);
        when(mapper.selectBySpuId(7L)).thenReturn(existing);

        ProductPromoConfigSaveReqVO req = buildReq(7L);
        service.save(req);

        verify(mapper, never()).insert(any(ProductPromoConfigDO.class));
        ArgumentCaptor<ProductPromoConfigDO> captor = ArgumentCaptor.forClass(ProductPromoConfigDO.class);
        verify(mapper).updateById(captor.capture());
        assertEquals(33L, captor.getValue().getId(), "更新必须保留 id");
        assertEquals(7L, captor.getValue().getSpuId());
    }

    @Test
    void mapBySpuIds_emptyInput_returnsEmpty() {
        Map<Long, ProductPromoConfigDO> resultNull = service.mapBySpuIds(null);
        Map<Long, ProductPromoConfigDO> resultEmpty = service.mapBySpuIds(Collections.emptyList());

        assertTrue(resultNull.isEmpty());
        assertTrue(resultEmpty.isEmpty());
        verify(mapper, never()).selectListBySpuIds(any());
    }

    @Test
    void mapBySpuIds_returnsKeyedMap() {
        ProductPromoConfigDO a = ProductPromoConfigDO.builder().spuId(1L).build();
        ProductPromoConfigDO b = ProductPromoConfigDO.builder().spuId(2L).build();
        List<Long> ids = Arrays.asList(1L, 2L, 3L);
        when(mapper.selectListBySpuIds(ids)).thenReturn(Arrays.asList(a, b));

        Map<Long, ProductPromoConfigDO> result = service.mapBySpuIds(ids);

        assertEquals(2, result.size());
        assertSame(a, result.get(1L));
        assertSame(b, result.get(2L));
        assertNull(result.get(3L), "未配置的商品不在 map 中");
    }

    private ProductPromoConfigSaveReqVO buildReq(Long spuId) {
        ProductPromoConfigSaveReqVO req = new ProductPromoConfigSaveReqVO();
        req.setSpuId(spuId);
        req.setConsumePointRatio(new BigDecimal("1.00"));
        req.setTuijianEnabled(true);
        req.setTuijianN(4);
        req.setTuijianRatios("[25,25,25,25]");
        req.setPoolEnabled(true);
        return req;
    }

}
