package cn.iocoder.yudao.module.merchant.service.saas;

import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.saas.MerchantSubscriptionOrderDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.saas.SaasPackageConfigDO;

import java.util.List;

/**
 * SaaS 订阅服务 — 商户购买套餐 / 续期 / 检查到期。
 *
 * <p>核心数据流：</p>
 * <ol>
 *   <li>{@link #listEnabledPackages()} 续费页拉套餐列表</li>
 *   <li>{@link #createSubscriptionOrder} 商户选套餐 → 建订阅订单 → 调通联拿支付链接</li>
 *   <li>{@link #markPaid} 通联回调 / 轮询命中 → 续期到 expire_at + 1 年 +
 *       AI 视频额度 +N + service_package_level 切到新档</li>
 *   <li>{@link #isExpired} 商户登录拦截器查到期</li>
 *   <li>{@link #getEffectiveLevel} 算当前生效级别（同时持有 PRO + BASIC 时优先 PRO；
 *       PRO 到期回 BASIC；都到期则 EXPIRED）</li>
 * </ol>
 */
public interface SaasSubscriptionService {

    /** 列出所有可购套餐（C 端续费页用） */
    List<SaasPackageConfigDO> listEnabledPackages();

    /**
     * 商户选套餐 → 创建订阅订单（status=WAITING）+ 返通联支付 reqsn。
     * 实际调通联拿支付链接由 controller 调 cashier 完成（reqsn = S${orderId}）。
     */
    MerchantSubscriptionOrderDO createSubscriptionOrder(Long merchantId, String level);

    /**
     * 通联回调 / 轮询命中 → 标已支付 + 续期商户服务。
     *
     * <p>幂等：CAS UPDATE status=0→1，重复回调 short-circuit。</p>
     *
     * @param orderId 订阅订单 id（来自 reqsn 解析 S 前缀后）
     * @param paidAmountFen 通联回执金额（用于二次校验）
     */
    void markPaid(Long orderId, int paidAmountFen);

    /**
     * 商户当前生效级别（考虑到期 + 试用 + 平台商户）。
     * 返回 PLATFORM / PRO / BASIC / TRIAL / EXPIRED。
     */
    String getEffectiveLevel(MerchantDO merchant);

    /** 商户当前是否过期（平台商户永远 false） */
    boolean isExpired(MerchantDO merchant);
}
