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

    // M4 修复：从 ENV 读私钥/商户号，不再 hardcode
    // 跑前须 export ALLINPAY_SM2_PRIVATE_KEY=... + ALLINPAY_APPID=... + ALLINPAY_MERCHANT_NO=...
    private static final String SM2_PRIV = envOrSkip("ALLINPAY_SM2_PRIVATE_KEY");
    private static final String APPID = envOrSkip("ALLINPAY_APPID");
    private static final String CUSID = envOrSkip("ALLINPAY_MERCHANT_NO");

    private static String envOrSkip(String name) {
        String v = System.getenv(name);
        if (v == null || v.isEmpty()) {
            throw new org.opentest4j.TestAbortedException("ENV 未设：" + name);
        }
        return v;
    }
    private static final String QUERY_URL = "https://vsp.allinpay.com/apiweb/tranx/query";
    private static final String UNION_URL = "https://syb.allinpay.com/apiweb/h5unionpay/unionorder";
    private static final String ONEPAY_URL = "https://syb.allinpay.com/apiweb/h5unionpay/onepay";

    /** 实测聚合收银台 onepay：用户在通联页面选择 微信/支付宝/云闪付/快捷 */
    @Test
    void liveOnepay_aggregateCashier() throws Exception {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("cusid", CUSID);
        p.put("appid", APPID);
        p.put("version", "12");
        p.put("trxamt", "100");
        p.put("reqsn", "ONEPAY_" + System.currentTimeMillis());
        p.put("randomstr", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        p.put("body", "AI视频套餐测试");
        p.put("front_url", "https://www.doupaidoudian.com/m/quota?paid=1");
        p.put("notify_url", "https://www.doupaidoudian.com/admin-api/merchant/allinpay/pay-notify");
        // onepay 必填：订单超时时间（绝对时间 yyyyMMddHHmmss，2h 后过期）
        p.put("expiretime", new java.text.SimpleDateFormat("yyyyMMddHHmmss")
                .format(new java.util.Date(System.currentTimeMillis() + 2 * 3600_000L)));
        p.put("signtype", "SM2");
        String sign = AllinpayCashierService.signSm2(p, SM2_PRIV, APPID);
        p.put("sign", sign);

        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=')
                .append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        HttpURLConnection con = (HttpURLConnection) new URL(ONEPAY_URL).openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setInstanceFollowRedirects(false);
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        con.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
        con.setRequestProperty("Referer", "https://www.doupaidoudian.com/m/");
        con.setConnectTimeout(8_000);
        con.setReadTimeout(15_000);
        try (OutputStream os = con.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = con.getResponseCode();
        String loc = con.getHeaderField("Location");
        java.io.InputStream is = (code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream();
        String resp = is == null ? "" : new String(readAll(is), StandardCharsets.UTF_8);
        boolean rejected = (loc != null && loc.contains("exception.html"))
                          || (resp.contains("sign") && resp.contains("失败"));
        System.out.println("==== onepay 聚合收银台 (Android UA) → " + (rejected ? "❌ REJECTED" : "✅ OK") + " ====");
        System.out.println("HTTP=" + code);
        System.out.println("Location=" + loc);
        if (resp.length() > 0) System.out.println("RESP=" + (resp.length() > 400 ? resp.substring(0, 400) + "..." : resp));
    }

    /**
     * 实际打通联 H5 收银台 unionorder 下单接口（用 appid 作 SM2 userId + PLAIN 编码）。
     *
     * <p>预期：retcode=SUCCESS + payinfo（H5 收银台跳转 URL）；payinfo 长这样
     * https://syb.allinpay.com/apiweb/h5unionpay/?...</p>
     */
    @Test
    void liveUnionOrder_chineseBody_with_centerDot() throws Exception {
        runUnionOrder("中文 body 含中点 ·", "体验装 · 3 条", "https://www.doupaidoudian.com/m/#/pages/ai-video/quota?paid=1");
    }

    @Test
    void liveUnionOrder_pureAscii() throws Exception {
        runUnionOrder("纯 ASCII body", "test", "https://www.doupaidoudian.com/m/");
    }

    @Test
    void liveUnionOrder_chineseNoDot() throws Exception {
        runUnionOrder("中文无中点", "体验装3条", "https://www.doupaidoudian.com/m/");
    }

    @Test
    void liveUnionOrder_returlNoHashFragment() throws Exception {
        runUnionOrder("returl 无 # fragment", "test", "https://www.doupaidoudian.com/m/quota");
    }

    @Test
    void liveUnionOrder_realProductionShape() throws Exception {
        // 完全模拟用户线上 source（reqsn=12 数字短串、trxamt=2990、含中文 body 中点、returl 含 #）
        runUnionOrderFixedReqsn("用户线上完整 shape", "体验装 · 3 条",
                "https://www.doupaidoudian.com/m/#/pages/ai-video/quota?paid=1",
                "12", 2990);
    }

    @Test
    void liveUnionOrder_reqsnRepeated() throws Exception {
        // 通联可能拒绝重复 reqsn（同一个 cusid+reqsn 已下过单）
        runUnionOrderFixedReqsn("固定 reqsn=12 第二次提交", "test",
                "https://www.doupaidoudian.com/m/", "12", 100);
    }

    private void runUnionOrderFixedReqsn(String label, String body, String returl,
                                          String reqsn, int amount) throws Exception {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("cusid", CUSID);
        p.put("appid", APPID);
        p.put("version", "12");
        p.put("trxamt", String.valueOf(amount));
        p.put("reqsn", reqsn);
        p.put("randomstr", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        p.put("body", body);
        p.put("returl", returl);
        p.put("notify_url", "https://www.doupaidoudian.com/admin-api/merchant/allinpay/pay-notify");
        p.put("signtype", "SM2");
        String sign = AllinpayCashierService.signSm2(p, SM2_PRIV, APPID);
        p.put("sign", sign);

        StringBuilder reqBody = new StringBuilder();
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (reqBody.length() > 0) reqBody.append('&');
            reqBody.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=')
                   .append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        HttpURLConnection con = (HttpURLConnection) new URL(UNION_URL).openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setInstanceFollowRedirects(false);
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        con.setRequestProperty("User-Agent",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1");
        con.setRequestProperty("Referer", "https://www.doupaidoudian.com/m/");
        con.setConnectTimeout(8_000);
        con.setReadTimeout(15_000);
        try (OutputStream os = con.getOutputStream()) {
            os.write(reqBody.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = con.getResponseCode();
        String loc = con.getHeaderField("Location");
        java.io.InputStream is = (code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream();
        String resp = is == null ? "" : new String(readAll(is), StandardCharsets.UTF_8);
        boolean rejected = (loc != null && loc.contains("exception.html"))
                           || (resp.contains("sign") && resp.contains("失败"));
        System.out.println("==== " + label + " → " + (rejected ? "❌ REJECTED" : "✅ OK") + " ====");
        System.out.println("reqsn=" + reqsn + " amount=" + amount);
        System.out.println("HTTP=" + code + " Location=" + (loc == null ? "<none>" : loc));
        if (rejected) System.out.println("RESP=" + (resp.length() > 300 ? resp.substring(0, 300) : resp));
        System.out.println();
    }

    private void runUnionOrder(String label, String body, String returl) throws Exception {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("cusid", CUSID);
        p.put("appid", APPID);
        p.put("version", "12");
        p.put("trxamt", "100");
        p.put("reqsn", "TEST_" + System.currentTimeMillis());
        p.put("randomstr", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        p.put("body", body);
        p.put("returl", returl);
        p.put("notify_url", "https://www.doupaidoudian.com/admin-api/merchant/allinpay/pay-notify");
        p.put("signtype", "SM2");
        String sign = AllinpayCashierService.signSm2(p, SM2_PRIV, APPID);
        p.put("sign", sign);

        StringBuilder reqBody = new StringBuilder();
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (reqBody.length() > 0) reqBody.append('&');
            reqBody.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=')
                   .append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        HttpURLConnection con = (HttpURLConnection) new URL(UNION_URL).openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setInstanceFollowRedirects(false);
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        con.setRequestProperty("User-Agent",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1");
        con.setRequestProperty("Referer", "https://www.doupaidoudian.com/m/");
        con.setConnectTimeout(8_000);
        con.setReadTimeout(15_000);
        try (OutputStream os = con.getOutputStream()) {
            os.write(reqBody.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = con.getResponseCode();
        String loc = con.getHeaderField("Location");
        java.io.InputStream is = (code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream();
        String resp = is == null ? "" : new String(readAll(is), StandardCharsets.UTF_8);
        boolean rejected = (loc != null && loc.contains("exception.html"))
                           || (resp.contains("sign") && resp.contains("失败"));
        System.out.println("==== " + label + " → " + (rejected ? "❌ REJECTED" : "✅ OK") + " ====");
        System.out.println("body=" + body + " | returl=" + returl);
        System.out.println("HTTP=" + code + " Location=" + loc);
        if (rejected) System.out.println("RESP=" + (resp.length() > 200 ? resp.substring(0, 200) : resp));
        System.out.println();
    }

    @Test
    void liveUnionOrder_h5Cashier() throws Exception {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("cusid", CUSID);
        p.put("appid", APPID);
        p.put("version", "12");
        p.put("trxamt", "100"); // 1 元 = 100 分
        p.put("reqsn", "TEST_" + System.currentTimeMillis());
        p.put("randomstr", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        p.put("body", "AI视频套餐测试");
        p.put("returl", "https://www.doupaidoudian.com/m/#/pages/ai-video/quota?paid=1");
        p.put("notify_url", "https://www.doupaidoudian.com/admin-api/merchant/allinpay/pay-notify");
        p.put("signtype", "SM2");
        String sign = AllinpayCashierService.signSm2(p, SM2_PRIV, APPID);
        p.put("sign", sign);

        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=')
                .append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        HttpURLConnection con = (HttpURLConnection) new URL(UNION_URL).openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setInstanceFollowRedirects(false);  // 不自动跟 302，让我们看到真实跳转 URL
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        con.setRequestProperty("User-Agent",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1");
        con.setRequestProperty("Referer", "https://www.doupaidoudian.com/m/");
        con.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9");
        con.setConnectTimeout(8_000);
        con.setReadTimeout(15_000);
        try (OutputStream os = con.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = con.getResponseCode();
        java.io.InputStream is = (code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream();
        String resp = is == null ? "" : new String(readAll(is), StandardCharsets.UTF_8);
        String location = con.getHeaderField("Location");
        System.out.println("==== H5 unionorder (browser UA + 1 元) ====");
        System.out.println("REQUEST  body=" + body);
        System.out.println("RESPONSE HTTP=" + code + (location == null ? "" : " Location=" + location));
        System.out.println("RESPONSE body(前500字)=" + (resp.length() > 500 ? resp.substring(0, 500) + "...(" + resp.length() + " bytes)" : resp));
        System.out.println();
    }

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
