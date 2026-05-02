package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.promo.ProductPromoConfigRespVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.promo.ProductPromoConfigSaveReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.promo.PromoConfigRespVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.promo.PromoConfigSaveReqVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppQueuePositionRespVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopConsumePointRecordDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoPoolDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoPoolRoundDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoRecordDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopConsumePointRecordMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoPoolMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoPoolRoundMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoRecordMapper;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.merchant.service.promo.PoolSettlementService;
import cn.iocoder.yudao.module.merchant.service.promo.ProductPromoConfigService;
import cn.iocoder.yudao.module.merchant.service.promo.PromoConfigService;
import cn.iocoder.yudao.module.merchant.service.promo.PromoPointService;
import cn.iocoder.yudao.module.merchant.service.promo.PromoQueueService;
import cn.iocoder.yudao.module.merchant.service.promo.ReferralService;
import cn.iocoder.yudao.module.merchant.service.promo.StarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 商户小程序 - 营销配置（双积分 / 极差 / 推 N 反 1 / 星级积分池）
 *
 * 与管理后台的 PromoConfigController / ProductPromoConfigController 共用同一 Service，
 * 仅去掉 @PreAuthorize（小程序通过 JWT + 租户上下文鉴权，TenantBaseDO 自动隔离数据）。
 */
@Tag(name = "商户小程序 - 营销配置")
@RestController
@RequestMapping("/merchant/mini/promo")
@Validated
public class AppMerchantPromoController {

    @Resource
    private PromoConfigService promoConfigService;
    @Resource
    private ProductPromoConfigService productPromoConfigService;
    @Resource
    private PoolSettlementService poolSettlementService;
    @Resource
    private ShopPromoPoolMapper poolMapper;
    @Resource
    private ShopPromoPoolRoundMapper poolRoundMapper;
    @Resource
    private ReferralService referralService;
    @Resource
    private StarService starService;
    @Resource
    private PromoPointService promoPointService;
    @Resource
    private PromoQueueService promoQueueService;
    @Resource
    private ShopPromoRecordMapper promoRecordMapper;
    @Resource
    private ShopConsumePointRecordMapper consumePointRecordMapper;
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserReferralMapper shopUserReferralMapper;

    // ==================== 商户级营销配置 ====================

    @GetMapping("/config")
    @Operation(summary = "获取本商户的营销配置（不存在则返默认值）")
    public CommonResult<PromoConfigRespVO> getConfig() {
        PromoConfigDO config = promoConfigService.getConfig();
        PromoConfigRespVO resp = new PromoConfigRespVO();
        BeanUtils.copyProperties(config, resp);
        return success(resp);
    }

    @PutMapping("/config")
    @Operation(summary = "保存本商户的营销配置（upsert）")
    public CommonResult<Boolean> saveConfig(@Valid @RequestBody PromoConfigSaveReqVO reqVO) {
        promoConfigService.saveConfig(reqVO);
        return success(true);
    }

    // ==================== 商品级营销配置 ====================

    @GetMapping("/product-config")
    @Operation(summary = "获取某商品的营销配置（不存在则返默认值，全关）")
    @Parameter(name = "spuId", description = "商品 SPU ID", required = true)
    public CommonResult<ProductPromoConfigRespVO> getProductConfig(@RequestParam("spuId") @NotNull Long spuId) {
        ProductPromoConfigDO config = productPromoConfigService.getBySpuId(spuId);
        ProductPromoConfigRespVO resp = new ProductPromoConfigRespVO();
        BeanUtils.copyProperties(config, resp);
        return success(resp);
    }

    @PutMapping("/product-config")
    @Operation(summary = "保存某商品的营销配置（upsert）")
    public CommonResult<Boolean> saveProductConfig(@Valid @RequestBody ProductPromoConfigSaveReqVO reqVO) {
        productPromoConfigService.save(reqVO);
        return success(true);
    }

    // ==================== 星级积分池 ====================

    @GetMapping("/pool/info")
    @Operation(summary = "查看本商户当前积分池余额")
    public CommonResult<Map<String, Object>> getPoolInfo() {
        ShopPromoPoolDO pool = poolMapper.selectCurrent();
        Map<String, Object> result = new HashMap<>();
        result.put("balance", pool == null ? 0L : pool.getBalance());
        result.put("lastSettledAt", pool == null ? null : pool.getLastSettledAt());
        return success(result);
    }

