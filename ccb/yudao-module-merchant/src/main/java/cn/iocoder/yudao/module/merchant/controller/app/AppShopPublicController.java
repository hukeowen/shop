package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopBrokerageConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopBrokerageConfigMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
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
    public CommonResult<Map<String, Object>> getShopInfo(
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
        // 构造 map 返回，多带一个 distanceMeter（用户传了经纬度且店铺有经纬度时才有值）
        Map<String, Object> resp = cn.hutool.core.bean.BeanUtil.beanToMap(shop, false, true);
        if (userLng != null && userLat != null
                && shop.getLongitude() != null && shop.getLatitude() != null
                && shop.getLongitude().signum() != 0 && shop.getLatitude().signum() != 0) {
            int meter = haversineMeter(
                    userLng.doubleValue(), userLat.doubleValue(),
                    shop.getLongitude().doubleValue(), shop.getLatitude().doubleValue());
            resp.put("distanceMeter", meter);
        }
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
