package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserStarMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 团队极差递减实现。
 */
@Service
@Slf4j
public class CommissionServiceImpl implements CommissionService {

    @Resource
    private ShopUserStarMapper userStarMapper;
    @Resource
    private PromoConfigService promoConfigService;
    @Resource
    private ReferralService referralService;
    @Resource
    private PromoPointService promoPointService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleOrderPaid(Long buyerUserId, long paidAmount, Long orderId) {
        if (buyerUserId == null || paidAmount <= 0 || orderId == null) {
            return;
        }
        PromoConfigDO config = promoConfigService.getConfig();
        List<BigDecimal> rates = parseRates(config.getCommissionRates());
        if (rates.isEmpty()) {
            return;
        }

        int issuedStar = 0;                      // 已发星级（0 = 未发）
        BigDecimal issuedRate = BigDecimal.ZERO; // 已发累计 %

        // 步 1：买家自己（如有星级）
        Integer buyerStar = readStar(buyerUserId);
        if (buyerStar != null && buyerStar > 0 && buyerStar <= rates.size()) {
            BigDecimal rate = rates.get(buyerStar - 1);
            long amount = computeAmount(paidAmount, rate);
            if (amount > 0) {
                promoPointService.addPromoPoint(buyerUserId, amount, "COMMISSION", orderId,
                        "团队极差 自身 star=" + buyerStar);
            }
            issuedStar = buyerStar;
            issuedRate = rate;
        }

        // 步 2：沿推荐链向上递减分润
        List<Long> ancestors = referralService.getAncestors(buyerUserId, 50);
        for (Long ancestorId : ancestors) {
            Integer star = readStar(ancestorId);
            if (star == null || star <= 0 || star > rates.size()) {
                continue;
            }
            if (star <= issuedStar) {
                continue; // 不高于已发星级 → 跳过
            }
            BigDecimal myRate = rates.get(star - 1);
            BigDecimal diff = myRate.subtract(issuedRate);
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                long amount = computeAmount(paidAmount, diff);
                if (amount > 0) {
                    promoPointService.addPromoPoint(ancestorId, amount, "COMMISSION", orderId,
                            "团队极差 star=" + star + " diff=" + diff + "%");
                }
            }
            issuedStar = star;
            issuedRate = myRate;
        }
    }

    private Integer readStar(Long userId) {
        ShopUserStarDO star = userStarMapper.selectByUserId(userId);
        return star == null ? null : star.getCurrentStar();
    }

    private long computeAmount(long paidAmount, BigDecimal ratePercent) {
        return BigDecimal.valueOf(paidAmount)
                .multiply(ratePercent)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                .longValueExact();
    }

    private List<BigDecimal> parseRates(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<Number> raw = JsonUtils.parseArray(json, Number.class);
            if (raw == null) {
                return Collections.emptyList();
            }
            List<BigDecimal> result = new ArrayList<>(raw.size());
            for (Number num : raw) {
                result.add(num == null ? BigDecimal.ZERO : new BigDecimal(num.toString()));
            }
            return result;
        } catch (Exception e) {
            log.warn("[parseRates] 解析失败 {}: {}", json, e.getMessage());
            return Collections.emptyList();
        }
    }

}
