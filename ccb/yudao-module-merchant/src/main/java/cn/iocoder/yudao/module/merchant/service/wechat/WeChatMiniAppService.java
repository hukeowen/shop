package cn.iocoder.yudao.module.merchant.service.wechat;

/**
 * 微信小程序登录/手机号相关服务
 */
public interface WeChatMiniAppService {

    /**
     * 通过 jsCode 换取 openid + sessionKey + unionId（code2Session）
     *
     * @param jsCode 小程序 wx.login 返回的 code
     * @return 解析后的结果；{@link Jscode2SessionResult#isSuccess()} 可判断是否成功
     */
    Jscode2SessionResult jscode2session(String jsCode);

    /**
     * 解密 getPhoneNumber 回调中的 encryptedData，返回手机号
     *
     * <p>AES-128-CBC + PKCS7，sessionKey 用于 AES Key，iv 为 16 字节初始向量（Base64）。</p>
     *
     * @param sessionKey    Base64 编码的 session_key
     * @param encryptedData Base64 编码的加密数据
     * @param iv            Base64 编码的初始向量
     * @return 11 位手机号
     */
    String decryptPhoneEncryptedData(String sessionKey, String encryptedData, String iv);

    /**
     * 从 Redis 取 sessionKey
     */
    String getSessionKey(String openid);

    /**
     * 将 sessionKey 写入 Redis，TTL 取自配置
     */
    void cacheSessionKey(String openid, String sessionKey);
}
