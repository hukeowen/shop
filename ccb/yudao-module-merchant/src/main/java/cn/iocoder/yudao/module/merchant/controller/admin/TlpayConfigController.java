package cn.iocoder.yudao.module.merchant.controller.admin;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.tlpay.TlpayConfigPageReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.tlpay.TlpayConfigRespVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.tlpay.TlpayConfigSaveReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 通联支付配置（每商户独立直清模式）
 *
 * <p>每个商户在通联自己开户拿 cusId + 平台 RSA 密钥对：</p>
 * <ul>
 *   <li>tl_mch_id = 通联 cusId</li>
 *   <li>tl_app_id = 通联 appId</li>
 *   <li>tl_rsa_private_key = 商户 RSA 私钥（请求签名）</li>
 *   <li>tl_rsa_public_key = 通联 RSA 公钥（回调验签）</li>
 *   <li>tl_notify_url = 异步回调地址（空 = 走全局默认）</li>
 * </ul>
 *
 * <p>私钥安全：列表 / 详情接口不返明文，仅返 *Configured 布尔；编辑时
 * 前端留空字段不更新（保留旧值），传 __CLEAR__ 才清空。</p>
 */
@Tag(name = "管理后台 - 通联支付配置")
@RestController
@RequestMapping("/merchant/tlpay")
@Validated
public class TlpayConfigController {

    /** 哨兵字符串：编辑时传该值表示主动清空字段 */
    private static final String SENTINEL_CLEAR = "__CLEAR__";

    @Resource
    private ShopInfoMapper shopInfoMapper;

    @GetMapping("/page")
    @Operation(summary = "分页查询商户通联配置（私钥脱敏）")
    @PreAuthorize("@ss.hasPermission('merchant:tlpay:query')")
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore
    public CommonResult<PageResult<TlpayConfigRespVO>> getPage(@Valid TlpayConfigPageReqVO reqVO) {
        LambdaQueryWrapper<ShopInfoDO> wrapper = new LambdaQueryWrapper<ShopInfoDO>()
                .orderByDesc(ShopInfoDO::getId);
        if (reqVO.getShopName() != null && !reqVO.getShopName().isEmpty()) {
            wrapper.like(ShopInfoDO::getShopName, reqVO.getShopName());
        }
        if (reqVO.getEnabled() != null) {
            wrapper.eq(ShopInfoDO::getTlEnabled, reqVO.getEnabled());
        }
        long total = shopInfoMapper.selectCount(wrapper);
        int offset = (reqVO.getPageNo() - 1) * reqVO.getPageSize();
        wrapper.last("LIMIT " + reqVO.getPageSize() + " OFFSET " + offset);
        List<ShopInfoDO> list = shopInfoMapper.selectList(wrapper);

        List<TlpayConfigRespVO> respList = new ArrayList<>(list.size());
        for (ShopInfoDO shop : list) {
            respList.add(toResp(shop));
        }
        return success(new PageResult<>(respList, total));
    }

    @GetMapping("/get")
    @Operation(summary = "查单条配置（私钥脱敏）")
    @PreAuthorize("@ss.hasPermission('merchant:tlpay:query')")
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore
    public CommonResult<TlpayConfigRespVO> getOne(@RequestParam("id") Long id) {
        ShopInfoDO shop = shopInfoMapper.selectById(id);
        if (shop == null) {
            throw ServiceExceptionUtil.exception0(404, "店铺不存在");
        }
        return success(toResp(shop));
    }

    @PutMapping("/save")
    @Operation(summary = "保存某商户的通联支付配置")
    @PreAuthorize("@ss.hasPermission('merchant:tlpay:edit')")
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore
    public CommonResult<Boolean> save(@Valid @RequestBody TlpayConfigSaveReqVO reqVO) {
        ShopInfoDO shop = shopInfoMapper.selectById(reqVO.getId());
        if (shop == null) {
            throw ServiceExceptionUtil.exception0(404, "店铺不存在");
        }
        // 平铺字段更新（避免覆盖其它字段）
        ShopInfoDO patch = new ShopInfoDO();
        patch.setId(reqVO.getId());
        if (reqVO.getTlEnabled() != null) patch.setTlEnabled(reqVO.getTlEnabled());
        if (reqVO.getTlMchId() != null) patch.setTlMchId(reqVO.getTlMchId().trim());
        if (reqVO.getTlAppId() != null) patch.setTlAppId(reqVO.getTlAppId().trim());
        if (reqVO.getTlSignType() != null && !reqVO.getTlSignType().isEmpty()) {
            patch.setTlSignType(reqVO.getTlSignType().trim());
        }
        if (reqVO.getTlNotifyUrl() != null) patch.setTlNotifyUrl(reqVO.getTlNotifyUrl().trim());
        // 私钥 / 公钥：空串保留不动；__CLEAR__ 清空；其它则覆盖
        applyKey(reqVO.getTlRsaPrivateKey(), patch::setTlRsaPrivateKey);
        applyKey(reqVO.getTlRsaPublicKey(), patch::setTlRsaPublicKey);
        shopInfoMapper.updateById(patch);
        return success(true);
    }

    private void applyKey(String input, java.util.function.Consumer<String> setter) {
        if (input == null) return;
        if (input.isEmpty()) return;
        if (SENTINEL_CLEAR.equals(input.trim())) {
            setter.accept("");
            return;
        }
        setter.accept(input.trim());
    }

    private TlpayConfigRespVO toResp(ShopInfoDO shop) {
        TlpayConfigRespVO vo = new TlpayConfigRespVO();
        vo.setId(shop.getId());
        vo.setTenantId(shop.getTenantId());
        vo.setShopName(shop.getShopName());
        vo.setTlEnabled(shop.getTlEnabled());
        vo.setTlMchId(shop.getTlMchId());
        vo.setTlAppId(shop.getTlAppId());
        vo.setTlSignType(shop.getTlSignType());
        vo.setTlNotifyUrl(shop.getTlNotifyUrl());
        vo.setPrivateKeyConfigured(shop.getTlRsaPrivateKey() != null && !shop.getTlRsaPrivateKey().isEmpty());
        vo.setPublicKeyConfigured(shop.getTlRsaPublicKey() != null && !shop.getTlRsaPublicKey().isEmpty());
        return vo;
    }

}
