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
    @Operation(summary = "获取店铺详情（通过 tenantId 或 shopId，至少传一个）")
    @Parameter(name = "tenantId", description = "租户ID（与 shopId 二选一）")
    @Parameter(name = "shopId", description = "店铺ID（与 tenantId 二选一）")
    @PermitAll
    @TenantIgnore
    public CommonResult<ShopInfoDO> getShopInfo(
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestParam(value = "shopId", required = false) Long shopId) {
        if (tenantId == null && shopId == null) {
            return CommonResult.error(400, "tenantId 或 shopId 至少传一个");
        }
        ShopInfoDO shop = tenantId != null
                ? shopInfoMapper.selectByTenantId(tenantId)
                : shopInfoMapper.selectById(shopId);
        return success(shop);
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
