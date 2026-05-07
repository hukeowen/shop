package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppSimpleSpuCreateReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSkuSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuRespVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuUpdateStatusReqVO;
import cn.iocoder.yudao.module.product.convert.spu.ProductSpuConvert;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
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

    @Resource
    private ProductSkuService productSkuService;

    @Resource
    private cn.iocoder.yudao.module.product.service.brand.ProductBrandService productBrandService;

    @Resource
    private cn.iocoder.yudao.module.product.service.category.ProductCategoryService productCategoryService;

    // ==================== #16 极简商品发布 ====================

    @PostMapping("/simple-create")
    @Operation(summary = "极简商品发布（拍照+名称+价格，默认立即上架）")
    public CommonResult<Long> simpleCreate(@Valid @RequestBody AppSimpleSpuCreateReqVO reqVO) {
        ProductSpuSaveReqVO spuReqVO = buildSpuSaveReqVO(reqVO);
        Long spuId = productSpuService.createSpu(spuReqVO);
        // 简易发布立即上架（mall ProductSpuStatusEnum.ENABLE=1）：
        // yudao mall createSpu 默认 status=0 (DISABLE)，C 端 /app-api/product/spu/page
        // 和 shop-home 公开接口都按 status=1 过滤，不上架则商品在 C 端不可见。
        ProductSpuUpdateStatusReqVO statusReq = new ProductSpuUpdateStatusReqVO();
        statusReq.setId(spuId);
        statusReq.setStatus(1);
        productSpuService.updateSpuStatus(statusReq);
        return success(spuId);
    }

    // ==================== #17 商品列表管理 ====================

    @GetMapping("/page")
    @Operation(summary = "分页查询商品列表")
    public CommonResult<PageResult<ProductSpuDO>> getSpuPage(@Valid ProductSpuPageReqVO pageReqVO) {
        return success(productSpuService.getSpuPage(pageReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "查询商品详情（含 SKU）")
    @Parameter(name = "id", description = "商品 ID", required = true)
    public CommonResult<ProductSpuRespVO> getSpu(@RequestParam("id") Long id) {
        ProductSpuDO spu = productSpuService.getSpu(id);
        if (spu == null) {
            return success(null);
        }
        List<ProductSkuDO> skus = productSkuService.getSkuListBySpuId(spu.getId());
        return success(ProductSpuConvert.INSTANCE.convert(spu, skus));
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
        // 分类兜底链（按优先级）：
        //   1) categoryName 非空 → findOrCreate(categoryName) — 推荐路径，AI 识别出中文名
        //   2) categoryId 非空且 DB 真存在 → 直接用（兼容老前端传 ID 的场景）
        //   3) brand 字符串非空 → findOrCreate(brand) — 没正经分类时用类目兜底
        //   4) 全空 → findOrCreate「通用」
        Long categoryId = null;
        String catName = reqVO.getCategoryName();
        if (catName != null && !catName.trim().isEmpty()) {
            categoryId = productCategoryService.findOrCreateCategory(catName.trim());
        }
        if (categoryId == null && reqVO.getCategoryId() != null
                && productCategoryService.getCategory(reqVO.getCategoryId()) != null) {
            categoryId = reqVO.getCategoryId();
        }
        if (categoryId == null) {
            String fallback = (reqVO.getBrand() != null && !reqVO.getBrand().trim().isEmpty())
                    ? reqVO.getBrand().trim() : "通用";
            categoryId = productCategoryService.findOrCreateCategory(fallback);
        }
        if (categoryId == null) categoryId = productCategoryService.findOrCreateCategory("通用");
        spu.setCategoryId(categoryId);
        // brandId 必填（yudao ProductSpuSaveReqVO @NotNull + service validateProductBrand）：
        // - 前端 AI 识别能给出 brand（可口可乐/旺仔）→ findOrCreate 那个品牌
        // - 识别不到（地摊/水果摊）→ 前端传通用类目（小吃/水果/零食/饮品）作为 brand 字符串
        // - 全空 → 兜底「通用」一个保底品牌（findOrCreate 复用第一次创建的）
        String brandName = reqVO.getBrand() != null && !reqVO.getBrand().trim().isEmpty()
                ? reqVO.getBrand().trim() : "通用";
        spu.setBrandId(productBrandService.findOrCreateBrand(brandName));
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
