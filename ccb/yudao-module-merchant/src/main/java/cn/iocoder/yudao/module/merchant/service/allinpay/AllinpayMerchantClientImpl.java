package cn.iocoder.yudao.module.merchant.service.allinpay;

import cn.iocoder.yudao.module.merchant.config.AllinpayProperties;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 通联收付通 - 商户进件真实现
 *
 * <p>仅在 {@code merchant.allinpay.enabled=true} 时实例化；其他时候由
 * {@link AllinpayMerchantClientNoop} 兜底（编译期）或 Spring 不创建本 bean。</p>
 *
 * <p>API 端点（生产 https://vsp.allinpay.com）：</p>
 * <ul>
 *     <li>POST /apiweb/cusreg/cusreg — 商户进件提交</li>
 *     <li>POST /apiweb/cusreg/queryreg — 进件状态查询</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(prefix = "merchant.allinpay", name = "enabled", havingValue = "true")
@Slf4j
public class AllinpayMerchantClientImpl implements AllinpayMerchantClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private AllinpayProperties props;

    private volatile OkHttpClient http;

    private OkHttpClient http() {
        OkHttpClient c = http;
        if (c == null) {
            synchronized (this) {
                c = http;
                if (c == null) {
                    c = new OkHttpClient.Builder()
                            .connectTimeout(props.getConnectTimeoutSec(), TimeUnit.SECONDS)
                            .readTimeout(props.getReadTimeoutSec(), TimeUnit.SECONDS)
                            .build();
                    http = c;
                }
            }
        }
        return c;
    }

    @Override
    public OpenMerchantResult openMerchant(ShopInfoDO shop) {
        String outOrderId = "TX" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6);

        boolean personal = "4".equals(shop.getComproperty());
        String merchantName = shop.getMerchantFullName() != null && !shop.getMerchantFullName().isEmpty()
                ? shop.getMerchantFullName() : shop.getShopName();

        // 通联商户进件参数（对齐《商户进件》文档 merchantapi/add 字段；EncryptTypeHandler 读出已是明文）
        Map<String, String> params = new LinkedHashMap<>();
        params.put("orgid", safe(props.getOrgId()));
        params.put("cusid", safe(props.getOrgId()));            // 文档：cusid = orgid
        params.put("version", "11");
        params.put("randomstr", outOrderId);                    // 商户自生成随机串
        params.put("merchantid", outOrderId);                   // 代理商系统商户唯一识别号（反查回调用）
        params.put("merchantname", safe(merchantName));         // 商户名称
        params.put("shortname", safe(shop.getShopName()));      // 商户简称
        params.put("servicephone", safe(shop.getMobile()));     // 客服电话
        params.put("comproperty", safe(shop.getComproperty())); // 商户性质
        // 法人/经营者
        params.put("legal", safe(shop.getLegalName()));
        params.put("legalidtype", "01");                        // 默认身份证
        params.put("legalidno", safe(shop.getLegalIdNo()));
        params.put("legalidexpire", safe(shop.getLegalIdExpire(), "长期"));
        // 营业执照（非个人）
        if (!personal) {
            params.put("corpbusname", safe(merchantName));
            params.put("creditcode", safe(shop.getCreditCode()));
            params.put("creditcodeexpire", safe(shop.getCreditCodeExpire(), "长期"));
        }
        // 地址 / 联系人
        params.put("address", safe(shop.getAddress()));
        params.put("busaddress", safe(shop.getAddress()));
        params.put("contactperson", safe(shop.getLegalName()));
        params.put("contactphone", safe(shop.getMobile()));
        // 结算账户
        params.put("clearmode", "1");                           // 1=银行卡
        params.put("acctname", safe(shop.getSettleAcctName()));
        params.put("acctid", safe(shop.getSettleAcctNo()));
        params.put("accttype", safe(shop.getSettleAcctType()));
        params.put("accttp", "00");                             // 00=借记卡
        params.put("bankcode", safe(shop.getSettleBankCode()));
        params.put("cnapsno", safe(shop.getSettleCnapsNo()));
        params.put("notifyurl", safe(props.getRegisterNotifyUrl()));
        // 资质照（文档要求 URL；私有 TOS key 需现签可公网访问的 URL — 接入真实凭据时补签发逻辑）
        params.put("legalidpicfront", safe(shop.getIdCardFrontKey()));
        params.put("legalidpicback", safe(shop.getIdCardBackKey()));
        if (!personal) {
            params.put("corpbuspic", safe(shop.getBusinessLicenseKey()));
        }
        // TODO 真实进件还需附录编码：mccid(所属行业) / districtcode(所在区) / cnapsno/bankcode 附录映射，
        //      以及把私有 TOS key 现签成通联可拉取的临时 URL（注意有效期需覆盖审核时长）。

        params.put("sign", AllinpaySignUtils.signRequest(params, props.getPlatformRsaPrivateKey()));

        // 端点对齐《商户进件》文档：测试 /vsppcusapi/merchantapi/add，生产 /cusapi/merchantapi/add
        return postForm("/vsppcusapi/merchantapi/add", params, outOrderId);
    }

    @Override
    public OpenMerchantResult queryMerchantStatus(String outOrderId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("orgid", props.getOrgId());
        params.put("outorderid", outOrderId);
        params.put("sign", AllinpaySignUtils.signRequest(params, props.getPlatformRsaPrivateKey()));
        return postForm("/apiweb/cusreg/queryreg", params, outOrderId);
    }

    private OpenMerchantResult postForm(String path, Map<String, String> params, String outOrderId) {
        FormBody.Builder fb = new FormBody.Builder();
        params.forEach((k, v) -> fb.add(k, v == null ? "" : v));
        Request req = new Request.Builder()
                .url(props.getApiBaseUrl() + path)
                .post(fb.build())
                .build();
        try (Response resp = http().newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                log.warn("[allinpay] {} 返 {} body={}", path, resp.code(), body);
                throw new RuntimeException("通联接口返 " + resp.code());
            }
            JsonNode json = MAPPER.readTree(body);
            // 标准响应：{ retcode: '0000', retmsg: '成功', data: { mchntid: 'xxx', mchntkey: 'xxx', status: '01' } }
            String retcode = json.path("retcode").asText("");
            if (!"0000".equals(retcode) && !"SUCCESS".equalsIgnoreCase(retcode)) {
                String retmsg = json.path("retmsg").asText("通联返回错误");
                log.warn("[allinpay] {} 业务失败 retcode={} retmsg={}", path, retcode, retmsg);
                OpenMerchantResult fail = new OpenMerchantResult();
                fail.setOutOrderId(outOrderId);
                fail.setStatus(OpenMerchantResult.Status.REJECTED);
                fail.setRejectReason(retmsg);
                return fail;
            }
            JsonNode data = json.path("data");
            OpenMerchantResult ok = new OpenMerchantResult();
            ok.setOutOrderId(outOrderId);
            ok.setTlMchId(data.path("mchntid").asText(null));
            ok.setTlMchKey(data.path("mchntkey").asText(null));
            String s = data.path("status").asText("01"); // 01=审核中 02=通过 03=驳回
            ok.setStatus("02".equals(s) ? OpenMerchantResult.Status.APPROVED
                    : "03".equals(s) ? OpenMerchantResult.Status.REJECTED
                    : OpenMerchantResult.Status.PENDING);
            return ok;
        } catch (IOException e) {
            log.error("[allinpay] {} 网络异常 outOrderId={}", path, outOrderId, e);
            throw new RuntimeException("通联接口网络异常：" + e.getMessage(), e);
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }

    /** 空值兜底为 def（用于有默认值的进件字段，如证件有效期默认"长期"）。 */
    private static String safe(String s, String def) { return s == null || s.isEmpty() ? def : s; }
}
