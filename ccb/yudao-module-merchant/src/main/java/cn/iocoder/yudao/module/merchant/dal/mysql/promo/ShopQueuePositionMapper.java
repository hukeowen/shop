package cn.iocoder.yudao.module.merchant.dal.mysql.promo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueuePositionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 商品队列位置 Mapper。
 *
 * 核心查询：在某个商品队列里，按"A 层优先（按 promoted_at 升序）→ B 层（按 joined_at 升序）"找队首。
 * 用 layer 倒序（'A' < 'B' in ASCII，但我们要 A 先 → 用 ORDER BY layer ASC ✅）。
 */
@Mapper
public interface ShopQueuePositionMapper extends BaseMapperX<ShopQueuePositionDO> {

    /**
     * 取某用户某商品的队列记录（可能不存在 / 已 EXITED）。
     *
     * <p>跨租户读：queue_position 写入用 buyer 自己 tenant，但 v8 推 N 反 1 引擎
     * 检查 parent 是否已激活 spu 时，parent 和 buyer 可能不在同一租户（如 SaaS 套餐场景：
     * A 在 tenant=1005 激活，B 在 tenant=1006 买）。spu_id 全局唯一，userId 全局，跨租户安全。</p>
     */
    default ShopQueuePositionDO selectByUserAndSpu(Long userId, Long spuId) {
        return cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.executeIgnore(() ->
                selectOne(new LambdaQueryWrapperX<ShopQueuePositionDO>()
                        .eq(ShopQueuePositionDO::getUserId, userId)
                        .eq(ShopQueuePositionDO::getSpuId, spuId)));
    }

    /** 用户在「当前租户」是否买过任意推 N 反 1 商品（有队列记录即算）。≥1 返回 true。 */
    default boolean existsByUserId(Long userId) {
        return selectCount(new LambdaQueryWrapperX<ShopQueuePositionDO>()
                .eq(ShopQueuePositionDO::getUserId, userId)) > 0;
    }

    /**
     * 取队首：A 层最早进 A 的优先；A 空时取 B 层最早入队的。
     * 仅看 status = QUEUEING。
     */
    default ShopQueuePositionDO selectQueueHead(Long spuId) {
        return selectOne(new LambdaQueryWrapperX<ShopQueuePositionDO>()
                .eq(ShopQueuePositionDO::getSpuId, spuId)
                .eq(ShopQueuePositionDO::getStatus, "QUEUEING")
                .orderByAsc(ShopQueuePositionDO::getLayer)         // 'A' < 'B'，A 先
                .orderByAsc(ShopQueuePositionDO::getPromotedAt)    // A 层内按晋升时间升序
                .orderByAsc(ShopQueuePositionDO::getJoinedAt)      // B 层 / 同时间用入队时间
                .last("LIMIT 1"));
    }

    /**
     * 自然队列「最近一个 QUEUEING 用户」— 新买家进入时，奖前一个（最近入队）自然用户。
     * 链路：1 激活 → 2 买（奖 1） → 3 买（奖 2） → 4 买（奖 3）...
     * 用 joined_at DESC 让每个新买家奖到他的"上一位"。
     */
    default ShopQueuePositionDO selectQueueLatest(Long spuId) {
        return selectOne(new LambdaQueryWrapperX<ShopQueuePositionDO>()
                .eq(ShopQueuePositionDO::getSpuId, spuId)
                .eq(ShopQueuePositionDO::getStatus, "QUEUEING")
                .orderByDesc(ShopQueuePositionDO::getJoinedAt)
                .last("LIMIT 1"));
    }

    /** 列出某商品所有 QUEUEING 状态的位置（调试 / 商户后台展示） */
    default List<ShopQueuePositionDO> selectListBySpuQueueing(Long spuId) {
        return selectList(new LambdaQueryWrapperX<ShopQueuePositionDO>()
                .eq(ShopQueuePositionDO::getSpuId, spuId)
                .eq(ShopQueuePositionDO::getStatus, "QUEUEING")
                .orderByAsc(ShopQueuePositionDO::getLayer)
                .orderByAsc(ShopQueuePositionDO::getPromotedAt)
                .orderByAsc(ShopQueuePositionDO::getJoinedAt));
    }

    /** 列出某用户当前所有 QUEUEING 状态的位置（用户端"我的队列"页用） */
    default List<ShopQueuePositionDO> selectListByUserIdQueueing(Long userId) {
        return selectList(new LambdaQueryWrapperX<ShopQueuePositionDO>()
                .eq(ShopQueuePositionDO::getUserId, userId)
                .eq(ShopQueuePositionDO::getStatus, "QUEUEING")
                .orderByAsc(ShopQueuePositionDO::getLayer)
                .orderByAsc(ShopQueuePositionDO::getPromotedAt)
                .orderByAsc(ShopQueuePositionDO::getJoinedAt));
    }

}
