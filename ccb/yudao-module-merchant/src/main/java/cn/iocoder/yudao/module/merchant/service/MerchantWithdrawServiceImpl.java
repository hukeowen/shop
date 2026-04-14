package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.withdraw.MerchantWithdrawAuditReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.withdraw.MerchantWithdrawPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantWithdrawApplyDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantWithdrawApplyMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

/**
 * 商户提现申请 Service 实现
 */
@Service
@Validated
@Slf4j
public class MerchantWithdrawServiceImpl implements MerchantWithdrawService {

    @Resource
    private MerchantWithdrawApplyMapper merchantWithdrawApplyMapper;
    @Resource
    private ShopInfoMapper shopInfoMapper;

    @Override
    public PageResult<MerchantWithdrawApplyDO> getWithdrawPage(MerchantWithdrawPageReqVO pageReqVO) {
        return merchantWithdrawApplyMapper.selectPage(pageReqVO);
    }

    @Override
    public MerchantWithdrawApplyDO getWithdraw(Long id) {
        return merchantWithdrawApplyMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditWithdraw(MerchantWithdrawAuditReqVO auditReqVO, Long auditorId) {
        MerchantWithdrawApplyDO apply = merchantWithdrawApplyMapper.selectById(auditReqVO.getId());
        if (apply == null) {
            throw exception0(1_020_003_000, "提现申请不存在");
        }
        if (apply.getStatus() != 0) {
            throw exception0(1_020_003_001, "申请已审核，请勿重复操作");
        }

        MerchantWithdrawApplyDO update = new MerchantWithdrawApplyDO();
        update.setId(apply.getId());
        update.setAuditorId(auditorId);
        update.setAuditTime(LocalDateTime.now());

        if (Boolean.TRUE.equals(auditReqVO.getApproved())) {
            if (auditReqVO.getVoucherUrl() == null || auditReqVO.getVoucherUrl().isEmpty()) {
                throw exception0(1_020_003_002, "审核通过时必须上传转账凭证");
            }
            update.setStatus(1); // 已转账
            update.setVoucherUrl(auditReqVO.getVoucherUrl());
            log.info("[auditWithdraw] 商户 tenantId={} 提现申请 id={} 审核通过，凭证={}",
                    apply.getTenantId(), apply.getId(), auditReqVO.getVoucherUrl());
        } else {
            update.setStatus(2); // 驳回
            update.setRejectReason(auditReqVO.getRejectReason());
            // 退还冻结余额
            ShopInfoDO shopInfo = shopInfoMapper.selectByTenantId(apply.getTenantId());
            if (shopInfo != null) {
                ShopInfoDO balanceUpdate = new ShopInfoDO();
                balanceUpdate.setId(shopInfo.getId());
                balanceUpdate.setBalance(shopInfo.getBalance() + apply.getAmount());
                shopInfoMapper.updateById(balanceUpdate);
                log.info("[auditWithdraw] 驳回提现，退还余额 {} 分给 tenantId={}", apply.getAmount(), apply.getTenantId());
            }
            log.info("[auditWithdraw] 商户 tenantId={} 提现申请 id={} 已驳回，原因={}",
                    apply.getTenantId(), apply.getId(), auditReqVO.getRejectReason());
        }
        merchantWithdrawApplyMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createWithdraw(MerchantWithdrawApplyDO apply) {
        // 余额校验
        ShopInfoDO shopInfo = shopInfoMapper.selectByTenantId(apply.getTenantId());
        if (shopInfo == null) {
            throw exception0(1_020_003_003, "店铺信息不存在");
        }
        if (shopInfo.getBalance() == null || shopInfo.getBalance() < apply.getAmount()) {
            throw exception0(1_020_003_004, "余额不足，当前余额：" + (shopInfo.getBalance() != null ? shopInfo.getBalance() : 0) + "分");
        }
        // 冻结余额（扣减）
        ShopInfoDO update = new ShopInfoDO();
        update.setId(shopInfo.getId());
        update.setBalance(shopInfo.getBalance() - apply.getAmount());
        shopInfoMapper.updateById(update);

        apply.setStatus(0); // 待审核
        merchantWithdrawApplyMapper.insert(apply);
        log.info("[createWithdraw] 商户 tenantId={} 提交提现申请 amount={}分，余额从{}扣至{}",
                apply.getTenantId(), apply.getAmount(), shopInfo.getBalance(), shopInfo.getBalance() - apply.getAmount());
    }

}
