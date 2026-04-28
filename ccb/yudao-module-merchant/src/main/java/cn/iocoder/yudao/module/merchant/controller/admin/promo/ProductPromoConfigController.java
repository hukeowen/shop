package cn.iocoder.yudao.module.merchant.controller.admin.promo;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.promo.ProductPromoConfigRespVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.promo.ProductPromoConfigSaveReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;
import cn.iocoder.yudao.module.merchant.service.promo.ProductPromoConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商品营销配置（消费积分/推 N 反 1/积分池）")
@RestController
@RequestMapping("/merchant/promo/product-config")
@Validated
public class ProductPromoConfigController {

    @Resource
    private ProductPromoConfigService productPromoConfigService;

    @GetMapping
    @Operation(summary = "获取某商品的营销配置（不存在则返默认值，全关）")
    @Parameter(name = "spuId", description = "商品 SPU ID", required = true)
    @PreAuthorize("@ss.hasPermission('merchant:product-promo-config:query')")
    public CommonResult<ProductPromoConfigRespVO> getConfig(@RequestParam("spuId") @NotNull Long spuId) {
        ProductPromoConfigDO config = productPromoConfigService.getBySpuId(spuId);
        ProductPromoConfigRespVO resp = new ProductPromoConfigRespVO();
        BeanUtils.copyProperties(config, resp);
        return success(resp);
    }

    @PutMapping
    @Operation(summary = "保存某商品的营销配置（upsert）")
    @PreAuthorize("@ss.hasPermission('merchant:product-promo-config:update')")
    public CommonResult<Boolean> saveConfig(@Valid @RequestBody ProductPromoConfigSaveReqVO reqVO) {
        productPromoConfigService.save(reqVO);
        return success(true);
    }

}
