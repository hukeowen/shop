package cn.iocoder.yudao.module.video.service;

import cn.hutool.core.io.IoUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantMapper;
import cn.iocoder.yudao.module.video.config.DouyinProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 抖音 Service 实现类
 * 完整实现 OAuth 授权 + 视频上传 + 发布
 */
@Service
@Slf4j
public class DouyinServiceImpl implements DouyinService {

    private static final String DOUYIN_OAUTH_URL = "https://open.douyin.com/platform/oauth/connect/";
    private static final String DOUYIN_ACCESS_TOKEN_URL = "https://open.douyin.com/oauth/access_token/";
    private static final String DOUYIN_REFRESH_TOKEN_URL = "https://open.douyin.com/oauth/renew_refresh_token/";
    private static final String DOUYIN_UPLOAD_VIDEO_URL = "https://open.douyin.com/api/douyin/v1/video/upload_video/";
    private static final String DOUYIN_CREATE_VIDEO_URL = "https://open.douyin.com/api/douyin/v1/video/create_video/";

    @Resource
    private DouyinProperties douyinProperties;

    @Resource
    private MerchantMapper merchantMapper;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    @Override
    public String getOAuthUrl(Long merchantId, String redirectUri) {
        return DOUYIN_OAUTH_URL
                + "?client_key=" + douyinProperties.getClientKey()
                + "&response_type=code"
                + "&scope=user_info,video.create,video.publish,video.data"
                + "&redirect_uri=" + redirectUri
                + "&state=" + merchantId;
    }

    @Override
    public void handleOAuthCallback(Long merchantId, String code) {
        log.info("[handleOAuthCallback] 商户({}) 抖音授权回调", merchantId);

        Map<String, String> params = new HashMap<>();
        params.put("client_key", douyinProperties.getClientKey());
        params.put("client_secret", douyinProperties.getClientSecret());
        params.put("code", code);
        params.put("grant_type", "authorization_code");

        Request request = new Request.Builder()
                .url(DOUYIN_ACCESS_TOKEN_URL)
                .post(RequestBody.create(JsonUtils.toJsonString(params), MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new RuntimeException("获取抖音access_token失败: " + response.code() + ", body: " + responseBody);
            }
            Map<String, Object> result = JsonUtils.parseObject(responseBody, Map.class);
            Map<String, Object> data = (Map<String, Object>) result.get("data");

            String accessToken = (String) data.get("access_token");
            String refreshToken = (String) data.get("refresh_token");
            String openId = (String) data.get("open_id");
            Integer expiresIn = (Integer) data.get("expires_in");

            log.info("[handleOAuthCallback] 获取token成功, openId: {}, expiresIn: {}s", openId, expiresIn);

            updateMerchantDouyinToken(merchantId, accessToken, refreshToken, openId,
                    LocalDateTime.now().plusSeconds(expiresIn));

        } catch (IOException e) {
            log.error("[handleOAuthCallback] 获取access_token异常", e);
            throw new RuntimeException("获取抖音access_token异常", e);
        }
    }

    @Override
    public void refreshAccessToken(Long merchantId) {
        log.info("[refreshAccessToken] 商户({}) 刷新抖音token", merchantId);
        String refreshToken = getMerchantRefreshToken(merchantId);
        if (refreshToken == null) {
            throw new RuntimeException("商户未绑定抖音账号");
        }

        Map<String, String> params = new HashMap<>();
        params.put("client_key", douyinProperties.getClientKey());
        params.put("refresh_token", refreshToken);

        Request request = new Request.Builder()
                .url(DOUYIN_REFRESH_TOKEN_URL)
                .post(RequestBody.create(JsonUtils.toJsonString(params), MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new RuntimeException("刷新抖音token失败: " + responseBody);
            }
            Map<String, Object> result = JsonUtils.parseObject(responseBody, Map.class);
            Map<String, Object> data = (Map<String, Object>) result.get("data");

            String newAccessToken = (String) data.get("access_token");
            String newRefreshToken = (String) data.get("refresh_token");
            Integer expiresIn = (Integer) data.get("expires_in");

            updateMerchantDouyinToken(merchantId, newAccessToken, newRefreshToken, null,
                    LocalDateTime.now().plusSeconds(expiresIn));

            log.info("[refreshAccessToken] token刷新成功");
        } catch (IOException e) {
            log.error("[refreshAccessToken] 刷新token异常", e);
            throw new RuntimeException("刷新抖音token异常", e);
        }
    }

