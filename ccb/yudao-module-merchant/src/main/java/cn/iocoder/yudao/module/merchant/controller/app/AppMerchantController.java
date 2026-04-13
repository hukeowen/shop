package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.MerchantCreateReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.MerchantRespVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - 商户入驻")
@RestController
@RequestMapping("/merchant/app")
@Validated
public class AppMerchantController {

    @Resource
    private MerchantService merchantService;

    @PostMapping("/apply")
    @Operation(summary = "提交入驻申请")
    public CommonResult<Long> applyMerchant(@Valid @RequestBody MerchantCreateReqVO createReqVO) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(merchantService.createMerchant(createReqVO, userId));
    }

    @GetMapping("/get")
    @Operation(summary = "获取商户信息（公开）")
    public CommonResult<MerchantRespVO> getMerchantById(@RequestParam("id") Long id) {
        MerchantDO merchant = merchantService.getMerchant(id);
        if (merchant == null) {
            return success(null);
        }
        MerchantRespVO respVO = new MerchantRespVO();
        respVO.setId(merchant.getId());
        respVO.setName(merchant.getName());
        respVO.setLogo(merchant.getLogo());
        respVO.setContactName(merchant.getContactName());
        respVO.setContactPhone(merchant.getContactPhone());
        respVO.setStatus(merchant.getStatus());
        respVO.setMiniAppQrCodeUrl(merchant.getMiniAppQrCodeUrl());
        respVO.setCreateTime(merchant.getCreateTime());
        return success(respVO);
    }

    @GetMapping("/my")
    @Operation(summary = "获取我的商户信息")
    public CommonResult<MerchantRespVO> getMyMerchant() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        MerchantDO merchant = merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            return success(null);
        }
        MerchantRespVO respVO = new MerchantRespVO();
        respVO.setId(merchant.getId());
        respVO.setName(merchant.getName());
        respVO.setLogo(merchant.getLogo());
        respVO.setContactName(merchant.getContactName());
        respVO.setContactPhone(merchant.getContactPhone());
        respVO.setStatus(merchant.getStatus());
        respVO.setWxSubMchId(merchant.getWxSubMchId());
        respVO.setMiniAppQrCodeUrl(merchant.getMiniAppQrCodeUrl());
        respVO.setDouyinBound(merchant.getDouyinAccessToken() != null);
        respVO.setCreateTime(merchant.getCreateTime());
        return success(respVO);
    }

}
