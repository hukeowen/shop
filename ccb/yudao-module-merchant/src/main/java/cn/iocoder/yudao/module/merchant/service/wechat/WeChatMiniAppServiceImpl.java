package cn.iocoder.yudao.module.merchant.service.wechat;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.merchant.config.WeChatMiniAppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.PHONE_DECRYPT_FAILED;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.WX_LOGIN_FAILED;

/**
 * 微信小程序服务实现
 */
@Service
@Slf4j
public class WeChatMiniAppServiceImpl implements WeChatMiniAppService {

    private static final String REDIS_KEY_PREFIX = "wx:session:";

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    /** access_token 内存缓存（单例服务，进程内共享）。提前 5 分钟刷新，避免临界过期。 */
    private volatile String cachedAccessToken;
    private volatile long accessTokenExpireAt; // epoch millis，到点前视为有效

    @Resource
    private WeChatMiniAppProperties properties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile OkHttpClient okHttpClient;

    private OkHttpClient client() {
        OkHttpClient c = this.okHttpClient;
        if (c == null) {
            synchronized (this) {
                if (this.okHttpClient == null) {
                    this.okHttpClient = new OkHttpClient.Builder()
                            .connectTimeout(properties.getHttpTimeoutSeconds(), TimeUnit.SECONDS)
                            .readTimeout(properties.getHttpTimeoutSeconds(), TimeUnit.SECONDS)
                            .writeTimeout(properties.getHttpTimeoutSeconds(), TimeUnit.SECONDS)
                            .build();
                }
                c = this.okHttpClient;
            }
        }
        return c;
    }

