package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;
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
 * 团队极差奖（v8 改造）。
 *
 * <p>v8 规则（详见 docs/design/marketing-system-v8.md 第 3.3 节）：</p>
 * <ul>
 *   <li>商品级配置 starRatios（星级返奖比例数组）</li>
 *   <li>用户星级按 (user, spu) 独立（shop_user_star.spu_id 维度）</li>
 *   <li>沿 buyer 上链就近递增算法：星级严格递增才能拿，按自己星级整额拿（不是差额）</li>
 *   <li>触发基数：订单 spu 行 paidAmount（抵扣后实付）</li>
 *   <li>无 UNIQUE 限制：每订单都触发</li>
 * </ul>
 */
@Service
@Slf4j
public class CommissionServiceImpl implements CommissionService {

    @Resource
    private ShopUserStarMapper userStarMapper;
    @Resource
    private ReferralService referralService;
    @Resource
    private PromoPointService promoPointService;

    /** v6/v7 老接口：商户级共用极差。已废弃，仅保留向后兼容；新代码用 handleOrderPaidV8。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleOrderPaid(Long buyerUserId, long paidAmount, Long orderId) {
        // v8: 没有 spuId 信息无法走商品级极差；保留空实现兼容旧 caller 即可
        log.debug("[CommissionService] v6/v7 老接口被调用 buyer={} paidAmount={} order={}，v8 已废弃",
                buyerUserId, paidAmount, orderId);
    }

    /**
     * v8: 商品级团队极差奖触发。
     *
     * @param config       商品配置（含 star_count, star_ratios）
     * @param buyerUserId  买家
     * @param spuId        商品
     * @param paidAmount   订单 spu 行实付（抵扣后，分）
     * @param orderId      订单 ID（用于 promo_record 幂等）
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleOrderPaidV8(ProductPromoConfigDO config, Long buyerUserId, Long spuId,
                                  long paidAmount, Long orderId) {
        if (config == null || buyerUserId == null || paidAmount <= 0 || orderId == null) return;
        Integer starCount = config.getStarCount();
        if (starCount == null || starCount <= 0) return;
        List<BigDecimal> ratios = parseRates(config.getStarRatios());
        if (ratios.isEmpty()) return;

        // 沿链就近递增：lastStar=0，每往上一个 parent 必须严格 > lastStar 才能拿
        int lastStar = 0;
        // 步 1：buyer 自己（如有星级也参与）
        ShopUserStarDO buyerStar = userStarMapper.selectByUserAndSpu(buyerUserId, spuId);
        Integer bs = buyerStar == null ? null : buyerStar.getCurrentStar();
        if (bs != null && bs > 0 && bs <= ratios.size()) {
            BigDecimal rate = ratios.get(bs - 1);
            long award = computeAmount(paidAmount, rate);
            if (award > 0) {
                promoPointService.addPromoPoint(buyerUserId, award, "COMMISSION", orderId,
                        "v8 团队极差 自身 star=" + bs + " spu=" + spuId);
            }
            lastStar = bs;
        }

        // 步 2：沿推荐链向上找；星级严格递增才拿（按自己星级整额，非差额）
        List<Long> ancestors = referralService.getAncestors(buyerUserId, 50);
        for (Long ancestorId : ancestors) {
            ShopUserStarDO ast = userStarMapper.selectByUserAndSpu(ancestorId, spuId);
            Integer s = ast == null ? null : ast.getCurrentStar();
            if (s == null || s <= 0 || s > ratios.size()) continue;
            if (s <= lastStar) continue;  // 不大于 lastStar → 跳过该 parent，继续上溯
            BigDecimal rate = ratios.get(s - 1);
            long award = computeAmount(paidAmount, rate);
            if (award > 0) {
                promoPointService.addPromoPoint(ancestorId, award, "COMMISSION", orderId,
                        "v8 团队极差 star=" + s + " spu=" + spuId + " by=" + buyerUserId);
            }
            lastStar = s;
        }
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
