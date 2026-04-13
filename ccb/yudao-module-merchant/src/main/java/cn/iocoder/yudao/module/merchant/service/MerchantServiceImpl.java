package cn.iocoder.yudao.module.merchant.service;

import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.MerchantAuditReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.MerchantCreateReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.MerchantPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantMapper;
import cn.iocoder.yudao.module.merchant.enums.MerchantStatusEnum;
import cn.binarywang.wx.miniapp.api.WxMaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

/**
 * 商户 Service 实现类
 */
@Service
@Validated
@Slf4j
public class MerchantServiceImpl implements MerchantService {

    @Resource
    private MerchantMapper merchantMapper;

    @Autowired(required = false)
    private WxMaService wxMaService;

    @Value("${merchant.qrcode.page:pages/shop/index}")
    private String qrCodePage;

    @Value("${merchant.qrcode.upload-path:/tmp/qrcode/}")
    private String qrCodeUploadPath;

    @Override
    public Long createMerchant(MerchantCreateReqVO createReqVO, Long userId) {
        // 校验是否已申请
        MerchantDO existMerchant = merchantMapper.selectByUserId(userId);
        if (existMerchant != null) {
            throw exception0(1_020_001_000, "您已提交过商户入驻申请");
        }
        // 创建商户
        MerchantDO merchant = MerchantDO.builder()
                .name(createReqVO.getName())
                .logo(createReqVO.getLogo())
                .contactName(createReqVO.getContactName())
                .contactPhone(createReqVO.getContactPhone())
                .licenseNo(createReqVO.getLicenseNo())
                .licenseUrl(createReqVO.getLicenseUrl())
                .legalPersonName(createReqVO.getLegalPersonName())
                .legalPersonIdCard(createReqVO.getLegalPersonIdCard())
                .legalPersonIdCardFrontUrl(createReqVO.getLegalPersonIdCardFrontUrl())
                .legalPersonIdCardBackUrl(createReqVO.getLegalPersonIdCardBackUrl())
                .bankAccountName(createReqVO.getBankAccountName())
                .bankAccountNo(createReqVO.getBankAccountNo())
                .bankName(createReqVO.getBankName())
                .businessCategory(createReqVO.getBusinessCategory())
                .status(MerchantStatusEnum.PENDING.getStatus())
                .userId(userId)
                .build();
        merchantMapper.insert(merchant);
        return merchant.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditMerchant(MerchantAuditReqVO auditReqVO) {
        MerchantDO merchant = merchantMapper.selectById(auditReqVO.getId());
        if (merchant == null) {
            throw exception0(1_020_001_001, "商户不存在");
        }
        MerchantDO updateObj = new MerchantDO();
        updateObj.setId(merchant.getId());
        updateObj.setAuditTime(LocalDateTime.now());
        if (Boolean.TRUE.equals(auditReqVO.getApproved())) {
            updateObj.setStatus(MerchantStatusEnum.APPROVED.getStatus());
            // 审核通过后自动生成小程序码
            try {
                String qrCodeUrl = generateMiniAppQrCode(merchant.getId());
                updateObj.setMiniAppQrCodeUrl(qrCodeUrl);
            } catch (Exception e) {
                log.warn("[auditMerchant] 自动生成小程序码失败，可稍后手动生成", e);
            }
        } else {
            updateObj.setStatus(MerchantStatusEnum.REJECTED.getStatus());
            updateObj.setRejectReason(auditReqVO.getRejectReason());
        }
        merchantMapper.updateById(updateObj);
    }

    @Override
    public MerchantDO getMerchant(Long id) {
        return merchantMapper.selectById(id);
    }

    @Override
    public MerchantDO getMerchantByUserId(Long userId) {
        return merchantMapper.selectByUserId(userId);
    }

    @Override
    public PageResult<MerchantDO> getMerchantPage(MerchantPageReqVO pageReqVO) {
        return merchantMapper.selectPage(pageReqVO);
    }

    @Override
    public void submitWxPayApplyment(Long merchantId) {
        MerchantDO merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw exception0(1_020_001_001, "商户不存在");
        }
        if (!MerchantStatusEnum.APPROVED.getStatus().equals(merchant.getStatus())) {
            throw exception0(1_020_001_002, "商户未审核通过，无法提交进件");
        }

        log.info("[submitWxPayApplyment] 商户({}) 提交微信支付进件", merchantId);

        // 调用微信支付服务商 API 提交特约商户进件
        // POST https://api.mch.weixin.qq.com/v3/applyment4sub/applyment/
        // 实际项目中需要使用 wechatpay-java SDK 调用
        // 这里构建请求体展示完整参数
        /*
         * 请求参数说明：
         * business_code: 业务申请编号（唯一）
         * contact_info: 超级管理员信息
         *   - contact_name: 联系人姓名（加密）
         *   - mobile_phone: 联系人手机号（加密）
         * subject_info: 主体资料
         *   - subject_type: 主体类型（SUBJECT_TYPE_INDIVIDUAL=个体户, SUBJECT_TYPE_ENTERPRISE=企业）
         *   - business_license_info: 营业执照信息
         *     - license_copy: 营业执照照片
         *     - license_number: 注册号/统一社会信用代码
         *     - merchant_name: 商户名称
         *     - legal_representative: 法人姓名
         *   - identity_info: 经营者/法人身份证信息
         *     - id_card_copy: 身份证正面照片
         *     - id_card_national: 身份证反面照片
         *     - id_card_name: 身份证姓名（加密）
         *     - id_card_number: 身份证号码（加密）
         * business_info: 经营资料
         *   - merchant_shortname: 商户简称
         *   - service_phone: 客服电话
         * settlement_info: 结算规则
         *   - settlement_id: 结算规则ID
         *   - qualification_type: 资质类型
         * bank_account_info: 结算银行账户
         *   - bank_account_type: 账户类型
         *   - account_name: 开户名称（加密）
         *   - account_bank: 开户银行
         *   - account_number: 银行账号（加密）
         */

        String applymentId = "applyment_" + IdUtil.fastSimpleUUID();

        // 更新进件状态
        MerchantDO updateObj = new MerchantDO();
        updateObj.setId(merchantId);
        updateObj.setWxApplymentId(applymentId);
        updateObj.setWxApplymentStatus("APPLYMENT_STATE_AUDITING");
        merchantMapper.updateById(updateObj);

        log.info("[submitWxPayApplyment] 进件已提交, applymentId: {}", applymentId);
    }

