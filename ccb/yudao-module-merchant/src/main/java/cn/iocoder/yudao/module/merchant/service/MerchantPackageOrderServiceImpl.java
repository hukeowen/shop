package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.merchant.controller.app.vo.aivideo.AppMerchantPackagePurchaseRespVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.AiVideoPackageDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantPackageOrderDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantPackageOrderMapper;
import cn.iocoder.yudao.module.merchant.enums.ai.VideoQuotaBizTypeEnum;
import cn.iocoder.yudao.module.merchant.framework.config.MerchantPackageProperties;
import cn.iocoder.yudao.module.pay.api.order.PayOrderApi;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderCreateReqDTO;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.dal.dataobject.app.PayAppDO;
import cn.iocoder.yudao.module.pay.enums.order.PayOrderStatusEnum;
import cn.iocoder.yudao.module.pay.service.app.PayAppService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.date.LocalDateTimeUtils.addTime;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.PACKAGE_ORDER_CHANNEL_UNSUPPORTED;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.PACKAGE_ORDER_NOT_FOUND;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.PACKAGE_ORDER_OPENID_MISSING;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.PACKAGE_ORDER_PAY_NOT_SUCCESS;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.PACKAGE_ORDER_PRICE_INVALID;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.PAY_APP_ID_NOT_CONFIGURED;

/**
 * {@link MerchantPackageOrderService} 实现类。
 *
 * <p>与 yudao-module-pay 的边界：
 * <ul>
 *   <li>仅调 {@link PayOrderApi#createOrder(PayOrderCreateReqDTO)} —— 创建支付单</li>
 *   <li>仅调 {@link PayAppService#getApp(Long)} —— 把配置的 payAppId 翻译成 appKey</li>
 *   <li>支付 JSAPI 签名由前端直接调 pay 模块的 {@code /app-api/pay/order/submit} 生成</li>
 *   <li>支付成功回调由 pay 模块 HTTP POST 到我方 {@code /update-paid} endpoint</li>
 * </ul>
 * </p>
 */
@Service
@Validated
@Slf4j
public class MerchantPackageOrderServiceImpl implements MerchantPackageOrderService {

    /** 支付单过期时长，微信建议 ≤ 2h。 */
    private static final Duration PAY_ORDER_EXPIRE = Duration.ofHours(2L);

    /**
     * 白名单支付渠道。Phase 0.3.3 只开 {@code wx_lite}；接入支付宝 / H5 时扩这里。
     *
     * <p>不引用 {@code PayChannelEnum} 的原因：那是 pay 模块枚举，不想让上层强耦合。</p>
     */
    private static final String CHANNEL_WX_LITE = "wx_lite";

    @Resource
    private MerchantPackageOrderMapper packageOrderMapper;

    @Resource
    private AiVideoPackageService packageService;

    @Resource
    private MerchantService merchantService;

    @Resource
    private MemberUserMapper memberUserMapper;

    @Resource
    private PayOrderApi payOrderApi;

    @Resource
    private PayAppService payAppService;

