package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopBrokerageConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopBrokerageConfigMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 商户小程序 - 店铺设置（#26）
 */
@Tag(name = "商户小程序 - 店铺设置")
@RestController
@RequestMapping("/merchant/mini/shop")
@Validated
public class AppMerchantShopController {

    @Resource
    private ShopInfoMapper shopInfoMapper;
    @Resource
    private ShopBrokerageConfigMapper shopBrokerageConfigMapper;

    @GetMapping("/info")
    @Operation(summary = "获取店铺信息")
    public CommonResult<ShopInfoDO> getShopInfo() {
        Long tenantId = TenantContextHolder.getTenantId();
        return success(shopInfoMapper.selectByTenantId(tenantId));
    }

    @PutMapping("/info")
    @Operation(summary = "更新店铺信息（名称/封面/简介/公告/营业时间/地址）")
    public CommonResult<Boolean> updateShopInfo(@Valid @RequestBody ShopInfoDO updateDO) {
        Long tenantId = TenantContextHolder.getTenantId();
        ShopInfoDO existing = shopInfoMapper.selectByTenantId(tenantId);
        if (existing == null) {
            throw exception0(1_020_005_000, "店铺信息不存在");
        }
        // 只允许更新可编辑字段
        ShopInfoDO update = new ShopInfoDO();
        update.setId(existing.getId());
        update.setShopName(updateDO.getShopName());
        update.setCoverUrl(updateDO.getCoverUrl());
        update.setDescription(updateDO.getDescription());
        update.setNotice(updateDO.getNotice());
        update.setBusinessHours(updateDO.getBusinessHours());
        update.setMobile(updateDO.getMobile());
        update.setLongitude(updateDO.getLongitude());
        update.setLatitude(updateDO.getLatitude());
        update.setAddress(updateDO.getAddress());
        shopInfoMapper.updateById(update);
        return success(true);
    }

    @GetMapping("/brokerage-config")
    @Operation(summary = "获取返佣与积分配置")
    public CommonResult<ShopBrokerageConfigDO> getBrokerageConfig() {
        ShopBrokerageConfigDO config = shopBrokerageConfigMapper.selectCurrent();
        if (config == null) {
            // 返回默认空配置（前端第一次进入时展示默认值）
            config = new ShopBrokerageConfigDO();
            config.setBrokerageEnabled(false);
            config.setFirstBrokeragePercent(java.math.BigDecimal.ZERO);
            config.setSecondBrokeragePercent(java.math.BigDecimal.ZERO);
            config.setFreezeDays(7);
            config.setPushReturnEnabled(false);
            config.setPushN(5);
            config.setReturnAmount(0);
            config.setPointPerYuan(0);
            config.setMinWithdrawAmount(10000);
        }
        return success(config);
    }

    @PutMapping("/brokerage-config")
    @Operation(summary = "保存返佣与积分配置（upsert）")
    public CommonResult<Boolean> saveBrokerageConfig(@RequestBody ShopBrokerageConfigDO reqDO) {
        ShopBrokerageConfigDO existing = shopBrokerageConfigMapper.selectCurrent();
        if (existing == null) {
            shopBrokerageConfigMapper.insert(reqDO);
        } else {
            reqDO.setId(existing.getId());
            shopBrokerageConfigMapper.updateById(reqDO);
        }
        return success(true);
    }

    @PutMapping("/status")
    @Operation(summary = "更新营业状态（1正常 2暂停营业）")
    public CommonResult<Boolean> updateShopStatus(@RequestParam("status") Integer status) {
        Long tenantId = TenantContextHolder.getTenantId();
        ShopInfoDO existing = shopInfoMapper.selectByTenantId(tenantId);
        if (existing == null) {
            throw exception0(1_020_005_000, "店铺信息不存在");
        }
        ShopInfoDO update = new ShopInfoDO();
        update.setId(existing.getId());
        update.setStatus(status);
        shopInfoMapper.updateById(update);
        return success(true);
    }

}
