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
    @Resource
    private cn.iocoder.yudao.module.member.api.user.MemberUserApi memberUserApi;
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopQueuePositionMapper shopQueuePositionMapper;

    // ==================== 商户级营销配置 ====================

    @GetMapping("/config")
    @Operation(summary = "获取营销配置")
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore
    public CommonResult<PromoConfigRespVO> getConfig(
            @RequestParam(name = "tenantId", required = false) Long tenantId) {
        // 决定查哪个 tenant：
        //   1) query 显式 tenantId → 用它（C 端跨店 / 商户老板从 storage 读 merchant tenant）
        //   2) 否则用 JWT token 自带 tenant（issueTokenForMerchant 签发时设的 merchant tenant）
        // 注：@TenantIgnore 让本方法 ctx 是 ignore，selectCurrent 用的空 wrapper 在 ignore 下
        //     会全表 selectOne 撞 TooManyResults。必须显式 execute 一个具体 tenant。
        Long targetTenant = (tenantId != null && tenantId > 0)
                ? tenantId
                : cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId();
        if (targetTenant == null || targetTenant <= 0) {
            // tenant 既没在 query 也不在 token → 返默认（空配置）
            return success(new PromoConfigRespVO());
        }
        return cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.execute(targetTenant, () -> {
            Long ctxTenant = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId();
            PromoConfigDO config = promoConfigService.getConfig();
            log.info("[GET /config] target={} ctx={} configId={} np={}",
                    targetTenant, ctxTenant,
                    config == null ? null : config.getId(),
                    config == null ? null : config.getNaturalPushEnabled());
            PromoConfigRespVO resp = new PromoConfigRespVO();
            BeanUtils.copyProperties(config, resp);
            resp.setId(config.getId());
            resp.setDirectCommissionRatio(config.getDirectCommissionRatio());
            resp.setNaturalPushEnabled(config.getNaturalPushEnabled());
            resp.setConsumePointRedeemEnabled(config.getConsumePointRedeemEnabled());
            resp.setConsumePointRedeemRatio(config.getConsumePointRedeemRatio());
            return success(resp);
        });
    }

    @PutMapping("/config")
    @Operation(summary = "保存本商户的营销配置（upsert）")
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore
    public CommonResult<Boolean> saveConfig(@Valid @RequestBody PromoConfigSaveReqVO reqVO) {
        // 商户老板用 member_user 身份登录商户端 (token tenant=0)，但配置必须写到 merchant tenant
        // 由前端 promo.js 设 header tenant-id = 商户 merchant tenant
        Long headerTenant = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId();
        if (headerTenant == null || headerTenant <= 0) {
            throw new IllegalArgumentException("缺少 tenant 上下文：请刷新页面重新登录");
        }
        cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.execute(headerTenant,
                () -> promoConfigService.saveConfig(reqVO));
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
    @Operation(summary = "批量取商品营销配置")
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

    @GetMapping("/my-spu-stars")
    @Operation(summary = "我在某店所有购买过商品的星级 + 升星规则（star.vue 直接渲染）")
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore
    public CommonResult<java.util.List<java.util.Map<String, Object>>> getMySpuStars(
            @RequestParam("tenantId") Long tenantId) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        java.util.List<java.util.Map<String, Object>> resp = new java.util.ArrayList<>();
        cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.execute(tenantId, () -> {
            // 1) 用户在该店所有 spu_id>0 的 star 行
            java.util.List<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO> rows =
                    userStarMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO>()
                            .eq(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO::getUserId, userId)
                            .gt(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO::getSpuId, 0L));
            if (rows == null || rows.isEmpty()) return null;
            java.util.List<Long> spuIds = new java.util.ArrayList<>();
            for (cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO r : rows) {
                if (r.getSpuId() != null) spuIds.add(r.getSpuId());
            }
            // 2) 拉对应 promo_config（升星规则 / starCount 等）
            java.util.Map<Long, cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO> cfgMap = new java.util.HashMap<>();
            java.util.List<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO> cfgs =
                    productPromoConfigMapper.selectListBySpuIds(spuIds);
            if (cfgs != null) for (cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO c : cfgs) cfgMap.put(c.getSpuId(), c);
            // 3) 拉 SPU 名 + 图
            java.util.Map<Long, cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO> spuMap = new java.util.HashMap<>();
            try {
                java.util.List<cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO> spus =
                        productSpuService.getSpuList(spuIds);
                if (spus != null) for (cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO s : spus) spuMap.put(s.getId(), s);
            } catch (Exception ignore) {}
            // 4) 组装
            for (cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO r : rows) {
                java.util.Map<String, Object> v = new java.util.HashMap<>();
                v.put("spuId", r.getSpuId());
                v.put("currentStar", r.getCurrentStar());
                v.put("directCount", r.getDirectCount());
                v.put("teamSalesCount", r.getTeamSalesCount());
                v.put("teamSalesAmount", r.getTeamSalesAmount());
                cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO s = spuMap.get(r.getSpuId());
                if (s != null) {
                    v.put("spuName", s.getName());
                    v.put("picUrl", s.getPicUrl());
                }
                cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO cfg = cfgMap.get(r.getSpuId());
                if (cfg != null) {
                    v.put("starCount", cfg.getStarCount());
                    v.put("starUpgradeRules", cfg.getStarUpgradeRules());
                    v.put("starRatios", cfg.getStarRatios());
                    v.put("tuijianN", cfg.getTuijianN());
                    v.put("tuijianRatios", cfg.getTuijianRatios());
                    v.put("spuPrice", s != null ? s.getPrice() : null);
                }
                resp.add(v);
            }
            return null;
        });
        return success(resp);
    }

    @Resource
    private cn.iocoder.yudao.module.product.service.spu.ProductSpuService productSpuService;

    @GetMapping("/account")
    @Operation(summary = "用户账户余额")
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore // 跨租户：手动按 header 分支
    public CommonResult<ShopUserStarDO> getMyAccount() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        // header tenant-id 决定行为：
        //   - 传了具体商户租户（>0，且不是 user 自己的 0/默认）→ 单店模式，仅返该店余额
        //     用于 checkout 抵扣展示（"本店消费积分 X"）
        //   - 没传 / 传 0 → 跨店聚合（钱包页"我的积分"显示全部）
        Long headerTenant = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId();
        boolean perTenant = headerTenant != null && headerTenant > 0;

        cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO agg =
                new cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO();
        agg.setUserId(userId);
        agg.setPromoPointBalance(0L);
        agg.setConsumePointBalance(0L);
        try {
            // 单店：仅查 header tenant；跨店：executeIgnore 拉全部
            java.util.List<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO> all;
            if (perTenant) {
                // 在 header tenant 上下文里查（TenantIgnore 已开 → 手动切回去）
                all = cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.execute(headerTenant, () ->
                        userStarMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO>()
                                        .eq(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO::getUserId, userId)));
            } else {
                all = cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.executeIgnore(() ->
                        userStarMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO>()
                                        .eq(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO::getUserId, userId)));
            }
            long sumPromo = 0, sumConsume = 0;
            int maxStar = 0;
            long sumDirect = 0, sumTeam = 0, sumTeamAmt = 0;
            for (cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO row : all) {
                Long spu = row.getSpuId();
                Long pb = row.getPromoPointBalance();
                Long cb = row.getConsumePointBalance();
                if (spu == null || spu == 0L) {
                    sumPromo += pb == null ? 0 : pb;
                    sumConsume += cb == null ? 0 : cb;
                } else {
                    if (row.getCurrentStar() != null && row.getCurrentStar() > maxStar) maxStar = row.getCurrentStar();
                    if (row.getDirectCount() != null) sumDirect += row.getDirectCount();
                    if (row.getTeamSalesCount() != null) sumTeam += row.getTeamSalesCount();
                    if (row.getTeamSalesAmount() != null) sumTeamAmt += row.getTeamSalesAmount();
                }
            }
            agg.setPromoPointBalance(sumPromo);
            agg.setConsumePointBalance(sumConsume);
            agg.setCurrentStar(maxStar);
            agg.setDirectCount((int) sumDirect);
            agg.setTeamSalesCount((int) sumTeam);
            agg.setTeamSalesAmount(sumTeamAmt);
        } catch (Exception e) {
            log.warn("[getMyAccount] 聚合失败 userId={} perTenant={} headerTenant={} : {}",
                    userId, perTenant, headerTenant, e.getMessage());
        }
        return success(agg);
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

    // ==================== 邀请资格（仅完成购买推 N 反 1 商品的用户才能分享）====================

    /**
     * 邀请资格查询：当前用户在哪些店买过「推 N 反 1」商品（shop_queue_position 存在即激活）。
     *
     * 返回：
     * - eligible：是否有任意店激活（true 才允许分享）
     * - shops：[{ tenantId, shopName, queueingCount, completedCount }]
     *   前端按 shop 生成各自的 invite 链接（必须带正确 tenantId 才能正确绑定 referral）
     */
    @GetMapping("/invite-eligibility")
    @Operation(summary = "邀请资格：列出当前用户已激活的店铺列表（购买过推 N 反 1 商品）")
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore
    public CommonResult<Map<String, Object>> getInviteEligibility() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        Map<String, Object> result = new HashMap<>();
        java.util.List<Map<String, Object>> shopList = new java.util.ArrayList<>();
        if (userId == null) {
            result.put("eligible", false);
            result.put("shops", shopList);
            return success(result);
        }
        // 跨租户聚合用户的 shop_queue_position
        java.util.List<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueuePositionDO> all =
                cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.executeIgnore(() ->
                        shopQueuePositionMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueuePositionDO>()
                                        .eq(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueuePositionDO::getUserId, userId)));
        // 按 tenant 聚合
        Map<Long, int[]> tenantAgg = new java.util.LinkedHashMap<>();
        if (all != null) {
            for (cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueuePositionDO p : all) {
                if (p == null || p.getTenantId() == null) continue;
                int[] cnt = tenantAgg.computeIfAbsent(p.getTenantId(), k -> new int[2]);
                if ("QUEUEING".equalsIgnoreCase(p.getStatus())) {
                    cnt[0]++;
                } else {
                    cnt[1]++;
                }
            }
        }
        if (tenantAgg.isEmpty()) {
            result.put("eligible", false);
            result.put("shops", shopList);
            return success(result);
        }
        // 一次性批量查 shopName
        java.util.Map<Long, String> shopNameMap = new java.util.HashMap<>();
        try {
            java.util.List<cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO> infos =
                    shopInfoMapper.selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO>()
                                    .in(cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO::getTenantId, tenantAgg.keySet()));
            if (infos != null) {
                for (cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO info : infos) {
                    if (info != null && info.getTenantId() != null) {
                        shopNameMap.put(info.getTenantId(), info.getShopName());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[getInviteEligibility] 批量查 shop_info 失败：{}", e.getMessage());
        }
        for (Map.Entry<Long, int[]> e : tenantAgg.entrySet()) {
            Long tid = e.getKey();
            Map<String, Object> shop = new HashMap<>();
            shop.put("tenantId", tid);
            shop.put("shopName", shopNameMap.getOrDefault(tid, "店铺 #" + tid));
            shop.put("queueingCount", e.getValue()[0]);
            shop.put("completedCount", e.getValue()[1]);
            // 每店挑一个「推 N 反 1」招牌商品给海报展示用 ——
            // 优先 tuijianEnabled=true 且 tuijianN>0；同店多个则取 tuijianN 最大的
            Map<String, Object> topSpu = pickTopTuijianSpuForShop(tid);
            if (topSpu != null) shop.put("topTuijianSpu", topSpu);
            shopList.add(shop);
        }
        result.put("eligible", true);
        result.put("shops", shopList);
        return success(result);
    }

    /**
     * 海报用：挑该店启用了「推 N 反 1」的招牌商品。
     * 选择策略：tuijianEnabled=true 且 tuijianN>0；多个时取 N 最大；
     * 没有时返回 null（前端用兜底文案）。
     */
    private Map<String, Object> pickTopTuijianSpuForShop(Long tenantId) {
        try {
            return cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.execute(tenantId, () -> {
                java.util.List<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO> cfgs =
                        productPromoConfigMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO>()
                                        .eq(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO::getTuijianEnabled, Boolean.TRUE)
                                        .gt(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO::getTuijianN, 0)
                                        .orderByDesc(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO::getTuijianN));
                if (cfgs == null || cfgs.isEmpty()) return null;
                cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO best = cfgs.get(0);
                Map<String, Object> spuRet = new HashMap<>();
                spuRet.put("spuId", best.getSpuId());
                spuRet.put("tuijianN", best.getTuijianN());
                spuRet.put("tuijianRatios", best.getTuijianRatios());
                try {
                    cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO spu =
                            productSpuService.getSpu(best.getSpuId());
                    if (spu != null) {
                        spuRet.put("spuName", spu.getName());
                        spuRet.put("spuPic", spu.getPicUrl());
                        spuRet.put("price", spu.getPrice());
                        spuRet.put("marketPrice", spu.getMarketPrice());
                    }
                } catch (Exception ignore) {}
                return spuRet;
            });
        } catch (Exception e) {
            log.warn("[pickTopTuijianSpuForShop] tenant={} 失败：{}", tenantId, e.getMessage());
            return null;
        }
    }

    // ==================== 中奖公榜 / 滚动条 / 今日入账 ====================

    /** 手机号脱敏：138****6789 */
    private static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) return mobile == null ? "" : mobile;
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    /** V044 合规整改：sourceType → 商业化友好描述（去敏感词） */
    private static String sourceTypeLabel(String t) {
        if (t == null) return "促销奖励";
        switch (t) {
            case "DIRECT":              return "邀请有礼";
            case "QUEUE":               return "邀请累积奖";
            case "COMMISSION":          return "分享激励";       // 原"团队佣金"已下线（V044）
            case "POOL":
            case "POOL_V8":             return "促销优惠";
            case "SELF_BATCH":
            case "SELF_PROGRESS":
            case "SELF_COMMISSION":     return "复购感谢奖";
            case "REFERRAL_PROGRESS":
            case "REFERRAL_COMMISSION": return "分享感谢奖";
            case "CONVERT":             return "积分兑换";
            case "WITHDRAW":            return "提现";
            case "MANUAL_PATCH":        return "账户调整";
            case "REDEEM_ORDER":        return "订单抵扣";
            case "ORDER_DEDUCT":        return "订单使用";
            default:                    return "促销奖励";
        }
    }

    @GetMapping("/winners")
    @Operation(summary = "中奖公榜 Top N — 按派奖金额从大到小排，相同金额按时间倒序")
    @Parameter(name = "limit", description = "返回条数，默认 100，最大 200")
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore
    public CommonResult<List<Map<String, Object>>> listWinners(
            @RequestParam(name = "limit", required = false, defaultValue = "100") Integer limit) {
        int n = Math.min(Math.max(limit == null ? 100 : limit, 1), 200);
        Long headerTenant = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId();
        boolean perTenant = headerTenant != null && headerTenant > 0;

        java.util.List<ShopPromoRecordDO> records;
        java.util.concurrent.Callable<java.util.List<ShopPromoRecordDO>> q = () ->
                promoRecordMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ShopPromoRecordDO>()
                                .gt(ShopPromoRecordDO::getAmount, 0L)
                                .orderByDesc(ShopPromoRecordDO::getAmount)
                                .orderByDesc(ShopPromoRecordDO::getId)
                                .last("LIMIT " + n));
        if (perTenant) {
            records = cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.execute(headerTenant, q);
        } else {
            records = cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.executeIgnore(q);
        }
        return success(toWinnersVO(records));
    }

    @GetMapping("/winners-ticker")
    @Operation(summary = "首页滚动条 — 跨店最新派奖 N 条")
    @Parameter(name = "limit", description = "默认 8")
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore
    public CommonResult<List<Map<String, Object>>> listWinnersTicker(
            @RequestParam(name = "limit", required = false, defaultValue = "8") Integer limit) {
        int n = Math.min(Math.max(limit == null ? 8 : limit, 1), 30);
        java.util.List<ShopPromoRecordDO> records = cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.executeIgnore(() ->
                promoRecordMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ShopPromoRecordDO>()
                                .gt(ShopPromoRecordDO::getAmount, 0L)
                                .orderByDesc(ShopPromoRecordDO::getId)
                                .last("LIMIT " + n)));
        return success(toWinnersVO(records));
    }

    /** ShopPromoRecordDO 批量补 shopName / userMask → VO Map */
    private java.util.List<Map<String, Object>> toWinnersVO(java.util.List<ShopPromoRecordDO> records) {
        java.util.List<Map<String, Object>> resp = new java.util.ArrayList<>();
        if (records == null || records.isEmpty()) return resp;

        java.util.Set<Long> tenantIds = new java.util.HashSet<>();
        java.util.Set<Long> userIds = new java.util.HashSet<>();
        for (ShopPromoRecordDO r : records) {
            if (r.getTenantId() != null) tenantIds.add(r.getTenantId());
            if (r.getUserId() != null) userIds.add(r.getUserId());
        }
        java.util.Map<Long, String> shopNameMap = new java.util.HashMap<>();
        if (!tenantIds.isEmpty()) {
            try {
                java.util.List<cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO> infos =
                        shopInfoMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO>()
                                        .in(cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO::getTenantId, tenantIds));
                for (cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO i : infos) {
                    if (i.getTenantId() != null && i.getShopName() != null) shopNameMap.put(i.getTenantId(), i.getShopName());
                }
            } catch (Exception e) {
                log.warn("[toWinnersVO] 查 shop_info 失败：{}", e.getMessage());
            }
        }
        java.util.Map<Long, String> userMobileMap = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            try {
                java.util.Map<Long, cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO> userMap =
                        memberUserApi.getUserMap(userIds);
                if (userMap != null) {
                    for (java.util.Map.Entry<Long, cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO> e : userMap.entrySet()) {
                        cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO u = e.getValue();
                        if (u != null && u.getMobile() != null) {
                            userMobileMap.put(e.getKey(), u.getMobile());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[toWinnersVO] 批拉 member_user 失败：{}", e.getMessage());
            }
        }

        for (ShopPromoRecordDO r : records) {
            Map<String, Object> v = new HashMap<>();
            v.put("id", r.getId());
            v.put("userId", r.getUserId());
            v.put("userMask", maskMobile(userMobileMap.get(r.getUserId())));
            v.put("tenantId", r.getTenantId());
            v.put("shopName", r.getTenantId() != null ? shopNameMap.get(r.getTenantId()) : null);
            v.put("amount", r.getAmount());
            v.put("sourceType", r.getSourceType());
            v.put("sourceLabel", sourceTypeLabel(r.getSourceType()));
            v.put("remark", r.getRemark());
            v.put("createTime", r.getCreateTime());
            resp.add(v);
        }
        return resp;
    }

    @GetMapping("/today-stat")
    @Operation(summary = "今日入账汇总（推广积分 + 消费积分 + 派奖次数）；未登录 = 全网汇总，登录 = 当前用户")
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore
    public CommonResult<Map<String, Object>> getTodayStat() {
        // 未登录场景：登录页 hero 要展示"全网今日派奖"，userId=null 时不加 userId 过滤
        final Long userId = SecurityFrameworkUtils.getLoginUserId();
        Long headerTenant = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId();
        boolean perTenant = headerTenant != null && headerTenant > 0;

        java.time.LocalDateTime start = java.time.LocalDate.now().atStartOfDay();
        java.time.LocalDateTime end = java.time.LocalDate.now().plusDays(1).atStartOfDay();

        java.util.concurrent.Callable<long[]> agg = () -> {
            long[] sums = new long[3];
            java.util.List<ShopPromoRecordDO> promo = promoRecordMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ShopPromoRecordDO>()
                            .eq(userId != null, ShopPromoRecordDO::getUserId, userId)
                            .gt(ShopPromoRecordDO::getAmount, 0L)
                            .ge(ShopPromoRecordDO::getCreateTime, start)
                            .lt(ShopPromoRecordDO::getCreateTime, end));
            for (ShopPromoRecordDO r : promo) {
                sums[0] += r.getAmount() == null ? 0 : r.getAmount();
                sums[2] += 1;
            }
            java.util.List<ShopConsumePointRecordDO> consume = consumePointRecordMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ShopConsumePointRecordDO>()
                            .eq(userId != null, ShopConsumePointRecordDO::getUserId, userId)
                            .gt(ShopConsumePointRecordDO::getAmount, 0L)
                            .ge(ShopConsumePointRecordDO::getCreateTime, start)
                            .lt(ShopConsumePointRecordDO::getCreateTime, end));
            for (ShopConsumePointRecordDO r : consume) {
                sums[1] += r.getAmount() == null ? 0 : r.getAmount();
            }
            return sums;
        };

        long[] sums;
        if (perTenant) {
            sums = cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.execute(headerTenant, agg);
        } else {
            sums = cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.executeIgnore(agg);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("promoAmountToday",   sums[0]);   // 推广积分(分)
        result.put("consumeAmountToday", sums[1]);   // 消费积分(分)
        result.put("awardCountToday",    sums[2]);   // 派奖笔数
        return success(result);
    }

}