    @Override
    public String uploadVideo(Long merchantId, String videoUrl) {
        log.info("[uploadVideo] 商户({}) 上传视频到抖音", merchantId);
        String accessToken = getValidAccessToken(merchantId);
        String openId = getMerchantOpenId(merchantId);

        // 1. 下载视频文件
        byte[] videoBytes;
        try {
            InputStream is = new URL(videoUrl).openStream();
            videoBytes = IoUtil.readBytes(is);
        } catch (IOException e) {
            throw new RuntimeException("下载视频文件失败: " + videoUrl, e);
        }

        // 2. 上传到抖音
        RequestBody fileBody = RequestBody.create(videoBytes, MediaType.parse("video/mp4"));
        MultipartBody multipartBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("video", "video.mp4", fileBody)
                .build();

        Request request = new Request.Builder()
                .url(DOUYIN_UPLOAD_VIDEO_URL + "?open_id=" + openId)
                .post(multipartBody)
                .addHeader("access-token", accessToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new RuntimeException("上传视频到抖音失败: " + responseBody);
            }
            Map<String, Object> result = JsonUtils.parseObject(responseBody, Map.class);
            Map<String, Object> data = (Map<String, Object>) result.get("data");

            String videoId = (String) data.get("video_id");
            log.info("[uploadVideo] 视频上传成功, videoId: {}", videoId);
            return videoId;
        } catch (IOException e) {
            log.error("[uploadVideo] 上传视频异常", e);
            throw new RuntimeException("上传视频到抖音异常", e);
        }
    }

    @Override
    public String publishVideo(Long merchantId, String videoId, String title) {
        log.info("[publishVideo] 商户({}) 发布视频, videoId: {}", merchantId, videoId);
        String accessToken = getValidAccessToken(merchantId);
        String openId = getMerchantOpenId(merchantId);

        Map<String, Object> body = new HashMap<>();
        body.put("video_id", videoId);
        body.put("text", title);

        Request request = new Request.Builder()
                .url(DOUYIN_CREATE_VIDEO_URL + "?open_id=" + openId)
                .post(RequestBody.create(JsonUtils.toJsonString(body), MediaType.parse("application/json")))
                .addHeader("access-token", accessToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new RuntimeException("发布视频到抖音失败: " + responseBody);
            }
            Map<String, Object> result = JsonUtils.parseObject(responseBody, Map.class);
            Map<String, Object> data = (Map<String, Object>) result.get("data");

            String itemId = (String) data.get("item_id");
            log.info("[publishVideo] 视频发布成功, itemId: {}", itemId);
            return itemId;
        } catch (IOException e) {
            log.error("[publishVideo] 发布视频异常", e);
            throw new RuntimeException("发布视频到抖音异常", e);
        }
    }

    // ========== 商户抖音Token读写 ==========

    private void updateMerchantDouyinToken(Long merchantId, String accessToken,
                                            String refreshToken, String openId,
                                            LocalDateTime expireTime) {
        MerchantDO updateObj = new MerchantDO();
        updateObj.setId(merchantId);
        updateObj.setDouyinAccessToken(accessToken);
        if (refreshToken != null) {
            updateObj.setDouyinRefreshToken(refreshToken);
        }
        if (openId != null) {
            updateObj.setDouyinOpenId(openId);
        }
        updateObj.setDouyinTokenExpireTime(expireTime);
        merchantMapper.updateById(updateObj);
        log.info("[updateMerchantDouyinToken] 商户({}) 抖音token已更新", merchantId);
    }

    private String getMerchantAccessToken(Long merchantId) {
        MerchantDO merchant = merchantMapper.selectById(merchantId);
        return merchant != null ? merchant.getDouyinAccessToken() : null;
    }

    private String getMerchantRefreshToken(Long merchantId) {
        MerchantDO merchant = merchantMapper.selectById(merchantId);
        return merchant != null ? merchant.getDouyinRefreshToken() : null;
    }

    private String getMerchantOpenId(Long merchantId) {
        MerchantDO merchant = merchantMapper.selectById(merchantId);
        return merchant != null ? merchant.getDouyinOpenId() : null;
    }

    private LocalDateTime getMerchantTokenExpireTime(Long merchantId) {
        MerchantDO merchant = merchantMapper.selectById(merchantId);
        return merchant != null ? merchant.getDouyinTokenExpireTime() : null;
    }

    /**
     * 获取有效的 access_token，如果过期则自动刷新
     */
    private String getValidAccessToken(Long merchantId) {
        String accessToken = getMerchantAccessToken(merchantId);
        LocalDateTime expireTime = getMerchantTokenExpireTime(merchantId);
        if (accessToken == null) {
            throw new RuntimeException("商户未绑定抖音账号");
        }
        if (expireTime != null && expireTime.isBefore(LocalDateTime.now().plusMinutes(10))) {
            refreshAccessToken(merchantId);
            accessToken = getMerchantAccessToken(merchantId);
        }
        return accessToken;
    }

}
