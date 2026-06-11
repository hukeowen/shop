package cn.iocoder.yudao.module.merchant.controller.admin;

import cn.hutool.crypto.SecureUtil;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.merchant.config.AllinpayProperties;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.service.allinpay.AllinpaySignUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 通联收付通异步通知 webhook
 *
 * <p>路径 {@code /admin-api/merchant/pay/tl-notify} 必须在 yudao.security.permit-all_urls
 * 白名单内（通联回调没有 yudao 自己的 token），靠通联公钥验签做身份验证。</p>
 *
 * <p>响应：必须严格按通联约定返 {@code SUCCESS}（plain text）才视为接收成功，
 * 否则通联会重试，最多重试 5 次，间隔递增。</p>
 */
@Tag(name = "管理后台 - 通联异步通知")
@RestController
@RequestMapping("/merchant")
@Slf4j
@cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore  // 通联回调没有 tenant-id header
public class AllinpayNotifyController {

    @Resource
    private AllinpayProperties props;
    @Resource
    private ShopInfoMapper shopInfoMapper;
    @Resource
    private cn.iocoder.yudao.module.merchant.service.allinpay.AllinpayCashierService cashierService;

    @Value("${merchant.field-encrypt-key:dev_key_12345678}")
    private String fieldEncryptKey;

    @Value("${ALLINPAY_DIAG_TOKEN:}")
    private String diagToken;

