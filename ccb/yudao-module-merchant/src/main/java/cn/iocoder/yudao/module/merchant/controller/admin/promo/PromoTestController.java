package cn.iocoder.yudao.module.merchant.controller.admin.promo;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueuePositionDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopReferralContributionDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserReferralDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopQueueEventMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopQueuePositionMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopReferralContributionMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserReferralMapper;
import cn.iocoder.yudao.module.merchant.service.promo.ProductPromoConfigService;
import cn.iocoder.yudao.module.merchant.service.promo.PromoQueueService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * v7 推 N 反 1 全链路模拟测试 controller。
 *
 * <p>路径前缀 /merchant/promo/test/** 已加入 yudao.security.permit-all_urls 白名单 +
 * yudao.tenant.ignore-urls 白名单（自带 tenantId 入参，用 TenantUtils.execute 内部切）。
 * 仅本地/测试用，生产应移除。</p>
 */
@RestController
@RequestMapping("/merchant/promo/test")
public class PromoTestController {

    @Resource
    private PromoQueueService promoQueueService;
    @Resource
    private ProductPromoConfigService productPromoConfigService;
    @Resource
    private ShopQueuePositionMapper queueMapper;
    @Resource
    private ShopQueueEventMapper eventMapper;
    @Resource
    private ShopReferralContributionMapper contributionMapper;
    @Resource
    private ShopUserReferralMapper referralMapper;

