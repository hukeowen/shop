package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopOfflinePaymentDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopOfflinePaymentMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * C 端 - 线下转账收款（商户未开通在线支付时的收款链路）。
 *
 * <p>顾客下单后：
 * <ol>
 *   <li>GET /info：看商户收款码（微信/支付宝）+ 应付金额 + 当前凭证状态</li>
 *   <li>POST /submit-proof：上传付款凭证截图（status → 1 待商户确认）</li>
 * </ol>
 *
 * <p>跨租户：顾客 token 的 tenant ≠ 商户 tenant，故方法标 {@link TenantIgnore}，
 * 按全局唯一的 order_id 反查收款记录，再用记录里的 tenantId 取商户店铺信息。
 * 鉴权：校验登录用户 == 记录里的 buyer userId，防越权看/改他人订单。</p>
 */
@Tag(name = "C 端 - 线下转账收款")
@RestController
@RequestMapping("/merchant/mini/offline-pay")
@Validated
@Slf4j
public class AppMerchantOfflinePayController {

    @Resource
    private ShopOfflinePaymentMapper shopOfflinePaymentMapper;
    @Resource
    private ShopInfoMapper shopInfoMapper;

    @GetMapping("/info")
    @Operation(summary = "获取线下转账收款信息（收款码 + 应付 + 凭证状态）")
    @TenantIgnore
    public CommonResult<Map<String, Object>> getInfo(@RequestParam("orderId") Long orderId) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null || userId <= 0) {
            throw exception0(1_031_001_010, "请先登录");
        }
        ShopOfflinePaymentDO rec = shopOfflinePaymentMapper.selectByOrderId(orderId);
        Map<String, Object> resp = new HashMap<>();
        if (rec == null) {
            // 该订单不是线下转账单（如在线支付 / 已走通联）
            resp.put("found", false);
            return success(resp);
        }
        if (!userId.equals(rec.getUserId())) {
            throw exception0(403, "无权查看该订单");
        }
        resp.put("found", true);
        resp.put("orderId", rec.getOrderId());
        resp.put("payPrice", rec.getPayPrice());
        resp.put("status", rec.getStatus());
        resp.put("proofUrl", rec.getProofUrl());
        resp.put("payChannel", rec.getPayChannel());
        resp.put("buyerRemark", rec.getBuyerRemark());
        resp.put("rejectReason", rec.getRejectReason());
        resp.put("submitTime", rec.getSubmitTime());
        resp.put("confirmTime", rec.getConfirmTime());
        // 用记录里的商户 tenant 取店铺收款码。本方法已 @TenantIgnore，且 selectByTenantId
        // 显式按 tenant_id 列过滤，故能正确取到商户店铺（不会被自动租户过滤拦掉）。
        Long tenantId = rec.getTenantId();
        ShopInfoDO shop = tenantId == null ? null : shopInfoMapper.selectByTenantId(tenantId);
        if (shop != null) {
            resp.put("shopName", shop.getShopName());
            resp.put("merchantMobile", shop.getMobile());
            resp.put("wechatPayQrUrl", shop.getWechatPayQrUrl());
            resp.put("alipayPayQrUrl", shop.getAlipayPayQrUrl());
        }
        return success(resp);
    }

    @PostMapping("/submit-proof")
    @Operation(summary = "上传付款凭证（顾客线下转账后）")
    @TenantIgnore
    public CommonResult<Boolean> submitProof(@Valid @RequestBody SubmitProofReqVO req) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null || userId <= 0) {
            throw exception0(1_031_001_010, "请先登录");
        }
        ShopOfflinePaymentDO rec = shopOfflinePaymentMapper.selectByOrderId(req.getOrderId());
        if (rec == null) {
            throw exception0(400, "该订单无需上传线下付款凭证");
        }
        if (!userId.equals(rec.getUserId())) {
            throw exception0(403, "无权操作该订单");
        }
        if (Integer.valueOf(ShopOfflinePaymentDO.STATUS_CONFIRMED).equals(rec.getStatus())) {
            throw exception0(400, "商户已确认收款，无需重复上传");
        }
        ShopOfflinePaymentDO update = new ShopOfflinePaymentDO();
        update.setId(rec.getId());
        update.setProofUrl(req.getProofUrl());
        update.setPayChannel(req.getPayChannel());
        update.setBuyerRemark(req.getRemark());
        update.setStatus(ShopOfflinePaymentDO.STATUS_SUBMITTED);
        update.setSubmitTime(LocalDateTime.now());
        // 注：上次驳回原因不清空（MP updateById NOT_NULL 策略也写不进 null），C 端按 status 展示即可
        shopOfflinePaymentMapper.updateById(update);
        log.info("[offline-pay] 顾客上传付款凭证 orderId={} userId={} channel={}",
                req.getOrderId(), userId, req.getPayChannel());
        return success(true);
    }

    @Data
    public static class SubmitProofReqVO {
        @NotNull(message = "orderId 不能为空")
        private Long orderId;
        @NotNull(message = "付款凭证不能为空")
        private String proofUrl;
        /** 付款渠道：wechat / alipay */
        private String payChannel;
        /** 顾客备注（如转账后四位 / 留言） */
        private String remark;
    }

}
