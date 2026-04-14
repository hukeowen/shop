package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopBrokerageConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.TenantSubscriptionDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopBrokerageConfigMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.TenantSubscriptionMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 商户小程序 - 返佣/积分/提现配置（#21 #22）
 */
@Tag(name = "商户小程序 - 返佣与积分配置")
@RestController
@RequestMapping("/merchant/mini/config")
@Validated
@Slf4j
public class AppMerchantConfigController {

    @Resource
    private ShopBrokerageConfigMapper shopBrokerageConfigMapper;
    @Resource
    private TenantSubscriptionMapper tenantSubscriptionMapper;

    @GetMapping("/brokerage")
    @Operation(summary = "获取返佣与积分配置")
    public CommonResult<ShopBrokerageConfigDO> getBrokerageConfig() {
        ShopBrokerageConfigDO config = shopBrokerageConfigMapper.selectCurrent();
        return success(config);
    }

    @PutMapping("/brokerage")
    @Operation(summary = "保存返佣与积分配置")
    public CommonResult<Boolean> saveBrokerageConfig(@Valid @RequestBody ShopBrokerageConfigDO configDO) {
        requirePaidSubscription();
        ShopBrokerageConfigDO existing = shopBrokerageConfigMapper.selectCurrent();
        if (existing == null) {
            shopBrokerageConfigMapper.insert(configDO);
        } else {
            configDO.setId(existing.getId());
            shopBrokerageConfigMapper.updateById(configDO);
        }
        return success(true);
    }

    private void requirePaidSubscription() {
        Long tenantId = TenantContextHolder.getTenantId();
        TenantSubscriptionDO sub = tenantSubscriptionMapper.selectByTenantId(tenantId);
        if (sub == null || sub.getStatus() == null || sub.getStatus() < 2) {
            throw exception0(1_020_008_000, "需要付费订阅后才能使用推广功能，当前为试用状态");
        }
    }

}
