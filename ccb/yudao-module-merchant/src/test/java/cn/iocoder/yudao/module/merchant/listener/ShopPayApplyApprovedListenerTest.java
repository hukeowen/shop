package cn.iocoder.yudao.module.merchant.listener;

import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.event.ShopPayApplyApprovedEvent;
import cn.iocoder.yudao.module.merchant.service.allinpay.AllinpayMerchantClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link ShopPayApplyApprovedListener} — 通联开户回写状态机
 *
 * 覆盖：
 *   - 店铺不存在 → 跳过不抛
 *   - 已开通 (已有 tlMchId) → 跳过避免重复发起
 *   - 通联同步 APPROVED → 状态 2 + tlMchId/tlMchKey 加密写库
 *   - 通联同步 REJECTED → 状态 3 + rejectReason
 *   - 通联异步 PENDING → 状态 4 (通联进件中)，等 webhook
 *   - openMerchant 抛异常 → log，不破坏主事务
 */
class ShopPayApplyApprovedListenerTest {

    private AllinpayMerchantClient allinpayClient;
    private ShopInfoMapper shopInfoMapper;
    private ShopPayApplyApprovedListener listener;

    @BeforeEach
    void setUp() {
        allinpayClient = mock(AllinpayMerchantClient.class);
        shopInfoMapper = mock(ShopInfoMapper.class);
        listener = new ShopPayApplyApprovedListener();
        ReflectionTestUtils.setField(listener, "allinpayClient", allinpayClient);
        ReflectionTestUtils.setField(listener, "shopInfoMapper", shopInfoMapper);
        // AES 必须 16/24/32 字节；测试用 16 字节 dev key
        ReflectionTestUtils.setField(listener, "fieldEncryptKey", "dev_key_12345678");
    }

    @Test
    void onShopPayApplyApproved_shopMissing_skipsSilently() {
        when(shopInfoMapper.selectById(99L)).thenReturn(null);

        listener.onShopPayApplyApproved(new ShopPayApplyApprovedEvent(this, 99L, 1L));

        verify(allinpayClient, never()).openMerchant(any());
        verify(shopInfoMapper, never()).updateById(any(ShopInfoDO.class));
    }

    @Test
    void onShopPayApplyApproved_alreadyOpened_skipsToAvoidDuplicate() {
        ShopInfoDO existing = new ShopInfoDO();
        existing.setId(1L);
        existing.setTlOpenOrderId("TX-EXISTING");
        existing.setTlMchId("TLREAL12345");
        when(shopInfoMapper.selectById(1L)).thenReturn(existing);

        listener.onShopPayApplyApproved(new ShopPayApplyApprovedEvent(this, 1L, 1L));

        verify(allinpayClient, never()).openMerchant(any());
        verify(shopInfoMapper, never()).updateById(any(ShopInfoDO.class));
    }

    @Test
    void onShopPayApplyApproved_syncApproved_writesMchIdAndStatus2() {
        ShopInfoDO existing = new ShopInfoDO();
        existing.setId(2L);
        when(shopInfoMapper.selectById(2L)).thenReturn(existing);

        AllinpayMerchantClient.OpenMerchantResult ok = new AllinpayMerchantClient.OpenMerchantResult();
        ok.setOutOrderId("TX-SYNC-OK");
        ok.setStatus(AllinpayMerchantClient.OpenMerchantResult.Status.APPROVED);
        ok.setTlMchId("TLREAL2024");
        ok.setTlMchKey("plain-secret-key");
        when(allinpayClient.openMerchant(existing)).thenReturn(ok);

        listener.onShopPayApplyApproved(new ShopPayApplyApprovedEvent(this, 2L, 1L));

        ArgumentCaptor<ShopInfoDO> cap = ArgumentCaptor.forClass(ShopInfoDO.class);
        verify(shopInfoMapper).updateById(cap.capture());
        ShopInfoDO update = cap.getValue();
        assertEquals(2L, update.getId());
        assertEquals(2, update.getPayApplyStatus());
        assertEquals("TLREAL2024", update.getTlMchId());
        assertEquals("TX-SYNC-OK", update.getTlOpenOrderId());
        assertNotNull(update.getTlMchKey(), "tlMchKey 必须 AES 加密后入库");
        assertNotEquals("plain-secret-key", update.getTlMchKey(),
                "tlMchKey 不能明文入库");
    }

    @Test
    void onShopPayApplyApproved_syncRejected_writesStatus3WithReason() {
        ShopInfoDO existing = new ShopInfoDO();
        existing.setId(3L);
        when(shopInfoMapper.selectById(3L)).thenReturn(existing);

        AllinpayMerchantClient.OpenMerchantResult rej = new AllinpayMerchantClient.OpenMerchantResult();
        rej.setOutOrderId("TX-SYNC-REJ");
        rej.setStatus(AllinpayMerchantClient.OpenMerchantResult.Status.REJECTED);
        rej.setRejectReason("法人身份证模糊");
        when(allinpayClient.openMerchant(existing)).thenReturn(rej);

        listener.onShopPayApplyApproved(new ShopPayApplyApprovedEvent(this, 3L, 1L));

        ArgumentCaptor<ShopInfoDO> cap = ArgumentCaptor.forClass(ShopInfoDO.class);
        verify(shopInfoMapper).updateById(cap.capture());
        ShopInfoDO update = cap.getValue();
        assertEquals(3, update.getPayApplyStatus());
        assertTrue(update.getPayApplyRejectReason().contains("法人身份证模糊"));
        assertNull(update.getTlMchId());
    }

    @Test
    void onShopPayApplyApproved_async_writesStatus4_pendingWebhook() {
        ShopInfoDO existing = new ShopInfoDO();
        existing.setId(4L);
        when(shopInfoMapper.selectById(4L)).thenReturn(existing);

        AllinpayMerchantClient.OpenMerchantResult pending = new AllinpayMerchantClient.OpenMerchantResult();
        pending.setOutOrderId("TX-PENDING");
        pending.setStatus(AllinpayMerchantClient.OpenMerchantResult.Status.PENDING);
        when(allinpayClient.openMerchant(existing)).thenReturn(pending);

        listener.onShopPayApplyApproved(new ShopPayApplyApprovedEvent(this, 4L, 1L));

        ArgumentCaptor<ShopInfoDO> cap = ArgumentCaptor.forClass(ShopInfoDO.class);
        verify(shopInfoMapper).updateById(cap.capture());
        ShopInfoDO update = cap.getValue();
        assertEquals(4, update.getPayApplyStatus(), "PENDING → 状态 4 = 通联进件中");
        assertEquals("TX-PENDING", update.getTlOpenOrderId());
        assertNull(update.getTlMchId());
    }

    @Test
    void onShopPayApplyApproved_clientThrows_doesNotPropagateNorUpdate() {
        ShopInfoDO existing = new ShopInfoDO();
        existing.setId(5L);
        when(shopInfoMapper.selectById(5L)).thenReturn(existing);
        when(allinpayClient.openMerchant(existing)).thenThrow(new RuntimeException("通联超时"));

        // 不抛异常（保留状态 2 已开通；运维事后重试或定时任务补救）
        assertDoesNotThrow(() -> listener.onShopPayApplyApproved(
                new ShopPayApplyApprovedEvent(this, 5L, 1L)));

        verify(shopInfoMapper, never()).updateById(any(ShopInfoDO.class));
    }
}
