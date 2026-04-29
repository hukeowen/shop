package cn.iocoder.yudao.module.merchant.controller.admin;

import cn.hutool.crypto.SecureUtil;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.merchant.config.AllinpayProperties;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.service.allinpay.AllinpaySignUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 通联收付通异步通知 webhook
 *
 * <p>路径 {@code /admin-api/merchant/pay/tl-notify} 必须在 yudao.security.permit-all_urls
 * 白名单内（通联回调没有 yudao 自己的 token），靠通联公钥验签做身份验证。</p>
 *
 * <p>响应：必须严格按通联约定返 {@code SUCCESS}（plain text）才视为接收成功，
 * 否则通联会重试，最多重试 5 次，间隔递增。</p>
 */
@Tag(name = "管理后台 - 通联异步通知")
@RestController
@RequestMapping("/merchant/pay")
@Slf4j
public class AllinpayNotifyController {

    @Resource
    private AllinpayProperties props;
    @Resource
    private ShopInfoMapper shopInfoMapper;

    @Value("${merchant.field-encrypt-key:dev_key_12345678}")
    private String fieldEncryptKey;

    /**
     * 进件结果异步通知
     *
     * <p>通联 POST form-urlencoded：</p>
     * <pre>
     *   outorderid=TX1234...
     *   status=02   // 02=通过 03=驳回 04=待补充资料
     *   mchntid=8881234567890       // 仅 status=02
     *   mchntkey=ABCDEF....         // 仅 status=02
     *   rejectreason=...            // 仅 status=03
     *   sign=base64                  // RSA SHA256 签名
     * </pre>
     */
    @PostMapping(value = "/tl-notify", produces = "text/plain;charset=UTF-8")
    @Operation(summary = "通联进件 / 支付结果异步通知")
    public String onAllinpayNotify(@RequestParam Map<String, String> params) {
        log.info("[allinpay-notify] receive params keys={}", params.keySet());

        String sign = params.remove("sign");
        if (sign == null || sign.isEmpty()) {
            log.warn("[allinpay-notify] 拒绝：missing sign");
            return "FAIL_SIGN_MISSING";
        }
        // 用通联公钥验签
        try {
            if (!AllinpaySignUtils.verifyNotify(params, sign, props.getAllinpayRsaPublicKey())) {
                log.warn("[allinpay-notify] 拒绝：sign 验证失败");
                return "FAIL_SIGN_INVALID";
            }
        } catch (Exception e) {
            log.warn("[allinpay-notify] 验签异常：{}", e.getMessage());
            return "FAIL_VERIFY_EXCEPTION";
        }

        String outOrderId = params.get("outorderid");
        if (outOrderId == null || outOrderId.isEmpty()) {
            return "FAIL_OUTORDERID_MISSING";
        }

        // 反查店铺；ShopInfoDO 是平台级表，不需要 tenant 上下文
        ShopInfoDO shop = TenantUtils.executeIgnore(() -> shopInfoMapper.selectByTlOpenOrderId(outOrderId));
        if (shop == null) {
            log.warn("[allinpay-notify] 找不到 outOrderId={} 对应的店铺", outOrderId);
            return "FAIL_ORDER_NOT_FOUND";
        }

        String status = params.get("status");
        ShopInfoDO update = new ShopInfoDO();
        update.setId(shop.getId());
        if ("02".equals(status)) {
            // 开户成功
            String mchntId = params.get("mchntid");
            String mchntKey = params.get("mchntkey");
            if (mchntId == null || mchntId.isEmpty()) {
                log.warn("[allinpay-notify] status=02 但 mchntid 为空 outOrderId={}", outOrderId);
                return "FAIL_MCHNT_ID_MISSING";
            }
            update.setTlMchId(mchntId);
            if (mchntKey != null && !mchntKey.isEmpty()) {
                update.setTlMchKey(SecureUtil.aes(fieldEncryptKey.getBytes()).encryptHex(mchntKey));
            }
            update.setOnlinePayEnabled(true);
            update.setPayApplyStatus(2);
            update.setPayApplyRejectReason("");
            log.info("[allinpay-notify] 店铺 {} 通联开户成功 mchId={}", shop.getId(), mchntId);
        } else if ("03".equals(status)) {
            update.setPayApplyStatus(3);
            update.setPayApplyRejectReason("通联拒绝：" + params.getOrDefault("rejectreason", ""));
            update.setOnlinePayEnabled(false);
            log.warn("[allinpay-notify] 店铺 {} 通联驳回 reason={}", shop.getId(), params.get("rejectreason"));
        } else {
            log.info("[allinpay-notify] 店铺 {} 状态变化 status={}（未变更落库）", shop.getId(), status);
            return "SUCCESS";  // 接收 + 不更新 也回 SUCCESS 防通联重试
        }

        TenantUtils.executeIgnore(() -> shopInfoMapper.updateById(update));
        // TODO P2-11 审计日志：记录这次状态变化（旧状态/新状态/通联原始 params）
        return "SUCCESS";
    }

    /** 占位健康检查（debug） */
    @PostMapping(value = "/tl-notify-echo", produces = "application/json")
    public Map<String, Object> echo(@RequestParam Map<String, String> params) {
        Map<String, Object> r = new HashMap<>();
        r.put("ok", true);
        r.put("received", params.size());
        return r;
    }
}
