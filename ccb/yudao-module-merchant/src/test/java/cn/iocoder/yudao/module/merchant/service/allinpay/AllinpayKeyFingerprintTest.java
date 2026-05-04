package cn.iocoder.yudao.module.merchant.service.allinpay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 打印 ENV 中 SM2 私钥的 fingerprint，跟生产 /diag 端点的 sm2PrivKeyFp 对照。
 *
 * <p>M4 修复：不再 hardcode 私钥到代码里；通过 ENV {@code ALLINPAY_SM2_PRIVATE_KEY}
 * 注入。git 历史里以前的副本要后续 filter-repo 清理 + 通联控制台轮换密钥。</p>
 */
@EnabledIfEnvironmentVariable(named = "ALLINPAY_SM2_PRIVATE_KEY", matches = ".+")
class AllinpayKeyFingerprintTest {

    @Test
    void printLocalFingerprint() {
        String key = System.getenv("ALLINPAY_SM2_PRIVATE_KEY");
        String fp = AllinpayCashierService.keyFingerprint(key);
        System.out.println("===== 本地 SM2 私钥 fingerprint =====");
        System.out.println(fp);
        System.out.println("=====================================");
        System.out.println("生产侧 GET /admin-api/merchant/allinpay/diag?token=<TOKEN> 看 sm2PrivKeyFp");
        System.out.println("两者一致 = 同一份密钥；不一致 = systemd ENV 加载时被截断/换行");
    }
}
