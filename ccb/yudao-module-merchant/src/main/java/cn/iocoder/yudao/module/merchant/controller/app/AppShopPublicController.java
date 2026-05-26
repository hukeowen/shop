package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.impl.ClientIpRateLimiterKeyResolver;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.merchant.controller.app.vo.shop.AppShopPublicInfoRespVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopBrokerageConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MemberShopRelDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopBrokerageConfigMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.MemberShopRelMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.PromoConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import java.util.HashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 用户小程序 - 店铺公开发现接口（跨租户）
 */
@Tag(name = "用户端 - 店铺公开信息")
@RestController
@RequestMapping("/merchant/shop/public")
@Validated
@lombok.extern.slf4j.Slf4j
public class AppShopPublicController {

    @Resource
    private ShopInfoMapper shopInfoMapper;
    @Resource
    private ShopBrokerageConfigMapper shopBrokerageConfigMapper;
    @Resource
    private PromoConfigMapper promoConfigMapper;
    @Resource
    private MemberShopRelMapper memberShopRelMapper;
    @Resource
    private cn.iocoder.yudao.module.product.service.spu.ProductSpuService productSpuService;
    @Resource
    private cn.iocoder.yudao.module.product.service.sku.ProductSkuService productSkuService;
    @Resource
    private cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper productSpuMapper;
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.promo.ProductPromoConfigMapper productPromoConfigMapper;