    @Override
    public Jscode2SessionResult jscode2session(String jsCode) {
        if (StrUtil.isBlank(properties.getAppId()) || StrUtil.isBlank(properties.getAppSecret())) {
            log.error("[jscode2session] WECHAT_MINI_APP_ID/SECRET 未配置，无法换取 openid");
            throw exception(WX_LOGIN_FAILED);
        }
        HttpUrl url = HttpUrl.parse(properties.getJscode2sessionUrl());
        if (url == null) {
            throw exception(WX_LOGIN_FAILED);
        }
        HttpUrl fullUrl = url.newBuilder()
                .addQueryParameter("appid", properties.getAppId())
                .addQueryParameter("secret", properties.getAppSecret())
                .addQueryParameter("js_code", jsCode)
                .addQueryParameter("grant_type", "authorization_code")
                .build();
        Request request = new Request.Builder().url(fullUrl).get().build();
        try (Response response = client().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("[jscode2session] 微信返回非 2xx，code={}", response.code());
                throw exception(WX_LOGIN_FAILED);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw exception(WX_LOGIN_FAILED);
            }
            String bodyStr = body.string();
            JsonNode node = objectMapper.readTree(bodyStr);
            Jscode2SessionResult result = new Jscode2SessionResult();
            if (node.has("openid")) {
                result.setOpenid(node.get("openid").asText());
            }
            if (node.has("unionid")) {
                result.setUnionid(node.get("unionid").asText());
            }
            if (node.has("session_key")) {
                result.setSessionKey(node.get("session_key").asText());
            }
            if (node.has("errcode")) {
                result.setErrcode(node.get("errcode").asInt());
            }
            if (node.has("errmsg")) {
                result.setErrmsg(node.get("errmsg").asText());
            }
            if (!result.isSuccess()) {
                log.warn("[jscode2session] 微信返回失败，errcode={}, errmsg={}",
                        result.getErrcode(), result.getErrmsg());
                throw exception(WX_LOGIN_FAILED);
            }
            log.info("[jscode2session] 成功，openidPrefix={}",
                    maskOpenid(result.getOpenid()));
            return result;
        } catch (IOException e) {
            log.warn("[jscode2session] 调用微信接口 IO 异常：{}", e.getMessage());
            throw exception(WX_LOGIN_FAILED);
        }
    }

    @Override
    public String decryptPhoneEncryptedData(String sessionKey, String encryptedData, String iv) {
        if (StrUtil.isBlank(sessionKey) || StrUtil.isBlank(encryptedData) || StrUtil.isBlank(iv)) {
            throw exception(PHONE_DECRYPT_FAILED);
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(sessionKey);
            byte[] dataBytes = Base64.getDecoder().decode(encryptedData);
            byte[] ivBytes = Base64.getDecoder().decode(iv);

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            // 微信要求 AES-128-CBC + PKCS7；Java 内置的 PKCS5Padding 对 16 字节块等价于 PKCS7
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(dataBytes);
            String plainJson = new String(decrypted, StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(plainJson);
            if (!node.has("phoneNumber")) {
                log.warn("[decryptPhoneEncryptedData] 解密成功但缺少 phoneNumber 字段");
                throw exception(PHONE_DECRYPT_FAILED);
            }
            String phone = node.get("phoneNumber").asText();
            if (StrUtil.isBlank(phone)) {
                throw exception(PHONE_DECRYPT_FAILED);
            }
            return phone;
        } catch (Exception e) {
            // 不输出 sessionKey/encryptedData/iv，任何一段都是敏感材料
            log.warn("[decryptPhoneEncryptedData] 解密失败：{}", e.getClass().getSimpleName());
            throw exception(PHONE_DECRYPT_FAILED);
        }
    }

    @Override
    public String getSessionKey(String openid) {
        if (StrUtil.isBlank(openid)) {
            return null;
        }
        return stringRedisTemplate.opsForValue().get(REDIS_KEY_PREFIX + openid);
    }

    @Override
    public void cacheSessionKey(String openid, String sessionKey) {
        if (StrUtil.isBlank(openid) || StrUtil.isBlank(sessionKey)) {
            return;
        }
        stringRedisTemplate.opsForValue().set(
                REDIS_KEY_PREFIX + openid, sessionKey,
                Duration.ofSeconds(properties.getSessionKeyTtlSeconds()));
    }

    @Override
    public String getAccessToken() {
        long now = System.currentTimeMillis();
        String token = this.cachedAccessToken;
        if (token != null && now < this.accessTokenExpireAt) {
            return token;
        }
        synchronized (this) {
            // double-check：并发时只有一个线程去刷新
            if (this.cachedAccessToken != null && System.currentTimeMillis() < this.accessTokenExpireAt) {
                return this.cachedAccessToken;
            }
            if (StrUtil.isBlank(properties.getAppId()) || StrUtil.isBlank(properties.getAppSecret())) {
                throw new IllegalStateException("WECHAT_MINI_APP_ID/SECRET 未配置，无法获取 access_token");
            }
            // ⚠ 用 stable_token（稳定版）而非 cgi-bin/token：小程序 access_token 是全局单例，
            //   会员登录(SocialClient)也在获取普通 token，两边都用 cgi-bin/token 会互相把对方冲失效
            //   (errcode 40001 invalid credential)。stable_token 独立、可频繁获取、不被普通 token 冲掉。
            HttpUrl url = new HttpUrl.Builder()
                    .scheme("https")
                    .host("api.weixin.qq.com")
                    .addPathSegments("cgi-bin/stable_token")
                    .build();
            String reqBody = objectMapper.createObjectNode()
                    .put("grant_type", "client_credential")
                    .put("appid", properties.getAppId())
                    .put("secret", properties.getAppSecret())
                    .put("force_refresh", false)
                    .toString();
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(JSON_MEDIA_TYPE, reqBody))
                    .build();
            try (Response response = client().newCall(request).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    throw new IllegalStateException("获取 access_token 失败，HTTP code=" + response.code());
                }
                JsonNode node = objectMapper.readTree(body.string());
                if (!node.has("access_token")) {
                    throw new IllegalStateException("获取 access_token 失败，errcode="
                            + (node.has("errcode") ? node.get("errcode").asInt() : -1)
                            + ", errmsg=" + (node.has("errmsg") ? node.get("errmsg").asText() : "unknown"));
                }
                String newToken = node.get("access_token").asText();
                long expiresIn = node.has("expires_in") ? node.get("expires_in").asLong() : 7200L;
                // 提前 5 分钟过期，避免临界拿到即将失效的 token
                this.accessTokenExpireAt = System.currentTimeMillis() + Math.max(60L, expiresIn - 300L) * 1000L;
                this.cachedAccessToken = newToken;
                log.info("[getAccessToken] 刷新成功，expiresIn={}s", expiresIn);
                return newToken;
            } catch (IOException e) {
                throw new IllegalStateException("获取 access_token IO 异常：" + e.getMessage(), e);
            }
        }
    }

    @Override
    public byte[] getUnlimitedQRCode(String scene, String page, String envVersion) {
        if (StrUtil.isBlank(scene)) {
            throw new IllegalStateException("生成小程序码失败：scene 不能为空");
        }
        String token = getAccessToken();
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.weixin.qq.com")
                .addPathSegments("wxa/getwxacodeunlimit")
                .addQueryParameter("access_token", token)
                .build();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scene", scene);
        if (StrUtil.isNotBlank(page)) {
            payload.put("page", page);
        }
        payload.put("check_path", false);
        payload.put("env_version", StrUtil.isNotBlank(envVersion) ? envVersion : "release");
        payload.put("width", 280);
        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            throw new IllegalStateException("生成小程序码失败：序列化请求体异常", e);
        }
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(JSON_MEDIA_TYPE, bodyJson))
                .build();
        try (Response response = client().newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IllegalStateException("生成小程序码失败，HTTP code=" + response.code());
            }
            byte[] bytes = body.bytes();
            // 微信成功返回 PNG 二进制；失败返回 JSON（以 '{' 开头）。据首字节判定。
            if (bytes.length > 0 && bytes[0] == '{') {
                String errStr = new String(bytes, StandardCharsets.UTF_8);
                JsonNode node = objectMapper.readTree(errStr);
                throw new IllegalStateException("生成小程序码失败，errcode="
                        + (node.has("errcode") ? node.get("errcode").asInt() : -1)
                        + ", errmsg=" + (node.has("errmsg") ? node.get("errmsg").asText() : errStr));
            }
            return bytes;
        } catch (IOException e) {
            throw new IllegalStateException("生成小程序码 IO 异常：" + e.getMessage(), e);
        }
    }

    /**
     * 日志脱敏 openid（只保留前 8 位）
     */
    private static String maskOpenid(String openid) {
        if (openid == null || openid.length() <= 8) {
            return "***";
        }
        return openid.substring(0, 8) + "***";
    }
}
