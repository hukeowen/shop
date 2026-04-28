package cn.iocoder.yudao.module.system.framework.sms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.time.Duration;

@ConfigurationProperties(prefix = "yudao.sms-code")
@Validated
@Data
public class SmsCodeProperties {

    /**
     * 过期时间
     */
    @NotNull(message = "过期时间不能为空")
    private Duration expireTimes;
    /**
     * 短信发送频率
     */
    @NotNull(message = "短信发送频率不能为空")
    private Duration sendFrequency;
    /**
     * 每日发送最大数量
     */
    @NotNull(message = "每日发送最大数量不能为空")
    private Integer sendMaximumQuantityPerDay;
    /**
     * 验证码最小值
     */
    @NotNull(message = "验证码最小值不能为空")
    private Integer beginCode;
    /**
     * 验证码最大值
     */
    @NotNull(message = "验证码最大值不能为空")
    private Integer endCode;

    /**
     * 演示模式：true 时 sendSmsCode 不调真实短信渠道，仅把验证码插入 sms_code 表，
     * 同时跳过"发送频率"限制；可用 demoCode 指定固定验证码（如 888888）。
     */
    private Boolean demoMode = false;

    /**
     * 演示模式下使用的固定验证码（仅 demoMode=true 生效）。
     * 留空则按 beginCode/endCode 随机。
     */
    private String demoCode;

}
