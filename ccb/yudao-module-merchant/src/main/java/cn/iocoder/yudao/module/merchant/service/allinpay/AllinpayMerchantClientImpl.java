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

        // 通联进件参数（关键字段，完整列表参见官方文档）
        Map<String, String> params = new LinkedHashMap<>();
        params.put("orgid", props.getOrgId());
        params.put("outorderid", outOrderId);
        params.put("mchntname", safe(shop.getShopName()));      // 商户名
        params.put("mchntshortname", safe(shop.getShopName()));  // 商户简称
        params.put("contact", safe(shop.getMobile()));            // 联系电话
        params.put("address", safe(shop.getAddress()));
        params.put("notifyurl", safe(props.getRegisterNotifyUrl()));
        // KYC 资质 TOS key（通联会反查我们这一侧拿真图，或要求我们 base64 上传 — 接入时按文档调整）
        params.put("idcardfront", safe(shop.getIdCardFrontKey()));
        params.put("idcardback", safe(shop.getIdCardBackKey()));
        params.put("license", safe(shop.getBusinessLicenseKey()));
        // TODO 生产前补全：法人姓名 / 法人身份证号 / 银行卡号 / 开户行 / 经营类目码 等

        params.put("sign", AllinpaySignUtils.signRequest(params, props.getPlatformRsaPrivateKey()));

        return postForm("/apiweb/cusreg/cusreg", params, outOrderId);
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
}
