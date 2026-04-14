package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
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
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 用户小程序 - 店铺相关（公开接口，无需登录或仅需 member token）
 * #29 tenant解析 / #30 首页附近商户 / #31 分类页 / #33 店铺详情
 */
@Tag(name = "用户小程序 - 店铺与附近商户")
@RestController
@RequestMapping("/user/shop")
@Validated
public class AppUserShopController {

    @Resource
    private ShopInfoMapper shopInfoMapper;

    // ==================== #29 tenant 解析 ====================

    @GetMapping("/resolve-tenant")
    @Operation(summary = "通过租户ID获取店铺基本信息（扫码入店后用于展示）")
    @Parameter(name = "tenantId", description = "租户ID", required = true)
    @PermitAll
    @TenantIgnore
    public CommonResult<ShopInfoDO> resolveShop(@RequestParam("tenantId") Long tenantId) {
        return success(shopInfoMapper.selectByTenantId(tenantId));
    }

    // ==================== #30 首页附近商户 / #31 附近页+分类 ====================

    @GetMapping("/nearby")
    @Operation(summary = "查询附近商户列表（基于经纬度距离排序）")
    @PermitAll
    @TenantIgnore
    public CommonResult<List<ShopInfoDO>> getNearbyShops(
            @RequestParam("latitude") BigDecimal latitude,
            @RequestParam("longitude") BigDecimal longitude,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "limit", defaultValue = "20") @Max(100) @Min(1) int limit) {
        List<ShopInfoDO> shops = shopInfoMapper.selectNearby(
                latitude.subtract(new BigDecimal("0.1")), latitude.add(new BigDecimal("0.1")),
                longitude.subtract(new BigDecimal("0.1")), longitude.add(new BigDecimal("0.1")),
                latitude, longitude, Math.min(limit, 100));
        return success(shops);
    }

    @GetMapping("/by-category")
    @Operation(summary = "按分类查询商户列表（按销量排序）")
    @PermitAll
    @TenantIgnore
    public CommonResult<List<ShopInfoDO>> getShopsByCategory(
            @RequestParam("categoryId") Long categoryId,
            @RequestParam(value = "limit", defaultValue = "20") @Max(100) @Min(1) int limit) {
        List<ShopInfoDO> shops = shopInfoMapper.selectByCategory(categoryId, Math.min(limit, 100));
        return success(shops);
    }

    // ==================== #33 店铺详情 ====================

    @GetMapping("/detail")
    @Operation(summary = "获取店铺详情（用户端）")
    @Parameter(name = "tenantId", description = "租户ID", required = true)
    @PermitAll
    @TenantIgnore
    public CommonResult<ShopInfoDO> getShopDetail(@RequestParam("tenantId") Long tenantId) {
        return success(shopInfoMapper.selectByTenantId(tenantId));
    }

}
