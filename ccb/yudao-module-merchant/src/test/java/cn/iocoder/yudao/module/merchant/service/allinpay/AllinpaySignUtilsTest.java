package cn.iocoder.yudao.module.merchant.service.allinpay;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AllinpaySignUtilsTest {

    @Test
    void buildSignSource_skipsEmptyAndSign_sortsByKey() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("z", "1");
        p.put("a", "2");
        p.put("m", "");          // 空值跳过
        p.put("sign", "abc");    // sign 字段跳过
        p.put("k", null);        // null 跳过

        String src = AllinpaySignUtils.buildSignSource(p);
        assertEquals("a=2&z=1", src);
    }

    @Test
    void signRequest_then_verifyNotify_roundTrip() throws Exception {
        // 用 JDK 现场生成 RSA 2048 密钥对模拟"平台私钥 / 通联公钥"
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        String privPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        String pubPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(kp.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";

        Map<String, String> params = new HashMap<>();
        params.put("orgid", "ORG12345");
        params.put("outorderid", "TX1700000000000");
        params.put("mchntname", "测试摊");
        params.put("amount", "10000");

        String sign = AllinpaySignUtils.signRequest(params, privPem);
        assertNotNull(sign);
        assertFalse(sign.isEmpty());

        // 验签：模拟回调（params 不含 sign 字段）
        assertTrue(AllinpaySignUtils.verifyNotify(params, sign, pubPem),
                "同一参数集 + 平台私钥签 + 通联公钥验，必须通过");

        // 篡改任一字段 → 验签失败
        params.put("amount", "99999");
        assertFalse(AllinpaySignUtils.verifyNotify(params, sign, pubPem),
                "篡改 amount 后验签必须失败");
    }

    @Test
    void signRequest_withoutPrivateKey_throws() {
        Map<String, String> p = new HashMap<>();
        p.put("k", "v");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> AllinpaySignUtils.signRequest(p, ""));
        assertTrue(ex.getMessage().contains("通联平台私钥未配置"));
    }

    @Test
    void verifyNotify_withMissingSign_returnsFalse() {
        Map<String, String> p = new HashMap<>();
        p.put("k", "v");
        assertFalse(AllinpaySignUtils.verifyNotify(p, "", "fake-pub-key"));
        assertFalse(AllinpaySignUtils.verifyNotify(p, null, "fake-pub-key"));
    }
}
