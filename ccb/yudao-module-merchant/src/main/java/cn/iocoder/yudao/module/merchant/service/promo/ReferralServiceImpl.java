package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MemberShopRelDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserReferralDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopQueuePositionMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserReferralMapper;
import cn.iocoder.yudao.module.merchant.service.MemberShopRelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 推荐链服务实现 — **per-tenant 终生绑定 + 上级资格校验**。
 *
 * <p>核心语义（用户原话）：
 * <ul>
 *   <li>每个店铺独立：同一用户在不同店可有不同上级（也可同一上级）</li>
 *   <li>终生绑定：B 在 X 店一旦绑过上级 A，之后 D 再推 X 店给 B 不会改</li>
 *   <li>上级资格：A 必须在 X 店买过「推 N 反 1」商品（shop_queue_position 存在）才能成为 X 店的上级</li>
 *   <li>首次进店：B 在 X 店没有任何关系才能绑（首单 / 被推 / 自然访问任一已发生都算"有关系"）</li>
 * </ul>
 *
 * <p>实现要点：
 * <ul>
 *   <li>所有查询/写入均按 <strong>当前 TenantContextHolder.tenantId</strong> 进行；不再用 executeIgnore 跨租户</li>
 *   <li>调用方必须保证调用 bindParent 时已切到目标店铺 tenant（前端通过 tenant-id header 控制）</li>
 *   <li>UNIQUE(tenant_id, user_id) 数据库唯一键兜底并发</li>
 * </ul>
 */
@Service
@Slf4j
public class ReferralServiceImpl implements ReferralService {

    @Resource
    private ShopUserReferralMapper referralMapper;
    @Resource
    private MemberShopRelService memberShopRelService;
    @Resource
    private ShopQueuePositionMapper queuePositionMapper;

    @Override
    public boolean bindParent(Long userId, Long parentUserId, Long orderId) {
        if (userId == null || parentUserId == null) {
            return false;
        }
        if (Objects.equals(userId, parentUserId)) {
            log.warn("[bindParent] 用户 {} 不能将自己绑为上级", userId);
            return false;
        }
        Long currentTenantId = TenantContextHolder.getTenantId();
        if (currentTenantId == null || currentTenantId <= 0) {
            log.warn("[bindParent] 拒绝：tenant 上下文为空 / 0，无法 per-tenant 绑定（userId={} parent={})",
                    userId, parentUserId);
            return false;
        }
        // per-tenant 查重：仅本店有无绑定记录
        ShopUserReferralDO existing = referralMapper.selectByUserId(userId);
        if (existing != null) {
            log.info("[bindParent] tenant={} user={} 已绑 parent={}，本次拟绑 parent={} 忽略（per-tenant 终生）",
                    currentTenantId, userId, existing.getParentUserId(), parentUserId);
            return false;
        }
        // ===== 上级资格校验：parent 必须在当前 tenant 买过「推 N 反 1」商品 (任一 SPU 有 shop_queue_position) =====
        boolean parentActivated = queuePositionMapper.existsByUserId(parentUserId);
        if (!parentActivated) {
            log.info("[bindParent] tenant={} 上级 {} 未在本店激活（无 shop_queue_position），拒绝绑定 user={}",
                    currentTenantId, parentUserId, userId);
            return false;
        }
        // ===== 首次进店校验：user 在本店没有过任何关系 =====
        // 用户原话："用户之前自己点过这个店铺，那就不算拉新了"
        // member_shop_rel 存在 → 之前已访问 / 下单 → 不再补绑：
        //   · referrer = parent → 已绑过同一上级，幂等成功
        //   · referrer != parent（含 NULL = 自己点的）→ 拒绝
        MemberShopRelDO existingRel = memberShopRelService.getByUserAndTenant(userId, currentTenantId);
        if (existingRel != null
                && !Objects.equals(existingRel.getReferrerUserId(), parentUserId)) {
            log.info("[bindParent] tenant={} user={} 已有 rel(referrer={}) 不为 parent={}，拒绝补绑（已不算拉新）",
                    currentTenantId, userId, existingRel.getReferrerUserId(), parentUserId);
            return false;
        }
        // 防环
        if (parentUserId > 0 && hasAncestor(parentUserId, userId, 50)) {
            log.warn("[bindParent] user {} 拟绑上级 {} 在 tenant={} 会形成环，已拒绝",
                    userId, parentUserId, currentTenantId);
            return false;
        }
        ShopUserReferralDO record = ShopUserReferralDO.builder()
                .userId(userId)
                .parentUserId(parentUserId)
                .boundAt(LocalDateTime.now())
                .boundOrderId(orderId)
                .build();
        try {
            referralMapper.insert(record);
            // 同步落 member_shop_rel.referrer_user_id（首次入店时顺手建 + 绑 referrer）
            if (existingRel == null) {
                memberShopRelService.getOrCreateWithReferrer(userId, currentTenantId, parentUserId);
            }
            log.info("[bindParent] ✓ tenant={} user={} parent={} 绑定成功",
                    currentTenantId, userId, parentUserId);
            return true;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发场景：另一个事务先插入，本次视为已绑定
            return false;
        }
    }

    @Override
    public Long getDirectParent(Long userId) {
        // per-tenant 读：当前 TenantContextHolder 上下文内才有上级；
        // 跨店访问时 caller 须切到目标店 tenant 再调（trade afterPayOrder 等已在订单 tenant 内）。
        ShopUserReferralDO record = referralMapper.selectByUserId(userId);
        if (record == null || record.getParentUserId() == null) {
            return 0L;
        }
        return record.getParentUserId();
    }

    @Override
    public List<Long> getAncestors(Long userId, int maxDepth) {
        List<Long> chain = new ArrayList<>();
        if (userId == null) {
            return chain;
        }
        Set<Long> visited = new HashSet<>();
        visited.add(userId);
        Long current = userId;
        int depth = 0;
        int cap = maxDepth > 0 ? maxDepth : 50;
        while (depth < cap) {
            Long parent = getDirectParent(current);
            if (parent == null || parent <= 0) {
                break;
            }
            if (visited.contains(parent)) {
                log.warn("[getAncestors] 检测到环 user={} parent={}，提前结束", current, parent);
                break;
            }
            chain.add(parent);
            visited.add(parent);
            current = parent;
            depth++;
        }
        return chain;
    }

    @Override
    public boolean isNatural(Long userId) {
        Long parent = getDirectParent(userId);
        return parent == null || parent <= 0;
    }

    @Override
    public int countDirectChildren(Long userId) {
        return referralMapper.selectListByParentUserId(userId).size();
    }

    /**
     * 防环检查：从 startUserId 向上找 maxDepth 层，是否能找到 candidate（per-tenant，在当前 tenant 内查）。
     */
    private boolean hasAncestor(Long startUserId, Long candidate, int maxDepth) {
        Long current = startUserId;
        for (int i = 0; i < maxDepth; i++) {
            Long parent = getDirectParent(current);
            if (parent == null || parent <= 0) {
                return false;
            }
            if (Objects.equals(parent, candidate)) {
                return true;
            }
            current = parent;
        }
        return false;
    }

}
