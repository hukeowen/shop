package cn.iocoder.yudao.module.merchant.service.allinpay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 实际打到通联生产服务器的 SM2 签名连通性验证。
 *
 * <p>不依赖通联返回真订单数据 — 用一个不存在的 reqsn 触发 "查无此单"，
 * 但 sign 必须先通过通联验签才会走到那一步：</p>
 * <ul>
 *   <li>retcode=FAIL + retmsg 含 "签名" / "sign" → SM2 签名实现不对</li>
 *   <li>retcode=FAIL + retmsg 含 "订单不存在" / "未查询到" → 签名通过，仅订单不存在 ✓</li>
 *   <li>retcode=SUCCESS → 居然有这个订单（不太可能）</li>
 * </ul>
 *
 * <p>仅当 ENV {@code ALLINPAY_LIVE_TEST=1} 时才跑（避免 CI 误打外网）。</p>
 */
@EnabledIfEnvironmentVariable(named = "ALLINPAY_LIVE_TEST", matches = "1")
class AllinpayLiveQueryTest {

    private static final String SM2_PRIV =
            "MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgHyKo17p2KF0U6cj6GlQcorXoqCi72WMtbhEPZyy7Zwig" +
            "CgYIKoEcz1UBgi2hRANCAAT4f9rjq/efa14G66MhDe48RpEXXEeXP0hJce4tCHtra31ocFAsRDWNK8qMmISPFrdOlH+v" +
            "EdMW2e22xz+ir71X";
    private static final String APPID = "00240592";
    private static final String CUSID = "56165105331VE5Z";
    private static final String QUERY_URL = "https://vsp.allinpay.com/apiweb/tranx/query";

    @Test
    void liveQuery_withAppidAsUserId() throws Exception {
        run("appid as userId", APPID);
    }

    @Test
    void liveQuery_withCusidAsUserId() throws Exception {
        run("cusid as userId", CUSID);
    }

    @Test
    void liveQuery_withDefaultSm2Id() throws Exception {
        run("SM2 default id 1234567812345678", "1234567812345678");
    }

    @Test
    void liveQuery_plainEncoding_appid() throws Exception {
        runPlain("PLAIN encoding + appid", APPID);
    }

    @Test
    void liveQuery_plainEncoding_default() throws Exception {
        runPlain("PLAIN encoding + default id", "1234567812345678");
    }

    /** 用 hutool SM2 的 usePlainEncoding（输出 R||S 64 bytes → base64 88 字符）签 */
    private void runPlain(String label, String userId) throws Exception {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("cusid", CUSID);
        p.put("appid", APPID);
        p.put("reqsn", "NONEXIST_TEST_" + UUID.randomUUID().toString().substring(0, 8));
        p.put("randomstr", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        p.put("signtype", "SM2");

        // 复制 buildSignSource 逻辑（私有方法不便反射）
        java.util.TreeMap<String, String> sorted = new java.util.TreeMap<>(p);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if ("sign".equalsIgnoreCase(e.getKey())) continue;
            String v = e.getValue();
            if (v == null || v.isEmpty()) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(e.getKey()).append('=').append(v);
        }
        String source = sb.toString();
        cn.hutool.crypto.asymmetric.SM2 sm2 = cn.hutool.crypto.SmUtil.sm2(SM2_PRIV, null);
        sm2.usePlainEncoding();   // ← 关键：plain 模式
        byte[] sig = sm2.sign(source.getBytes(StandardCharsets.UTF_8),
                userId.getBytes(StandardCharsets.UTF_8));
        String sign = java.util.Base64.getEncoder().encodeToString(sig);
        p.put("sign", sign);

        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=')
                .append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        HttpURLConnection con = (HttpURLConnection) new URL(QUERY_URL).openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        con.setConnectTimeout(8_000);
        con.setReadTimeout(15_000);
        try (OutputStream os = con.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = con.getResponseCode();
        java.io.InputStream is = (code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream();
        String resp = is == null ? "" : new String(readAll(is), StandardCharsets.UTF_8);
        System.out.println("==== " + label + " (sign=" + sign + ") ====");
        System.out.println("HTTP " + code);
        System.out.println(resp);
        System.out.println();
    }

    private void run(String label, String userId) throws Exception {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("cusid", CUSID);
        p.put("appid", APPID);
        p.put("reqsn", "NONEXIST_TEST_" + UUID.randomUUID().toString().substring(0, 8));
        p.put("randomstr", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        p.put("signtype", "SM2");
        String sign = AllinpayCashierService.signSm2(p, SM2_PRIV, userId);
        p.put("sign", sign);

        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=')
                .append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }

        HttpURLConnection con = (HttpURLConnection) new URL(QUERY_URL).openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        con.setConnectTimeout(8_000);
        con.setReadTimeout(15_000);
        try (OutputStream os = con.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = con.getResponseCode();
        java.io.InputStream is = (code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream();
        String resp = is == null ? "" : new String(readAll(is), StandardCharsets.UTF_8);
        System.out.println("==== " + label + " ====");
        System.out.println("HTTP " + code);
        System.out.println(resp);
        System.out.println();
    }

    private static byte[] readAll(java.io.InputStream is) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
