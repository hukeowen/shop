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
@lombok.extern.slf4j.Slf4j
public class AppMerchantPromoController {

    @Resource
    private PromoConfigService promoConfigService;
    @Resource
    private ProductPromoConfigService productPromoConfigService;
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.promo.ProductPromoConfigMapper productPromoConfigMapper;
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
    @Resource
    private cn.iocoder.yudao.module.merchant.service.MemberShopRelService memberShopRelService;
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper shopInfoMapper;
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserStarMapper userStarMapper;

    // ==================== 商户级营销配置 ====================

    @GetMapping("/config")
    @Operation(summary = "获取营销配置（不传 tenantId 用 ctx 商户；C 端 checkout 必须传 tenantId 走目标店铺）")
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore
    public CommonResult<PromoConfigRespVO> getConfig(
            @RequestParam(name = "tenantId", required = false) Long tenantId) {
        // C 端跨店：显式传 tenantId → 切上下文到该店；商户自己看自家 → 不传走 ctx
        Long oldTid = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId();
        if (tenantId != null && tenantId > 0) {
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(tenantId);
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setIgnore(false);
        }
        try {
            PromoConfigDO config = promoConfigService.getConfig();
            PromoConfigRespVO resp = new PromoConfigRespVO();
            BeanUtils.copyProperties(config, resp);
            // BeanUtils 在 JDK8 反射 corner case 不拷 BigDecimal/Boolean 包装类，主动兜底
            resp.setDirectCommissionRatio(config.getDirectCommissionRatio());
            resp.setNaturalPushEnabled(config.getNaturalPushEnabled());
            resp.setConsumePointRedeemEnabled(config.getConsumePointRedeemEnabled());
            resp.setConsumePointRedeemRatio(config.getConsumePointRedeemRatio());
            return success(resp);
        } finally {
            // 复位上下文（避免污染后续同请求其他 service）
            if (tenantId != null && tenantId > 0) {
                cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(oldTid);
            }
        }
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

    @GetMapping("/product-configs")
    @Operation(summary = "批量取商品营销配置（C 端 shop-home 用，判断哪些商品启用了推 N 反 1 → 选「招牌」）")
    @Parameter(name = "spuIds", description = "SPU ID 逗号串，如 1,2,3", required = true)
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore
    public CommonResult<java.util.List<ProductPromoConfigRespVO>> listProductConfigs(
            @RequestParam("spuIds") String spuIds) {
        java.util.List<ProductPromoConfigRespVO> result = new java.util.ArrayList<>();
        if (spuIds == null || spuIds.isEmpty()) return success(result);
        java.util.List<Long> idList = new java.util.ArrayList<>();
        for (String s : spuIds.split(",")) {
            String t = s.trim();
            if (t.isEmpty()) continue;
            try { idList.add(Long.parseLong(t)); } catch (NumberFormatException ignore) {}
        }
        if (idList.isEmpty()) return success(result);
        java.util.List<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO> rows =
                productPromoConfigMapper.selectListBySpuIds(idList);
        if (rows == null) return success(result);
        for (cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO row : rows) {
            ProductPromoConfigRespVO vo = new ProductPromoConfigRespVO();
            BeanUtils.copyProperties(row, vo);
            result.add(vo);
        }
        return success(result);
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

    // ==================== v8 SPU 级星级奖池（按商品独立池） ====================

    @Resource
    private cn.iocoder.yudao.module.merchant.service.promo.SpuPoolSettleService spuPoolSettleService;
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.promo.SpuStarPoolMapper spuStarPoolMapper;
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.promo.SpuStarPoolSettleRecordMapper spuStarPoolSettleRecordMapper;
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.promo.SpuStarPoolPayoutItemMapper spuStarPoolPayoutItemMapper;

    @GetMapping("/spu-pool/balance")
    @Operation(summary = "v8 查某 SPU 的池余额 + 累计入/出")
    @Parameter(name = "spuId", required = true)
    public CommonResult<Map<String, Object>> getSpuPoolBalance(@RequestParam("spuId") @NotNull Long spuId) {
        cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolDO pool =
                spuStarPoolMapper.selectBySpuId(spuId);
        Map<String, Object> resp = new HashMap<>();
        if (pool == null) {
            resp.put("poolBalance", 0L);
            resp.put("totalIn", 0L);
            resp.put("totalOut", 0L);
        } else {
            resp.put("poolBalance", pool.getPoolBalance() == null ? 0L : pool.getPoolBalance());
            resp.put("totalIn", pool.getTotalIn() == null ? 0L : pool.getTotalIn());
            resp.put("totalOut", pool.getTotalOut() == null ? 0L : pool.getTotalOut());
        }
        return success(resp);
    }

    @PostMapping("/spu-pool/settle")
    @Operation(summary = "v8 立即结算某 SPU 的奖池")
    public CommonResult<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolSettleRecordDO> settleSpuPool(
            @RequestParam("spuId") @NotNull Long spuId,
            @RequestParam(value = "remark", required = false) String remark) {
        return success(spuPoolSettleService.settle(spuId, remark));
    }

    @GetMapping("/spu-pool/settle-records")
    @Operation(summary = "v8 分页查某 SPU 的历次结算单（倒序）")
    public CommonResult<PageResult<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolSettleRecordDO>> listSpuPoolRecords(
            @RequestParam("spuId") @NotNull Long spuId,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return success(spuStarPoolSettleRecordMapper.selectPageBySpuId(spuId, pageNo, pageSize));
    }

    @GetMapping("/spu-pool/settle-record/payouts")
    @Operation(summary = "v8 查某次结算的中奖明细")
    public CommonResult<List<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolPayoutItemDO>> listSpuPoolPayouts(
            @RequestParam("settleId") @NotNull Long settleId) {
        return success(spuStarPoolPayoutItemMapper.selectListBySettleId(settleId));
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
    @Operation(summary = "当前用户星级 / 双积分余额（v8: star/direct/team 按 SPU 聚合最高 + 累计）")
    public CommonResult<ShopUserStarDO> getMyAccount() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        ShopUserStarDO base = promoPointService.getOrCreateAccount(userId);
        // v8 升星都写在 spu_id>0 行（商品级账户），全局账户 spu_id=0 永远是 0
        // → UI 看到 currentStar=0 困惑。聚合：max(star) + sum(direct/team) 覆盖回 base。
        // 余额（promoPointBalance/consumePointBalance）不动，仍是全局共享。
        try {
            java.util.List<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO> all =
                    userStarMapper.selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO>()
                                    .eq(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO::getUserId, userId)
                                    .gt(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO::getSpuId, 0L));
            if (all != null && !all.isEmpty()) {
                int maxStar = 0;
                long sumDirect = 0;
                long sumTeam = 0;
                long sumTeamAmt = 0;
                for (cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO row : all) {
                    if (row.getCurrentStar() != null && row.getCurrentStar() > maxStar) maxStar = row.getCurrentStar();
                    if (row.getDirectCount() != null) sumDirect += row.getDirectCount();
                    if (row.getTeamSalesCount() != null) sumTeam += row.getTeamSalesCount();
                    if (row.getTeamSalesAmount() != null) sumTeamAmt += row.getTeamSalesAmount();
                }
                base.setCurrentStar(maxStar);
                base.setDirectCount((int) sumDirect);
                base.setTeamSalesCount((int) sumTeam);
                base.setTeamSalesAmount(sumTeamAmt);
            }
        } catch (Exception e) {
            log.warn("[getMyAccount] SPU 聚合失败 userId={} : {}", userId, e.getMessage());
        }
        return success(base);
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
    @Operation(summary = "当前用户在所有商品队列中的位置（跨店聚合，仅 QUEUEING）")
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore
    public CommonResult<List<AppQueuePositionRespVO>> listMyQueues() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        // 1. 找用户加入的所有店铺
        java.util.List<cn.iocoder.yudao.module.merchant.dal.dataobject.MemberShopRelDO> rels =
                memberShopRelService.listByUserId(userId);
        if (rels == null || rels.isEmpty()) {
            return success(java.util.Collections.emptyList());
        }
        java.util.List<AppQueuePositionRespVO> aggregated = new java.util.ArrayList<>();
        // 2. 每个 tenant 切上下文查队列 + 商品 + 营销配置
        for (cn.iocoder.yudao.module.merchant.dal.dataobject.MemberShopRelDO rel : rels) {
            Long tid = rel.getTenantId();
            if (tid == null || tid <= 0) continue;
            try {
                cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.execute(tid, () -> {
                    java.util.List<AppQueuePositionRespVO> rows = promoQueueService.listMyQueueing(userId);
                    if (rows == null || rows.isEmpty()) return;
                    for (AppQueuePositionRespVO vo : rows) {
                        if (vo.getTenantId() == null) vo.setTenantId(tid);
                    }
                    aggregated.addAll(rows);
                });
            } catch (Exception e) {
                // 单个店铺失败不影响其他店铺聚合结果，但要 log.warn 留排查痕迹
                log.warn("[listMyQueues] tenant={} userId={} 聚合失败：{}", tid, userId, e.getMessage());
            }
        }
        if (aggregated.isEmpty()) return success(aggregated);
        // 3. 批量一次性查 shopName（shop_info 是 BaseDO，跨租户直接 IN 查询，避免 N 次往返）
        java.util.Set<Long> tenantIdSet = aggregated.stream()
                .map(AppQueuePositionRespVO::getTenantId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, String> shopNameMap = new java.util.HashMap<>(tenantIdSet.size());
        if (!tenantIdSet.isEmpty()) {
            try {
                java.util.List<cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO> infos =
                        shopInfoMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO>()
                                        .in(cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO::getTenantId, tenantIdSet));
                if (infos != null) {
                    for (cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO info : infos) {
                        if (info != null && info.getTenantId() != null && info.getShopName() != null) {
                            shopNameMap.put(info.getTenantId(), info.getShopName());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[listMyQueues] 批量查 shop_info 失败，shopName 字段将留空：{}", e.getMessage());
            }
        }
        for (AppQueuePositionRespVO vo : aggregated) {
            if (vo.getShopName() == null && vo.getTenantId() != null) {
                vo.setShopName(shopNameMap.getOrDefault(vo.getTenantId(), null));
            }
        }
        return success(aggregated);
    }

}
