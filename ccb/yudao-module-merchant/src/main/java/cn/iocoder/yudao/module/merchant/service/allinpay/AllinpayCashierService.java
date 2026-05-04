package cn.iocoder.yudao.module.merchant.service.allinpay;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.merchant.config.AllinpayProperties;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantPackageOrderDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantPackageOrderMapper;
import cn.iocoder.yudao.module.merchant.service.MerchantPackageOrderService;
import cn.iocoder.yudao.module.merchant.service.MerchantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 通联收银宝 H5 收银台桥接（套餐购买专用）。
 *
 * <p>对接产品：通联「H5 收银台 - 银联 H5」 cashier。文档：
 * <ul>
 *   <li>H5 下单：{@code POST https://syb.allinpay.com/apiweb/h5unionpay/unionorder}</li>
 *   <li>交易查询：{@code POST https://vsp.allinpay.com/apiweb/tranx/query}</li>
 *   <li>异步通知：通联 POST 到 {@code merchant.allinpay.pay-notify-url}</li>
 *   <li>同步回跳：通联 GET 到 {@code merchant.allinpay.h5-cashier-return-url}</li>
 * </ul>
 *
 * <p><b>签名规范（RSA SHA1withRSA）</b>：
 * <ol>
 *   <li>所有非空字段（除 sign 外）按 ASCII 升序排序</li>
 *   <li>拼成 {@code key1=value1&key2=value2&...}（signtype 参与签名）</li>
 *   <li>用商户 RSA 私钥做 SHA1withRSA 签名，结果 Base64 编码作为 {@code sign}</li>
 *   <li>验签反向：用通联公钥 SHA1withRSA 验证</li>
 * </ol>
 * </p>
 *
 * <p>关键字段映射：</p>
 * <ul>
 *   <li>cusid = merchant.allinpay.merchant-no（商户号）</li>
 *   <li>appid = merchant.allinpay.appid（应用 ID）</li>
 *   <li>reqsn = merchant_package_order.id（业务订单号）</li>
 *   <li>trxamt = merchant_package_order.price（金额，单位分）</li>
 *   <li>trxstatus = 2000 视为支付成功</li>
 * </ul>
 */
@Service
@Slf4j
public class AllinpayCashierService {

    private static final String SIGN_ALG_RSA = "SHA1withRSA";
    private static final String CHARSET = "UTF-8";

    static {
        // SM2 需要 BouncyCastle Provider（hutool SmUtil 也依赖）
        if (java.security.Security.getProvider("BC") == null) {
            try {
                java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
            } catch (Throwable ignore) {}
        }
    }
    /** 通联约定：trxstatus=2000 表示交易成功 */
    private static final String TRX_STATUS_SUCCESS = "2000";
    /** 通联收银台 H5 下单 endpoint（生产）。测试环境为 https://syb-test.allinpay.com */
    private static final String H5_UNIONORDER_PATH = "/apiweb/h5unionpay/unionorder";
    /** 通联交易查询 endpoint（生产 vsp.allinpay.com / 测试 syb-test.allinpay.com） */
    private static final String QUERY_TRX_PATH = "/apiweb/tranx/query";

    @Resource
    private AllinpayProperties props;

    @Resource
    private MerchantPackageOrderMapper packageOrderMapper;

    @Resource
    private MerchantPackageOrderService packageOrderService;

    @Resource
    private MerchantService merchantService;

    @Resource(name = "allinpayRestTemplate")
    private RestTemplate restTemplate;

    // ============================================================
    // 1. 收银台下单：构造 form 给前端跳通联
    // ============================================================

    /** 私钥指纹（SHA1 前 8 hex），用于跨环境对比是否同一份私钥（不泄漏私钥本身）。 */
    public static String keyFingerprint(String pemKey) {
        if (pemKey == null || pemKey.isEmpty()) return "<empty>";
        String stripped = stripPem(pemKey);
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] dig = md.digest(stripped.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4 && i < dig.length; i++) sb.append(String.format("%02x", dig[i]));
            return sb + "(" + stripped.length() + "chars)";
        } catch (Exception e) { return "<err>"; }
    }

    /** 构造通联收银台请求参数 — 前端 form POST 跳转（用默认 iPhone Safari UA）。 */
    public CashierForm buildCashierForm(Long orderId) {
        return buildCashierForm(orderId, null);
    }

    /**
     * 构造通联收银台 — 可指定客户端 UA。
     *
     * <p>通联根据 UA 决定推荐支付通道：iPhone Safari → Apple Pay；
     * 微信内置浏览器 → 微信支付；Android Chrome → 银联/聚合。
     * controller 应把 HttpServletRequest.getHeader("User-Agent") 透传过来，
     * 让通联按用户真实浏览器推支付方式。</p>
     */
    public CashierForm buildCashierForm(Long orderId, String clientUserAgent) {
        long t0 = System.currentTimeMillis();
        log.info("[allinpay/cashier] ───── buildCashierForm START orderId={} ─────", orderId);
        if (!props.isH5Configured()) {
            log.error("[allinpay/cashier] 配置未就绪 signType={} appid={} merchantNo={} rsaPrivLen={} sm2PrivLen={}",
                    props.getSignType(), props.getAppid(), props.getMerchantNo(),
                    props.getPlatformRsaPrivateKey() == null ? 0 : props.getPlatformRsaPrivateKey().length(),
                    props.getSm2PrivateKey() == null ? 0 : props.getSm2PrivateKey().length());
            throw new IllegalStateException("通联收银台未配置（appid / merchant-no / 私钥）");
        }
        MerchantPackageOrderDO order = TenantUtils.executeIgnore(() -> packageOrderMapper.selectById(orderId));
        if (order == null) {
            log.error("[allinpay/cashier] 订单不存在 orderId={}", orderId);
            throw new IllegalStateException("订单不存在: " + orderId);
        }
        log.info("[allinpay/cashier] 订单加载 orderId={} merchantId={} packageName={} priceFen={} payStatus={}",
                order.getId(), order.getMerchantId(), order.getPackageName(),
                order.getPrice(), order.getPayStatus());
        if (order.getPayStatus() != null
                && order.getPayStatus() != MerchantPackageOrderDO.PAY_STATUS_WAITING) {
            log.error("[allinpay/cashier] 订单非待支付 orderId={} payStatus={}", orderId, order.getPayStatus());
            throw new IllegalStateException("订单非待支付状态，不可重复唤起收银台");
        }

        String signType = resolveSignType();
        Map<String, String> p = new LinkedHashMap<>();
        p.put("cusid", props.getMerchantNo());
        p.put("appid", props.getAppid());
        p.put("version", "12");
        p.put("trxamt", String.valueOf(order.getPrice()));
        p.put("reqsn", String.valueOf(order.getId()));
        p.put("randomstr", randomStr());
        p.put("body", truncate(order.getPackageName(), 64));
        p.put("returl", props.getH5CashierReturnUrl());
        p.put("notify_url", props.getPayNotifyUrl());
        p.put("signtype", signType);

        String source = buildSignSource(p);
        String privFp = "SM2".equalsIgnoreCase(signType)
                ? keyFingerprint(props.getSm2PrivateKey())
                : keyFingerprint(props.getPlatformRsaPrivateKey());
        log.info("[allinpay/cashier] 签名 signType={} userId(=appid)={} privKeyFingerprint={} source={}",
                signType, props.getAppid(), privFp, source);
        String sign = signWith(p, signType);
        p.put("sign", sign);
        log.info("[allinpay/cashier] 签名结果 sign={} ({} chars，PLAIN=88 / DER≈96)",
                sign, sign.length());

        String base = props.getApiBaseUrl();
        String baseRaw = base;
        if (base == null || base.isEmpty()) {
            base = "https://syb.allinpay.com";
        } else {
            // H5 下单接口在 syb 域名；用户若配 vsp 自动纠正
            base = base.replace("vsp.allinpay.com", "syb.allinpay.com")
                       .replace("test-vsp.allinpay.com", "syb-test.allinpay.com");
        }
        String cashierUrl = base.replaceAll("/+$", "") + H5_UNIONORDER_PATH;
        if (!java.util.Objects.equals(baseRaw, base)) {
            log.info("[allinpay/cashier] base URL 自动纠正 {} → {}", baseRaw, base);
        }
        // 关键：后端直接 POST 通联拿 302 Location，避开浏览器 form 编码差异
        // 之前用前端 form POST + body 含中文（"体验装 · 3 条"）→ 浏览器 Content-Type 不带
        // charset=UTF-8 → 通联 server 解码差异 → sign 验证失败。
        // 后端用 URLEncoder.encode + UTF-8 + Content-Type 显式声明 charset，跟我们签
        // 时用的字符串完全一致 → 通联接受，返 302 跳真实收银台。
        try {
            StringBuilder bodyStr = new StringBuilder();
            for (Map.Entry<String, String> e : p.entrySet()) {
                if (bodyStr.length() > 0) bodyStr.append('&');
                bodyStr.append(java.net.URLEncoder.encode(e.getKey(), "UTF-8")).append('=')
                       .append(java.net.URLEncoder.encode(e.getValue(), "UTF-8"));
            }
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) new java.net.URL(cashierUrl).openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setInstanceFollowRedirects(false);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
            // 透传客户端真实 UA → 通联按用户实际浏览器推支付方式（微信/支付宝/银联等）
            // 没传时 fallback 通用 Android Chrome（避免 iPhone UA 默认推 Apple Pay）
            String ua = (clientUserAgent != null && !clientUserAgent.isEmpty())
                    ? clientUserAgent
                    : "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Mobile Safari/537.36";
            con.setRequestProperty("User-Agent", ua);
            con.setRequestProperty("Referer", props.getH5CashierReturnUrl());
            log.info("[allinpay/cashier] forward UA={}", ua);
            con.setConnectTimeout(8_000);
            con.setReadTimeout(15_000);
            try (java.io.OutputStream os = con.getOutputStream()) {
                os.write(bodyStr.toString().getBytes(StandardCharsets.UTF_8));
            }
            int httpCode = con.getResponseCode();
            String location = con.getHeaderField("Location");
            log.info("[allinpay/cashier] 通联响应 HTTP={} Location={}", httpCode, location);

            if (httpCode >= 300 && httpCode < 400 && location != null && !location.isEmpty()) {
                if (location.contains("exception.html")) {
                    log.error("[allinpay/cashier] 通联拒签 location={}", location);
                    throw new IllegalStateException("通联拒签：" + location);
                }
                log.info("[allinpay/cashier] ───── DONE orderId={} redirectUrl={} cost={}ms ─────",
                        order.getId(), location, System.currentTimeMillis() - t0);
                CashierForm res = new CashierForm(location, java.util.Collections.emptyMap());
                res.setRedirect(true);
                res.setRedirectUrl(location);
                return res;
            }
            // 非 302：fallback 回原 form POST
            log.warn("[allinpay/cashier] 通联非 302（fallback 前端 form POST）HTTP={}", httpCode);
        } catch (IllegalStateException ise) {
            throw ise;
        } catch (Exception ex) {
            log.warn("[allinpay/cashier] 后端打通联失败 fallback 前端 form: {}", ex.getMessage());
        }

        log.info("[allinpay/cashier] ───── DONE orderId={} (fallback form) cashierUrl={} cost={}ms ─────",
                order.getId(), cashierUrl, System.currentTimeMillis() - t0);
        return new CashierForm(cashierUrl, p);
    }

    // ============================================================
    // 2. 异步通知：通联 POST 上来 → 验签 → markPaidExternal
    // ============================================================

    @TenantIgnore  // 通联回调没有 tenant-id header；内部按订单反查 tenant 后切上下文
    public String handlePayNotify(Map<String, String> notifyParams) {
        long t0 = System.currentTimeMillis();
        log.info("[allinpay/notify] ───── 收到通联异步通知 keys={} ─────",
                notifyParams == null ? null : notifyParams.keySet());
        if (notifyParams == null || notifyParams.isEmpty()) {
            log.warn("[allinpay/notify] 空参数，拒绝");
            return "fail:empty";
        }
        // 完整 dump 通联 form 字段（除 sign 外打全值，sign 截短防日志爆炸）
        notifyParams.forEach((k, v) -> {
            if ("sign".equalsIgnoreCase(k)) {
                log.info("[allinpay/notify] field {}={}（{} chars）", k,
                        v == null || v.length() < 20 ? v : v.substring(0, 16) + "...",
                        v == null ? 0 : v.length());
            } else {
                log.info("[allinpay/notify] field {}={}", k, v);
            }
        });
        try {
            String reqsn = notifyParams.getOrDefault("cusorderid", notifyParams.get("reqsn"));
            String trxstatus = notifyParams.getOrDefault("trxstatus", "");
            String trxamtStr = notifyParams.getOrDefault("trxamt", "0");
            String sign = notifyParams.get("sign");
            String notifySignType = notifyParams.getOrDefault("signtype", resolveSignType());

            // 1. 验签
            Map<String, String> verifyParams = new TreeMap<>(notifyParams);
            verifyParams.remove("sign");
            String source = buildSignSource(verifyParams);
            log.info("[allinpay/notify] 验签 signType={} userId(=appid)={} source={}",
                    notifySignType, props.getAppid(), source);
            boolean ok = verifyWith(verifyParams, sign, notifySignType);
            log.info("[allinpay/notify] 验签结果={} reqsn={}", ok, reqsn);
            if (!ok) {
                log.warn("[allinpay/notify] {} 验签失败 reqsn={} sign={}",
                        notifySignType, reqsn, sign);
                return "fail:sign";
            }

            // 2. 非成功状态：通联仍要回 success
            if (!TRX_STATUS_SUCCESS.equals(trxstatus)) {
                log.info("[allinpay/notify] reqsn={} trxstatus={}（非 2000 非成功，回 success 不重发）",
                        reqsn, trxstatus);
                return "success";
            }

            // 3. 解析订单号 + 金额
            Long oid;
            try { oid = Long.parseLong(reqsn); }
            catch (Exception e) {
                log.warn("[allinpay/notify] 非法 reqsn={}", reqsn);
                return "fail:bad_reqsn";
            }
            int trxamtFen;
            try { trxamtFen = Integer.parseInt(trxamtStr); }
            catch (Exception e) {
                log.warn("[allinpay/notify] 非法 trxamt={}", trxamtStr);
                return "fail:bad_amount";
            }

            // 4. markPaidExternal（不走 yudao pay_order）
            log.info("[allinpay/notify] 调 markPaidExternal orderId={} amount={}", oid, trxamtFen);
            packageOrderService.markPaidExternal(oid, trxamtFen, "ALLINPAY_NOTIFY");
            log.info("[allinpay/notify] ───── DONE reqsn={} amount={} cost={}ms ─────",
                    oid, trxamtFen, System.currentTimeMillis() - t0);
            return "success";
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException se) {
            log.warn("[allinpay/notify] 业务异常 code={} msg={}", se.getCode(), se.getMessage());
            return "fail:" + se.getCode();
        } catch (Exception e) {
            log.error("[allinpay/notify] 处理失败", e);
            return "fail";
        }
    }

    // ============================================================
    // 3. 主动查询：异步通知漏发兜底；轮询 5/15/25/35s/1m/2m
    // ============================================================

    /**
     * 主动查询通联订单状态。
     *
     * @return trxstatus，2000 = 成功；其它 = 未成功；null = 通信失败 / 查无此单
     */
    public QueryResult queryOrder(Long orderId) {
        if (!props.isH5Configured()) {
            log.debug("[allinpay/query] 配置未就绪，跳过 orderId={}", orderId);
            return null;
        }
        long t0 = System.currentTimeMillis();
        String signType = resolveSignType();
        Map<String, String> p = new LinkedHashMap<>();
        p.put("cusid", props.getMerchantNo());
        p.put("appid", props.getAppid());
        p.put("reqsn", String.valueOf(orderId));
        p.put("randomstr", randomStr());
        p.put("signtype", signType);
        log.info("[allinpay/query] orderId={} signType={} userId(=appid)={} source={}",
                orderId, signType, props.getAppid(), buildSignSource(p));
        p.put("sign", signWith(p, signType));

        String base = props.getApiBaseUrl();
        if (base == null || base.isEmpty()) base = "https://vsp.allinpay.com";
        else {
            base = base.replace("syb.allinpay.com", "vsp.allinpay.com")
                       .replace("syb-test.allinpay.com", "syb-test.allinpay.com");
        }
        String url = base.replaceAll("/+$", "") + QUERY_TRX_PATH;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            p.forEach(body::add);
            HttpEntity<MultiValueMap<String, String>> req = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(url, req, Map.class);
            log.info("[allinpay/query] orderId={} url={} resp={} cost={}ms",
                    orderId, url, resp, System.currentTimeMillis() - t0);
            if (resp == null) return null;
            String retcode = String.valueOf(resp.getOrDefault("retcode", ""));
            if (!"SUCCESS".equals(retcode)) {
                log.warn("[allinpay/query] orderId={} retcode={} retmsg={} errmsg={}",
                        orderId, retcode, resp.get("retmsg"), resp.get("errmsg"));
                return null;
            }
            String trxstatus = String.valueOf(resp.getOrDefault("trxstatus", ""));
            String trxamt = String.valueOf(resp.getOrDefault("trxamt", "0"));
            int amt = 0;
            try { amt = Integer.parseInt(trxamt); } catch (Exception ignore) {}
            log.info("[allinpay/query] orderId={} trxstatus={} trxamt={}（2000=成功 / 1001=无此交易 / 其它=进行中）",
                    orderId, trxstatus, amt);
            return new QueryResult(trxstatus, amt);
        } catch (Exception e) {
            log.warn("[allinpay/query] orderId={} url={} 查询异常: {}", orderId, url, e.getMessage(), e);
            return null;
        }
    }

    // ============================================================
    // 签名工具
    // ============================================================

    /** 当前商户号在通联控制台配的签名类型（RSA / SM2） */
    private String resolveSignType() {
        String t = props.getSignType();
        return (t != null && "SM2".equalsIgnoreCase(t)) ? "SM2" : "RSA";
    }

    /** 按 signType 调对应签名方法（RSA→私钥；SM2→sm2 私钥） */
    private String signWith(Map<String, String> params, String signType) {
        if ("SM2".equalsIgnoreCase(signType)) {
            return signSm2(params, props.getSm2PrivateKey(), props.getAppid());
        }
        return signRsa(params, props.getPlatformRsaPrivateKey());
    }

    /** 按 signType 验签 */
    private boolean verifyWith(Map<String, String> params, String sign, String signType) {
        if ("SM2".equalsIgnoreCase(signType)) {
            return verifySm2(params, sign, props.getSm2PublicKey(), props.getAppid());
        }
        return verifyRsa(params, sign, props.getAllinpayRsaPublicKey());
    }

    /**
     * 通联收银宝 SM2 签名：sign = Base64(SM3withSM2_PLAIN(privKey, userId=appid, source))
     *
     * <p><b>实测验证</b>（against https://vsp.allinpay.com/apiweb/tranx/query）：</p>
     * <ul>
     *   <li><b>encoding = PLAIN</b>（R||S 各 32 byte 直接拼成 64 byte 后 Base64；不是 DER asn1）</li>
     *   <li><b>userId = appid</b>（不是 cusid，也不是 SM2 默认 1234567812345678）</li>
     * </ul>
     * <p>用 DER 编码 + 任何 userId，通联都返 "sign验证失败,请检查密钥配置"；
     * 切到 PLAIN + appid 后通联返 retcode=SUCCESS 通过签名验证。</p>
     */
    public static String signSm2(Map<String, String> params, String pemPrivateKey, String appidUserId) {
        if (pemPrivateKey == null || pemPrivateKey.isEmpty()) {
            throw new IllegalStateException("通联 SM2 私钥未配置（merchant.allinpay.sm2-private-key）");
        }
        String source = buildSignSource(params);
        try {
            cn.hutool.crypto.asymmetric.SM2 sm2 =
                    cn.hutool.crypto.SmUtil.sm2(stripPem(pemPrivateKey), null);
            sm2.usePlainEncoding();   // ← 关键：通联收银宝实测要 PLAIN (R||S)
            byte[] sig = sm2.sign(
                    source.getBytes(StandardCharsets.UTF_8),
                    (appidUserId == null ? "" : appidUserId).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException("通联 SM2 签名失败: " + e.getMessage(), e);
        }
    }

    public static boolean verifySm2(Map<String, String> params, String sign, String pemPublicKey, String appidUserId) {
        if (sign == null || sign.isEmpty()) return false;
        if (pemPublicKey == null || pemPublicKey.isEmpty()) {
            log.warn("[verifySm2] 通联 SM2 公钥未配置（merchant.allinpay.sm2-public-key），跳过验签");
            return false;
        }
        try {
            String source = buildSignSource(params);
            cn.hutool.crypto.asymmetric.SM2 sm2 =
                    cn.hutool.crypto.SmUtil.sm2(null, stripPem(pemPublicKey));
            sm2.usePlainEncoding();   // 同样 PLAIN 模式
            return sm2.verify(
                    source.getBytes(StandardCharsets.UTF_8),
                    Base64.getDecoder().decode(sign),
                    (appidUserId == null ? "" : appidUserId).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("[verifySm2] 验签异常: {}", e.getMessage());
            return false;
        }
    }

    /** ASCII 升序拼 key=value& 拼接（跳过 sign 和空值），SHA1withRSA 私钥签名，Base64。 */
    public static String signRsa(Map<String, String> params, String pemPrivateKey) {
        if (pemPrivateKey == null || pemPrivateKey.isEmpty()) {
            throw new IllegalStateException("通联 RSA 私钥未配置");
        }
        String source = buildSignSource(params);
        try {
            byte[] der = Base64.getDecoder().decode(stripPem(pemPrivateKey));
            PrivateKey pk = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            Signature s = Signature.getInstance(SIGN_ALG_RSA);
            s.initSign(pk);
            s.update(source.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(s.sign());
        } catch (Exception e) {
            throw new IllegalStateException("通联签名失败: " + e.getMessage(), e);
        }
    }

    public static boolean verifyRsa(Map<String, String> params, String sign, String pemPublicKey) {
        if (sign == null || sign.isEmpty()) return false;
        if (pemPublicKey == null || pemPublicKey.isEmpty()) {
            log.warn("[verifyRsa] 通联公钥未配置（merchant.allinpay.allinpay-rsa-public-key），跳过验签");
            return false;
        }
        String source = buildSignSource(params);
        try {
            byte[] der = Base64.getDecoder().decode(stripPem(pemPublicKey));
            PublicKey pk = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            Signature s = Signature.getInstance(SIGN_ALG_RSA);
            s.initVerify(pk);
            s.update(source.getBytes(StandardCharsets.UTF_8));
            return s.verify(Base64.getDecoder().decode(sign));
        } catch (Exception e) {
            log.warn("[verifyRsa] 验签异常: {}", e.getMessage());
            return false;
        }
    }

    private static String buildSignSource(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if ("sign".equalsIgnoreCase(k)) continue;
            if (v == null || v.isEmpty()) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(k).append('=').append(v);
        }
        return sb.toString();
    }

    private static String stripPem(String pem) {
        return pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s+", "");
    }

    private static String randomStr() {
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder(32);
        String alpha = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (int i = 0; i < 32; i++) sb.append(alpha.charAt(r.nextInt(alpha.length())));
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    public static String getTrxStatusSuccess() { return TRX_STATUS_SUCCESS; }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class CashierForm {
        private String cashierUrl;
        private Map<String, String> params;
        /** redirect=true 时前端用 redirectUrl 直接 location.href 跳；false 时用 form POST */
        private boolean redirect;
        private String redirectUrl;

        public CashierForm(String cashierUrl, Map<String, String> params) {
            this(cashierUrl, params, false, null);
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class QueryResult {
        /** 通联交易状态码：2000=成功 */
        private String trxstatus;
        /** 交易金额（分） */
        private int trxamt;
        public boolean isSuccess() { return TRX_STATUS_SUCCESS.equals(trxstatus); }
    }
}
