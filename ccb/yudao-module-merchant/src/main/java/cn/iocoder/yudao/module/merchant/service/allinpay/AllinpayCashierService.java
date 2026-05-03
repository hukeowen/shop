package cn.iocoder.yudao.module.merchant.service.allinpay;

import cn.iocoder.yudao.module.merchant.config.AllinpayProperties;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantPackageOrderDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantPackageOrderMapper;
import cn.iocoder.yudao.module.merchant.service.MerchantPackageOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 通联收付通 H5 收银台桥接（套餐购买专用）。
 *
 * <p>通联 H5 网关有多种接入方式（被扫 / 主扫 / 公众号 / H5），最通用的是 H5 网关 form 提交：
 * 业务侧把订单参数 + MD5 签名拼成 form，引导浏览器 POST 到通联收银台 URL，
 * 用户付款完成后通联 GET return_url + POST notify_url 回调。</p>
 *
 * <p>本类不发起 HTTP 请求 — 它只构造好通联收银台需要的 form 字段，前端用拿到的
 * cashierUrl + form 字段拼出来重定向。最大限度避免对通联具体 API 路径的硬依赖
 * （沙箱/生产 endpoint 不一样、合同里的功能版本也不一样），实际线上由 prod 的
 * yaml {@code merchant.allinpay.api-base-url} 决定。</p>
 *
 * <p>签名规范（通联标准 MD5）：参数按 key 字典序排序 → 拼 key1=value1&...
 * → 末尾追加 "&key=" + md5Key → MD5(UTF-8) → 大写。</p>
 */
@Service
@Slf4j
public class AllinpayCashierService {

    @Resource
    private AllinpayProperties props;

    @Resource
    private MerchantPackageOrderMapper packageOrderMapper;

    @Resource
    private MerchantPackageOrderService packageOrderService;

    /** 通联 H5 收银台路径（如沙箱/生产不一样可在 yaml 覆盖；这里写最常见 URL） */
    private static final String H5_CASHIER_PATH = "/gateway/index.do";

    /**
     * 给业务订单构造 H5 收银台跳转参数。
     *
     * @param orderId merchant_package_order.id
     * @return cashierUrl + form 字段；前端 POST 跳转
     */
    public CashierForm buildCashierForm(Long orderId) {
        if (!props.isH5Configured()) {
            throw new IllegalStateException("通联 H5 收银台未配置（merchant.allinpay.appid/merchant-no/md5-key）");
        }
        MerchantPackageOrderDO order = packageOrderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalStateException("订单不存在: " + orderId);
        }
        if (order.getPayStatus() != null
                && order.getPayStatus() != MerchantPackageOrderDO.PAY_STATUS_WAITING) {
            throw new IllegalStateException("订单非待支付状态，不可重复唤起收银台");
        }

        // 1. 构造请求参数（通联收银台标准字段）
        Map<String, String> params = new TreeMap<>();
        params.put("inputCharset", "1");                 // 1=UTF-8
        params.put("pickupUrl", props.getH5CashierReturnUrl());   // 同步返回 URL
        params.put("receiveUrl", props.getPayNotifyUrl());        // 异步通知 URL
        params.put("version", "v1.0");
        params.put("language", "1");                     // 1=简体中文
        params.put("signType", "1");                     // 1=MD5
        params.put("merchantId", props.getMerchantNo());
        params.put("orderNo", String.valueOf(order.getId()));
        params.put("orderAmount", String.valueOf(order.getPrice())); // 单位：分
        params.put("orderCurrency", "0");
        params.put("orderDatetime", new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
        params.put("productName", truncate(order.getPackageName(), 64));
        params.put("ext1", "package:" + order.getPackageId());
        params.put("payType", "");                       // 留空，通联收银台让用户选

        // 2. MD5 签名（key 字典序 + 末尾追加 &key=md5Key 再 MD5 大写）
        String sign = signMd5(params, props.getMd5Key());
        params.put("signMsg", sign);

        // 3. 收银台 URL
        String base = props.getApiBaseUrl();
        if (base == null || base.isEmpty()) base = "https://test-vsp.allinpay.com";
        String cashierUrl = base.replaceAll("/+$", "") + H5_CASHIER_PATH;

        log.info("[buildCashierForm] orderId={} amount={} merchantNo={} cashierUrl={}",
                order.getId(), order.getPrice(), props.getMerchantNo(), cashierUrl);

        return new CashierForm(cashierUrl, params);
    }

    /**
     * 处理通联异步通知（pay-notify webhook）。
     *
     * @param notifyParams 通联 POST 上来的 form 参数
     * @return 文本响应：成功 "success"，失败 "fail"
     */
    public String handlePayNotify(Map<String, String> notifyParams) {
        if (notifyParams == null || notifyParams.isEmpty()) {
            return "fail:empty";
        }
        try {
            String orderNo = notifyParams.get("orderNo");
            String payResult = notifyParams.getOrDefault("payResult", "");
            String orderStatus = notifyParams.getOrDefault("orderStatus", "");
            String signMsg = notifyParams.getOrDefault("signMsg", "");

            // 1. MD5 验签
            Map<String, String> verifyParams = new TreeMap<>(notifyParams);
            verifyParams.remove("signMsg");
            String expected = signMd5(verifyParams, props.getMd5Key());
            if (!expected.equalsIgnoreCase(signMsg)) {
                log.warn("[allinpayPayNotify] MD5 验签失败 orderNo={} expected={} got={}",
                        orderNo, expected, signMsg);
                return "fail:sign";
            }

            // 2. 校验支付结果（payResult=1 / orderStatus=SUCCESS 视为成功；不同版本字段名可能略不同）
            boolean paid = "1".equals(payResult)
                    || "SUCCESS".equalsIgnoreCase(orderStatus)
                    || "0000".equals(notifyParams.get("retCode"));
            if (!paid) {
                log.info("[allinpayPayNotify] 非成功状态 orderNo={} payResult={} orderStatus={}",
                        orderNo, payResult, orderStatus);
                return "success"; // 通联要求收到就回 success，避免重发
            }

            // 3. 调业务侧 markPaid（内部已 CAS + 加配额）
            Long oid = Long.parseLong(orderNo);
            packageOrderService.markPaid(oid, null);
            log.info("[allinpayPayNotify] 订单 {} 通联回调标记支付成功", oid);
            return "success";
        } catch (Exception e) {
            log.error("[allinpayPayNotify] 处理失败 params={}", notifyParams, e);
            return "fail:" + e.getMessage();
        }
    }

    private static String signMd5(Map<String, String> params, String md5Key) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (v == null || v.isEmpty() || "signMsg".equalsIgnoreCase(k) || "signType".equalsIgnoreCase(k)) {
                continue;
            }
            if (sb.length() > 0) sb.append('&');
            sb.append(k).append('=').append(v);
        }
        sb.append("&key=").append(md5Key);
        return md5Hex(sb.toString()).toUpperCase();
    }

    private static String md5Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] dig = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class CashierForm {
        /** 通联收银台 URL（POST 目标） */
        private String cashierUrl;
        /** 表单字段（包含 signMsg） */
        private Map<String, String> params;
    }
}