    /**
     * 模拟支付回调 → 触发 PromoQueueService.handleOrderPaid。
     *
     * <p>用法（curl 例子）：</p>
     * <pre>
     * curl -X POST 'http://localhost:48080/admin-api/merchant/promo/test/simulate-pay?
     *     tenantId=162&buyerUserId=1&spuId=3&paidAmount=10000&count=1&orderId=1001'
     * curl -X POST 'http://localhost:48080/admin-api/merchant/promo/test/simulate-pay?
     *     tenantId=162&buyerUserId=2&parentUserId=1&spuId=3&paidAmount=10000&orderId=1002'
     * </pre>
     *
     * @param tenantId      租户（商户）
     * @param buyerUserId   买家 userId
     * @param parentUserId  上级 userId（可选；传了会先 upsert shop_user_referral）
     * @param spuId         商品 SPU
     * @param paidAmount    实付总额（分）
     * @param count         件数（默认 1，单件实付 = paidAmount/count）
     * @param orderId       订单 ID（用于幂等，调用方自己造）
     * @return 调用前后的 buyer 队列位置 / 该用户作为 child 的 contribution / 最近 5 条事件
     */
    @PostMapping("/simulate-pay")
    public CommonResult<Map<String, Object>> simulatePay(
            @RequestParam Long tenantId,
            @RequestParam Long buyerUserId,
            @RequestParam(required = false) Long parentUserId,
            @RequestParam Long spuId,
            @RequestParam long paidAmount,
            @RequestParam(required = false, defaultValue = "1") int count,
            @RequestParam Long orderId) {

        Map<String, Object> result = new HashMap<>();
        TenantUtils.execute(tenantId, () -> {
            if (parentUserId != null && parentUserId > 0) {
                ShopUserReferralDO existing = referralMapper.selectByUserId(buyerUserId);
                if (existing == null) {
                    referralMapper.insert(ShopUserReferralDO.builder()
                            .userId(buyerUserId)
                            .parentUserId(parentUserId)
                            .boundAt(LocalDateTime.now())
                            .boundOrderId(orderId)
                            .build());
                } else if (!parentUserId.equals(existing.getParentUserId())) {
                    existing.setParentUserId(parentUserId);
                    referralMapper.updateById(existing);
                }
            }

            ProductPromoConfigDO config = productPromoConfigService.getBySpuId(spuId);
            result.put("configFound", config != null);
            if (config == null) {
                result.put("error", "no productPromoConfig for spuId=" + spuId);
                return;
            }
            result.put("tuijianN", config.getTuijianN());
            result.put("ratios", config.getTuijianRatios());

            long unitPaid = count <= 0 ? paidAmount : paidAmount / count;
            promoQueueService.handleOrderPaid(config, buyerUserId, spuId, paidAmount, unitPaid, orderId);

            // 取调用后的状态
            ShopQueuePositionDO buyer = queueMapper.selectByUserAndSpu(buyerUserId, spuId);
            result.put("buyerPosition", positionToMap(buyer));

            if (parentUserId != null && parentUserId > 0) {
                ShopQueuePositionDO parent = queueMapper.selectByUserAndSpu(parentUserId, spuId);
                result.put("parentPosition", positionToMap(parent));
            }

            List<ShopReferralContributionDO> contribs = contributionMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ShopReferralContributionDO>()
                            .eq(ShopReferralContributionDO::getSpuId, spuId)
                            .eq(ShopReferralContributionDO::getChildUserId, buyerUserId));
            result.put("contributionsByThisChild", contribs.stream().map(this::contribToMap).toArray());

            // 最近 5 条事件（debug 用）
            List<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueueEventDO> recent =
                    eventMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueueEventDO>()
                            .eq(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueueEventDO::getSpuId, spuId)
                            .orderByDesc(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueueEventDO::getId)
                            .last("LIMIT 5"));
            result.put("recentEvents", recent.stream().map(this::eventToMap).toArray());
        });
        return success(result);
    }

    /**
     * 重置一个 SPU 的所有 v7 状态（清 queue position + contribution + event + referral）。
     * 重复测试时用。
     */
    @Resource
    private javax.sql.DataSource dataSource;

    @PostMapping("/reset")
    public CommonResult<Map<String, Object>> reset(
            @RequestParam Long tenantId,
            @RequestParam Long spuId) {
        Map<String, Object> result = new HashMap<>();
        // 硬删（绕过 mybatis-plus 软删；UNIQUE 索引不含 deleted 列，软删会让 insert 复活时 dup）
        try (java.sql.Connection c = dataSource.getConnection();
             java.sql.Statement s = c.createStatement()) {
            int q = s.executeUpdate("DELETE FROM shop_queue_position WHERE tenant_id=" + tenantId + " AND spu_id=" + spuId);
            int co = s.executeUpdate("DELETE FROM shop_referral_contribution WHERE tenant_id=" + tenantId + " AND spu_id=" + spuId);
            int e = s.executeUpdate("DELETE FROM shop_queue_event WHERE tenant_id=" + tenantId + " AND spu_id=" + spuId);
            int r = s.executeUpdate("DELETE FROM shop_user_referral WHERE tenant_id=" + tenantId);
            result.put("deletedQueue", q);
            result.put("deletedContribution", co);
            result.put("deletedEvent", e);
            result.put("deletedReferral", r);
        } catch (java.sql.SQLException ex) {
            result.put("error", ex.getMessage());
        }
        return success(result);
    }

    private Map<String, Object> positionToMap(ShopQueuePositionDO p) {
        if (p == null) return null;
        Map<String, Object> m = new HashMap<>();
        m.put("userId", p.getUserId());
        m.put("state", p.getState());
        m.put("status", p.getStatus());
        m.put("layer", p.getLayer());
        m.put("accumulatedCount", p.getAccumulatedCount());
        m.put("accumulatedAmount", p.getAccumulatedAmount());
        return m;
    }

    private Map<String, Object> contribToMap(ShopReferralContributionDO c) {
        Map<String, Object> m = new HashMap<>();
        m.put("parentUserId", c.getParentUserId());
        m.put("childUserId", c.getChildUserId());
        m.put("parentStateAt", c.getParentStateAt());
        m.put("awardAmount", c.getAwardAmount());
        m.put("sourceOrderId", c.getSourceOrderId());
        return m;
    }

    private Map<String, Object> eventToMap(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueueEventDO e) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", e.getEventType());
        m.put("buyer", e.getSourceUserId());
        m.put("recipient", e.getBeneficiaryUserId());
        m.put("orderId", e.getSourceOrderId());
        m.put("position", e.getPositionIndex());
        m.put("ratio", e.getRatioPercent());
        m.put("amount", e.getAmount());
        return m;
    }
}
