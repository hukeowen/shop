package cn.iocoder.yudao.module.merchant.job.promo;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoPoolDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoPoolMapper;
import cn.iocoder.yudao.module.merchant.service.promo.PoolSettlementService;
import cn.iocoder.yudao.module.merchant.service.promo.PromoConfigService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

/**
 * 积分池自动结算 Job。
 *
 * 触发频率：建议 quartz 配置每小时跑一次（cron `0 0 * * * ?`）。
 * 模式：默认 FULL（全员均分）。LOTTERY 留给商户在后台手动触发。
 *
 * 算法（带 @TenantIgnore 跨租户扫描）：
 *   1. 查所有 balance > 0 的 pool（无视当前 tenant context）
 *   2. 对每个 tenant：切到该 tenant 上下文 → 读 config →
 *      若 poolEnabled = true 且当前时间已越过 (lastSettledAt 之后的下一次 cron 触发点) → 触发 settleNow("FULL")
 *   3. 单租户失败不影响其它租户
 */
@Slf4j
@Component
public class PromoPoolSettleJob implements JobHandler {

    @Resource
    private ShopPromoPoolMapper poolMapper;
    @Resource
    private PromoConfigService promoConfigService;
    @Resource
    private PoolSettlementService poolSettlementService;

    @Override
    @TenantIgnore  // 跨租户扫描；具体结算时再切回各 tenant
    public String execute(String param) {
        Date now = new Date();
        // 跨租户拉所有有余额的池
        List<ShopPromoPoolDO> pools = poolMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ShopPromoPoolDO>()
                        .gt("balance", 0));

        int triggered = 0;
        int skipped = 0;
        int failed = 0;
        for (ShopPromoPoolDO pool : pools) {
            Long tenantId = pool.getTenantId();
            try {
                boolean fired = TenantUtils.execute(tenantId, () -> trySettleOneTenant(pool, now));
                if (fired) triggered++;
                else skipped++;
            } catch (Exception e) {
                log.error("[PromoPoolSettleJob] tenant={} 结算失败", tenantId, e);
                failed++;
            }
        }
        String msg = String.format(
                "扫描 %d 个 pool，触发 %d 个，跳过 %d 个，失败 %d 个",
                pools.size(), triggered, skipped, failed);
        log.info("[PromoPoolSettleJob] {}", msg);
        return msg;
    }

    /**
     * @return true = 已触发结算；false = 跳过（cron 未到 / 配置关闭 / 余额变 0）
     */
    private boolean trySettleOneTenant(ShopPromoPoolDO pool, Date now) {
        PromoConfigDO config = promoConfigService.getConfig();
        if (config == null || !Boolean.TRUE.equals(config.getPoolEnabled())) {
            return false;
        }
        String cronExpr = config.getPoolSettleCron();
        if (cronExpr == null || cronExpr.isEmpty()) {
            return false;
        }
        CronExpression cron;
        try {
            cron = new CronExpression(cronExpr);
        } catch (ParseException e) {
            log.warn("[PromoPoolSettleJob] tenant={} cron 解析失败 '{}': {}",
                    pool.getTenantId(), cronExpr, e.getMessage());
            return false;
        }
        // 计算"上次结算之后的下一次 cron 触发点"是否 ≤ now
        Date lastSettled = pool.getLastSettledAt() == null
                ? null
                : java.sql.Timestamp.valueOf(pool.getLastSettledAt());
        // 起点：取 max(lastSettled, now - 30天)，避免历史 NULL 把 cron 触发点拉到很久之前
        Date scanFrom = lastSettled != null
                ? lastSettled
                : new Date(now.getTime() - 30L * 24 * 60 * 60 * 1000);
        Date nextFire = cron.getNextValidTimeAfter(scanFrom);
        if (nextFire == null || nextFire.after(now)) {
            return false;
        }
        // 自动结算模式按商户配置；非法值兜底 FULL
        String mode = config.getPoolSettleMode();
        if (mode == null || (!"FULL".equals(mode) && !"LOTTERY".equals(mode))) {
            mode = "FULL";
        }
        log.info("[PromoPoolSettleJob] tenant={} 触发 {} 结算（cron={}, lastSettled={}, nextFire={}）",
                pool.getTenantId(), mode, cronExpr, scanFrom, nextFire);
        poolSettlementService.settleNow(mode);
        return true;
    }

}
