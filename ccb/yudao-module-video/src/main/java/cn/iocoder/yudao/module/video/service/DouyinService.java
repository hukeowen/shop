package cn.iocoder.yudao.module.video.service;

/**
 * 抖音 Service 接口
 * 负责 OAuth 授权和视频发布
 */
public interface DouyinService {

    /**
     * 获取抖音 OAuth 授权URL
     *
     * @param merchantId 商户编号
     * @param redirectUri 回调地址
     * @return 授权URL
     */
    String getOAuthUrl(Long merchantId, String redirectUri);

    /**
     * 处理抖音 OAuth 回调
     *
     * @param merchantId 商户编号
     * @param code 授权码
     */
    void handleOAuthCallback(Long merchantId, String code);

    /**
     * 刷新抖音 access_token
     *
     * @param merchantId 商户编号
     */
    void refreshAccessToken(Long merchantId);

    /**
     * 上传视频到抖音
     *
     * @param merchantId 商户编号
     * @param videoUrl   视频URL
     * @return 抖音视频ID
     */
    String uploadVideo(Long merchantId, String videoUrl);

    /**
     * 发布视频到抖音
     *
     * @param merchantId 商户编号
     * @param videoId    抖音视频ID
     * @param title      视频标题
     * @return 抖音作品ID (item_id)
     */
    String publishVideo(Long merchantId, String videoId, String title);

}
