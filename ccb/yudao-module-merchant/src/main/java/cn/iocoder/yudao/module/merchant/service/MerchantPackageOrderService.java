package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.module.merchant.controller.app.vo.aivideo.AppMerchantPackagePurchaseRespVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantPackageOrderDO;

import java.time.LocalDateTime;

/**
 * 商户套餐订单 Service（Phase 0.3.3）。
 *
 * <p>业务边界：
 * <ul>
 *   <li>{@link #createOrder} — 下单：创建 merchant_package_order + pay_order，返回给前端</li>
 *   <li>{@link #getByPayOrderId} — 支付回调反查用</li>
 *   <li>{@link #markPaid} — 支付回调成功后落状态并给配额</li>
 * </ul>
 * </p>
 *
 * <p>实际的 JSAPI 签名参数由前端拿 {@code payOrderId} 调用 pay 模块的
 * {@code POST /app-api/pay/order/submit}，不走本 Service——保持关注点分离。</p>
 */
public interface MerchantPackageOrderService {

    /**
     * 下单：创建业务订单 + pay_order，返回前端所需字段。
     *
     * <p>事务语义：单事务内执行 {@code INSERT merchant_package_order}
     * 与 {@code PayOrderApi.createOrder(...)}（后者内部也是独立事务，pay 模块实现），
     * 若 {@code createOrder} 抛异常则我方 INSERT 回滚——避免孤儿业务订单。</p>
     *
     * @param merchantId 当前登录商户 ID（已在 controller 层校验）
     * @param memberUserId 当前登录会员用户 ID，用来取 miniAppOpenId 传给微信
     * @param packageId 目标套餐 ID
     * @param channelCode 支付渠道码（目前仅 {@code wx_lite} 通过白名单）
     * @param userIp 用户 IP（pay 模块强制要求）
     * @return 前端下一步提交支付所需信息
     */
    AppMerchantPackagePurchaseRespVO createOrder(Long merchantId, Long memberUserId,
                                                 Long packageId, String channelCode, String userIp);

    /**
     * 按 pay_order_id 反查业务订单。支付回调 controller 第一步调用。
     *
     * @return 匹配订单，找不到返回 null（说明不是套餐订单，直接忽略回调）
     */
    MerchantPackageOrderDO getByPayOrderId(Long payOrderId);

    /**
     * 将业务订单置为「已支付」并给商户加配额。
     *
     * <p>本方法由支付回调 controller 调用，幂等：
     * <ul>
     *   <li>已是 {@link MerchantPackageOrderDO#PAY_STATUS_PAID} 的订单直接返回</li>
     *   <li>{@code merchantService.increaseVideoQuota} 里 {@code uk_biz(1, payOrderId)} UNIQUE
     *       保证同一 pay_order 重复回调不会重复加配额</li>
     * </ul>
     * </p>
     *
     * <p>事务语义：整个方法在 {@code REQUIRED} 事务里——订单状态更新 + 配额增加必须
     * 一起成功；{@code increaseVideoQuota} 是 {@code REQUIRES_NEW}，独立提交 + 写流水，
     * 回来失败了上层事务也能回滚状态。</p>
     *
     * @param orderId 业务订单 ID
     * @param payTime 支付成功时间（来自 pay 模块的 payOrder.successTime，兜底用 now）
     */
    void markPaid(Long orderId, LocalDateTime payTime);

}