    /**
     * 进件对接诊断（测试用）：用指定 tenant 的通联凭据（appid + RSA 私钥）签名，
     * 提交一笔【全测试数据】的进件到通联，返回原始响应，用来验证「对接 / 签名 / 端点」是否通。
     *
     * <p>用测试数据（不含真实身份证），即便通联因字段不合法驳回，也能证明：
     * 签名通过 + 端点正确 + 请求被解析 = 对接成功。失败的进件可再次提交，不留垃圾记录。</p>
     *
     * <p>token 校验：必须传对 ALLINPAY_DIAG_TOKEN，否则拒绝。已加入 permit-all 白名单。</p>
     */
    @PostMapping(value = "/allinpay/diag-register", produces = "application/json")
    @Operation(summary = "通联进件对接诊断（测试）")
    public Map<String, Object> diagRegister(
            @RequestParam("token") String token,
            @RequestParam(value = "credTenantId", defaultValue = "1010") Long credTenantId,
            @RequestParam(value = "appidOverride", required = false) String appidOverride,
            @RequestParam(value = "signMode", defaultValue = "rsa1") String signMode,
            @RequestParam(value = "md5KeyOverride", required = false) String md5KeyOverride,
            @RequestParam(value = "url", required = false) String url,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        Map<String, Object> out = new HashMap<>();
        if (diagToken == null || diagToken.isEmpty() || !diagToken.equals(token)) {
            out.put("ok", false);
            out.put("error", "token 无效");
            return out;
        }
        // 取代理商进件凭据（appid + RSA 私钥），shop_info 平台级表，TenantIgnore 读
        ShopInfoDO cred = TenantUtils.executeIgnore(() -> {
            return shopInfoMapper.selectByTenantId(credTenantId);
        });
        if (cred == null) {
            out.put("ok", false);
            out.put("error", "凭据店铺不存在 tenant=" + credTenantId);
            return out;
        }
        String orgid = props.getOrgId();
        String appid = (appidOverride != null && !appidOverride.isEmpty()) ? appidOverride : cred.getTlAppId();
        String rsaPriv = cred.getTlRsaPrivateKey(); // EncryptTypeHandler 读出已是明文
        String testNo = "TEST" + System.currentTimeMillis();

        // 构造一笔【测试】进件（全部测试值，带「测试」字样，便于识别且不含真实 PII）
        Map<String, String> p = new LinkedHashMap<>();
        p.put("orgid", nz(orgid));
        p.put("cusid", nz(orgid));
        p.put("appid", nz(appid));
        p.put("version", "11");
        p.put("randomstr", testNo);
        p.put("merchantid", testNo);
        p.put("merchantname", "测试商户请勿审核");
        p.put("shortname", "测试商户");
        p.put("servicephone", "02888888888");
        p.put("comproperty", "1");
        p.put("legal", "测试");
        p.put("legalidtype", "01");
        p.put("legalidno", "510104199001011234");
        p.put("legalidexpire", "长期");
        p.put("address", "四川省成都市测试地址");
        p.put("busaddress", "四川省成都市测试地址");
        p.put("contactperson", "测试");
        p.put("contactphone", "02888888888");
        p.put("clearmode", "1");
        p.put("acctname", "测试商户");
        p.put("acctid", "6225880000000000");
        p.put("accttype", "1");
        p.put("accttp", "00");
        p.put("bankcode", "0102");
        p.put("creditcode", "91510100000000000X");

        String sm2Priv = cred.getTlSm2PrivateKey();
        String sign;
        try {
            if ("sm2".equalsIgnoreCase(signMode)) {
                p.put("signtype", "SM2");
                sign = cn.iocoder.yudao.module.merchant.service.allinpay.AllinpayCashierService
                        .signSm2(p, sm2Priv, appid);
            } else if ("rsa".equalsIgnoreCase(signMode)) {
                // 旧：SHA256withRSA（收银宝实际是 SHA1，这个只留作对比）
                p.put("signtype", "RSA");
                sign = AllinpaySignUtils.signRequest(p, rsaPriv);
            } else if ("md5".equalsIgnoreCase(signMode)) {
                String md5Key = (md5KeyOverride != null && !md5KeyOverride.isEmpty())
                        ? md5KeyOverride : props.getMd5Key();
                sign = cn.hutool.crypto.SecureUtil.md5(
                        AllinpaySignUtils.buildSignSource(p) + "&key=" + nz(md5Key)).toUpperCase();
            } else {
                // 默认 rsa1：SHA1withRSA（收银宝标准，与收银台支付同款 signRsa）
                p.put("signtype", "RSA");
                sign = cn.iocoder.yudao.module.merchant.service.allinpay.AllinpayCashierService
                        .signRsa(p, rsaPriv);
            }
        } catch (Exception e) {
            out.put("ok", false);
            out.put("error", "签名失败：" + e.getMessage());
            out.put("signSource", AllinpaySignUtils.buildSignSource(p));
            return out;
        }
        String signSource = AllinpaySignUtils.buildSignSource(p); // 含 signtype，供核对
        p.put("sign", sign);

        out.put("orgid", orgid);
        out.put("appid", appid);
        out.put("signMode", signMode);
        out.put("credTenant", credTenantId);
        out.put("signSource", signSource);
        out.put("signLen", sign.length());

        if (dryRun) {
            out.put("ok", true);
            out.put("dryRun", true);
            return out;
        }

        String endpoint = (url != null && !url.isEmpty()) ? url
                : "https://cus.allinpay.com/cusapi/merchantapi/add";
        out.put("endpoint", endpoint);
        try {
            OkHttpClient http = new OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build();
            FormBody.Builder fb = new FormBody.Builder();
            p.forEach((k, v) -> fb.add(k, v == null ? "" : v));
            Request req = new Request.Builder().url(endpoint).post(fb.build()).build();
            try (Response resp = http.newCall(req).execute()) {
                String body = resp.body() != null ? resp.body().string() : "";
                out.put("ok", true);
                out.put("httpCode", resp.code());
                out.put("response", body.length() > 2000 ? body.substring(0, 2000) : body);
            }
        } catch (Exception e) {
            out.put("ok", false);
            out.put("error", "通联请求异常：" + e.getMessage());
        }
        return out;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    /**
     * 进件结果异步通知
     *
     * <p>通联 POST form-urlencoded：</p>
     * <pre>
     *   outorderid=TX1234...
     *   status=02   // 02=通过 03=驳回 04=待补充资料
     *   mchntid=8881234567890       // 仅 status=02
     *   mchntkey=ABCDEF....         // 仅 status=02
     *   rejectreason=...            // 仅 status=03
     *   sign=base64                  // RSA SHA256 签名
     * </pre>
     */
    @PostMapping(value = "/pay/tl-notify", produces = "text/plain;charset=UTF-8")
    @Operation(summary = "通联进件 / 支付结果异步通知")
    public String onAllinpayNotify(@RequestParam Map<String, String> params) {
        log.info("[allinpay-notify] receive params keys={}", params.keySet());

        String sign = params.remove("sign");
        if (sign == null || sign.isEmpty()) {
            log.warn("[allinpay-notify] 拒绝：missing sign");
            return "FAIL_SIGN_MISSING";
        }
        // 用通联公钥验签
        try {
            if (!AllinpaySignUtils.verifyNotify(params, sign, props.getAllinpayRsaPublicKey())) {
                log.warn("[allinpay-notify] 拒绝：sign 验证失败");
                return "FAIL_SIGN_INVALID";
            }
        } catch (Exception e) {
            log.warn("[allinpay-notify] 验签异常：{}", e.getMessage());
            return "FAIL_VERIFY_EXCEPTION";
        }

        String outOrderId = params.get("outorderid");
        if (outOrderId == null || outOrderId.isEmpty()) {
            return "FAIL_OUTORDERID_MISSING";
        }

        // 反查店铺；ShopInfoDO 是平台级表，不需要 tenant 上下文
        ShopInfoDO shop = TenantUtils.executeIgnore(() -> shopInfoMapper.selectByTlOpenOrderId(outOrderId));
        if (shop == null) {
            log.warn("[allinpay-notify] 找不到 outOrderId={} 对应的店铺", outOrderId);
            return "FAIL_ORDER_NOT_FOUND";
        }

        String status = params.get("status");
        ShopInfoDO update = new ShopInfoDO();
        update.setId(shop.getId());
        if ("02".equals(status)) {
            // 开户成功
            String mchntId = params.get("mchntid");
            String mchntKey = params.get("mchntkey");
            if (mchntId == null || mchntId.isEmpty()) {
                log.warn("[allinpay-notify] status=02 但 mchntid 为空 outOrderId={}", outOrderId);
                return "FAIL_MCHNT_ID_MISSING";
            }
            update.setTlMchId(mchntId);
            if (mchntKey != null && !mchntKey.isEmpty()) {
                update.setTlMchKey(SecureUtil.aes(fieldEncryptKey.getBytes()).encryptHex(mchntKey));
            }
            update.setOnlinePayEnabled(true);
            update.setPayApplyStatus(2);
            update.setPayApplyRejectReason("");
            log.info("[allinpay-notify] 店铺 {} 通联开户成功 mchId={}", shop.getId(), mchntId);
        } else if ("03".equals(status)) {
            update.setPayApplyStatus(3);
            update.setPayApplyRejectReason("通联拒绝：" + params.getOrDefault("rejectreason", ""));
            update.setOnlinePayEnabled(false);
            log.warn("[allinpay-notify] 店铺 {} 通联驳回 reason={}", shop.getId(), params.get("rejectreason"));
        } else {
            log.info("[allinpay-notify] 店铺 {} 状态变化 status={}（未变更落库）", shop.getId(), status);
            return "SUCCESS";  // 接收 + 不更新 也回 SUCCESS 防通联重试
        }

        TenantUtils.executeIgnore(() -> shopInfoMapper.updateById(update));
        // TODO P2-11 审计日志：记录这次状态变化（旧状态/新状态/通联原始 params）
        return "SUCCESS";
    }

    /** 占位健康检查（debug） */
    @PostMapping(value = "/pay/tl-notify-echo", produces = "application/json")
    public Map<String, Object> echo(@RequestParam Map<String, String> params) {
        Map<String, Object> r = new HashMap<>();
        r.put("ok", true);
        r.put("received", params.size());
        return r;
    }

    /**
     * 通联 H5 收银台支付结果异步通知（套餐购买）。
     *
     * <p>路径与 yaml {@code merchant.allinpay.pay-notify-url} 对齐：
     * {@code /admin-api/merchant/allinpay/pay-notify}
     * （RequestMapping=/merchant + 本方法 /allinpay/pay-notify，
     * yudao admin-api 前缀由 nginx 兜底）。</p>
     *
     * <p>必须返 "success" 文本（不要 JSON）让通联停止重试。</p>
     */
    @PostMapping(value = "/allinpay/pay-notify", produces = "text/plain;charset=UTF-8")
    @Operation(summary = "通联 H5 收银台 - 支付结果异步通知")
    public String onAllinpayPayNotify(@RequestParam Map<String, String> params) {
        log.info("[allinpay/pay-notify] receive keys={}", params.keySet());
        return cashierService.handlePayNotify(params);
    }

    /** 进件通知保留旧路径兼容（如有通联控制台 register-notify-url 写老地址） */
    @PostMapping(value = "/allinpay/register-notify", produces = "text/plain;charset=UTF-8")
    @Operation(summary = "通联进件 - 异步通知（兼容路径）")
    public String onAllinpayRegisterNotify(@RequestParam Map<String, String> params) {
        return onAllinpayNotify(params);
    }
}
