package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MemberShopRelDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MemberShopRelMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

/**
 * 会员×商户关系 Service 实现
 */
@Service
@Validated
@Slf4j
public class MemberShopRelServiceImpl implements MemberShopRelService {

    @Resource
    private MemberShopRelMapper memberShopRelMapper;

    @Override
    @TenantIgnore
    public MemberShopRelDO getOrCreate(Long userId, Long tenantId) {
        MemberShopRelDO rel = memberShopRelMapper.selectByUserIdAndTenantId(userId, tenantId);
        if (rel != null) {
            return rel;
        }
        LocalDateTime now = LocalDateTime.now();
        MemberShopRelDO newRel = MemberShopRelDO.builder()
                .userId(userId)
                .tenantId(tenantId)
                .balance(0)
                .points(0)
                .firstVisitAt(now)
                .lastVisitAt(now)
                .build();
        memberShopRelMapper.insert(newRel);
        return newRel;
    }

    @Override
    @TenantIgnore
    public void addBalance(Long userId, Long tenantId, int delta) {
        int affected = memberShopRelMapper.incrementBalance(userId, tenantId, delta);
        if (affected == 0) {
            // 记录不存在时先创建再重试
            getOrCreate(userId, tenantId);
            memberShopRelMapper.incrementBalance(userId, tenantId, delta);
        }
    }

    @Override
    @TenantIgnore
    public void addPoints(Long userId, Long tenantId, int delta) {
        int affected = memberShopRelMapper.incrementPoints(userId, tenantId, delta);
        if (affected == 0) {
            getOrCreate(userId, tenantId);
            memberShopRelMapper.incrementPoints(userId, tenantId, delta);
        }
    }

    @Override
    @TenantIgnore
    public void bindReferrer(Long userId, Long tenantId, Long referrerUserId) {
        memberShopRelMapper.bindReferrer(userId, tenantId, referrerUserId);
    }

    @Override
    @TenantIgnore
    public MemberShopRelDO getByUserAndTenant(Long userId, Long tenantId) {
        return memberShopRelMapper.selectByUserIdAndTenantId(userId, tenantId);
    }

    @Override
    @TenantIgnore
    public void updateLastVisitAt(Long userId, Long tenantId) {
        memberShopRelMapper.updateLastVisitAt(userId, tenantId, LocalDateTime.now());
    }

    @Override
    @TenantIgnore
    public int deductBalance(Long userId, Long tenantId, int amount) {
        return memberShopRelMapper.deductBalance(userId, tenantId, amount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @TenantIgnore
    public void balanceToPoints(Long userId, Long tenantId, int amountFen) {
        MemberShopRelDO rel = getOrCreate(userId, tenantId);
        int currentBalance = rel.getBalance() != null ? rel.getBalance() : 0;
        if (currentBalance < amountFen) {
            throw exception0(1_031_001_000, "余额不足，当前余额：" + currentBalance + " 分");
        }
        // 原子扣余额（带充足校验）
        int affected = memberShopRelMapper.incrementBalance(userId, tenantId, -amountFen);
        if (affected == 0) {
            throw exception0(1_031_001_001, "扣减余额失败，请重试");
        }
        // 加积分
        memberShopRelMapper.incrementPoints(userId, tenantId, amountFen);
    }

}
