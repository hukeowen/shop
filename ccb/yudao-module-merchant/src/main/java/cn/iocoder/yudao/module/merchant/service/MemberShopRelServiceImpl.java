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
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.MemberOrderBalanceLogMapper memberOrderBalanceLogMapper;

    @Override
    @TenantIgnore
    public MemberShopRelDO getOrCreate(Long userId, Long tenantId) {
        return getOrCreateWithReferrer(userId, tenantId, null);
    }

    @Override
    @TenantIgnore
    public MemberShopRelDO getOrCreateWithReferrer(Long userId, Long tenantId, Long referrerUserId) {
        MemberShopRelDO rel = memberShopRelMapper.selectByUserIdAndTenantId(userId, tenantId);
        if (rel != null) {
            // 已存在：v6 严格语义 — 不再覆盖 referrer_user_id，原值即"首次进店时的归属"
            return rel;
        }
        LocalDateTime now = LocalDateTime.now();
        // 防自绑：用户不能把自己绑成自己的上级
        Long safeReferrer = (referrerUserId != null
                && referrerUserId > 0
                && !referrerUserId.equals(userId)) ? referrerUserId : null;
        MemberShopRelDO newRel = MemberShopRelDO.builder()
                .userId(userId)
                .tenantId(tenantId)
                .balance(0)
                .points(0)
                .firstVisitAt(now)
                .lastVisitAt(now)
                .referrerUserId(safeReferrer)
                .build();
        try {
            memberShopRelMapper.insert(newRel);
            return newRel;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发：另一个事务先插入，回查返回（视为"已存在"，不再补 referrer）
            return memberShopRelMapper.selectByUserIdAndTenantId(userId, tenantId);
        }
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
    public java.util.List<MemberShopRelDO> listByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return java.util.Collections.emptyList();
        }
        return memberShopRelMapper.selectListByUserId(userId);
    }

    @Override
    @TenantIgnore
    public void updateLastVisitAt(Long userId, Long tenantId) {
        memberShopRelMapper.updateLastVisitAt(userId, tenantId, LocalDateTime.now());
    }

    @Override
    @TenantIgnore
    public void setFavorite(Long userId, Long tenantId, boolean favorite) {
        MemberShopRelDO rel = memberShopRelMapper.selectByUserIdAndTenantId(userId, tenantId);
        if (rel == null) return;
        MemberShopRelDO upd = new MemberShopRelDO();
        upd.setId(rel.getId());
        upd.setFavorite(favorite);
        memberShopRelMapper.updateById(upd);
    }

    @Override
    @TenantIgnore
    public int deductBalance(Long userId, Long tenantId, int amount) {
        return memberShopRelMapper.deductBalance(userId, tenantId, amount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @TenantIgnore
    public boolean deductBalanceForOrder(Long userId, Long tenantId, Long orderId, int amount) {
        if (amount <= 0) {
            return false;
        }
        // 1. 已抵扣过则直接返回（幂等）
        cn.iocoder.yudao.module.merchant.dal.dataobject.MemberOrderBalanceLogDO existing =
                memberOrderBalanceLogMapper.selectByUserTenantOrder(userId, tenantId, orderId);
        if (existing != null) {
            return false;
        }
        // 2. 原子扣减余额（WHERE balance >= amount）
        int affected = memberShopRelMapper.deductBalance(userId, tenantId, amount);
        if (affected == 0) {
            throw exception0(1_031_001_004, "余额不足");
        }
        // 3. 写日志（UNIQUE(user,tenant,order) 配合 @Transactional 抗并发）
        try {
            memberOrderBalanceLogMapper.insert(
                    cn.iocoder.yudao.module.merchant.dal.dataobject.MemberOrderBalanceLogDO.builder()
                            .userId(userId)
                            .tenantId(tenantId)
                            .orderId(orderId)
                            .amount(amount)
                            .build());
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发场景：另一事务先插入了相同 (user,tenant,order)；本事务回滚（@Transactional），余额恢复
            throw exception0(1_031_001_005, "订单已抵扣过，请刷新后重试");
        }
        return true;
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
