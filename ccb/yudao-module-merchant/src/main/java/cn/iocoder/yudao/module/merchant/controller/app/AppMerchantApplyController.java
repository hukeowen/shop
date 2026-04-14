package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantApplyReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantApplyDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantApplyMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 用户端 - 商户入驻申请（#39 #40）
 * 无需登录，公开接口
 */
@Tag(name = "用户端 - 商户入驻申请")
@RestController
@RequestMapping("/merchant/apply")
@Validated
public class AppMerchantApplyController {

    @Resource
    private MerchantApplyMapper merchantApplyMapper;

    @PostMapping("/submit")
    @Operation(summary = "提交入驻申请（5步表单合并提交）")
    @PermitAll
    @TenantIgnore
    public CommonResult<Long> submitApply(@Valid @RequestBody AppMerchantApplyReqVO reqVO) {
        // 检查手机号是否已有待审核或已通过的申请
        MerchantApplyDO existing = merchantApplyMapper.selectByMobile(reqVO.getMobile());
        if (existing != null) {
            if (existing.getStatus() == 0) {
                throw exception0(1_020_007_000, "该手机号已有待审核的申请，请耐心等待");
            }
            if (existing.getStatus() == 1) {
                throw exception0(1_020_007_001, "该手机号已入驻成功，请直接登录商户小程序");
            }
            // 状态=2（驳回）可以重新提交，更新旧记录（保留 existing.getId()）
        }

        MerchantApplyDO apply = new MerchantApplyDO();
        if (existing != null && existing.getStatus() == 2) {
            apply.setId(existing.getId());
        }
        // 第1步
        apply.setShopName(reqVO.getShopName());
        apply.setCategoryId(reqVO.getCategoryId() != null ? reqVO.getCategoryId() : 0L);
        apply.setMobile(reqVO.getMobile());
        apply.setReferrerMobile(reqVO.getReferrerMobile());
        // 第2步
        apply.setLicenseUrl(reqVO.getLicenseUrl());
        apply.setIdCardFront(reqVO.getIdCardFront());
        apply.setIdCardBack(reqVO.getIdCardBack());
        // 第3步
        apply.setLongitude(reqVO.getLongitude());
        apply.setLatitude(reqVO.getLatitude());
        apply.setAddress(reqVO.getAddress());
        // 第4步
        apply.setWxMchType(reqVO.getWxMchType() != null ? reqVO.getWxMchType() : 0);
        apply.setWxMchId(reqVO.getWxMchId());
        // 默认状态
        apply.setStatus(0); // 待审核

        if (apply.getId() != null) {
            apply.setRejectReason(null);
            apply.setAuditTime(null);
            apply.setAuditorId(null);
            merchantApplyMapper.updateById(apply);
        } else {
            merchantApplyMapper.insert(apply);
        }
        return success(apply.getId());
    }

    @GetMapping("/status")
    @Operation(summary = "查询入驻申请状态（通过手机号）")
    @Parameter(name = "mobile", description = "手机号", required = true)
    @PermitAll
    @TenantIgnore
    public CommonResult<Map<String, Object>> getApplyStatus(@RequestParam("mobile") String mobile) {
        MerchantApplyDO apply = merchantApplyMapper.selectByMobile(mobile);
        if (apply == null) {
            return success(null);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("status", apply.getStatus());
        result.put("rejectReason", apply.getRejectReason());
        result.put("shopName", apply.getShopName());
        return success(result);
    }

}
