package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolPayoutItemDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolSettleRecordDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ProductPromoConfigMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserStarMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.SpuStarPoolMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.SpuStarPoolPayoutItemMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.SpuStarPoolSettleRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v8 SPU 级星级奖池结算实现。
 *
 * 核心流程（全程一个事务）：
 *   1. 读规则 + 校验池余额 > 0
 *   2. FOR UPDATE 锁池行（防并发结算）
 *   3. 预插 settle_record（拿 settleId 做积分流水 sourceId）
 *   4. 按规则逐星分发：
 *      - 该星无用户 → 整段额度回流 remainder（不丢钱）
 *      - EQUAL → 全员均分；零头回流 remainder
 *      - LOTTERY → 不重复抽 min(winners, 实际人数)；零头回流 remainder
 *   5. 一次性扣池 pool_balance -= distributed, total_out += distributed
 *   6. 回填 settle_record（after/distributed/seed）
 *
 * 幂等：PromoPointService.addPromoPoint 按 (userId, "POOL_V8", settleId) 三元组去重，
 *      同一 settleId 重放 add 调用对该用户最多入账 1 次。
 *
 * 残值处理：sum(ratio)=100 已强校验，但 EQUAL/LOTTERY 整除零头 + 该星空人 都会让残值留池，
 *          下次结算继续分（业务上等价于"少分给商家、多攒到下轮"）。
 */
@Service
@Slf4j
public class SpuPoolSettleServiceImpl implements SpuPoolSettleService {

    private static final String SOURCE_TYPE = "POOL_V8";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Resource
    private ProductPromoConfigMapper productPromoConfigMapper;
    @Resource
    private SpuStarPoolMapper spuStarPoolMapper;
    @Resource
    private SpuStarPoolSettleRecordMapper settleRecordMapper;
    @Resource
    private SpuStarPoolPayoutItemMapper payoutItemMapper;
    @Resource
    private ShopUserStarMapper shopUserStarMapper;
    @Resource
    private PromoPointService promoPointService;

    /** 测试可注入确定性 Random。 */
    private RandomFactory randomFactory = seed -> new Random(seed);

    public void setRandomFactory(RandomFactory factory) {
        this.randomFactory = factory;
    }