    @Resource
    private MerchantPackageProperties packageProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppMerchantPackagePurchaseRespVO createOrder(Long merchantId, Long memberUserId,
                                                        Long packageId, String channelCode, String userIp) {
        // 1. 白名单渠道（防越权传 wallet / 其他）
        if (!CHANNEL_WX_LITE.equals(channelCode)) {
            throw exception(PACKAGE_ORDER_CHANNEL_UNSUPPORTED);
        }

        // 2. 解析支付应用 appKey
        String appKey = resolvePayAppKey();

        // 3. 校验 openid（wx_lite JSAPI 必需）
        MemberUserDO member = memberUserMapper.selectById(memberUserId);
        if (member == null || member.getMiniAppOpenId() == null || member.getMiniAppOpenId().isEmpty()) {
            throw exception(PACKAGE_ORDER_OPENID_MISSING);
        }

        // 4. 校验套餐可售 + 锁定快照
        AiVideoPackageDO pkg = packageService.validatePackageAvailable(packageId);

        // 5. 先插业务订单（拿到 packageOrderId 作为 merchantOrderId）
        MerchantPackageOrderDO order = MerchantPackageOrderDO.builder()
                .merchantId(merchantId)
                .packageId(pkg.getId())
                .packageName(pkg.getName())
                .videoCount(pkg.getVideoCount())
                .price(pkg.getPrice())
                .payStatus(MerchantPackageOrderDO.PAY_STATUS_WAITING)
                .build();
        packageOrderMapper.insert(order);

        // 6. 创建 pay_order
        Integer priceInt = safeToIntPrice(pkg.getPrice());
        Long payOrderId = payOrderApi.createOrder(new PayOrderCreateReqDTO()
                .setAppKey(appKey)
                .setUserIp(userIp)
                .setUserId(memberUserId)
                .setUserType(UserTypeEnum.MEMBER.getValue())
                .setMerchantOrderId(order.getId().toString())
                .setSubject(truncateSubject("套餐:" + pkg.getName()))
                .setBody(pkg.getVideoCount() + " 条 AI 视频")
                .setPrice(priceInt)
                .setExpireTime(addTime(PAY_ORDER_EXPIRE)));

        // 7. 回填 pay_order_id
        MerchantPackageOrderDO update = new MerchantPackageOrderDO();
        update.setId(order.getId());
        update.setPayOrderId(payOrderId);
        packageOrderMapper.updateById(update);

        log.info("[createOrder] 套餐订单创建成功 merchantId={} packageId={} orderId={} payOrderId={} price={}",
                merchantId, packageId, order.getId(), payOrderId, priceInt);

        // 8. 组装响应
        AppMerchantPackagePurchaseRespVO resp = new AppMerchantPackagePurchaseRespVO();
        resp.setPackageOrderId(order.getId());
        resp.setPayOrderId(payOrderId);
        resp.setChannelCode(channelCode);
        resp.setPrice(pkg.getPrice());
        resp.setPackageName(pkg.getName());
        resp.setVideoCount(pkg.getVideoCount());
        return resp;
    }

    @Override
    public MerchantPackageOrderDO getByPayOrderId(Long payOrderId) {
        return packageOrderMapper.selectByPayOrderId(payOrderId);
    }

