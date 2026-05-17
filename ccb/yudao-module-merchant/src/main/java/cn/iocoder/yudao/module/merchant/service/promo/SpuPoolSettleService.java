package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolSettleRecordDO;

/**
 * v8 SPU 级星级奖池结算 Service。
 *
 * <p>每个 SPU 一个独立池子；商户后台手工触发结算，按 product_promo_config.pool_dist_rules
 * 把当前池余额按星级分配给该 SPU 上达到对应星级的用户的推广积分。</p>
 *
 * <p>原子性：单事务内完成「锁池 → 计算 → 写 N 条积分流水 → 写结算单 + 明细 → 扣池」。</p>
 *
 * <p>幂等：依靠 PromoPointService.addPromoPoint 内置的 (user, sourceType, sourceId) 三元组去重 +
 * pool 的 FOR UPDATE 行锁，防止两个商户管理员双击重复结算同一个池子。</p>
 */
public interface SpuPoolSettleService {

    /**
     * 手工触发某 SPU 的奖池结算。
     *
     * @param spuId 商品 SPU
     * @param remark 备注（可空）
     * @return 结算单（含分配明细已写入 spu_star_pool_payout_item）
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 池余额=0 / 规则未配 / 规则非法
     */
    SpuStarPoolSettleRecordDO settle(Long spuId, String remark);

}
