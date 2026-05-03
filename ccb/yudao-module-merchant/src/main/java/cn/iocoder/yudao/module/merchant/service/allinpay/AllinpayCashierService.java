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

    private static final String SIGN_ALG = "SHA1withRSA";
    private static final String CHARSET = "UTF-8";
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

    /** 构造通联收银台请求参数 — 前端 form POST 跳转。 */
    public CashierForm buildCashierForm(Long orderId) {
        if (!props.isH5Configured()) {
            throw new IllegalStateException("通联收银台未配置（appid / merchant-no / rsa private key）");
        }
        MerchantPackageOrderDO order = TenantUtils.executeIgnore(() -> packageOrderMapper.selectById(orderId));
        if (order == null) {
            throw new IllegalStateException("订单不存在: " + orderId);
        }
        if (order.getPayStatus() != null
                && order.getPayStatus() != MerchantPackageOrderDO.PAY_STATUS_WAITING) {
            throw new IllegalStateException("订单非待支付状态，不可重复唤起收银台");
        }

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
        p.put("signtype", "RSA");
        p.put("sign", signRsa(p, props.getPlatformRsaPrivateKey()));

        String base = props.getApiBaseUrl();
        if (base == null || base.isEmpty()) {
            base = "https://syb.allinpay.com";
        } else {
            // 兼容：用户配的是 vsp 域名时纠正为 syb（H5 下单只走 syb）
            base = base.replace("vsp.allinpay.com", "syb.allinpay.com")
                       .replace("test-vsp.allinpay.com", "syb-test.allinpay.com");
        }
        String cashierUrl = base.replaceAll("/+$", "") + H5_UNIONORDER_PATH;

        log.info("[buildCashierForm] orderId={} cusid={} trxamt={} cashierUrl={}",
                order.getId(), props.getMerchantNo(), order.getPrice(), cashierUrl);

        return new CashierForm(cashierUrl, p);
    }

    // ============================================================
    // 2. 异步通知：通联 POST 上来 → 验签 → markPaidExternal
    // ============================================================

    @TenantIgnore  // 通联回调没有 tenant-id header；内部按订单反查 tenant 后切上下文
    public String handlePayNotify(Map<String, String> notifyParams) {
        if (notifyParams == null || notifyParams.isEmpty()) {
            return "fail:empty";
        }
        try {
            String reqsn = notifyParams.getOrDefault("cusorderid", notifyParams.get("reqsn"));
            String trxstatus = notifyParams.getOrDefault("trxstatus", "");
            String trxamtStr = notifyParams.getOrDefault("trxamt", "0");
            String sign = notifyParams.get("sign");

            // 1. RSA 验签
            Map<String, String> verifyParams = new TreeMap<>(notifyParams);
            verifyParams.remove("sign");
            if (!verifyRsa(verifyParams, sign, props.getAllinpayRsaPublicKey())) {
                log.warn("[allinpay/notify] RSA 验签失败 reqsn={}", reqsn);
                return "fail:sign";
            }

            // 2. 不是成功状态：通联仍要回 success 阻止重发
            if (!TRX_STATUS_SUCCESS.equals(trxstatus)) {
                log.info("[allinpay/notify] reqsn={} trxstatus={}（非成功，已收单不重发）", reqsn, trxstatus);
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

            // 4. 调外部回调专用 markPaid（不走 yudao pay_order 反查）
            packageOrderService.markPaidExternal(oid, trxamtFen, "ALLINPAY_NOTIFY");
            log.info("[allinpay/notify] reqsn={} 标记支付成功 amount={}", oid, trxamtFen);
            return "success";
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException se) {
            // 业务异常（金额不一致 / 订单不存在）— 已 log，回 fail 让通联重试不一定有用，记录即可
            log.warn("[allinpay/notify] 业务异常 code={} msg={}", se.getCode(), se.getMessage());
            return "fail:" + se.getCode();
        } catch (Exception e) {
            log.error("[allinpay/notify] 处理失败 params={}", notifyParams, e);
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
        if (!props.isH5Configured()) return null;

        Map<String, String> p = new LinkedHashMap<>();
        p.put("cusid", props.getMerchantNo());
        p.put("appid", props.getAppid());
        p.put("reqsn", String.valueOf(orderId));
        p.put("randomstr", randomStr());
        p.put("signtype", "RSA");
        p.put("sign", signRsa(p, props.getPlatformRsaPrivateKey()));

        String base = props.getApiBaseUrl();
        if (base == null || base.isEmpty()) base = "https://vsp.allinpay.com";
        else {
            base = base.replace("syb.allinpay.com", "vsp.allinpay.com")
                       .replace("syb-test.allinpay.com", "syb-test.allinpay.com"); // 测试环境查询也走 syb-test
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
            if (resp == null) return null;
            String retcode = String.valueOf(resp.getOrDefault("retcode", ""));
            if (!"SUCCESS".equals(retcode)) {
                log.info("[allinpay/query] reqsn={} retcode={} retmsg={}",
                        orderId, retcode, resp.get("retmsg"));
                return null;
            }
            String trxstatus = String.valueOf(resp.getOrDefault("trxstatus", ""));
            String trxamt = String.valueOf(resp.getOrDefault("trxamt", "0"));
            int amt = 0;
            try { amt = Integer.parseInt(trxamt); } catch (Exception ignore) {}
            return new QueryResult(trxstatus, amt);
        } catch (Exception e) {
            log.warn("[allinpay/query] reqsn={} 查询异常: {}", orderId, e.getMessage());
            return null;
        }
    }

    // ============================================================
    // 签名工具
    // ============================================================

    /** ASCII 升序拼 key=value& 拼接（跳过 sign 和空值），SHA1withRSA 私钥签名，Base64。 */
    public static String signRsa(Map<String, String> params, String pemPrivateKey) {
        if (pemPrivateKey == null || pemPrivateKey.isEmpty()) {
            throw new IllegalStateException("通联 RSA 私钥未配置");
        }
        String source = buildSignSource(params);
        try {
            byte[] der = Base64.getDecoder().decode(stripPem(pemPrivateKey));
            PrivateKey pk = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            Signature s = Signature.getInstance(SIGN_ALG);
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
            Signature s = Signature.getInstance(SIGN_ALG);
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
