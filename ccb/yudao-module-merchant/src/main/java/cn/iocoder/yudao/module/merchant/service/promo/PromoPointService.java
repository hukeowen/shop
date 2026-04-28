package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO;

/**
 * 双积分账本服务。
 *
 * 概念：
 *   - 推广积分（promo_point_balance）：来源 DIRECT/QUEUE/COMMISSION/POOL；可转消费 / 可线下提现
 *   - 消费积分（consume_point_balance）：来源 CONSUME/CONVERT；仅本商户消费时抵现
 *
 * 防重：所有变更都按 (userId, sourceType, sourceId) 三元组去重；同一来源只能写一次。
 *
 * 余额承载：ShopUserStarDO 同时承载两类积分余额，避免再开账户表。
 */
public interface PromoPointService {

    /**
     * 推广积分入账（DIRECT / QUEUE / COMMISSION / POOL）。
     *
     * @return true = 成功入账；false = 来源已写过（幂等命中）
     */
    boolean addPromoPoint(Long userId, long amount, String sourceType, Long sourceId, String remark);

    /** 推广积分扣减（CONVERT 转出 / WITHDRAW 提现锁定）。 */
    boolean deductPromoPoint(Long userId, long amount, String sourceType, Long sourceId, String remark);

    /** 消费积分入账（CONSUME / CONVERT 转入）。 */
    boolean addConsumePoint(Long userId, long amount, String sourceType, Long sourceId, String remark);

    /** 消费积分扣减（REDEEM 下单抵扣）。 */
    boolean deductConsumePoint(Long userId, long amount, String sourceType, Long sourceId, String remark);

    /**
     * 用户主动转换：推广积分 → 消费积分，按商户配置的 pointConversionRatio 换算。
     *
     * @param idempotencyKey 调用方提供的幂等键（如客户端 UUID 转 long），保证双击/重试不会重复转换；
     *                       推广扣减 / 消费入账 共用此键，分别落入两侧流水但互不冲突。
     */
    void convertPromoToConsume(Long userId, long promoAmount, Long idempotencyKey);

    /** 取或创建用户的星级 / 积分账户。 */
    ShopUserStarDO getOrCreateAccount(Long userId);

}