    @FunctionalInterface
    public interface RandomFactory {
        Random create(long seed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpuStarPoolSettleRecordDO settle(Long spuId, String remark) {
        if (spuId == null) {
            throw ServiceExceptionUtil.exception0(1_031_003_001, "spuId 必填");
        }
        // 1. 读配置 + 解析规则
        ProductPromoConfigDO config = productPromoConfigMapper.selectBySpuId(spuId);
        if (config == null) {
            throw ServiceExceptionUtil.exception0(1_031_003_002, "该商品未配置营销规则");
        }
        String rulesJson = config.getPoolDistRules();
        if (rulesJson == null || rulesJson.trim().isEmpty() || "[]".equals(rulesJson.trim())) {
            throw ServiceExceptionUtil.exception0(1_031_003_003, "该商品未配置奖池分配规则");
        }
        List<Map<String, Object>> rules;
        try {
            rules = JsonUtils.parseArray(rulesJson, (Class) Map.class);
        } catch (Exception e) {
            throw ServiceExceptionUtil.exception0(1_031_003_004, "奖池分配规则 JSON 非法：" + e.getMessage());
        }
        if (rules == null || rules.isEmpty()) {
            throw ServiceExceptionUtil.exception0(1_031_003_003, "该商品未配置奖池分配规则");
        }

        // 2. FOR UPDATE 锁池
        SpuStarPoolDO pool = spuStarPoolMapper.selectBySpuIdForUpdate(spuId);
        if (pool == null || pool.getPoolBalance() == null || pool.getPoolBalance() <= 0) {
            throw ServiceExceptionUtil.exception0(1_031_003_005, "池余额为 0，无可分配资金");
        }
        long poolBefore = pool.getPoolBalance();

        // 3. 预插 settle_record（拿 settleId 用作积分流水 sourceId）
        long seed = ThreadLocalRandom.current().nextLong();
        Long operatorId = null;
        try {
            operatorId = SecurityFrameworkUtils.getLoginUserId();
        } catch (Exception ignored) {
            // 单测 / 内部触发时无登录上下文
        }
        SpuStarPoolSettleRecordDO record = SpuStarPoolSettleRecordDO.builder()
                .spuId(spuId)
                .poolBalanceBefore(poolBefore)
                .poolBalanceAfter(poolBefore)      // 占位，回填
                .totalDistributed(0L)              // 占位，回填
                .rulesSnapshot(rulesJson)
                .randomSeed(seed)
                .operatorId(operatorId)
                .operatorName("")
                .remark(remark == null ? "" : remark)
                .build();
        settleRecordMapper.insert(record);
        Long settleId = record.getId();

        // 4. 按规则逐星分发
        long remainder = poolBefore;
        Random lotteryRandom = randomFactory.create(seed);
        for (Map<String, Object> rule : rules) {
            int star = ((Number) rule.get("star")).intValue();
            BigDecimal ratio = new BigDecimal(rule.get("ratio").toString());
            String mode = rule.get("mode").toString();

            long allocation = BigDecimal.valueOf(poolBefore)
                    .multiply(ratio)
                    .divide(HUNDRED, 0, RoundingMode.DOWN)
                    .longValueExact();
            if (allocation <= 0) {
                continue;
            }

            List<ShopUserStarDO> candidates = shopUserStarMapper.selectListBySpuAndStar(spuId, star);
            if (candidates.isEmpty()) {
                log.info("[settle] spu={} star={} 无候选用户，金额 {} 分留池", spuId, star, allocation);
                continue;
            }

            List<ShopUserStarDO> winners;
            if ("LOTTERY".equals(mode)) {
                int desired = ((Number) rule.get("winners")).intValue();
                int actual = Math.min(desired, candidates.size());
                if (candidates.size() <= actual) {
                    winners = new ArrayList<>(candidates);
                } else {
                    List<ShopUserStarDO> shuffled = new ArrayList<>(candidates);
                    Collections.shuffle(shuffled, lotteryRandom);
                    winners = new ArrayList<>(shuffled.subList(0, actual));
                }
            } else {
                // EQUAL
                winners = candidates;
            }

            long perUser = allocation / winners.size();
            if (perUser <= 0) {
                // 池子太小 / 人太多：整段额度 < 人数，分不出来 → 留池
                log.info("[settle] spu={} star={} allocation={} 分给 {} 人，人均 = 0，整段留池",
                        spuId, star, allocation, winners.size());
                continue;
            }
            long distributedThisRule = perUser * winners.size();

            for (ShopUserStarDO winner : winners) {
                // 入账推广积分（addPromoPoint 内做 (user, source_type, source_id) 三元组去重）
                boolean ok = promoPointService.addPromoPoint(winner.getUserId(), perUser,
                        SOURCE_TYPE, settleId,
                        "SPU " + spuId + " 奖池结算 settleId=" + settleId);
                payoutItemMapper.insert(SpuStarPoolPayoutItemDO.builder()
                        .settleId(settleId)
                        .spuId(spuId)
                        .userId(winner.getUserId())
                        .star(star)
                        .mode(mode)
                        .amount(perUser)
                        .pointLedgerId(ok ? 1L : 0L)   // 1=入账成功；0=幂等命中或失败
                        .build());
            }
            remainder -= distributedThisRule;
        }

        // 5. 扣池
        long distributed = poolBefore - remainder;
        if (distributed > 0) {
            int rows = spuStarPoolMapper.decrementPoolForSettle(spuId, distributed);
            if (rows != 1) {
                throw new IllegalStateException(
                        "扣池失败 spuId=" + spuId + " distributed=" + distributed + " rows=" + rows);
            }
        }

        // 6. 回填 settle_record
        record.setTotalDistributed(distributed);
        record.setPoolBalanceAfter(remainder);
        settleRecordMapper.updateById(record);

        log.info("[settle] spu={} settleId={} poolBefore={} distributed={} remainder={}",
                spuId, settleId, poolBefore, distributed, remainder);
        return record;
    }

}
