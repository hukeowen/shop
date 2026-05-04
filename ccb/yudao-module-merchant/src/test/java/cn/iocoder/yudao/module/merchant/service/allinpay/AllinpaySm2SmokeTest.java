package cn.iocoder.yudao.module.merchant.service.allinpay;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 离线 SM2 签名链路自洽性测试 — 不联通联，不依赖生产密钥。
 *
 * <p>M4 修复：用运行时生成的临时 SM2 keypair 做自签自验，
 * 不再 hardcode 生产私钥到代码里。</p>
 *
 * <p>验证三件事：</p>
 * <ol>
 *   <li>签名 + 验签自洽（算法层正确，PLAIN 编码 + userId 进 SM3 哈希）</li>
 *   <li>userId 错配 → 验签失败（证明 userId 真的进了哈希）</li>
 *   <li>buildSignSource 字段排序与通联文档示例一致</li>
 * </ol>
 */
class AllinpaySm2SmokeTest {

    private static String SM2_PRIV;
    private static String SM2_PUB;
    private static final String APPID = "TESTAPPID";
    private static final String CUSID = "TESTCUSID";

    @BeforeAll
    static void genKeypair() {
        // 静态块加载 BC provider（AllinpayCashierService 静态块也加载）
        if (java.security.Security.getProvider("BC") == null) {
            java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }
        try {
            java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("EC", "BC");
            org.bouncycastle.jce.spec.ECParameterSpec sm2Spec =
                    new org.bouncycastle.jce.spec.ECParameterSpec(
                            org.bouncycastle.asn1.gm.GMNamedCurves.getByName("sm2p256v1").getCurve(),
                            org.bouncycastle.asn1.gm.GMNamedCurves.getByName("sm2p256v1").getG(),
                            org.bouncycastle.asn1.gm.GMNamedCurves.getByName("sm2p256v1").getN(),
                            org.bouncycastle.asn1.gm.GMNamedCurves.getByName("sm2p256v1").getH());
            kpg.initialize(sm2Spec);
            java.security.KeyPair kp = kpg.generateKeyPair();
            SM2_PRIV = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
            SM2_PUB  = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void signSm2_andSelfVerify_shouldPass() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("cusid", CUSID);
        p.put("appid", APPID);
        p.put("version", "12");
        p.put("trxamt", "2990");
        p.put("reqsn", "ORDER_TEST_001");
        p.put("randomstr", "82712208");
        p.put("body", "AI视频套餐3条");
        p.put("signtype", "SM2");

        String sign = AllinpayCashierService.signSm2(p, SM2_PRIV, APPID);
        assertNotNull(sign);
        assertFalse(sign.isEmpty());
        byte[] sigBytes = Base64.getDecoder().decode(sign);
        assertTrue(sigBytes.length >= 32, "PLAIN R||S 64 字节，DER 略大");

        boolean ok = AllinpayCashierService.verifySm2(p, sign, SM2_PUB, APPID);
        assertTrue(ok, "自签自验失败 — 私钥/公钥/userId 不匹配");
    }

    @Test
    void signSm2_userIdMustMatchOnVerify() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("cusid", CUSID);
        p.put("appid", APPID);
        p.put("trxamt", "100");
        p.put("reqsn", "TEST");
        p.put("signtype", "SM2");

        String sign = AllinpayCashierService.signSm2(p, SM2_PRIV, APPID);
        assertTrue(AllinpayCashierService.verifySm2(p, sign, SM2_PUB, APPID),
                "appid 当 userId 时自验应通过");
        assertFalse(AllinpayCashierService.verifySm2(p, sign, SM2_PUB, "WRONG"),
                "用错 userId 验签应该失败 — userId 必须真的参与 SM3");
    }

    @Test
    void buildSignSource_matchesAllinpayExample() {
        // 文档示例：
        // appid=00000051&cusid=990581007426001&randomstr=82712208&signtype=RSA&trxid=112094120001088317&version=11
        Map<String, String> p = new LinkedHashMap<>();
        p.put("cusid", "990581007426001");
        p.put("trxid", "112094120001088317");
        p.put("version", "11");
        p.put("appid", "00000051");
        p.put("randomstr", "82712208");
        p.put("signtype", "RSA");
        p.put("sign", "should_be_excluded");
        p.put("emptyField", "");

        String src = invokeBuildSignSource(p);
        assertEquals(
                "appid=00000051&cusid=990581007426001&randomstr=82712208&signtype=RSA&trxid=112094120001088317&version=11",
                src);
    }

    /** 反射调 private buildSignSource */
    private static String invokeBuildSignSource(Map<String, String> params) {
        try {
            java.lang.reflect.Method m = AllinpayCashierService.class
                    .getDeclaredMethod("buildSignSource", Map.class);
            m.setAccessible(true);
            return (String) m.invoke(null, params);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
