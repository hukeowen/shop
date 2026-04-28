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
        for (Long ancestorId : referralService.getAncestors(buyerUserId, 50)) {
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

}