    @Override
    public String generateMiniAppQrCode(Long merchantId) {
        log.info("[generateMiniAppQrCode] 商户({}) 生成小程序码", merchantId);

        if (wxMaService == null) {
            log.warn("[generateMiniAppQrCode] WxMaService未配置，跳过小程序码生成");
            return null;
        }

        try {
            // 使用 wxacode.getUnlimited 接口生成不限数量的小程序码
            // scene 参数最大32个字符
            String scene = "mid=" + merchantId;

            // 生成小程序码（二进制数据）
            byte[] qrCodeBytes = wxMaService.getQrcodeService().createWxaCodeUnlimitBytes(
                    scene,           // scene 参数
                    qrCodePage,      // 小程序页面路径
                    false,           // 是否需要透明底色
                    "release",       // 要打开的小程序版本
                    430,             // 二维码宽度
                    true,            // 自动配置线条颜色
                    null,            // 线条颜色
                    false            // 是否开启 Hyaline（透明底）
            );

            // 保存到文件并上传
            // 实际项目中应上传到 OSS（通过 yudao-module-infra 的文件服务）
            File dir = new File(qrCodeUploadPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String fileName = "merchant_" + merchantId + "_" + System.currentTimeMillis() + ".png";
            File file = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(qrCodeBytes);
            }

            String qrCodeUrl = "/qrcode/" + fileName;
            log.info("[generateMiniAppQrCode] 小程序码生成成功: {}", qrCodeUrl);

            // 更新商户记录
            MerchantDO updateObj = new MerchantDO();
            updateObj.setId(merchantId);
            updateObj.setMiniAppQrCodeUrl(qrCodeUrl);
            merchantMapper.updateById(updateObj);

            return qrCodeUrl;
        } catch (Exception e) {
            log.error("[generateMiniAppQrCode] 生成小程序码失败", e);
            throw new RuntimeException("生成小程序码失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void disableMerchant(Long merchantId) {
        MerchantDO merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw exception0(1_020_001_001, "商户不存在");
        }
        MerchantDO updateObj = new MerchantDO();
        updateObj.setId(merchantId);
        updateObj.setStatus(MerchantStatusEnum.DISABLED.getStatus());
        merchantMapper.updateById(updateObj);
    }

    @Override
    public void enableMerchant(Long merchantId) {
        MerchantDO merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw exception0(1_020_001_001, "商户不存在");
        }
        MerchantDO updateObj = new MerchantDO();
        updateObj.setId(merchantId);
        updateObj.setStatus(MerchantStatusEnum.APPROVED.getStatus());
        merchantMapper.updateById(updateObj);
    }

}
