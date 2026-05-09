package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserStarMapper;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * 星级评定实现。
 *
 * 并发安全：
 *   - direct_count / team_sales_count 用 atomic UPDATE col = col + ? 累加
 *   - current_star 升级用 conditional UPDATE（只有 newStar > 当前星级才落库），实现"只升不降"
 *   - getOrCreate 在并发首单下 catch DuplicateKeyException 重读
 */
@Service
@Slf4j
public class StarServiceImpl implements StarService {

    @Resource
    private ShopUserStarMapper userStarMapper;
    @Resource
    private PromoConfigService promoConfigService;
    @Resource
    private ReferralService referralService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleOrderPaid(Long buyerUserId, int qty, boolean countable) {
        if (buyerUserId == null || qty <= 0 || !countable) {
            return;
        }
        bumpTeamSales(buyerUserId, qty);
        for (Long ancestorId : referralService.getAncestors(buyerUserId, ReferralService.DEFAULT_MAX_DEPTH)) {
            bumpTeamSales(ancestorId, qty);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleReferralBound(Long parentUserId) {
        if (parentUserId == null || parentUserId <= 0) {
            return;
        }
        getOrCreate(parentUserId);
        userStarMapper.addDirectCount(parentUserId, 1);
        attemptUpgrade(parentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recompute(Long userId) {
        getOrCreate(userId);
        attemptUpgrade(userId);
        ShopUserStarDO acct = userStarMapper.selectByUserId(userId);
        return acct == null ? 0 : acct.getCurrentStar();
    }

    /** 累加 team_sales_count，并尝试升星 */
    private void bumpTeamSales(Long userId, int delta) {
        getOrCreate(userId);
        userStarMapper.addTeamSalesCount(userId, delta);
        attemptUpgrade(userId);
    }

    /**
     * 终生制 + 并发安全：
     *   1. 重读最新 direct_count / team_sales_count
     *   2. 计算最高可达星级 target
     *   3. 用 conditional UPDATE 提交（仅当 target > 当前星级才落库），并发下两个事务都提交也不会降级
     */
    private void attemptUpgrade(Long userId) {
        ShopUserStarDO acct = userStarMapper.selectByUserId(userId);
        if (acct == null) {
            return;
        }
        List<Rule> rules = parseRules(promoConfigService.getConfig());
        if (rules.isEmpty()) {
            return;
        }
        int target = acct.getCurrentStar() == null ? 0 : acct.getCurrentStar();
        while (target < rules.size()) {
            Rule rule = rules.get(target);
            if (acct.getDirectCount() >= rule.getDirectCount()
                    && acct.getTeamSalesCount() >= rule.getTeamSales()) {
                target++;
            } else {
                break;
            }
        }
        if (target > (acct.getCurrentStar() == null ? 0 : acct.getCurrentStar())) {
            userStarMapper.upgradeStarIfHigher(userId, target);
        }
    }

    /** 取或建账户；并发下两事务都尝试 insert，第二个会撞唯一键 → 重读返回。 */
    private ShopUserStarDO getOrCreate(Long userId) {
        ShopUserStarDO existing = userStarMapper.selectByUserId(userId);
        if (existing != null) {
            return existing;
        }
        ShopUserStarDO created = ShopUserStarDO.builder()
                .userId(userId)
                .directCount(0)
                .teamSalesCount(0)
                .currentStar(0)
                .promoPointBalance(0L)
                .consumePointBalance(0L)
                .build();
        try {
            userStarMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            ShopUserStarDO concurrent = userStarMapper.selectByUserId(userId);
            if (concurrent != null) {
                return concurrent;
            }
            throw e;
        }
    }

    private List<Rule> parseRules(PromoConfigDO config) {
        try {
            List<Rule> rules = JsonUtils.parseArray(config.getStarUpgradeRules(), Rule.class);
            return rules == null ? Collections.emptyList() : rules;
        } catch (Exception e) {
            log.warn("[parseRules] 解析失败 {}: {}", config.getStarUpgradeRules(), e.getMessage());
            return Collections.emptyList();
        }
    }

    @Data
    @NoArgsConstructor
    public static class Rule {
        private int directCount;
        private int teamSales;
    }

    // ============================================================
    // v8: 商品级升星 — (user, spu) 维度
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleOrderPaidV8(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO config,
                                  Long buyerUserId, Long spuId, int qty, long paidAmount) {
        if (config == null || buyerUserId == null || spuId == null || qty <= 0) return;
        Integer starCount = config.getStarCount();
        if (starCount == null || starCount <= 0) return;
        List<RuleV8> rules = parseRulesV8(config.getStarUpgradeRules());
        if (rules.isEmpty()) return;

        bumpTeamSalesV8(buyerUserId, spuId, qty, paidAmount, rules);
        for (Long ancestorId : referralService.getAncestors(buyerUserId, ReferralService.DEFAULT_MAX_DEPTH)) {
            bumpTeamSalesV8(ancestorId, spuId, qty, paidAmount, rules);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleReferralBoundV8(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO config,
                                       Long parentUserId, Long spuId) {
        if (config == null || parentUserId == null || parentUserId <= 0 || spuId == null) return;
        Integer starCount = config.getStarCount();
        if (starCount == null || starCount <= 0) return;
        List<RuleV8> rules = parseRulesV8(config.getStarUpgradeRules());
        if (rules.isEmpty()) return;

        getOrCreateBySpu(parentUserId, spuId);
        userStarMapper.addDirectCountBySpu(parentUserId, spuId, 1);
        attemptUpgradeV8(parentUserId, spuId, rules);
    }

    private void bumpTeamSalesV8(Long userId, Long spuId, int qty, long paidAmount, List<RuleV8> rules) {
        getOrCreateBySpu(userId, spuId);
        userStarMapper.addTeamSalesBySpu(userId, spuId, qty, paidAmount);
        attemptUpgradeV8(userId, spuId, rules);
    }

    private void attemptUpgradeV8(Long userId, Long spuId, List<RuleV8> rules) {
        ShopUserStarDO acct = userStarMapper.selectByUserAndSpu(userId, spuId);
        if (acct == null) return;
        int target = acct.getCurrentStar() == null ? 0 : acct.getCurrentStar();
        int directCount = acct.getDirectCount() == null ? 0 : acct.getDirectCount();
        long teamSalesAmount = acct.getTeamSalesAmount() == null ? 0L : acct.getTeamSalesAmount();
        while (target < rules.size()) {
            RuleV8 r = rules.get(target);
            if (directCount >= r.getDirectCount() && teamSalesAmount >= r.getTeamSales()) {
                target++;
            } else {
                break;
            }
        }
        int curr = acct.getCurrentStar() == null ? 0 : acct.getCurrentStar();
        if (target > curr) {
            userStarMapper.upgradeStarIfHigherBySpu(userId, spuId, target);
        }
    }

    private ShopUserStarDO getOrCreateBySpu(Long userId, Long spuId) {
        ShopUserStarDO existing = userStarMapper.selectByUserAndSpu(userId, spuId);
        if (existing != null) return existing;
        ShopUserStarDO created = ShopUserStarDO.builder()
                .userId(userId).spuId(spuId)
                .directCount(0).teamSalesCount(0).teamSalesAmount(0L)
                .currentStar(0)
                .promoPointBalance(0L).consumePointBalance(0L)
                .build();
        try {
            userStarMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            ShopUserStarDO concurrent = userStarMapper.selectByUserAndSpu(userId, spuId);
            if (concurrent != null) return concurrent;
            throw e;
        }
    }

    private List<RuleV8> parseRulesV8(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try {
            List<RuleV8> rules = JsonUtils.parseArray(json, RuleV8.class);
            return rules == null ? Collections.emptyList() : rules;
        } catch (Exception e) {
            log.warn("[parseRulesV8] 解析失败 {}: {}", json, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Data
    @NoArgsConstructor
    public static class RuleV8 {
        /** 升此星需要的直推下级数（在该商品上） */
        private int directCount;
        /** 升此星需要的团队链路销售实付（分） */
        private long teamSales;
    }
}
