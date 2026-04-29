package cn.iocoder.yudao.module.merchant.controller.admin;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.shop.ShopPayApplyAuditReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.shop.ShopPayApplyPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.service.KycSignService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 店铺在线支付开通审核")
@RestController
@RequestMapping("/merchant/shop")
@Validated
public class MerchantShopController {

    @Resource
    private ShopInfoMapper shopInfoMapper;
    @Resource
    private KycSignService kycSignService;

    @GetMapping("/pay-apply/page")
    @Operation(summary = "分页查询在线支付开通申请")
    @PreAuthorize("@ss.hasPermission('merchant:shop:pay-apply:query')")
    public CommonResult<PageResult<ShopInfoDO>> getPayApplyPage(@Valid ShopPayApplyPageReqVO pageReqVO) {
        LambdaQueryWrapper<ShopInfoDO> wrapper = new LambdaQueryWrapper<ShopInfoDO>()
                .isNotNull(ShopInfoDO::getPayApplyStatus)
                .orderByDesc(ShopInfoDO::getCreateTime);
        if (pageReqVO.getStatus() != null) {
            wrapper.eq(ShopInfoDO::getPayApplyStatus, pageReqVO.getStatus());
        }

        // 手动分页
        long total = shopInfoMapper.selectCount(wrapper);
        int offset = (pageReqVO.getPageNo() - 1) * pageReqVO.getPageSize();
        wrapper.last("LIMIT " + pageReqVO.getPageSize() + " OFFSET " + offset);
        List<ShopInfoDO> list = shopInfoMapper.selectList(wrapper);

        return success(new PageResult<>(list, total));
    }

    @GetMapping("/pay-apply/kyc-sign")
    @Operation(summary = "签发 KYC 资质 TOS key 的临时 GET URL（审核员预览）")
    @PreAuthorize("@ss.hasPermission('merchant:shop:pay-apply:query')")
    public CommonResult<Map<String, String>> signKycKey(@RequestParam String key,
                                                        @RequestParam(defaultValue = "3600") int ttl) {
        // 校验：key 必须确实属于某个有进件申请的店铺（防审核员越权拼任意 key）
        long match = shopInfoMapper.selectCount(
                new LambdaQueryWrapper<ShopInfoDO>()
                        .and(w -> w.eq(ShopInfoDO::getIdCardFrontKey, key)
                                .or().eq(ShopInfoDO::getIdCardBackKey, key)
                                .or().eq(ShopInfoDO::getBusinessLicenseKey, key)));
        if (match == 0) {
            throw ServiceExceptionUtil.exception0(BAD_REQUEST.getCode(), "key 未被任何店铺引用");
        }
        Map<String, String> resp = new HashMap<>();
        resp.put("url", kycSignService.sign(key, ttl));
        return success(resp);
    }

    @PutMapping("/pay-apply/audit")
    @Operation(summary = "审核在线支付开通申请（通过或驳回）")
    @PreAuthorize("@ss.hasPermission('merchant:shop:pay-apply:audit')")
    public CommonResult<Boolean> auditPayApply(@Valid @RequestBody ShopPayApplyAuditReqVO reqVO) {
        ShopInfoDO shop = shopInfoMapper.selectById(reqVO.getShopId());
        if (shop == null) {
            throw ServiceExceptionUtil.exception0(BAD_REQUEST.getCode(), "店铺不存在");
        }
        if (!Integer.valueOf(1).equals(shop.getPayApplyStatus())) {
            throw ServiceExceptionUtil.exception0(BAD_REQUEST.getCode(), "该申请不在审核中状态");
        }

        ShopInfoDO update = new ShopInfoDO();
        update.setId(reqVO.getShopId());
        if (Boolean.TRUE.equals(reqVO.getApproved())) {
            // 状态 2 = 已开通；tlMchId / tlMchKey 由通联进件 API 异步回调（webhook）写入。
            //   流程：审核通过(2) → AllinpayMerchantClient.openMerchant 异步发送进件 →
            //         通联回调 /admin-api/merchant/pay/tl-notify → 写真 tlMchId/tlMchKey
            // 当前阶段（通联接入未完成 = P1-6）：tlMchId 留空，状态先到 2，真接入完成后
            // 商户重新刷新页面即可看到通联号。
            update.setOnlinePayEnabled(true);
            update.setPayApplyStatus(2);
            // 清空驳回原因（如果之前驳回过又重新通过的场景）
            update.setPayApplyRejectReason("");
            // TODO P1-6: 在事务提交后 publish ShopPayApplyApprovedEvent，
            //          事件监听器异步调 AllinpayMerchantClient.openMerchant
        } else {
            if (reqVO.getRejectReason() == null || reqVO.getRejectReason().isEmpty()) {
                throw ServiceExceptionUtil.exception0(BAD_REQUEST.getCode(), "驳回原因不能为空");
            }
            update.setOnlinePayEnabled(false);
            update.setPayApplyStatus(3);
            update.setPayApplyRejectReason(reqVO.getRejectReason());
        }
        shopInfoMapper.updateById(update);
        return success(true);
    }

}