    /**
     * 支付回调业务落位入口。修复项（Phase 0.3.3 必修）：
     *
     * <ol>
     *   <li><b>HIGH 1 —— 二次校验支付单真实状态</b>：
     *       {@code /pay-callback} 是 {@code @PermitAll}，仅靠 merchantOrderId / payOrderId 对齐
     *       无法抵挡伪造回调（构造 POST 就能加配额）。这里多调一次
     *       {@link PayOrderApi#getOrder(Long)}，要求
     *       {@link PayOrderStatusEnum#SUCCESS} 且 price 完全等于业务订单 price，
     *       否则抛 {@link cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants#PACKAGE_ORDER_PAY_NOT_SUCCESS}。</li>
     *   <li><b>HIGH 2 —— 消除 TOCTOU，把 read-then-update 改 CAS</b>：
     *       用 {@code UPDATE ... WHERE pay_status=WAITING} 原子翻状态，affected=0 直接短路幂等。
     *       并发回调不会都进入 {@code increaseVideoQuota}。</li>
     *   <li><b>MEDIUM 3 —— 调换顺序</b>：先 CAS 状态、再加配额。
     *       CAS 在外层事务里（失败可回滚），加配额是 REQUIRES_NEW 独立事务。
     *       下游 {@code increaseVideoQuota} 失败时外层事务回滚，pay 模块重试可以再进来；
     *       不会出现"配额加了、状态没回、下次又加"的泄漏。</li>
     *   <li><b>MEDIUM 4 —— 回写 quota_log_id</b>：从 {@link QuotaChangeResult} 取 logId 补写
     *       {@code merchant_package_order.quota_log_id}，闭合审计链。</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markPaid(Long orderId, LocalDateTime payTime) {
        MerchantPackageOrderDO order = packageOrderMapper.selectById(orderId);
        if (order == null) {
            throw exception(PACKAGE_ORDER_NOT_FOUND);
        }

        // 【HIGH 1】二次反查 pay_order 真实状态：防伪造回调
        // 即使攻击者构造 merchantOrderId + payOrderId 对齐的 payload，也无法把一个未支付单变成
        // PayOrderDO.status=SUCCESS；加金额校验兜底二次攻击面。
        PayOrderRespDTO payOrder = order.getPayOrderId() == null
                ? null : payOrderApi.getOrder(order.getPayOrderId());
        if (payOrder == null
                || !PayOrderStatusEnum.SUCCESS.getStatus().equals(payOrder.getStatus())
                || !safeToIntPrice(order.getPrice()).equals(payOrder.getPrice())) {
            log.warn("[markPaid] 二次校验失败 orderId={} payOrderId={} payOrderStatus={} expectedPrice={} actualPrice={}",
                    orderId, order.getPayOrderId(),
                    payOrder == null ? null : payOrder.getStatus(),
                    order.getPrice(),
                    payOrder == null ? null : payOrder.getPrice());
            throw exception(PACKAGE_ORDER_PAY_NOT_SUCCESS);
        }

        // 【HIGH 2 + MEDIUM 3】CAS：先在外层事务里原子翻状态 WAITING→PAID，再动配额。
        // 并发两次回调 → 只有一个 affected=1，另一个 affected=0 幂等短路。
        int affected = packageOrderMapper.update(null,
                new LambdaUpdateWrapper<MerchantPackageOrderDO>()
                        .set(MerchantPackageOrderDO::getPayStatus, MerchantPackageOrderDO.PAY_STATUS_PAID)
                        .set(MerchantPackageOrderDO::getPayTime, payTime != null ? payTime : LocalDateTime.now())
                        .eq(MerchantPackageOrderDO::getId, orderId)
                        .eq(MerchantPackageOrderDO::getPayStatus, MerchantPackageOrderDO.PAY_STATUS_WAITING));
        if (affected == 0) {
            log.info("[markPaid] 订单 {} CAS 失败（已被处理或非待支付），幂等短路", orderId);
            return;
        }

        // 此时订单已原子进入 PAID，并发线程进不来；现在安全地加配额（REQUIRES_NEW 独立事务）。
        // 失败 → 本方法抛异常 → 外层事务回滚 CAS 撤销 → pay 模块重试下次能再次进入。
        QuotaChangeResult result = merchantService.increaseVideoQuota(
                order.getMerchantId(),
                order.getVideoCount(),
                VideoQuotaBizTypeEnum.PACKAGE_PURCHASE.getCode(),
                String.valueOf(order.getPayOrderId()),
                "购买套餐: " + order.getPackageName());

        // 【MEDIUM 4】回写 quota_log_id 闭合审计链。这条 UPDATE 若失败也会随外层事务回滚；
        // quotaLog 是 REQUIRES_NEW 已提交，但下次重试进来配额流水有 uk_biz 幂等兜底不会重复加。
        packageOrderMapper.update(null,
                new LambdaUpdateWrapper<MerchantPackageOrderDO>()
                        .set(MerchantPackageOrderDO::getQuotaLogId, result.getLogId())
                        .eq(MerchantPackageOrderDO::getId, orderId));

        log.info("[markPaid] 订单 {} 已标记为已支付，商户 {} 配额增至 {}，quotaLogId={}",
                orderId, order.getMerchantId(), result.getQuotaAfter(), result.getLogId());
    }

    /**
     * 解析配置的 payAppKey。优先取 {@code payAppKey}；其次按 {@code payAppId} 查 PayAppDO。
     * 配置缺失或查无应用时抛 {@link MerchantPackageProperties} 对应错误码。
     */
    private String resolvePayAppKey() {
        if (!packageProperties.isConfigured()) {
            throw exception(PAY_APP_ID_NOT_CONFIGURED);
        }
        if (packageProperties.getPayAppKey() != null && !packageProperties.getPayAppKey().isEmpty()) {
            return packageProperties.getPayAppKey();
        }
        PayAppDO app = payAppService.getApp(packageProperties.getPayAppId());
        if (app == null || app.getAppKey() == null || app.getAppKey().isEmpty()) {
            throw exception(PAY_APP_ID_NOT_CONFIGURED);
        }
        return app.getAppKey();
    }

    /**
     * 套餐价格 Long(分) → Integer。pay 模块 price 字段是 Integer（分），
     * 套餐价格极少会超过 20 亿分（20 million 元），Long→Integer 溢出实际不会发生，
     * 但仍做 safety check——超限直接抛。
     */
    private Integer safeToIntPrice(Long priceFen) {
        if (priceFen == null || priceFen <= 0 || priceFen > Integer.MAX_VALUE) {
            throw exception(PACKAGE_ORDER_PRICE_INVALID);
        }
        return priceFen.intValue();
    }

    /**
     * {@link PayOrderCreateReqDTO#getSubject()} 限长 32。
     */
    private String truncateSubject(String raw) {
        if (raw == null) {
            return "";
        }
        int max = PayOrderCreateReqDTO.SUBJECT_MAX_LENGTH;
        return raw.length() <= max ? raw : raw.substring(0, max);
    }

}
