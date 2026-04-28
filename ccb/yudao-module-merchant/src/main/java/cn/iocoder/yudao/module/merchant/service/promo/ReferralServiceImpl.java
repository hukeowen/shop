package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserReferralDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserReferralMapper;
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
 * 推荐链服务实现。
 *
 * - bindParent 不在事务内：单条 insert 即可；并发由 (tenant_id, user_id) 唯一键兜底，重复插入会触发约束异常被 catch 静默
 * - getAncestors 用 BFS 风格逐层向上拉，每跳一次查一次库；正常推荐链深度 ≤ 10 层，性能足够
 */
@Service
@Slf4j
public class ReferralServiceImpl implements ReferralService {

    @Resource
    private ShopUserReferralMapper referralMapper;

    @Override
    public boolean bindParent(Long userId, Long parentUserId, Long orderId) {
        if (userId == null || parentUserId == null) {
            return false;
        }
        if (Objects.equals(userId, parentUserId)) {
            log.warn("[bindParent] 用户 {} 不能将自己绑为上级", userId);
            return false;
        }
        // 已绑定（无论上级是谁），不再覆盖 — 终生绑定
        ShopUserReferralDO existing = referralMapper.selectByUserId(userId);
        if (existing != null) {
            return false;
        }
        // 检查 parent 不能是 userId 的下级（防环）：若 parent 的祖先链中包含 userId，则拒绝
        if (parentUserId > 0 && hasAncestor(parentUserId, userId, 50)) {
            log.warn("[bindParent] user {} 拟绑上级 {} 会形成环，已拒绝", userId, parentUserId);
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
            return true;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发场景：另一个事务先插入，本次视为已绑定
            return false;
        }
    }

    @Override
    public Long getDirectParent(Long userId) {
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
     * 防环检查：从 startUserId 向上找 maxDepth 层，是否能找到 candidate。
     * 用于 bindParent 时拒绝"把上级绑成下级"的环。
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
