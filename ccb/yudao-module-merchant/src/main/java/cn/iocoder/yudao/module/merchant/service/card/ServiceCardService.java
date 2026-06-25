package cn.iocoder.yudao.module.merchant.service.card;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.dal.dataobject.card.ServiceCardDefDO;

import java.util.List;
import java.util.Map;

/**
 * 服务卡包 / 核销 Service。
 *
 * <p>商家建商品配卡 → 用户付款发卡 → 用户出示码 → 商家核销（次数/时间谁先到都不能再用）。</p>
 */
public interface ServiceCardService {

    // ============ 商家：商品卡定义 ============

    /** 全量保存某商品的卡定义（先清后插）。tenantId 决定归属哪家店。 */
    void saveDefs(Long tenantId, Long spuId, List<ServiceCardDefDO> defs);

    /** 某商品的卡定义列表 */
    List<ServiceCardDefDO> listDefs(Long tenantId, Long spuId);

    // ============ 发卡（付款后，幂等） ============

    /**
     * 按订单发卡。依赖调用方已切到该订单的租户上下文（线上走 TradeOrderHandler，线下走 OrderPaidListener）。
     * 幂等：同一订单已发过卡则跳过。
     */
    void issueForOrder(Long orderId);

    // ============ 用户：我的卡包 ============

    /** 我的全部卡（跨店），含店名/剩余次数/有效期/有效状态 */
    List<Map<String, Object>> listMyCards(Long userId);

    /** 我的单张卡详情（用于出示码页） */
    Map<String, Object> getMyCard(Long userId, Long cardId);

    // ============ 商家：核销 ============

    /** 扫码/输码后查卡信息（不改数据）：返回卡名、用户手机号、剩余次数、是否可核销+原因 */
    Map<String, Object> verifyInfo(Long tenantId, String cardNo);

    /** 核销一次（原子）。成功返回核销结果；不可核销抛 ServiceException。 */
    Map<String, Object> redeem(Long tenantId, String cardNo, Long verifierId, String remark);

    /** 商户核销记录分页 */
    PageResult<Map<String, Object>> listVerifyRecords(Long tenantId, PageParam pageParam);

}
