package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoPoolRoundDO;

/**
 * 星级积分池结算（v6 文档第 8 节）。
 *
 * 一次结算 = 把当前池子余额发出去 + 池清零 + 写历史批次。
 *
 * 模式（mode）：
 *   - FULL    所有参与用户都中奖
 *   - LOTTERY 按 PromoConfig.poolLotteryRatio 抽取部分用户中奖
 *
 * 分配方式（来自 PromoConfig.poolDistributeMode）：
 *   - ALL  池子 / 中奖人数 = 每人金额
 *   - STAR 池子先按星级数均分到各桶；桶内再均分给该星级的中奖人
 *
 * 触发方式：
 *   - 商户后台手动触发（W4-3 endpoint）
 *   - cron 自动触发（后续接 quartz）
 */
public interface PoolSettlementService {

    /**
     * 立即触发本商户当前池子的一次结算。
     *
     * @param mode "FULL" 或 "LOTTERY"
     * @return 结算批次记录；池余额为 0 / 无参与用户时返 null（无事可做）
     */
    ShopPromoPoolRoundDO settleNow(String mode);

}
