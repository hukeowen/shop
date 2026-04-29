package cn.iocoder.yudao.module.merchant.service.allinpay;

import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AllinpayMerchantClientNoop} — enabled=false 时的兜底行为
 *
 * 验证：未真实调通联接口的情况下，业务流转仍能继续 (返 PENDING 状态)
 */
class AllinpayMerchantClientNoopTest {

    private final AllinpayMerchantClientNoop noop = new AllinpayMerchantClientNoop();

    @Test
    void openMerchant_returnsPendingWithNoopOrderId() {
        ShopInfoDO shop = new ShopInfoDO();
        shop.setId(123L);
        shop.setShopName("某测试摊");

        AllinpayMerchantClient.OpenMerchantResult r = noop.openMerchant(shop);

        assertNotNull(r);
        assertEquals(AllinpayMerchantClient.OpenMerchantResult.Status.PENDING, r.getStatus());
        assertNotNull(r.getOutOrderId());
        assertTrue(r.getOutOrderId().startsWith("NOOP-"),
                "noop 实现应返 NOOP- 前缀的 outOrderId，便于排查");
        assertNull(r.getTlMchId(), "noop 不分配真 mchId");
        assertNull(r.getTlMchKey(), "noop 不分配真 mchKey");
        assertEquals("通联进件未启用", r.getRejectReason());
    }

    @Test
    void queryMerchantStatus_returnsPending() {
        AllinpayMerchantClient.OpenMerchantResult r = noop.queryMerchantStatus("TX1700000000000");
        assertEquals(AllinpayMerchantClient.OpenMerchantResult.Status.PENDING, r.getStatus());
        assertEquals("TX1700000000000", r.getOutOrderId());
    }
}
