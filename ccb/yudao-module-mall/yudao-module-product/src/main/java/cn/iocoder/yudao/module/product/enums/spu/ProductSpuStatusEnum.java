package cn.iocoder.yudao.module.product.enums.spu;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 商品 SPU 状态
 *
 * @author 芋道源码
 */
@Getter
@AllArgsConstructor
public enum ProductSpuStatusEnum implements ArrayValuable<Integer> {

    RECYCLE(-1, "回收站"),
    DISABLE(0, "下架"),
    ENABLE(1, "上架"),
    /** V044 合规整改：新增审核态。商户创建/修改商品后强制进入此态，需平台审核通过才能上架 */
    PENDING_REVIEW(2, "审核中"),
    /** V044 合规整改：审核拒绝。商户可整改后重新提交 */
    REJECTED(3, "审核拒绝");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(ProductSpuStatusEnum::getStatus).toArray(Integer[]::new);

    /**
     * 状态
     */
    private final Integer status;
    /**
     * 状态名
     */
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    /**
     * 判断是否处于【上架】状态
     *
     * @param status 状态
     * @return 是否处于【上架】状态
     */
    public static boolean isEnable(Integer status) {
        return ENABLE.getStatus().equals(status);
    }

}
