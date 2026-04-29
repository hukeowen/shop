package cn.iocoder.yudao.module.merchant.service.allinpay;

import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 通联进件兜底实现：merchant.allinpay.enabled=false (默认) 时启用
 *
 * <p>仅 log 不调真接口；返 PENDING 状态保持业务流转。生产前必须通过设
 * {@code merchant.allinpay.enabled=true} + 配齐 RSA 密钥/orgId 切到真实现。</p>
 */
@Service
@ConditionalOnProperty(prefix = "merchant.allinpay", name = "enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class AllinpayMerchantClientNoop implements AllinpayMerchantClient {

    @Override
    public OpenMerchantResult openMerchant(ShopInfoDO shop) {
        log.warn("[allinpay-noop] 通联进件未启用 — shop {} 提交申请被忽略；如需真实开户请配 merchant.allinpay.enabled=true",
                shop.getId());
        OpenMerchantResult r = new OpenMerchantResult();
        r.setOutOrderId("NOOP-" + System.currentTimeMillis());
        r.setStatus(OpenMerchantResult.Status.PENDING);
        r.setRejectReason("通联进件未启用");
        return r;
    }

    @Override
    public OpenMerchantResult queryMerchantStatus(String outOrderId) {
        log.warn("[allinpay-noop] 通联进件未启用 — 查询 {} 返 PENDING", outOrderId);
        OpenMerchantResult r = new OpenMerchantResult();
        r.setOutOrderId(outOrderId);
        r.setStatus(OpenMerchantResult.Status.PENDING);
        return r;
    }
}
