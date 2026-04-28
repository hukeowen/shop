package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoPoolDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoPoolRoundDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoPoolMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoPoolRoundMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserStarMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 积分池结算实现。
 *
 * 设计要点：
 *   - 全程一个事务：发奖 / 清池 / 写批次 必须原子
 *   - 抽奖随机源用注入的 Random（测试时塞确定性 Random，prod 使用 SecureRandom）
 *   - 中奖列表 winners 序列化进 round.winners JSON 字段；明细用于审计 + 用户端历史展示
 *   - 单用户金额 = 整除（向下取整）；池余下零头并入"清池"操作丢失（< 中奖人数 分钱），可接受
 */
@Service
@Slf4j
public class PoolSettlementServiceImpl implements PoolSettlementService {

    @Resource
    private PromoConfigService promoConfigService;
    @Resource
    private ShopPromoPoolMapper poolMapper;
    @Resource
    private ShopPromoPoolRoundMapper roundMapper;
    @Resource
    private ShopUserStarMapper userStarMapper;
    @Resource
    private PromoPointService promoPointService;

    /** 可被测试通过 setter 替换为确定性 Random。 */
    private Random random = new SecureRandom();

    public void setRandom(Random random) {
        this.random = random;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopPromoPoolRoundDO settleNow(String mode) {
        if (mode == null || (!"FULL".equals(mode) && !"LOTTERY".equals(mode))) {
            throw new IllegalArgumentException("mode 必须是 FULL 或 LOTTERY，当前=" + mode);
        }
        PromoConfigDO config = promoConfigService.getConfig();
        if (config == null || !Boolean.TRUE.equals(config.getPoolEnabled())) {
            log.info("[settleNow] 商户未启用积分池，跳过");
            return null;
        }
        ShopPromoPoolDO pool = poolMapper.selectCurrent();
        if (pool == null || pool.getBalance() == null || pool.getBalance() <= 0) {
            log.info("[settleNow] 池余额 0，跳过");
            return null;
        }
        long totalAmount = pool.getBalance();

        // 取参与用户：currentStar ∈ poolEligibleStars
        List<Integer> eligibleStars = parseStarList(config.getPoolEligibleStars());
        if (eligibleStars.isEmpty()) {
            log.info("[settleNow] poolEligibleStars 为空，跳过");
            return null;
        }
        List<ShopUserStarDO> participants = userStarMapper.selectListByCurrentStarIn(eligibleStars);
        if (participants.isEmpty()) {
            log.info("[settleNow] 无参与用户，跳过");
            return null;
        }

        // 选中奖者
        List<ShopUserStarDO> winners;
        if ("LOTTERY".equals(mode)) {
            BigDecimal lotteryRatio = config.getPoolLotteryRatio() == null
                    ? BigDecimal.ZERO : config.getPoolLotteryRatio();
            int winnerCount = (int) BigDecimal.valueOf(participants.size())
                    .multiply(lotteryRatio)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                    .longValueExact();
            if (winnerCount <= 0) {
                log.info("[settleNow] 抽奖中奖人数 = 0，跳过");
                return null;
            }
            winners = pickRandom(participants, winnerCount);
        } else {
            winners = participants;
        }

        // 分配
        String distributeMode = config.getPoolDistributeMode() == null ? "ALL" : config.getPoolDistributeMode();
        List<Award> awards = "STAR".equals(distributeMode)
                ? splitByStar(totalAmount, winners)
                : splitEvenly(totalAmount, winners);

        // 先建 round 占位（拿 roundId 做积分流水的 sourceId）
        ShopPromoPoolRoundDO round = ShopPromoPoolRoundDO.builder()
                .totalAmount(totalAmount)
                .mode(mode)
                .distributeMode(distributeMode)
                .participantCount(participants.size())
                .winnerCount(winners.size())
                .winners("[]")  // 暂占位
                .settledAt(LocalDateTime.now())
                .build();
        roundMapper.insert(round);

        // 给每个中奖人发推广积分
        for (Award a : awards) {
            if (a.amount > 0) {
                promoPointService.addPromoPoint(a.userId, a.amount, "POOL", round.getId(),
                        "积分池结算 round=" + round.getId());
            }
        }

        // 条件清池：仅当余额 = 当时读到的 totalAmount 才清零；
        // 防并发：settle 期间又有 deposit 进来，应保留新增 deposit 给下一轮，不能误清
        int cleared = poolMapper.clearIfBalanceEquals(pool.getId(), totalAmount);
        if (cleared != 1) {
            throw new IllegalStateException(
                    "结算清池冲突：余额已被并发修改 pool.id=" + pool.getId() + " expect=" + totalAmount);
        }

        // 回填 winners JSON
        round.setWinners(JsonUtils.toJsonString(awards));
        roundMapper.updateById(round);

        log.info("[settleNow] round={} mode={} dist={} total={} winners={}/{}",
                round.getId(), mode, distributeMode, totalAmount, winners.size(), participants.size());
        return round;
    }

    /** ALL：全员均分；零头丢弃 */
    private List<Award> splitEvenly(long total, List<ShopUserStarDO> winners) {
        long perHead = total / winners.size();
        List<Award> result = new ArrayList<>(winners.size());
        for (ShopUserStarDO u : winners) {
            result.add(new Award(u.getUserId(), u.getCurrentStar(), perHead));
        }
        return result;
    }

    /** STAR：池先按星级数均分到桶；桶内再均分给该星级的中奖人 */
    private List<Award> splitByStar(long total, List<ShopUserStarDO> winners) {
        Map<Integer, List<ShopUserStarDO>> byStar = winners.stream()
                .collect(Collectors.groupingBy(ShopUserStarDO::getCurrentStar, TreeMap::new, Collectors.toList()));
        long perBucket = total / byStar.size();
        List<Award> result = new ArrayList<>(winners.size());
        for (Map.Entry<Integer, List<ShopUserStarDO>> entry : byStar.entrySet()) {
            List<ShopUserStarDO> bucket = entry.getValue();
            long perHead = perBucket / bucket.size();
            for (ShopUserStarDO u : bucket) {
                result.add(new Award(u.getUserId(), u.getCurrentStar(), perHead));
            }
        }
        return result;
    }

    /** 抽奖：从 participants 随机不重复抽 winnerCount 个 */
    private List<ShopUserStarDO> pickRandom(List<ShopUserStarDO> participants, int winnerCount) {
        if (winnerCount >= participants.size()) {
            return new ArrayList<>(participants);
        }
        List<ShopUserStarDO> shuffled = new ArrayList<>(participants);
        Collections.shuffle(shuffled, random);
        return new ArrayList<>(shuffled.subList(0, winnerCount));
    }

    private List<Integer> parseStarList(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try {
            List<Number> raw = JsonUtils.parseArray(json, Number.class);
            if (raw == null) return Collections.emptyList();
            List<Integer> out = new ArrayList<>(raw.size());
            for (Number n : raw) {
                if (n != null) out.add(n.intValue());
            }
            return out;
        } catch (Exception e) {
            log.warn("[parseStarList] 解析失败 {}: {}", json, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 中奖明细 — 序列化进 winners JSON 字段，便于商户后台 / 用户端展示。
     *
     * 字段保持可变（getter/setter 由 Lombok @Data 生成）+ 显式 @NoArgsConstructor，
     * 确保 Jackson 反序列化（pool/rounds 列表回查时）能 round-trip 不抛 InvalidDefinitionException。
     */
    @Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Award {
        private Long userId;
        private Integer star;
        private Long amount;
    }

    /**
     * SecureRandom 适配 Random 接口；nextInt(bound) 由 Random 父类按概率分布提供。
     */
    private static class SecureRandom extends Random {
        private final java.security.SecureRandom delegate = new java.security.SecureRandom();
        @Override
        protected int next(int bits) {
            byte[] b = new byte[4];
            delegate.nextBytes(b);
            return ((b[0] & 0xff) << 24 | (b[1] & 0xff) << 16 | (b[2] & 0xff) << 8 | (b[3] & 0xff)) >>> (32 - bits);
        }
    }

}
