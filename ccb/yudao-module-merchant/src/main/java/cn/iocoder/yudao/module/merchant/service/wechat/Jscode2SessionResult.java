package cn.iocoder.yudao.module.merchant.service.wechat;

import lombok.Data;
import lombok.ToString;

/**
 * 微信 jscode2session 返回结果。
 *
 * <pre>
 * 成功: { "openid": "...", "session_key": "...", "unionid": "..." }
 * 失败: { "errcode": 40029, "errmsg": "invalid code" }
 * </pre>
 */
@Data
public class Jscode2SessionResult {

    private String openid;
    private String unionid;
    /** 微信会话密钥，敏感字段，禁止落日志。 */
    @ToString.Exclude
    private String sessionKey;
    private Integer errcode;
    private String errmsg;

    public boolean isSuccess() {
        return (errcode == null || errcode == 0) && openid != null && !openid.isEmpty();
    }
}
