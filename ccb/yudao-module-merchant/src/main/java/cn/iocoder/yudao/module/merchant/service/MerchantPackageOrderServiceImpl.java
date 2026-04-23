package cn.iocoder.yudao.module.merchant.service;

import cn.hutool.core.util.ObjUtil;
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
import cn.iocoder.yudao.module.pay.dal.dataobject.app.PayAppDO;
import cn.iocoder.yudao.module.pay.service.app.PayAppService;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markPaid(Long orderId, LocalDateTime payTime) {
        MerchantPackageOrderDO order = packageOrderMapper.selectById(orderId);
        if (order == null) {
            throw exception(PACKAGE_ORDER_NOT_FOUND);
        }
        // 幂等：已是已支付状态直接返回，不再动配额
        if (ObjUtil.equals(order.getPayStatus(), MerchantPackageOrderDO.PAY_STATUS_PAID)) {
            log.info("[markPaid] 订单 {} 已是已支付状态，忽略重复回调", orderId);
            return;
        }

        // 给配额：biz_id=payOrderId 做幂等键；失败抛异常回滚本事务的状态更新
        int after = merchantService.increaseVideoQuota(
                order.getMerchantId(),
                order.getVideoCount(),
                VideoQuotaBizTypeEnum.PACKAGE_PURCHASE.getCode(),
                String.valueOf(order.getPayOrderId()),
                "购买套餐: " + order.getPackageName());

        // 更新订单状态（pay_status 保持 >= PAID 的幂等：只改 WAITING → PAID）
        MerchantPackageOrderDO update = new MerchantPackageOrderDO();
        update.setId(orderId);
        update.setPayStatus(MerchantPackageOrderDO.PAY_STATUS_PAID);
        update.setPayTime(payTime != null ? payTime : LocalDateTime.now());
        packageOrderMapper.updateById(update);

        log.info("[markPaid] 订单 {} 已标记为已支付，商户 {} 配额增至 {}",
                orderId, order.getMerchantId(), after);
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