    @PostMapping("/pool/settle")
    @Operation(summary = "立即触发积分池结算")
    @Parameter(name = "mode", description = "FULL 全员均分 / LOTTERY 抽奖", required = true, example = "FULL")
    public CommonResult<ShopPromoPoolRoundDO> settlePool(@RequestParam("mode") @NotNull String mode) {
        return success(poolSettlementService.settleNow(mode));
    }

    @GetMapping("/pool/rounds")
    @Operation(summary = "积分池结算历史（分页倒序）")
    public CommonResult<PageResult<ShopPromoPoolRoundDO>> listPoolRounds(@Valid PageParam pageParam) {
        return success(poolRoundMapper.selectPage(pageParam));
    }

    // ==================== 推荐链绑定 ====================

    /**
     * 当前登录用户绑定上级（首次有效，后续重复调用无副作用）。
     *
     * 典型调用时机：用户从分享链接（带 ?inviter=xxx）进入小程序后，登录完成立即调一次。
     * 或在用户首单 submit 前由前端调用一次（保证下一笔订单的引擎能识别到 parent）。
     *
     * 服务端做以下校验：自绑 / 形成环 / 已绑定（首次绑定生效）。
     */
    @PostMapping("/referral/bind")
    @Operation(summary = "绑定上级（首次有效）")
    @Parameter(name = "inviterUserId", description = "上级用户 ID（来自分享链路）", required = true)
    public CommonResult<Boolean> bindReferral(@RequestParam("inviterUserId") @NotNull Long inviterUserId,
                                              @RequestParam(value = "orderId", required = false) Long orderId) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        boolean newlyBound = referralService.bindParent(userId, inviterUserId, orderId);
        if (newlyBound) {
            // 上级 direct_count + 1，并尝试升星
            starService.handleReferralBound(inviterUserId);
        }
        return success(newlyBound);
    }

    @GetMapping("/referral/parent")
    @Operation(summary = "查询当前用户的上级（0 = 自然用户 / 未绑定）")
    public CommonResult<Long> getReferralParent() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(referralService.getDirectParent(userId));
    }

    /**
     * 跨所有店铺，统计当前用户已推荐的不重复下级数（C 端「我的」页跨店聚合用）。
     * 同一个朋友在多家店都被你推荐时只算 1 个。
     */
    @GetMapping("/referral/my-children-count")
    @Operation(summary = "跨店聚合：我推荐了多少不重复的好友")
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore
    public CommonResult<java.util.Map<String, Object>> myChildrenCount() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        java.util.List<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserReferralDO> list =
                cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.executeIgnore(() ->
                        shopUserReferralMapper.selectAllByParentUserIdAcrossTenants(userId));
        long count = list.stream()
                .map(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserReferralDO::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct().count();
        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("count", count);
        return success(resp);
    }

    // ==================== 用户钱包（双积分账户） ====================

    @GetMapping("/account")
    @Operation(summary = "当前用户星级 / 双积分余额")
    public CommonResult<ShopUserStarDO> getMyAccount() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(promoPointService.getOrCreateAccount(userId));
    }

    @GetMapping("/promo-records")
    @Operation(summary = "推广积分流水（分页倒序）")
    public CommonResult<PageResult<ShopPromoRecordDO>> listPromoRecords(@Valid PageParam pageParam) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(promoRecordMapper.selectPageByUser(userId, pageParam));
    }

    @GetMapping("/consume-records")
    @Operation(summary = "消费积分流水（分页倒序）")
    public CommonResult<PageResult<ShopConsumePointRecordDO>> listConsumeRecords(@Valid PageParam pageParam) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(consumePointRecordMapper.selectPageByUser(userId, pageParam));
    }

    @PostMapping("/convert")
    @Operation(summary = "推广积分 → 消费积分（按商户配置 ratio 换算）")
    @Parameter(name = "promoAmount", description = "扣减的推广积分(分)", required = true)
    @Parameter(name = "idempotencyKey", description = "幂等键（前端可用 Date.now()），同 key 重放不会重复转换", required = true)
    public CommonResult<Boolean> convertPromoToConsume(
            @RequestParam("promoAmount") @NotNull Long promoAmount,
            @RequestParam("idempotencyKey") @NotNull Long idempotencyKey) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        promoPointService.convertPromoToConsume(userId, promoAmount, idempotencyKey);
        return success(true);
    }

    // ==================== 我的队列状态 ====================

    @GetMapping("/my-queues")
    @Operation(summary = "当前用户在所有商品队列中的位置（仅 QUEUEING）")
    public CommonResult<List<AppQueuePositionRespVO>> listMyQueues() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(promoQueueService.listMyQueueing(userId));
    }

}
