package cn.iocoder.yudao.module.merchant.service;

/**
 * {@link MerchantService#increaseVideoQuota} / {@link MerchantService#decreaseVideoQuota}
 * 的返回值 DTO（Phase 0.3.3 #MEDIUM4）。
 *
 * <p>之前返回 {@code int quotaAfter}——调用方拿不到写入的流水主键，
 * 导致 {@code merchant_package_order.quota_log_id} 一直是 null，审计链断裂。
 * 这里把流水 PK 一并带出，由 {@code markPaid} 回写 package_order。</p>
 *
 * <p>不可变 + 包私有 setter ——不暴露构造后修改，避免调用方误用。</p>
 */
public final class QuotaChangeResult {

    /** 变动完成后商户剩余配额（与老签名 {@code int} 对齐）。 */
    private final int quotaAfter;

    /** 本次写入 {@code merchant_video_quota_log} 的主键；理论不会为 null，但调用方仍应判空。 */
    private final Long logId;

    public QuotaChangeResult(int quotaAfter, Long logId) {
        this.quotaAfter = quotaAfter;
        this.logId = logId;
    }

    public int getQuotaAfter() {
        return quotaAfter;
    }

    public Long getLogId() {
        return logId;
    }

}
