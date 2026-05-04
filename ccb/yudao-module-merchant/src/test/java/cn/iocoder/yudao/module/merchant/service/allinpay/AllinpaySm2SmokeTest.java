package cn.iocoder.yudao.module.merchant.service.allinpay;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 本地用通联给的 SM2 测试参数验证签名/验签链路自洽性。
 *
 * <p>不连通联网络；只验证：</p>
 * <ol>
 *   <li>用通联给的 PKCS8 SM2 私钥能否正确解析</li>
 *   <li>signSm2 + verifySm2 自签自验通过（说明 hutool SmUtil + ID + DER 编码都对齐）</li>
 *   <li>buildSignSource 字段排序符合通联示例</li>
 * </ol>
 */
class AllinpaySm2SmokeTest {

    /** docs/测试参数.txt 里通联给的 SM2 PKCS8 私钥（单行 base64） */
    private static final String SM2_PRIV =
            "MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgHyKo17p2KF0U6cj6GlQcorXoqCi72WMtbhEPZyy7Zwig" +
            "CgYIKoEcz1UBgi2hRANCAAT4f9rjq/efa14G66MhDe48RpEXXEeXP0hJce4tCHtra31ocFAsRDWNK8qMmISPFrdOlH+v" +
            "EdMW2e22xz+ir71X";

    /** 通联给的 SM2 X509 公钥（这份其实是商户**自己的**公钥，用于通联那边验我们的签——所以自签自验也用它） */
    private static final String SM2_PUB =
            "MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAE+H/a46v3n2teBuujIQ3uPEaRF1xHlz9ISXHuLQh7a2t9aHBQLEQ1jSvK" +
            "jJiEjxa3TpR/rxHTFtnttsc/oq+9Vw==";

    private static final String APPID = "00240592";
    private static final String CUSID = "56165105331VE5Z";

    @Test
    void signSm2_andSelfVerify_shouldPass() {
        // 1. 模拟收银台请求字段
        Map<String, String> p = new LinkedHashMap<>();
        p.put("cusid", CUSID);
        p.put("appid", APPID);
        p.put("version", "12");
        p.put("trxamt", "2990");
        p.put("reqsn", "ORDER_TEST_001");
        p.put("randomstr", "82712208");
        p.put("body", "AI视频套餐3条");
        p.put("signtype", "SM2");

        // 2. 签名
        String sign = AllinpayCashierService.signSm2(p, SM2_PRIV, APPID);
        System.out.println("[signSm2] sign(Base64) = " + sign);
        assertNotNull(sign, "签名结果不应为 null");
        assertFalse(sign.isEmpty(), "签名结果不应为空");
        // 验证 base64 合法性
        byte[] sigBytes = Base64.getDecoder().decode(sign);
        assertTrue(sigBytes.length >= 32, "DER 签名长度异常: " + sigBytes.length);

        // 3. 自验签：用同一组参数 + 同一份公钥应该 verify=true
        boolean ok = AllinpayCashierService.verifySm2(p, sign, SM2_PUB, APPID);
        assertTrue(ok, "自签自验失败 — 私钥 / 公钥 / userId 配对不上");
    }

    @Test
    void signSm2_userIdMustBeAppidNotDefault() {
        // 验证 userId=appid 与 userId=默认值 签出的结果**不一样**（确保 userId 真的进入了 SM3 哈希）
        Map<String, String> p = new LinkedHashMap<>();
        p.put("cusid", CUSID);
        p.put("appid", APPID);
        p.put("trxamt", "100");
        p.put("reqsn", "TEST");
        p.put("signtype", "SM2");

        String s1 = AllinpayCashierService.signSm2(p, SM2_PRIV, APPID);
        String s2 = AllinpayCashierService.signSm2(p, SM2_PRIV, "1234567812345678");
        // 因为 SM2 签名带随机数 k，每次签名结果都不同，相同 userId 也会不同 —— 但能验证才是关键
        boolean v1 = AllinpayCashierService.verifySm2(p, s1, SM2_PUB, APPID);
        boolean v2_wrongUserId = AllinpayCashierService.verifySm2(p, s1, SM2_PUB, "1234567812345678");
        assertTrue(v1, "appid 当 userId 时自验应通过");
        assertFalse(v2_wrongUserId, "用错 userId 验签应该失败");
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
