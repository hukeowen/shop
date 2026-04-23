package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantPackageOrderDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商户套餐订单 Mapper（Phase 0.3.3）。平台级表，不做租户过滤。
 */
@Mapper
public interface MerchantPackageOrderMapper extends BaseMapperX<MerchantPackageOrderDO> {

    /**
     * 按 pay_order_id 反查业务订单。支付回调 controller 用它做幂等落位。
     *
     * @param payOrderId {@link cn.iocoder.yudao.module.pay.dal.dataobject.order.PayOrderDO} 主键
     * @return 匹配订单，找不到返回 {@code null}
     */
    default MerchantPackageOrderDO selectByPayOrderId(Long payOrderId) {
        if (payOrderId == null) {
            return null;
        }
        return selectOne(MerchantPackageOrderDO::getPayOrderId, payOrderId);
    }

    /**
     * 按商户倒序分页。
     */
    default PageResult<MerchantPackageOrderDO> selectPageByMerchantIdOrderByCreateTime(
            Long merchantId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<MerchantPackageOrderDO>()
                .eq(MerchantPackageOrderDO::getMerchantId, merchantId)
                .orderByDesc(MerchantPackageOrderDO::getCreateTime)
                .orderByDesc(MerchantPackageOrderDO::getId));
    }

}
