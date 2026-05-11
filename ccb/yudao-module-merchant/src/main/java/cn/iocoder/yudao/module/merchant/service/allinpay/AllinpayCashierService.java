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
 *   <li>H5 下单（聚合收银台，用户在页面选 微信/支付宝/云闪付/快捷支付）：
 *       {@code POST https://syb.allinpay.com/apiweb/h5unionpay/onepay}</li>
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
    /** unionorder：通联按 UA 推单一通道（iOS→Apple Pay；微信内→微信；Android→银联） */
    private static final String H5_UNIONORDER_PATH = "/apiweb/h5unionpay/unionorder";
    /** onepay：聚合收银台（用户主动选 微信/支付宝/云闪付/快捷）。需通联控制台开通 onepay 产品权限。 */
    private static final String H5_ONEPAY_PATH = "/apiweb/h5unionpay/onepay";
    /** 通联交易查询 endpoint（生产 vsp.allinpay.com / 测试 syb-test.allinpay.com） */
    private static final String QUERY_TRX_PATH = "/apiweb/tranx/query";

    @Resource
    private AllinpayProperties props;

    @Resource
    private MerchantPackageOrderMapper packageOrderMapper;

    @Resource
    private MerchantPackageOrderService packageOrderService;

    /**
     * trade_order 已支付适配器（按 reqsn T 前缀路由）。
     * 用 ObjectProvider 延迟注入避免 / 防循环依赖（trade 模块也可能反向引用）。
     */
    @Resource
    private org.springframework.beans.factory.ObjectProvider<TradeOrderAllinpayService> tradeOrderAllinpayServiceProvider;

    /** trade mapper 用 ObjectProvider 避免循环（trade 模块也可能反向注入 merchant） */
    @Resource
    private org.springframework.beans.factory.ObjectProvider<cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper> tradeOrderMapperProvider;
    @Resource
    private org.springframework.beans.factory.ObjectProvider<cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper> tradeOrderItemMapperProvider;
    @Resource
    private org.springframework.beans.factory.ObjectProvider<cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper> shopInfoMapperProvider;

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
        if (!props.isH5Configured()) {
            log.error("[allinpay/cashier] 配置未就绪 signType={} appid={} merchantNo={}",
                    props.getSignType(), props.getAppid(), props.getMerchantNo());
            throw new IllegalStateException("通联收银台未配置（appid / merchant-no / 私钥）");
        }
        MerchantPackageOrderDO order = TenantUtils.executeIgnore(() -> packageOrderMapper.selectById(orderId));
        if (order == null) {
            throw new IllegalStateException("订单不存在: " + orderId);
        }
        log.info("[allinpay/cashier] 订单加载 orderId={} packageName={} priceFen={} payStatus={}",
                order.getId(), order.getPackageName(), order.getPrice(), order.getPayStatus());
        if (order.getPayStatus() != null
                && order.getPayStatus() != MerchantPackageOrderDO.PAY_STATUS_WAITING) {
            throw new IllegalStateException("订单非待支付状态，不可重复唤起收银台");
        }
        // 套餐场景：用全局 props 凭据
        return doBuildCashierForm(String.valueOf(order.getId()), order.getPrice(),
                order.getPackageName(), clientUserAgent, platformCredential());
    }

    /**
     * trade_order 调通联拿支付链接（mall 商品订单）。
     *
     * <p>reqsn 加 T 前缀区分 package_order；body 取首个商品 SPU 名称。</p>
     *
     * @param tradeOrderId trade_order.id
     * @param clientUserAgent 用户浏览器 UA（透传给通联，按 UA 推支付通道）
     * @return CashierForm 含 redirectUrl，前端 location.href 跳通联收银台
     */
    public CashierForm buildCashierFormForTrade(Long tradeOrderId, String clientUserAgent) {
        // 注意：不卡全局 props.isH5Configured() — trade 走每商户独立凭据，不依赖全局配置
        // 跨租户加载 trade_order（用 ObjectProvider 拿 trade mapper 避免循环依赖）
        cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO order =
                TenantUtils.executeIgnore(() -> tradeOrderMapperProvider.getIfAvailable() == null
                        ? null
                        : tradeOrderMapperProvider.getIfAvailable().selectById(tradeOrderId));
        if (order == null) {
            throw new IllegalStateException("trade_order 不存在: " + tradeOrderId);
        }
        if (order.getPayPrice() == null || order.getPayPrice() <= 0) {
            throw new IllegalStateException("订单 payPrice 异常 = " + order.getPayPrice());
        }
        if (Boolean.TRUE.equals(order.getPayStatus())) {
            throw new IllegalStateException("订单已支付，不可重复唤起收银台");
        }
        log.info("[allinpay/cashier-trade] tradeOrderId={} tenantId={} payPrice={} payStatus={}",
                order.getId(), order.getTenantId(), order.getPayPrice(), order.getPayStatus());

        // body 取首个订单项名称（含店铺标识便于通联账单识别）
        String body = "订单 " + order.getNo();
        try {
            cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO firstItem = null;
            if (tradeOrderItemMapperProvider.getIfAvailable() != null) {
                java.util.List<cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO> items =
                        TenantUtils.executeIgnore(() -> tradeOrderItemMapperProvider.getIfAvailable()
                                .selectListByOrderId(java.util.Collections.singletonList(tradeOrderId)));
                if (items != null && !items.isEmpty()) firstItem = items.get(0);
            }
            if (firstItem != null && firstItem.getSpuName() != null && !firstItem.getSpuName().isEmpty()) {
                body = firstItem.getSpuName();
            }
        } catch (Exception ignore) {}

        // 商品订单场景：用商户独立凭据（shop_info.tl_xxx）
        TlpayCredential cred = merchantCredentialForTenant(order.getTenantId());
        return doBuildCashierForm(TradeOrderAllinpayService.buildTradeReqsn(tradeOrderId),
                order.getPayPrice().longValue(), body, clientUserAgent, cred);
    }

    /**
     * 共用核心：构造 form / 签名 / POST 通联拿 302 跳转。
     *
     * @param cred 凭据（套餐用 platformCredential / 商品用 merchantCredentialForTenant）
     */
    private CashierForm doBuildCashierForm(String reqsn, long trxamtFen, String body,
                                           String clientUserAgent, TlpayCredential cred) {
        long t0 = System.currentTimeMillis();
        log.info("[allinpay/cashier] ───── START reqsn={} trxamt={} cusid={} ─────",
                reqsn, trxamtFen, cred.getCusId());
        String signType = cred.getSignType() == null ? "RSA" : cred.getSignType();
        boolean useOnepay = props.isUseOnepay();
        Map<String, String> p = new LinkedHashMap<>();
        p.put("cusid", cred.getCusId());
        p.put("appid", cred.getAppId());
        p.put("version", "12");
        p.put("trxamt", String.valueOf(trxamtFen));
        p.put("reqsn", reqsn);
        p.put("randomstr", randomStr());
        p.put("body", truncate(body, 64));
        if (useOnepay) {
            // 聚合收银台：front_url + 必填 expiretime
            p.put("front_url", props.getH5CashierReturnUrl());
            p.put("expiretime", new java.text.SimpleDateFormat("yyyyMMddHHmmss")
                    .format(new java.util.Date(System.currentTimeMillis() + 2 * 3600_000L)));
        } else {
            // unionorder：returl
            p.put("returl", props.getH5CashierReturnUrl());
        }
        // notify_url 用 credential 的（trade 每商户独立；空时 fallback 全局 props）
        p.put("notify_url", cred.getNotifyUrl() != null && !cred.getNotifyUrl().isEmpty()
                ? cred.getNotifyUrl() : props.getPayNotifyUrl());
        p.put("signtype", signType);

        String source = buildSignSource(p);
        String privFp = "SM2".equalsIgnoreCase(signType)
                ? keyFingerprint(cred.getSm2PrivateKey())
                : keyFingerprint(cred.getRsaPrivateKey());
        log.info("[allinpay/cashier] 签名 signType={} userId(=appid)={} privKeyFingerprint={} source={}",
                signType, cred.getAppId(), privFp, source);
        String sign = signWithCredential(p, cred);
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
        String cashierUrl = base.replaceAll("/+$", "")
                + (useOnepay ? H5_ONEPAY_PATH : H5_UNIONORDER_PATH);
        if (!java.util.Objects.equals(baseRaw, base)) {
            log.info("[allinpay/cashier] base URL 自动纠正 {} → {}", baseRaw, base);
        }
        // 关键：后端直接 POST 通联拿 302 Location，避开浏览器 form 编码差异
        // 之前用前端 form POST + body 含中文（"体验装 · 3 条"）→ 浏览器 Content-Type 不带
        // charset=UTF-8 → 通联 server 解码差异 → sign 验证失败。
        // 后端用 URLEncoder.encode + UTF-8 + Content-Type 显式声明 charset，跟我们签
        // 时用的字符串完全一致 → 通联接受，返 302 跳真实收银台。
        java.net.HttpURLConnection con = null;
        try {
            StringBuilder bodyStr = new StringBuilder();
            for (Map.Entry<String, String> e : p.entrySet()) {
                if (bodyStr.length() > 0) bodyStr.append('&');
                bodyStr.append(java.net.URLEncoder.encode(e.getKey(), "UTF-8")).append('=')
                       .append(java.net.URLEncoder.encode(e.getValue(), "UTF-8"));
            }
            con = (java.net.HttpURLConnection) new java.net.URL(cashierUrl).openConnection();
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
                drainAndClose(con);
                if (location.contains("exception.html")) {
                    log.error("[allinpay/cashier] 通联拒签 location={}", location);
                    throw new IllegalStateException("通联拒签：" + location);
                }
                log.info("[allinpay/cashier] ───── DONE reqsn={} redirectUrl={} cost={}ms ─────",
                        reqsn, location, System.currentTimeMillis() - t0);
                CashierForm res = new CashierForm(location, java.util.Collections.emptyMap());
                res.setRedirect(true);
                res.setRedirectUrl(location);
                return res;
            }
            // 非 302：通联出错（防爬 / 服务异常）— 不再 fallback 回前端 form POST
            // commit 9e0ad98 已证实前端 form POST 必 sign 错，回退路径反而把用户送进死路
            String errBody = readAndClose(con);
            log.error("[allinpay/cashier] 通联非 302 异常 HTTP={} body={}",
                    httpCode, errBody.length() > 200 ? errBody.substring(0, 200) : errBody);
            throw new IllegalStateException("通联响应异常 HTTP=" + httpCode);
        } catch (IllegalStateException ise) {
            throw ise;
        } catch (Exception ex) {
            log.error("[allinpay/cashier] 后端打通联失败 reqsn={}", reqsn, ex);
            if (con != null) drainAndClose(con);
            throw new IllegalStateException("通联请求失败：" + ex.getMessage(), ex);
        }
    }

    /** 排空 stream + disconnect 防 socket 泄漏（M3 修复）。 */
    private static void drainAndClose(java.net.HttpURLConnection con) {
        try {
            java.io.InputStream is = con.getInputStream();
            if (is != null) { byte[] buf = new byte[2048]; while (is.read(buf) >= 0) {} is.close(); }
        } catch (Exception ignore) {}
        try {
            java.io.InputStream es = con.getErrorStream();
            if (es != null) { byte[] buf = new byte[2048]; while (es.read(buf) >= 0) {} es.close(); }
        } catch (Exception ignore) {}
        try { con.disconnect(); } catch (Exception ignore) {}
    }

    /** 读 errorStream 内容（容错）+ disconnect。 */
    private static String readAndClose(java.net.HttpURLConnection con) {
        StringBuilder sb = new StringBuilder();
        try {
            java.io.InputStream is = con.getErrorStream();
            if (is == null) is = con.getInputStream();
            if (is != null) {
                byte[] buf = new byte[2048];
                int n;
                while ((n = is.read(buf)) >= 0) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                is.close();
            }
        } catch (Exception ignore) {}
        try { con.disconnect(); } catch (Exception ignore) {}
        return sb.toString();
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

            // 1. 按 reqsn 业务类型加载对应凭据（trade 走商户独立凭据 / 套餐走全局凭据）
            TlpayCredential cred;
            Long tradeOrderId = TradeOrderAllinpayService.parseTradeOrderId(reqsn);
            if (tradeOrderId != null) {
                cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO order =
                        TenantUtils.executeIgnore(() -> tradeOrderMapperProvider.getIfAvailable() == null
                                ? null : tradeOrderMapperProvider.getIfAvailable().selectById(tradeOrderId));
                if (order == null) {
                    log.warn("[allinpay/notify] reqsn={} trade 订单不存在", reqsn);
                    return "fail:order_not_found";
                }
                try {
                    cred = merchantCredentialForTenant(order.getTenantId());
                } catch (IllegalStateException e) {
                    log.warn("[allinpay/notify] reqsn={} 加载商户凭据失败：{}", reqsn, e.getMessage());
                    return "fail:credential";
                }
            } else {
                cred = platformCredential();
            }
            String notifySignType = notifyParams.getOrDefault("signtype",
                    cred.getSignType() == null ? "RSA" : cred.getSignType());

            // 2. 用 credential 验签
            Map<String, String> verifyParams = new TreeMap<>(notifyParams);
            verifyParams.remove("sign");
            String source = buildSignSource(verifyParams);
            log.info("[allinpay/notify] 验签 signType={} userId(=appid)={} source={}",
                    notifySignType, cred.getAppId(), source);
            boolean ok = verifyWithCredential(verifyParams, sign, cred);
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

            // 3. 解析金额
            int trxamtFen;
            try { trxamtFen = Integer.parseInt(trxamtStr); }
            catch (Exception e) {
                log.warn("[allinpay/notify] 非法 trxamt={}", trxamtStr);
                return "fail:bad_amount";
            }

            // 4. reqsn 路由：T 前缀 = trade_order（商城订单），无前缀 = merchant_package_order（套餐）
            //    （tradeOrderId 在 step 1 已 parse 出来）
            if (tradeOrderId != null) {
                log.info("[allinpay/notify] 识别为 trade_order tradeOrderId={} amount={}",
                        tradeOrderId, trxamtFen);
                TradeOrderAllinpayService tradeSvc = tradeOrderAllinpayServiceProvider.getIfAvailable();
                if (tradeSvc == null) {
                    log.error("[allinpay/notify] TradeOrderAllinpayService 不可用，回 fail 让通联重试");
                    return "fail:service_unavailable";
                }
                tradeSvc.markTradeOrderPaid(tradeOrderId, trxamtFen);
                log.info("[allinpay/notify] ───── DONE trade reqsn={} amount={} cost={}ms ─────",
                        reqsn, trxamtFen, System.currentTimeMillis() - t0);
                return "success";
            }

            // 5. 套餐订单流程（reqsn = 纯数字，无前缀）
            Long oid;
            try { oid = Long.parseLong(reqsn); }
            catch (Exception e) {
                log.warn("[allinpay/notify] 非法 reqsn={}", reqsn);
                return "fail:bad_reqsn";
            }
            log.info("[allinpay/notify] 调 markPaidExternal (package) orderId={} amount={}", oid, trxamtFen);
            packageOrderService.markPaidExternal(oid, trxamtFen, "ALLINPAY_NOTIFY");
            log.info("[allinpay/notify] ───── DONE package reqsn={} amount={} cost={}ms ─────",
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
     * 主动查询通联订单状态（package_order 入口；reqsn=纯数字）。
     *
     * @return trxstatus，2000 = 成功；其它 = 未成功；null = 通信失败 / 查无此单
     */
    public QueryResult queryOrder(Long orderId) {
        return queryByReqsn(String.valueOf(orderId), platformCredential());
    }

    /**
     * 按 reqsn 字符串查通联订单 + 自动按业务类型加载凭据。
     *
     * <p>T 前缀 → trade_order，按 tenantId 加载商户凭据；纯数字 → package_order 用全局凭据。
     * 调用方不需要手工传 credential。</p>
     */
    public QueryResult queryByReqsn(String reqsn) {
        TlpayCredential cred;
        Long tradeOrderId = TradeOrderAllinpayService.parseTradeOrderId(reqsn);
        if (tradeOrderId != null) {
            // trade 业务：按订单 tenantId 加载商户凭据
            cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO order =
                    TenantUtils.executeIgnore(() -> tradeOrderMapperProvider.getIfAvailable() == null
                            ? null : tradeOrderMapperProvider.getIfAvailable().selectById(tradeOrderId));
            if (order == null) {
                log.warn("[allinpay/query] reqsn={} trade 订单不存在", reqsn);
                return null;
            }
            try {
                cred = merchantCredentialForTenant(order.getTenantId());
            } catch (IllegalStateException e) {
                log.warn("[allinpay/query] reqsn={} 加载商户凭据失败：{}", reqsn, e.getMessage());
                return null;
            }
        } else {
            cred = platformCredential();
        }
        return queryByReqsn(reqsn, cred);
    }

    /** 按 credential 查通联订单（核心方法）。 */
    public QueryResult queryByReqsn(String reqsn, TlpayCredential cred) {
        if (cred == null || cred.getCusId() == null || cred.getCusId().isEmpty()) {
            log.debug("[allinpay/query] credential 未就绪，跳过 reqsn={}", reqsn);
            return null;
        }
        long t0 = System.currentTimeMillis();
        String signType = cred.getSignType() == null ? "RSA" : cred.getSignType();
        Map<String, String> p = new LinkedHashMap<>();
        p.put("cusid", cred.getCusId());
        p.put("appid", cred.getAppId());
        p.put("reqsn", reqsn);
        p.put("randomstr", randomStr());
        p.put("signtype", signType);
        log.info("[allinpay/query] reqsn={} signType={} userId(=appid)={} source={}",
                reqsn, signType, cred.getAppId(), buildSignSource(p));
        p.put("sign", signWithCredential(p, cred));

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
            log.info("[allinpay/query] reqsn={} url={} resp={} cost={}ms",
                    reqsn, url, resp, System.currentTimeMillis() - t0);
            if (resp == null) return null;
            String retcode = String.valueOf(resp.getOrDefault("retcode", ""));
            if (!"SUCCESS".equals(retcode)) {
                log.warn("[allinpay/query] reqsn={} retcode={} retmsg={} errmsg={}",
                        reqsn, retcode, resp.get("retmsg"), resp.get("errmsg"));
                return null;
            }
            String trxstatus = String.valueOf(resp.getOrDefault("trxstatus", ""));
            String trxamt = String.valueOf(resp.getOrDefault("trxamt", "0"));
            int amt = 0;
            try { amt = Integer.parseInt(trxamt); } catch (Exception ignore) {}
            log.info("[allinpay/query] reqsn={} trxstatus={} trxamt={}（2000=成功 / 1001=无此交易 / 其它=进行中）",
                    reqsn, trxstatus, amt);
            return new QueryResult(trxstatus, amt);
        } catch (Exception e) {
            log.warn("[allinpay/query] reqsn={} url={} 查询异常: {}", reqsn, url, e.getMessage(), e);
            return null;
        }
    }

    // ============================================================
    // 签名工具
    // ============================================================

    /** 当前商户号在通联控制台配的签名类型（RSA / SM2）— 套餐场景用全局 props */
    private String resolveSignType() {
        String t = props.getSignType();
        return (t != null && "SM2".equalsIgnoreCase(t)) ? "SM2" : "RSA";
    }

    /** 按 signType 调对应签名方法（套餐用，读全局 props） */
    private String signWith(Map<String, String> params, String signType) {
        if ("SM2".equalsIgnoreCase(signType)) {
            return signSm2(params, props.getSm2PrivateKey(), props.getAppid());
        }
        return signRsa(params, props.getPlatformRsaPrivateKey());
    }

    /** 按 signType 验签（套餐用，读全局 props） */
    private boolean verifyWith(Map<String, String> params, String sign, String signType) {
        if ("SM2".equalsIgnoreCase(signType)) {
            return verifySm2(params, sign, props.getSm2PublicKey(), props.getAppid());
        }
        return verifyRsa(params, sign, props.getAllinpayRsaPublicKey());
    }

    // ============================================================
    // 凭据抽象：一商户一通联账号支持
    // ============================================================

    /**
     * 通联凭据数据类 —— 同一接口签名 / 验签所需的所有商户级字段。
     *
     * <p>套餐订单（merchant_package_order）：平台对商户收钱，用全局 {@link AllinpayProperties} 凭据
     *    → {@link #platformCredential()}
     * 商品订单（trade_order）：用户对商户付款，资金 T+1 直达商户账户，用每商户独立凭据
     *    → {@link #merchantCredentialForTenant(Long)}
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TlpayCredential {
        private String cusId;          // 商户号（cusid）
        private String appId;          // appid
        private String signType;       // RSA / SM2
        private String rsaPrivateKey;  // 商户 RSA 私钥（PEM）
        private String rsaPublicKey;   // 通联 RSA 公钥（PEM）
        private String sm2PrivateKey;
        private String sm2PublicKey;
        /** 异步通知地址（空则用全局 props.payNotifyUrl） */
        private String notifyUrl;
    }

    /** 全局凭据（套餐场景）— 读 AllinpayProperties */
    public TlpayCredential platformCredential() {
        return TlpayCredential.builder()
                .cusId(props.getMerchantNo())
                .appId(props.getAppid())
                .signType(resolveSignType())
                .rsaPrivateKey(props.getPlatformRsaPrivateKey())
                .rsaPublicKey(props.getAllinpayRsaPublicKey())
                .sm2PrivateKey(props.getSm2PrivateKey())
                .sm2PublicKey(props.getSm2PublicKey())
                .notifyUrl(props.getPayNotifyUrl())
                .build();
    }

    /**
     * 按商户租户加载凭据（商品订单场景）— 读 shop_info.tl_xxx。
     *
     * <p>shop_info.tl_rsa_private_key / tl_rsa_public_key 是 AES 加密存储，
     *    EncryptTypeHandler 会自动解密为明文 PEM。</p>
     */
    public TlpayCredential merchantCredentialForTenant(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO shop =
                TenantUtils.executeIgnore(() -> shopInfoMapperProvider.getIfAvailable() == null
                        ? null
                        : shopInfoMapperProvider.getIfAvailable().selectByTenantId(tenantId));
        if (shop == null) {
            throw new IllegalStateException("店铺不存在 tenantId=" + tenantId);
        }
        if (!Boolean.TRUE.equals(shop.getTlEnabled())) {
            throw new IllegalStateException("商户未启用通联支付 tenantId=" + tenantId);
        }
        if (shop.getTlMchId() == null || shop.getTlMchId().isEmpty()) {
            throw new IllegalStateException("商户通联 cusId 未配置 tenantId=" + tenantId);
        }
        String signType = shop.getTlSignType() != null && !shop.getTlSignType().isEmpty()
                ? shop.getTlSignType() : "RSA";
        // 商户级独立直清场景下没分 SM2/RSA 两套，统一存 rsa_private_key / rsa_public_key
        // SM2 字段留空（若商户用 SM2 签名则需要扩 shop_info 加 sm2_xxx 字段，本期不做）
        return TlpayCredential.builder()
                .cusId(shop.getTlMchId())
                .appId(shop.getTlAppId() != null && !shop.getTlAppId().isEmpty()
                        ? shop.getTlAppId() : props.getAppid())  // appId 空时 fallback 全局
                .signType(signType)
                .rsaPrivateKey(shop.getTlRsaPrivateKey())
                .rsaPublicKey(shop.getTlRsaPublicKey())
                .notifyUrl(shop.getTlNotifyUrl() != null && !shop.getTlNotifyUrl().isEmpty()
                        ? shop.getTlNotifyUrl() : props.getPayNotifyUrl())  // 空 fallback 全局
                .build();
    }

    /** 用 credential 签名（替代写死 props 的 signWith） */
    private String signWithCredential(Map<String, String> params, TlpayCredential cred) {
        if ("SM2".equalsIgnoreCase(cred.getSignType())) {
            return signSm2(params, cred.getSm2PrivateKey(), cred.getAppId());
        }
        return signRsa(params, cred.getRsaPrivateKey());
    }

    /** 用 credential 验签 */
    private boolean verifyWithCredential(Map<String, String> params, String sign, TlpayCredential cred) {
        if ("SM2".equalsIgnoreCase(cred.getSignType())) {
            return verifySm2(params, sign, cred.getSm2PublicKey(), cred.getAppId());
        }
        return verifyRsa(params, sign, cred.getRsaPublicKey());
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
