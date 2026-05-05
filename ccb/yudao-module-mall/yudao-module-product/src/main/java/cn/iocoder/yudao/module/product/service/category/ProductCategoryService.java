package cn.iocoder.yudao.module.product.service.category;

import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductCategoryListReqVO;
import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductCategorySaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;

import javax.validation.Valid;
import java.util.Collection;
import java.util.List;

/**
 * 商品分类 Service 接口
 *
 * @author 芋道源码
 */
public interface ProductCategoryService {

    /**
     * 创建商品分类
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createCategory(@Valid ProductCategorySaveReqVO createReqVO);

    /**
     * 更新商品分类
     *
     * @param updateReqVO 更新信息
     */
    void updateCategory(@Valid ProductCategorySaveReqVO updateReqVO);

    /**
     * 删除商品分类
     *
     * @param id 编号
     */
    void deleteCategory(Long id);

    /**
     * 获得商品分类
     *
     * @param id 编号
     * @return 商品分类
     */
    ProductCategoryDO getCategory(Long id);

    /**
     * 校验商品分类
     *
     * @param id 分类编号
     */
    void validateCategory(Long id);

    /**
     * 获得商品分类的层级
     *
     * @param id 编号
     * @return 商品分类的层级
     */
    Integer getCategoryLevel(Long id);

    /**
     * 获得商品分类列表
     *
     * @param listReqVO 查询条件
     * @return 商品分类列表
     */
    List<ProductCategoryDO> getCategoryList(ProductCategoryListReqVO listReqVO);

    /**
     * 获得开启状态的商品分类列表
     *
     * @return 商品分类列表
     */
    List<ProductCategoryDO> getEnableCategoryList();

    /**
     * 获得开启状态的商品分类列表，指定编号
     *
     * @param ids 商品分类编号数组
     * @return 商品分类列表
     */
    List<ProductCategoryDO> getEnableCategoryList(List<Long> ids);

    /**
     * 校验商品分类是否有效。如下情况，视为无效：
     * 1. 商品分类编号不存在
     * 2. 商品分类被禁用
     * 3. 商品分类层级校验，必须使用第二级的商品分类及以下
     *
     * @param ids 商品分类编号数组
     */
    void validateCategoryList(Collection<Long> ids);

    /**
     * 按分类名查找；不存在时自动创建一个 enabled 顶级分类并返回 ID。
     * <p>用于 AI 上架商品时兜底分类（避免「商品分类不存在」），跟 brand findOrCreate
     * 同思路：地摊/水果摊/小吃这种业态没有正经分类树，让前端传通用类目名后端自动建。</p>
     *
     * @param name 分类名（≤ 30 字，trim）；为空返 null
     * @return 分类 ID；name 为空返 null
     */
    Long findOrCreateCategory(String name);

}
