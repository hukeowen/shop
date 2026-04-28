package cn.iocoder.yudao.module.merchant.controller.admin.promo;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.promo.PromoConfigRespVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.promo.PromoConfigSaveReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.service.promo.PromoConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商户营销配置（极差/升星/池子）")
@RestController
@RequestMapping("/merchant/promo/config")
@Validated
public class PromoConfigController {

    @Resource
    private PromoConfigService promoConfigService;

    @GetMapping
    @Operation(summary = "获取当前租户的营销配置（不存在则返默认值）")
    @PreAuthorize("@ss.hasPermission('merchant:promo-config:query')")
    public CommonResult<PromoConfigRespVO> getConfig() {
        PromoConfigDO config = promoConfigService.getConfig();
        PromoConfigRespVO resp = new PromoConfigRespVO();
        BeanUtils.copyProperties(config, resp);
        return success(resp);
    }

    @PutMapping
    @Operation(summary = "保存当前租户的营销配置（upsert）")
    @PreAuthorize("@ss.hasPermission('merchant:promo-config:update')")
    public CommonResult<Boolean> saveConfig(@Valid @RequestBody PromoConfigSaveReqVO reqVO) {
        promoConfigService.saveConfig(reqVO);
        return success(true);
    }

}
