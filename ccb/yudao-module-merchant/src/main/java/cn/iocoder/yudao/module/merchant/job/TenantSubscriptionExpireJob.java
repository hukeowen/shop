package cn.iocoder.yudao.module.merchant.job;

import cn.iocoder.yudao.module.merchant.service.MerchantApplyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 租户订阅到期定时任务
 * 每天凌晨1点扫描过期订阅，将状态更新为「过期」
 */
@Component
@Slf4j
public class TenantSubscriptionExpireJob {

    @Resource
    private MerchantApplyService merchantApplyService;

    @Scheduled(cron = "0 0 1 * * ?")
    public void execute() {
        log.info("[TenantSubscriptionExpireJob] 开始扫描过期订阅...");
        merchantApplyService.expireOverdueSubscriptions();
        log.info("[TenantSubscriptionExpireJob] 扫描完成");
    }

}
