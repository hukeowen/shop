package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
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
public class AppShopPublicController {

    @Resource
    private ShopInfoMapper shopInfoMapper;
    @Resource
    private ShopBrokerageConfigMapper shopBrokerageConfigMapper;
    @Resource
    private PromoConfigMapper promoConfigMapper;
    @Resource
    private MemberShopRelMapper memberShopRelMapper;

    @GetMapping("/list")
    @Operation(summary = "分页查询店铺列表（仅返回正常营业的店铺）")
    @PermitAll
    @TenantIgnore
    public CommonResult<PageResult<ShopInfoDO>> listShops(
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(pageNo);
        pageParam.setPageSize(pageSize);
        PageResult<ShopInfoDO> page = shopInfoMapper.selectPage(
                pageParam,
                new LambdaQueryWrapper<ShopInfoDO>().eq(ShopInfoDO::getStatus, 1));
        return success(page);
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

    @GetMapping("/info/visitor")
    @Operation(summary = "C 端：获取近 30 天访客数（独立接口，前端可异步拉避免阻塞 shop-home 首屏）")
    @Parameter(name = "tenantId", description = "店铺所属租户 ID", required = true)
    @PermitAll
    @TenantIgnore
    public CommonResult<Map<String, Object>> getVisitorCount(@RequestParam("tenantId") Long tenantId) {
        // 隐私保护：仅返数字，不返用户列表
        int visitorCount = TenantUtils.execute(tenantId, () -> {
            java.time.LocalDateTime since = java.time.LocalDateTime.now().minusDays(30);
            Long c = memberShopRelMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MemberShopRelDO>()
                            .ge(MemberShopRelDO::getLastVisitAt, since));
            return c == null ? 0 : c.intValue();
        });
        Map<String, Object> resp = new HashMap<>();
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