    @GetMapping("/list")
    @Operation(summary = "分页查询店铺列表（V039 三层闸门过滤：今日未打卡/主动打烊的店不返回；营业时间外的店权重靠后）")
    @PermitAll
    @TenantIgnore
    public CommonResult<PageResult<java.util.Map<String, Object>>> listShops(
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "kw", required = false) String kw,
            @RequestParam(value = "businessType", required = false) String businessType) {
        // V042 改造：放开「今日已打卡 + 未主动打烊」硬过滤，未营业店也显示但排最后。
        // SQL 层只过 status=1（HIDDEN/已删 不显示）；OPEN / OUTSIDE_HOURS / CLOSED 都返。
        // 内存排序：OPEN 优先 → OUTSIDE_HOURS / CLOSED 之后，按 sales30d DESC。
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(pageNo);
        // 多拉样本做内存排序：避免分页边界两个 OPEN 店被排到第二页之后
        pageParam.setPageSize(Math.max(pageSize * 3, 30));
        LambdaQueryWrapper<ShopInfoDO> w = new LambdaQueryWrapper<ShopInfoDO>()
                .eq(ShopInfoDO::getStatus, 1);
        if (kw != null && !kw.trim().isEmpty()) {
            w.like(ShopInfoDO::getShopName, kw.trim());
        }
        // V041: 行业分类过滤（用户从「分类」页点进来时带 businessType）
        if (businessType != null && !businessType.trim().isEmpty()) {
            w.eq(ShopInfoDO::getBusinessType, businessType.trim());
        }
        PageResult<ShopInfoDO> page = shopInfoMapper.selectPage(pageParam, w);
        // 加 isOpenNow 字段 + 排序：OPEN 优先 OUTSIDE_HOURS 之后
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>(page.getList().size());
        for (ShopInfoDO shop : page.getList()) {
            java.util.Map<String, Object> m = cn.hutool.core.bean.BeanUtil.beanToMap(shop, false, true);
            cn.iocoder.yudao.module.merchant.util.ShopOperatingUtils.OperatingStatus st =
                    cn.iocoder.yudao.module.merchant.util.ShopOperatingUtils.computeStatus(shop);
            m.put("isOpenNow", st == cn.iocoder.yudao.module.merchant.util.ShopOperatingUtils.OperatingStatus.OPEN);
            m.put("operatingStatus", st.name());
            list.add(m);
        }
        list.sort((a, b) -> {
            boolean ao = Boolean.TRUE.equals(a.get("isOpenNow"));
            boolean bo = Boolean.TRUE.equals(b.get("isOpenNow"));
            if (ao != bo) return ao ? -1 : 1;
            int as = a.get("sales30d") == null ? 0 : ((Number) a.get("sales30d")).intValue();
            int bs = b.get("sales30d") == null ? 0 : ((Number) b.get("sales30d")).intValue();
            return bs - as;
        });
        // 内存排序完按外部 pageSize 截一段
        int from = 0;
        int to = Math.min(pageSize, list.size());
        java.util.List<java.util.Map<String, Object>> slice = new java.util.ArrayList<>(list.subList(from, to));

        // V043: 给每个店注入 topSpu（"明星商品"）— 优先选启用了"推 N 反 1"的销量最高商品，
        // 兜底选全店销量最高商品。每店 1-2 次 SQL，N ≤ pageSize 可控。
        for (java.util.Map<String, Object> shop : slice) {
            Object tidObj = shop.get("tenantId");
            if (!(tidObj instanceof Number)) continue;
            Long tid = ((Number) tidObj).longValue();
            try {
                cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.execute(tid, () -> {
                    cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO chosen = null;
                    // 1) 优先：推 N 反 1 启用 + 销量最高
                    try {
                        java.util.List<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO> cfgs =
                                productPromoConfigMapper.selectList(
                                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO>()
                                                .eq(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO::getTuijianEnabled, true));
                        java.util.Set<Long> promoSpuIds = new java.util.HashSet<>();
                        if (cfgs != null) for (cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO c : cfgs) {
                            if (c.getSpuId() != null) promoSpuIds.add(c.getSpuId());
                        }
                        if (!promoSpuIds.isEmpty()) {
                            java.util.List<cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO> top =
                                    productSpuMapper.selectList(
                                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO>()
                                                    .in(cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO::getId, promoSpuIds)
                                                    .eq(cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO::getStatus, 1)
                                                    .orderByDesc(cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO::getSalesCount)
                                                    .last("LIMIT 1"));
                            if (top != null && !top.isEmpty()) chosen = top.get(0);
                        }
                    } catch (Exception ignore) {}
                    // 2) 兜底：全店销量最高
                    if (chosen == null) {
                        try {
                            java.util.List<cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO> top =
                                    productSpuMapper.selectList(
                                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO>()
                                                    .eq(cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO::getStatus, 1)
                                                    .orderByDesc(cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO::getSalesCount)
                                                    .last("LIMIT 1"));
                            if (top != null && !top.isEmpty()) chosen = top.get(0);
                        } catch (Exception ignore) {}
                    }
                    if (chosen != null) {
                        java.util.Map<String, Object> topSpu = new java.util.HashMap<>(6);
                        topSpu.put("id", chosen.getId());
                        topSpu.put("name", chosen.getName());
                        topSpu.put("picUrl", chosen.getPicUrl());
                        topSpu.put("price", chosen.getPrice());
                        topSpu.put("salesCount", chosen.getSalesCount());
                        shop.put("topSpu", topSpu);
                    }
                });
            } catch (Exception e) {
                log.warn("[listShops] inject topSpu tenant={} 失败: {}", tid, e.getMessage());
            }
        }

        PageResult<java.util.Map<String, Object>> resp = new PageResult<>();
        resp.setList(slice);
        resp.setTotal(page.getTotal());
        return success(resp);
    }

    @GetMapping("/info")
    @Operation(summary = "获取店铺详情（通过 tenantId 或 shopId，至少传一个）；可选 userLng/userLat 计算距离")
    @Parameter(name = "tenantId", description = "租户ID（与 shopId 二选一）")
    @Parameter(name = "shopId", description = "店铺ID（与 tenantId 二选一）")
    @Parameter(name = "userLng", description = "用户当前经度（可选，传则返 distanceMeter 字段）")
    @Parameter(name = "userLat", description = "用户当前纬度（可选）")
    @PermitAll
    @TenantIgnore
    public CommonResult<AppShopPublicInfoRespVO> getShopInfo(
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestParam(value = "shopId", required = false) Long shopId,
            @RequestParam(value = "userLng", required = false) java.math.BigDecimal userLng,
            @RequestParam(value = "userLat", required = false) java.math.BigDecimal userLat) {
        if (tenantId == null && shopId == null) {
            return CommonResult.error(400, "tenantId 或 shopId 至少传一个");
        }
        ShopInfoDO shop = tenantId != null
                ? shopInfoMapper.selectByTenantId(tenantId)
                : shopInfoMapper.selectById(shopId);
        if (shop == null) {
            return success(null);
        }
        AppShopPublicInfoRespVO resp = new AppShopPublicInfoRespVO();
        resp.setId(shop.getId());
        resp.setTenantId(shop.getTenantId());
        resp.setShopName(shop.getShopName());
        resp.setCategoryId(shop.getCategoryId());
        resp.setCoverUrl(shop.getCoverUrl());
        resp.setDescription(shop.getDescription());
        resp.setNotice(shop.getNotice());
        resp.setFeatureTags(shop.getFeatureTags());
        resp.setLongitude(shop.getLongitude());
        resp.setLatitude(shop.getLatitude());
        resp.setAddress(shop.getAddress());
        resp.setBusinessHours(shop.getBusinessHours());
        resp.setStatus(shop.getStatus());
        resp.setSales30d(shop.getSales30d());
        resp.setAvgRating(shop.getAvgRating());

        // 通联在线支付可用性：tlEnabled + cusId + 商户私钥 + 通联公钥 都配齐才算"商户可线上收款"
        resp.setOnlinePayEnabled(
                Boolean.TRUE.equals(shop.getTlEnabled())
                && shop.getTlMchId() != null && !shop.getTlMchId().isEmpty()
                && shop.getTlRsaPrivateKey() != null && !shop.getTlRsaPrivateKey().isEmpty()
                && shop.getTlRsaPublicKey() != null && !shop.getTlRsaPublicKey().isEmpty());

        // V039 营业状态：用户进店时立刻看到「营业中 / 营业时间外 / 已休业」三态
        cn.iocoder.yudao.module.merchant.util.ShopOperatingUtils.OperatingStatus opSt =
                cn.iocoder.yudao.module.merchant.util.ShopOperatingUtils.computeStatus(shop);
        resp.setIsOpenNow(opSt == cn.iocoder.yudao.module.merchant.util.ShopOperatingUtils.OperatingStatus.OPEN);
        resp.setOperatingStatus(opSt.name());
        resp.setBusinessHoursJson(shop.getBusinessHoursJson());

        // 距离（用户和店铺都有合法经纬度才算）
        if (userLng != null && userLat != null
                && shop.getLongitude() != null && shop.getLatitude() != null
                && shop.getLongitude().signum() != 0 && shop.getLatitude().signum() != 0) {
            resp.setDistanceMeter(haversineMeter(
                    userLng.doubleValue(), userLat.doubleValue(),
                    shop.getLongitude().doubleValue(), shop.getLatitude().doubleValue()));
        }

        // 星级折扣 + 满减（同 tenant ctx 内查）
        Long promoTenantId = shop.getTenantId();
        if (promoTenantId != null) {
            PromoConfigDO promo = TenantUtils.execute(promoTenantId,
                    () -> promoConfigMapper.selectCurrent());
            if (promo != null) {
                resp.setStarDiscountRates(promo.getStarDiscountRates());
                if (promo.getFullCutThreshold() != null && promo.getFullCutThreshold() > 0
                        && promo.getFullCutAmount() != null && promo.getFullCutAmount() > 0) {
                    resp.setFullCutThreshold(promo.getFullCutThreshold());
                    resp.setFullCutAmount(promo.getFullCutAmount());
                }
            }
        }
        // visitorCount30d 抽到独立接口 /info/visitor，前端非阻塞拉，避免主路径多 1 次 SQL
        return success(resp);
    }

    /**
     * visitorCount30d 内存 cache（5 分钟）。
     *
     * <p>近 30 天访客数变化缓慢，且 SELECT COUNT(*) 在 member_shop_rel 数据多时较重，
     * 加 5 分钟 cache 大幅减压且对用户体验无感。Map<tenantId, [count, expireMs]>。</p>
     */
    private static final java.util.concurrent.ConcurrentHashMap<Long, long[]> VISITOR_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long VISITOR_CACHE_TTL_MS = 5 * 60 * 1000L;

    @GetMapping("/products")
    @Operation(summary = "C 端：拉某店上架商品列表（按 tenantId 跨租户）")
    @Parameter(name = "tenantId", description = "店铺所属租户 ID", required = true)
    @PermitAll
    @TenantIgnore
    public CommonResult<PageResult<cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO>>
            listShopProducts(@RequestParam("tenantId") Long tenantId,
                             @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                             @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuPageReqVO reqVO =
                new cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuPageReqVO();
        reqVO.setPageNo(pageNo);
        reqVO.setPageSize(pageSize);
        // 仅查上架商品（status=1），TenantUtils.execute 切到目标租户 ctx 让 mp 自动加 WHERE tenant_id=
        reqVO.setTabType(cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuPageReqVO.FOR_SALE);
        PageResult<cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO> page =
                TenantUtils.execute(tenantId, () -> productSpuService.getSpuPage(reqVO));
        return success(page);
    }

    @GetMapping("/info/visitor")
    @Operation(summary = "C 端：获取近 30 天访客数（独立接口，前端可异步拉避免阻塞 shop-home 首屏）")
    @Parameter(name = "tenantId", description = "店铺所属租户 ID", required = true)
    @PermitAll
    @TenantIgnore
    @RateLimiter(time = 60, count = 30, keyResolver = ClientIpRateLimiterKeyResolver.class,
                 message = "操作过于频繁，请稍后再试")
    public CommonResult<Map<String, Object>> getVisitorCount(@RequestParam("tenantId") Long tenantId) {
        Map<String, Object> resp = new HashMap<>();
        long now = System.currentTimeMillis();
        long[] cached = VISITOR_CACHE.get(tenantId);
        if (cached != null && cached[1] > now) {
            resp.put("visitorCount30d", (int) cached[0]);
            return success(resp);
        }
        // miss：重新算（隐私保护：仅返数字，不返用户列表）
        int visitorCount = TenantUtils.execute(tenantId, () -> {
            java.time.LocalDateTime since = java.time.LocalDateTime.now().minusDays(30);
            Long c = memberShopRelMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MemberShopRelDO>()
                            .ge(MemberShopRelDO::getLastVisitAt, since));
            return c == null ? 0 : c.intValue();
        });
        VISITOR_CACHE.put(tenantId, new long[]{visitorCount, now + VISITOR_CACHE_TTL_MS});
        resp.put("visitorCount30d", visitorCount);
        return success(resp);
    }

    /**
     * Haversine 距离公式：球面两点之间的大圆距离（米）。
     * 精度对城市级（&lt; 50 km）足够；地球按平均半径 6371 km 算。
     */
    private static int haversineMeter(double lng1, double lat1, double lng2, double lat2) {
        final double R = 6_371_000d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(R * c);
    }

    @GetMapping("/config")
    @Operation(summary = "获取店铺积分配置（pointPerYuan）")
    @Parameter(name = "tenantId", description = "租户ID", required = true)
    @PermitAll
    @TenantIgnore
    public CommonResult<Map<String, Object>> getShopConfig(
            @RequestParam("tenantId") Long tenantId) {
        ShopBrokerageConfigDO config = TenantUtils.execute(tenantId,
                () -> shopBrokerageConfigMapper.selectCurrent());
        Map<String, Object> result = new HashMap<>();
        result.put("pointPerYuan", config != null ? config.getPointPerYuan() : 0);
        return success(result);
    }

}
