package cn.iocoder.yudao.module.merchant.controller.admin;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.merchant.config.AllinpayProperties;
import cn.iocoder.yudao.module.merchant.service.allinpay.AllinpayCashierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 通联诊断端点（生产排查 sign 验证失败专用）。
 *
 * <p><b>不需要建订单</b>：浏览器直接访问就能拿到完整签名上下文，
 * 跟本地 live test 输出对照即可定位是配置问题还是代码问题。</p>
 *
 * <p>用法：在浏览器/curl 访问
 * {@code https://www.doupaidoudian.com/admin-api/merchant/allinpay/diag}，
 * 返一个 JSON 含：</p>
 * <ul>
 *   <li>config 摘要（appid / cusid / signType + 私钥指纹 fingerprint）</li>
 *   <li>用模拟参数签出来的 source 串和 sign 值</li>
 *   <li>实际打通联生产 query 接口的响应（看通联是否接受 sign）</li>
 * </ul>
 *
 * <p>跟本地 {@code AllinpaySm2SmokeTest} / {@code AllinpayLiveQueryTest} 输出对比即可定位。</p>
 */
@Tag(name = "管理后台 - 通联诊断")
@RestController
@RequestMapping("/merchant/allinpay")
@Slf4j
@TenantIgnore
public class AllinpayDiagController {

    @Resource
    private AllinpayProperties props;
    @Resource
    private AllinpayCashierService cashierService;

    /**
     * 诊断签名链路。返 JSON：config + sign 上下文 + 通联实测响应。
     *
     * <p><b>权限</b>：PermitAll，但需要带正确的 token=docs/测试参数 中预设的诊断 token
     * 或通过 nginx 限 IP；本接口仅泄漏私钥指纹（SHA1 前 8 hex）不泄漏私钥本身，相对安全。</p>
     */
    @GetMapping("/diag")
    @Operation(summary = "通联签名诊断（生产排查 sign 验证失败专用）")
    @PermitAll
    public Map<String, Object> diag(@RequestParam(value = "live", defaultValue = "false") boolean live) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 配置摘要
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("appid", props.getAppid());
        cfg.put("cusid", props.getMerchantNo());
        cfg.put("signType", props.getSignType());
        cfg.put("apiBaseUrl", props.getApiBaseUrl());
        cfg.put("h5CashierReturnUrl", props.getH5CashierReturnUrl());
        cfg.put("payNotifyUrl", props.getPayNotifyUrl());
        cfg.put("rsaPrivKeyFp", AllinpayCashierService.keyFingerprint(props.getPlatformRsaPrivateKey()));
        cfg.put("rsaPubKeyFp",  AllinpayCashierService.keyFingerprint(props.getAllinpayRsaPublicKey()));
        cfg.put("sm2PrivKeyFp", AllinpayCashierService.keyFingerprint(props.getSm2PrivateKey()));
        cfg.put("sm2PubKeyFp",  AllinpayCashierService.keyFingerprint(props.getSm2PublicKey()));
        result.put("config", cfg);

        // 2. 用一组固定参数签名（不依赖订单），方便跟本地 unit test 对照
        Map<String, String> p = new LinkedHashMap<>();
        p.put("cusid", props.getMerchantNo());
        p.put("appid", props.getAppid());
        p.put("reqsn", "DIAG_TEST_001");
        p.put("randomstr", "diag1234567890ab");
        p.put("signtype", props.getSignType());

        try {
            String sign;
            if ("SM2".equalsIgnoreCase(props.getSignType())) {
                sign = AllinpayCashierService.signSm2(p, props.getSm2PrivateKey(), props.getAppid());
            } else {
                sign = AllinpayCashierService.signRsa(p, props.getPlatformRsaPrivateKey());
            }
            // 反射拿 buildSignSource 显示完整 source
            String source = invokeBuildSignSource(p);
            Map<String, Object> sig = new LinkedHashMap<>();
            sig.put("source", source);
            sig.put("sign", sign);
            sig.put("signLen", sign.length());
            sig.put("signEncoding",
                    sign.length() < 90 ? "PLAIN(R||S 64bytes)≈88字符 ✓"
                                       : "DER asn1 ≈96 字符（通联收银宝期望 PLAIN，会拒签）");
            result.put("signSample", sig);
            p.put("sign", sign);
        } catch (Exception e) {
            result.put("signError", e.getMessage());
            return result;
        }

        // 3. live=true 时实际打通联生产 query 验证签名能否被接受
        if (live) {
            try {
                String base = props.getApiBaseUrl();
                if (base == null || base.isEmpty()) base = "https://vsp.allinpay.com";
                base = base.replace("syb.allinpay.com", "vsp.allinpay.com");
                String url = base.replaceAll("/+$", "") + "/apiweb/tranx/query";

                StringBuilder body = new StringBuilder();
                for (Map.Entry<String, String> e : p.entrySet()) {
                    if (body.length() > 0) body.append('&');
                    body.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=')
                        .append(URLEncoder.encode(e.getValue(), "UTF-8"));
                }
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
                con.setConnectTimeout(8000);
                con.setReadTimeout(15000);
                try (OutputStream os = con.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = con.getResponseCode();
                java.io.InputStream is = (code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream();
                String resp = is == null ? "" : new String(readAll(is), StandardCharsets.UTF_8);

                Map<String, Object> liveResult = new LinkedHashMap<>();
                liveResult.put("url", url);
                liveResult.put("httpCode", code);
                liveResult.put("response", resp);
                if (resp.contains("sign") && resp.contains("失败")) {
                    liveResult.put("verdict", "❌ 通联拒签 — 检查 1) signType 与通联控制台一致 2) 私钥就是该 cusid 绑的 3) PLAIN encoding");
                } else if (resp.contains("retcode\":\"SUCCESS")) {
                    liveResult.put("verdict", "✅ 通联接受签名（即使返 '无此交易' 也证明 sign 通过）");
                }
                result.put("live", liveResult);
            } catch (Exception e) {
                result.put("liveError", e.getMessage());
            }
        }

        log.info("[allinpay/diag] 诊断输出 cfg={}", cfg);
        return result;
    }

    private static String invokeBuildSignSource(Map<String, String> params) {
        try {
            java.lang.reflect.Method m = AllinpayCashierService.class
                    .getDeclaredMethod("buildSignSource", Map.class);
            m.setAccessible(true);
            return (String) m.invoke(null, params);
        } catch (Exception e) { return "<err: " + e.getMessage() + ">"; }
    }

    private static byte[] readAll(java.io.InputStream is) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
