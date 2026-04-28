package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO;

/**
 * 星级积分池服务（v6 文档第八节）。
 *
 * 本轮先实现"累积"——订单成交时把实付 × 入池比例 计入池子。
 * "结算 / 抽奖 / 均分"放到 W4 cron 任务里实现。
 */
public interface PromoPoolService {

    /**
     * 累积一笔订单的入池金额（满足条件才执行）。
     *
     * 条件：
     *   - 商户级 PromoConfig.poolEnabled = true
     *   - 商品级 ProductPromoConfig.poolEnabled = true
     *
     * 入池金额 = 实付价 × shopConfig.poolRatio / 100，按分向下取整。
     */
    void depositIfEnabled(ProductPromoConfigDO productConfig, long paidAmount, Long orderId);

}
