package cn.iocoder.yudao.module.merchant.enums.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 视频配额流水业务类型。
 *
 * <p>与 {@code merchant_video_quota_log.biz_type} 列一一对应。</p>
 */
@Getter
@AllArgsConstructor
public enum VideoQuotaBizTypeEnum {

    /** 商户购买套餐充值（支付回调写入，0.3.3 实现）。 */
    PACKAGE_PURCHASE(1, "购买套餐"),

    /** 视频生成时预扣配额。 */
    VIDEO_GEN(2, "视频生成扣减"),

    /** 视频生成失败自动回补。 */
    VIDEO_REFUND(3, "生成失败回补"),

    /** 平台管理员手动调整。 */
    MANUAL_ADJUST(4, "平台手动调整");

    private final int code;
    private final String desc;

    public static VideoQuotaBizTypeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (VideoQuotaBizTypeEnum v : values()) {
            if (v.code == code) {
                return v;
            }
        }
        return null;
    }
}
