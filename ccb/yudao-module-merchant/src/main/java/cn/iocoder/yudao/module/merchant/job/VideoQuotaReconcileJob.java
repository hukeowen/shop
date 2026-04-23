package cn.iocoder.yudao.module.merchant.job;

import cn.hutool.core.date.DateUtil;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantVideoQuotaLogDO;
import cn.iocoder.yudao.module.merchant.service.MerchantVideoQuotaLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * AI 视频配额扣减流水对账告警（Phase 0.3.2 必修）。
 *
 * <p>每 5 分钟扫一次 VIDEO_GEN 方向的扣减流水：创建时间在 15 分钟到 1 小时之间，
 * 且 remark 里没有 taskId 绑定、也没有被 VIDEO_REFUND 回补的，视为可能丢失的"未完成扣减"，
 * 打告警日志。Phase 1+ 再接入真实告警渠道（企业微信/钉钉/Telegram）。</p>
 *
 * <p><em>暂不自动回补</em>——由人工判断后通过 admin 接口手动处理，避免误伤。</p>
 *
 * <p>{@code @EnableScheduling} 在 {@code yudao-spring-boot-starter-job} 里已开启，无需重复配置。</p>
 */
@Component
@Slf4j
public class VideoQuotaReconcileJob {

    @Resource
    private MerchantVideoQuotaLogService merchantVideoQuotaLogService;

    @Scheduled(cron = "0 */5 * * * ?")
    public void alertOrphanDebits() {
        Date end = DateUtil.offsetMinute(new Date(), -15);
        Date start = DateUtil.offsetMinute(new Date(), -60);
        List<MerchantVideoQuotaLogDO> orphans = merchantVideoQuotaLogService.findOrphanDebits(start, end);
        if (orphans.isEmpty()) {
            return;
        }
        log.error("[quota-reconcile] 发现 {} 条潜在丢失扣减流水，人工处理", orphans.size());
        for (MerchantVideoQuotaLogDO o : orphans) {
            log.error("[quota-reconcile]   merchantId={} bizId={} createTime={}",
                    o.getMerchantId(), o.getBizId(), o.getCreateTime());
        }
    }

}
