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
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.starter.annotation.LogRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.merchant.enums.MerchantLogRecordConstants.*;

/**
 * 管理后台 - 通联支付配置（每商户独立直清模式）
 *
 * <p>每个商户在通联自己开户拿 cusId + 平台 RSA 密钥对：</p>
 * <ul>
 *   <li>tl_mch_id = 通联 cusId</li>
 *   <li>tl_app_id = 通联 appId</li>
 *   <li>tl_rsa_private_key = 商户 RSA 私钥（请求签名；AES 加密存储）</li>
 *   <li>tl_rsa_public_key = 通联 RSA 公钥（回调验签；AES 加密存储）</li>
 *   <li>tl_notify_url = 异步回调地址（空 = 走全局默认）</li>
 * </ul>
 *
 * <p>安全：</p>
 * <ul>
 *   <li>私钥 / 公钥 AES 落库（mybatis-plus.encryptor.password 全局密钥）</li>
 *   <li>列表 / 详情接口不返明文，仅返 *Configured 布尔</li>
 *   <li>编辑 save 留空 = 保留不动；__CLEAR__ 主动清空；PEM 必须解析通过</li>
 *   <li>save 全程 @LogRecord 落审计</li>
 * </ul>
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
    @LogRecord(type = TLPAY_CONFIG_TYPE, subType = TLPAY_CONFIG_SAVE_SUB_TYPE,
            bizNo = "{{#reqVO.id}}", success = TLPAY_CONFIG_SAVE_SUCCESS)
    public CommonResult<Boolean> save(@Valid @RequestBody TlpayConfigSaveReqVO reqVO) {
        ShopInfoDO shop = shopInfoMapper.selectById(reqVO.getId());
        if (shop == null) {
            throw ServiceExceptionUtil.exception0(404, "店铺不存在");
        }

        // ============ 启用通联时 cusId 必填（防绕过前端） ============
        Boolean willEnable = reqVO.getTlEnabled() != null ? reqVO.getTlEnabled() : shop.getTlEnabled();
        if (Boolean.TRUE.equals(willEnable)) {
            String mch = reqVO.getTlMchId() != null ? reqVO.getTlMchId().trim() : shop.getTlMchId();
            if (mch == null || mch.isEmpty()) {
                throw ServiceExceptionUtil.exception0(BAD_REQUEST.getCode(), "启用通联时 cusId 必填");
            }
        }

        // ============ PEM 格式校验（投入小，避免到调通联时才发现签名失败） ============
        if (reqVO.getTlRsaPrivateKey() != null && !reqVO.getTlRsaPrivateKey().isEmpty()
                && !SENTINEL_CLEAR.equals(reqVO.getTlRsaPrivateKey().trim())) {
            validateRsaPrivateKey(reqVO.getTlRsaPrivateKey());
        }
        if (reqVO.getTlRsaPublicKey() != null && !reqVO.getTlRsaPublicKey().isEmpty()
                && !SENTINEL_CLEAR.equals(reqVO.getTlRsaPublicKey().trim())) {
            validateRsaPublicKey(reqVO.getTlRsaPublicKey());
        }

        // ============ 平铺字段更新（避免覆盖其它字段） ============
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
        // 同时记下"是否修改"用于审计日志
        boolean privateKeyChanged = applyKey(reqVO.getTlRsaPrivateKey(), patch::setTlRsaPrivateKey);
        boolean publicKeyChanged = applyKey(reqVO.getTlRsaPublicKey(), patch::setTlRsaPublicKey);

        shopInfoMapper.updateById(patch);

        // 推审计上下文（@LogRecord success 表达式使用）
        LogRecordContext.putVariable("shopId", reqVO.getId());
        LogRecordContext.putVariable("shopName", shop.getShopName());
        LogRecordContext.putVariable("tlEnabled", patch.getTlEnabled() != null ? patch.getTlEnabled() : shop.getTlEnabled());
        LogRecordContext.putVariable("tlMchId", patch.getTlMchId() != null ? patch.getTlMchId() : shop.getTlMchId());
        LogRecordContext.putVariable("privateKeyChanged", privateKeyChanged ? "是" : "否");
        LogRecordContext.putVariable("publicKeyChanged", publicKeyChanged ? "是" : "否");

        return success(true);
    }

    /**
     * 应用密钥变更：空串 = 不动（返 false）；__CLEAR__ = 清空（返 true）；其它 = 覆盖（返 true）。
     * 返回是否产生变更，用于审计日志（不记明文）。
     */
    private boolean applyKey(String input, java.util.function.Consumer<String> setter) {
        if (input == null) return false;
        if (input.isEmpty()) return false;
        if (SENTINEL_CLEAR.equals(input.trim())) {
            setter.accept("");
            return true;
        }
        setter.accept(input.trim());
        return true;
    }

    /** PEM 私钥格式校验 — 尝试解析，失败抛 BAD_REQUEST。 */
    private void validateRsaPrivateKey(String pem) {
        try {
            byte[] der = pemToDer(pem);
            KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw ServiceExceptionUtil.exception0(BAD_REQUEST.getCode(),
                    "商户 RSA 私钥格式错误，必须是 PKCS#8 PEM：" + e.getMessage());
        }
    }

    /** PEM 公钥格式校验 — 尝试解析，失败抛 BAD_REQUEST。 */
    private void validateRsaPublicKey(String pem) {
        try {
            byte[] der = pemToDer(pem);
            PublicKey unused = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw ServiceExceptionUtil.exception0(BAD_REQUEST.getCode(),
                    "通联 RSA 公钥格式错误，必须是 X.509 PEM：" + e.getMessage());
        }
    }

    /** 去掉 -----BEGIN/-----END 头尾和所有空白，剩下的 Base64 解码成 DER。 */
    private byte[] pemToDer(String pem) {
        String cleaned = pem
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s+", "");
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("PEM 内容为空");
        }
        return Base64.getDecoder().decode(cleaned);
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
