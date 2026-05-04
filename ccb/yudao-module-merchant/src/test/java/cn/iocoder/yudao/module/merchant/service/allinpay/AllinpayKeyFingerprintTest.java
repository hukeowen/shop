package cn.iocoder.yudao.module.merchant.service.allinpay;

import org.junit.jupiter.api.Test;

/** 打印本地 SM2 私钥的 fingerprint，用来跟生产 /diag 端点的 sm2PrivKeyFp 对照。 */
class AllinpayKeyFingerprintTest {

    private static final String SM2_PRIV =
            "MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgHyKo17p2KF0U6cj6GlQcorXoqCi72WMtbhEPZyy7Zwig" +
            "CgYIKoEcz1UBgi2hRANCAAT4f9rjq/efa14G66MhDe48RpEXXEeXP0hJce4tCHtra31ocFAsRDWNK8qMmISPFrdOlH+v" +
            "EdMW2e22xz+ir71X";

    @Test
    void printLocalFingerprint() {
        String fp = AllinpayCashierService.keyFingerprint(SM2_PRIV);
        System.out.println("===== 本地 SM2 私钥 fingerprint =====");
        System.out.println(fp);
        System.out.println("=====================================");
        System.out.println("生产侧请访问 /admin-api/merchant/allinpay/diag 看 sm2PrivKeyFp");
        System.out.println("两者一致 = 同一份私钥；不一致 = systemd ENV 加载时被截断/换行");
    }
}
