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

    /** 取某用户某商品的队列记录（可能不存在 / 已 EXITED） */
    default ShopQueuePositionDO selectByUserAndSpu(Long userId, Long spuId) {
        return selectOne(new LambdaQueryWrapperX<ShopQueuePositionDO>()
                .eq(ShopQueuePositionDO::getUserId, userId)
                .eq(ShopQueuePositionDO::getSpuId, spuId));
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
