package cn.iocoder.yudao.module.product.service.brand;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.brand.vo.*;
import cn.iocoder.yudao.module.product.dal.dataobject.brand.ProductBrandDO;

import javax.validation.Valid;
import java.util.Collection;
import java.util.List;

/**
 * 商品品牌 Service 接口
 *
 * @author 芋道源码
 */
public interface ProductBrandService {

    /**
     * 创建品牌
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createBrand(@Valid ProductBrandCreateReqVO createReqVO);

    /**
     * 更新品牌
     *
     * @param updateReqVO 更新信息
     */
    void updateBrand(@Valid ProductBrandUpdateReqVO updateReqVO);

    /**
     * 删除品牌
     *
     * @param id 编号
     */
    void deleteBrand(Long id);

    /**
     * 获得品牌
     *
     * @param id 编号
     * @return 品牌
     */
    ProductBrandDO getBrand(Long id);

    /**
     * 获得品牌列表
     *
     * @param ids 编号
     * @return 品牌列表
     */
    List<ProductBrandDO> getBrandList(Collection<Long> ids);

    /**
     * 获得品牌列表
     *
     * @param listReqVO 请求参数
     * @return 品牌列表
     */
    List<ProductBrandDO> getBrandList(ProductBrandListReqVO listReqVO);

    /**
     * 验证选择的商品分类是否合法
     *
     * @param id 分类编号
     */
    void validateProductBrand(Long id);

    /**
     * 获得品牌分页
     *
     * @param pageReqVO 分页查询
     * @return 品牌分页
     */
    PageResult<ProductBrandDO> getBrandPage(ProductBrandPageReqVO pageReqVO);

    /**
     * 获取指定状态的品牌列表
     *
     * @param status 状态
     * @return  返回品牌列表
     */
    List<ProductBrandDO> getBrandListByStatus(Integer status);

    /**
     * 按品牌名查找；不存在时自动创建一个 enabled 状态的品牌并返回 ID。
     * <p>用于 AI 识别上架商品时兜底品牌字段（避免「商品品牌不能为空」）。
     * 商户端的"小吃 / 水果 / 零食 / 饮品 / 通用"等通用类目作为品牌名落库即可。</p>
     *
     * @param name 品牌名（≤ 30 字，trim 过）；为空时返回 null
     * @return 品牌 ID；name 为空时返 null
     */
    Long findOrCreateBrand(String name);
}
