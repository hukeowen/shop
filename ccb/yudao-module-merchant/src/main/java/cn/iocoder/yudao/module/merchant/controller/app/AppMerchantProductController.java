package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppSimpleSpuCreateReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSkuSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuUpdateStatusReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 商户小程序 - 商品管理
 */
@Tag(name = "商户小程序 - 商品管理")
@RestController
@RequestMapping("/merchant/mini/product")
@Validated
public class AppMerchantProductController {

    /** 默认库存（无限量概念） */
    private static final int DEFAULT_STOCK = 9999;
    /** 默认配送方式：自提 */
    private static final List<Integer> DEFAULT_DELIVERY_TYPES = Collections.singletonList(2);
    /** 默认分类（小吃零食，需提前在 product_category 中创建） */
    private static final long DEFAULT_CATEGORY_ID = 1L;

    @Resource
    private ProductSpuService productSpuService;

    // ==================== #16 极简商品发布 ====================

    @PostMapping("/simple-create")
    @Operation(summary = "极简商品发布（拍照+名称+价格）")
    public CommonResult<Long> simpleCreate(@Valid @RequestBody AppSimpleSpuCreateReqVO reqVO) {
        ProductSpuSaveReqVO spuReqVO = buildSpuSaveReqVO(reqVO);
        Long spuId = productSpuService.createSpu(spuReqVO);
        return success(spuId);
    }

    // ==================== #17 商品列表管理 ====================

    @GetMapping("/page")
    @Operation(summary = "分页查询商品列表")
    public CommonResult<PageResult<ProductSpuDO>> getSpuPage(@Valid ProductSpuPageReqVO pageReqVO) {
        return success(productSpuService.getSpuPage(pageReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "编辑商品")
    public CommonResult<Boolean> updateSpu(@Valid @RequestBody ProductSpuSaveReqVO updateReqVO) {
        productSpuService.updateSpu(updateReqVO);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "上下架商品")
    public CommonResult<Boolean> updateSpuStatus(@Valid @RequestBody ProductSpuUpdateStatusReqVO reqVO) {
        productSpuService.updateSpuStatus(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除商品")
    @Parameter(name = "id", description = "商品编号", required = true)
    public CommonResult<Boolean> deleteSpu(@RequestParam("id") Long id) {
        productSpuService.deleteSpu(id);
        return success(true);
    }

    /**
     * 将极简VO转换为标准SPU创建VO，填充默认值
     */
    private ProductSpuSaveReqVO buildSpuSaveReqVO(AppSimpleSpuCreateReqVO reqVO) {
        ProductSpuSaveReqVO spu = new ProductSpuSaveReqVO();
        spu.setName(reqVO.getName());
        spu.setKeyword(reqVO.getName()); // 关键字默认=名称
        spu.setIntroduction(reqVO.getIntroduction() != null ? reqVO.getIntroduction() : reqVO.getName());
        spu.setDescription(reqVO.getName()); // 详情默认=名称
        spu.setCategoryId(reqVO.getCategoryId() != null ? reqVO.getCategoryId() : DEFAULT_CATEGORY_ID);
        spu.setBrandId(null); // 无品牌，跳过品牌校验
        spu.setPicUrl(reqVO.getPicUrl());
        spu.setSliderPicUrls(Collections.singletonList(reqVO.getPicUrl()));
        spu.setSort(0);
        spu.setSpecType(false); // 单规格
        spu.setDeliveryTypes(reqVO.getDeliveryTypes() != null ? reqVO.getDeliveryTypes() : DEFAULT_DELIVERY_TYPES);
        spu.setDeliveryTemplateId(null); // 自提无需物流模板
        spu.setGiveIntegral(reqVO.getGiveIntegral() != null ? reqVO.getGiveIntegral() : 0);
        spu.setSubCommissionType(false); // 不独立设置佣金，使用商户全局配置
        spu.setVirtualSalesCount(0);
        spu.setSalesCount(0);
        spu.setBrowseCount(0);

        // 构建默认单规格 SKU
        ProductSkuSaveReqVO sku = new ProductSkuSaveReqVO();
        sku.setName(reqVO.getName());
        sku.setPrice(reqVO.getPrice());
        sku.setMarketPrice(reqVO.getPrice());
        sku.setCostPrice(0);
        sku.setPicUrl(reqVO.getPicUrl());
        sku.setStock(reqVO.getStock() != null ? reqVO.getStock() : DEFAULT_STOCK);
        spu.setSkus(Collections.singletonList(sku));

        return spu;
    }

}
