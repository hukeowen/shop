package cn.iocoder.yudao.module.merchant.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.saas.MerchantSubscriptionOrderDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.saas.SaasPackageConfigDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.saas.MerchantSubscriptionOrderMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.saas.SaasPackageConfigMapper;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 平台运营总览（独立模块，不改 yudao 自带商城页面）。
 *
 * <p>超管跨所有商户租户聚合查看/管理，关联 shop_info 显示店铺名，
 * 免去顶部一个个切换租户。所有接口 @TenantIgnore 跨租户。</p>
 */
@Tag(name = "管理后台 - 平台运营总览")
@RestController
@RequestMapping("/merchant/platform")
@Validated
public class PlatformOverviewController {

    @Resource
    private ProductSpuMapper productSpuMapper;
    @Resource
    private ShopInfoMapper shopInfoMapper;
    @Resource
    private MerchantMapper merchantMapper;
    @Resource
    private SaasPackageConfigMapper saasPackageConfigMapper;
    @Resource
    private MerchantSubscriptionOrderMapper merchantSubscriptionOrderMapper;

    @GetMapping("/shops")
    @Operation(summary = "所有店铺（租户ID+店铺名+状态）—— 总览筛选下拉用")
    @PreAuthorize("@ss.hasPermission('merchant:platform:query')")
    @TenantIgnore
    public CommonResult<List<Map<String, Object>>> shops() {
        List<Map<String, Object>> resp = new ArrayList<>();
        for (ShopInfoDO s : shopInfoMapper.selectList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tenantId", s.getTenantId());
            m.put("shopName", s.getShopName());
            m.put("status", s.getStatus());
            resp.add(m);
        }
        return success(resp);
    }

    @GetMapping("/product/page")
    @Operation(summary = "平台跨租户商品总览（全部店铺，可按店铺/名称/状态筛选）")
    @PreAuthorize("@ss.hasPermission('merchant:platform:query')")
    @TenantIgnore
    public CommonResult<PageResult<Map<String, Object>>> productPage(
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "tenantId", required = false) Long tenantId) {
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(pageNo);
        pageParam.setPageSize(pageSize);
        LambdaQueryWrapperX<ProductSpuDO> q = new LambdaQueryWrapperX<ProductSpuDO>()
                .likeIfPresent(ProductSpuDO::getName, name)
                .eqIfPresent(ProductSpuDO::getStatus, status)
                .eqIfPresent(ProductSpuDO::getTenantId, tenantId)
                .orderByDesc(ProductSpuDO::getId);
        PageResult<ProductSpuDO> page = productSpuMapper.selectPage(pageParam, q);
        Map<Long, String> shopNames = loadShopNames();
        List<Map<String, Object>> list = new ArrayList<>();
        for (ProductSpuDO spu : page.getList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", spu.getId());
            m.put("tenantId", spu.getTenantId());
            m.put("shopName", shopNames.getOrDefault(spu.getTenantId(), "租户" + spu.getTenantId()));
            m.put("name", spu.getName());
            m.put("picUrl", spu.getPicUrl());
            m.put("price", spu.getPrice());
            m.put("status", spu.getStatus());
            m.put("salesCount", spu.getSalesCount());
            m.put("stock", spu.getStock());
            m.put("createTime", spu.getCreateTime());
            list.add(m);
        }
        return success(new PageResult<>(list, page.getTotal()));
    }

    @PutMapping("/product/update-status")
    @Operation(summary = "平台上/下架某店铺商品（0下架 1上架 4回收站）")
    @PreAuthorize("@ss.hasPermission('merchant:platform:query')")
    @TenantIgnore
    public CommonResult<Boolean> updateProductStatus(@RequestParam("id") Long id,
                                                     @RequestParam("status") Integer status) {
        ProductSpuDO update = new ProductSpuDO();
        update.setId(id);
        update.setStatus(status);
        productSpuMapper.updateById(update);
        return success(true);
    }

    @GetMapping("/subscription/page")
    @Operation(summary = "店铺套餐总览：每店当前套餐/到期时间/累计付费金额")
    @PreAuthorize("@ss.hasPermission('merchant:platform:query')")
    @TenantIgnore
    public CommonResult<PageResult<Map<String, Object>>> subscriptionPage(
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestParam(value = "level", required = false) String level) {
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(pageNo);
        pageParam.setPageSize(pageSize);
        LambdaQueryWrapperX<MerchantDO> q = new LambdaQueryWrapperX<MerchantDO>()
                .eqIfPresent(MerchantDO::getTenantId, tenantId)
                .eqIfPresent(MerchantDO::getServicePackageLevel, level)
                .orderByDesc(MerchantDO::getId);
        PageResult<MerchantDO> page = merchantMapper.selectPage(pageParam, q);
        Map<Long, String> shopNames = loadShopNames();
        // 套餐档位 → 套餐名（试用版兜底）
        Map<String, String> levelName = new HashMap<>();
        levelName.put("TRIAL", "试用版");
        levelName.put("PLATFORM", "平台");
        for (SaasPackageConfigDO c : saasPackageConfigMapper.selectList()) {
            levelName.put(c.getLevel(), c.getName());
        }
        // 商户 → 累计已付金额（PAID）
        Map<Long, Integer> paidMap = new HashMap<>();
        for (MerchantSubscriptionOrderDO o : merchantSubscriptionOrderMapper.selectList(
                MerchantSubscriptionOrderDO::getPayStatus, MerchantSubscriptionOrderDO.PAY_STATUS_PAID)) {
            paidMap.merge(o.getMerchantId(), o.getPayAmountFen() == null ? 0 : o.getPayAmountFen(), Integer::sum);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (MerchantDO m : page.getList()) {
            String lv = m.getServicePackageLevel();
            Map<String, Object> mp = new LinkedHashMap<>();
            mp.put("merchantId", m.getId());
            mp.put("tenantId", m.getTenantId());
            mp.put("shopName", shopNames.getOrDefault(m.getTenantId(), m.getName()));
            mp.put("level", lv);
            mp.put("levelName", lv == null ? "试用版" : levelName.getOrDefault(lv, lv));
            mp.put("serviceExpireAt", m.getServiceExpireAt());
            mp.put("paid", lv != null && !"TRIAL".equals(lv) && !"PLATFORM".equals(lv));
            mp.put("totalPaidFen", paidMap.getOrDefault(m.getId(), 0));
            list.add(mp);
        }
        return success(new PageResult<>(list, page.getTotal()));
    }

    private Map<Long, String> loadShopNames() {
        Map<Long, String> map = new HashMap<>();
        for (ShopInfoDO s : shopInfoMapper.selectList()) {
            map.put(s.getTenantId(), s.getShopName());
        }
        return map;
    }

}
