package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoPoolDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueueEventDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoPoolMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopQueueEventMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 积分池累积实现：仅做"入池"，结算逻辑（cron 抽奖/均分）后续 W4 再做。
 *
 * 幂等策略：复用 shop_queue_event，用 eventType=POOL_DEPOSIT、beneficiary_user_id=0
 * （0 表示系统/池本身不属于任一用户）作为标记；同 (orderId, spuId) 二次入池会被静默跳过。
 *
 * 并发：getOrCreate 同样捕获 DuplicateKeyException 重读，处理首次首单的并发场景。
 */
@Service
@Slf4j
public class PromoPoolServiceImpl implements PromoPoolService {

    private static final Long SYSTEM_BENEFICIARY = 0L;
    private static final String EVENT_POOL_DEPOSIT = "POOL_DEPOSIT";

    @Resource
    private ShopPromoPoolMapper poolMapper;
    @Resource
    private ShopQueueEventMapper eventMapper;
    @Resource
    private PromoConfigService promoConfigService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void depositIfEnabled(ProductPromoConfigDO productConfig, long paidAmount, Long orderId) {
        if (productConfig == null || !Boolean.TRUE.equals(productConfig.getPoolEnabled())) {
            return;
        }
        if (orderId == null || paidAmount <= 0) {
            return;
        }
        Long spuId = productConfig.getSpuId();
        if (spuId == null) {
            return;
        }
        // 幂等：同 (orderId, spuId) 已入池过 → 跳过
        if (eventMapper.existsByOrderAndBeneficiary(orderId, spuId, SYSTEM_BENEFICIARY, EVENT_POOL_DEPOSIT)) {
            log.debug("[depositIfEnabled] 幂等命中 order={} spu={}", orderId, spuId);
            return;
        }

        PromoConfigDO shopConfig = promoConfigService.getConfig();
        if (shopConfig == null || !Boolean.TRUE.equals(shopConfig.getPoolEnabled())) {
            return;
        }
        BigDecimal ratio = shopConfig.getPoolRatio();
        if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        long deposit = BigDecimal.valueOf(paidAmount)
                .multiply(ratio)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                .longValueExact();
        if (deposit <= 0) {
            return;
        }

        // 累加池余额：原子 UPDATE col = col + ? 防并发丢更新
        ShopPromoPoolDO pool = poolMapper.selectCurrent();
        if (pool == null) {
            ShopPromoPoolDO created = ShopPromoPoolDO.builder().balance(deposit).build();
            try {
                poolMapper.insert(created);
            } catch (DuplicateKeyException e) {
                // 并发下另一个事务先建了 pool — 走原子累加路径
                pool = poolMapper.selectCurrent();
                if (pool == null) {
                    throw e;
                }
                int rows = poolMapper.addBalance(pool.getId(), deposit);
                if (rows != 1) {
                    throw new IllegalStateException("pool addBalance 失败 pool.id=" + pool.getId());
                }
            }
        } else {
            int rows = poolMapper.addBalance(pool.getId(), deposit);
            if (rows != 1) {
                throw new IllegalStateException("pool addBalance 失败 pool.id=" + pool.getId());
            }
        }

        // 写幂等标记事件（同时也是审计流水）
        eventMapper.insert(ShopQueueEventDO.builder()
                .spuId(spuId)
                .eventType(EVENT_POOL_DEPOSIT)
                .beneficiaryUserId(SYSTEM_BENEFICIARY)
                .sourceUserId(SYSTEM_BENEFICIARY)
                .sourceOrderId(orderId)
                .positionIndex(0)
                .ratioPercent(ratio)
                .amount(deposit)
                .build());
        log.debug("[depositIfEnabled] order={} spu={} += {} 分", orderId, spuId, deposit);
    }

}
