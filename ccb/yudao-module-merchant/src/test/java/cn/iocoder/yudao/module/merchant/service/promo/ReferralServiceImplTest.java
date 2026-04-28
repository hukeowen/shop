package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MemberShopRelDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserReferralDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserReferralMapper;
import cn.iocoder.yudao.module.merchant.service.MemberShopRelService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link ReferralServiceImpl} 测试。
 *
 * 覆盖：
 *   1. bindParent：首次绑定成功；二次绑定保留旧值（first-binding-wins）
 *   2. bindParent：自绑、形成环 → 拒绝
 *   3. getAncestors：正常链路 + 自然用户终止 + 防环
 *   4. isNatural / countDirectChildren
 */
class ReferralServiceImplTest {

    private ShopUserReferralMapper mapper;
    private MemberShopRelService memberShopRelService;
    private ReferralServiceImpl service;
    private final Map<Long, ShopUserReferralDO> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        mapper = mock(ShopUserReferralMapper.class);
        memberShopRelService = mock(MemberShopRelService.class);
        service = new ReferralServiceImpl();
        ReflectionTestUtils.setField(service, "referralMapper", mapper);
        ReflectionTestUtils.setField(service, "memberShopRelService", memberShopRelService);
        store.clear();

        // 用 in-memory map 模拟 selectByUserId
        when(mapper.selectByUserId(any())).thenAnswer(inv -> store.get(inv.<Long>getArgument(0)));
        when(mapper.insert(any(ShopUserReferralDO.class))).thenAnswer(inv -> {
            ShopUserReferralDO r = inv.getArgument(0);
            store.put(r.getUserId(), r);
            return 1;
        });
        // 默认无 tenant 上下文 → ReferralServiceImpl 跳过 v6 严格校验，
        // 让现有 5 个老用例（不显式 setTenantId）保持向后兼容
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void bindParent_succeeds_firstTime() {
        boolean ok = service.bindParent(2L, 1L, 100L);

        assertTrue(ok);
        assertEquals(1L, service.getDirectParent(2L));
    }

    @Test
    void bindParent_keepsFirstBinding_andRejectsRebind() {
        service.bindParent(2L, 1L, 100L);
        boolean second = service.bindParent(2L, 99L, 200L);

        assertFalse(second);
        assertEquals(1L, service.getDirectParent(2L), "二次绑定不应覆盖首次绑定");
    }

    @Test
    void bindParent_rejectsSelfReference() {
        boolean ok = service.bindParent(2L, 2L, 100L);
        assertFalse(ok);
        assertTrue(service.isNatural(2L));
    }

    @Test
    void bindParent_rejectsCycle() {
        // 1 → 2 → 3，再让 1 绑 3 形成 1→3→2→3 环 → 拒绝
        service.bindParent(2L, 1L, 1L);
        service.bindParent(3L, 2L, 2L);
        boolean ok = service.bindParent(1L, 3L, 3L);

        assertFalse(ok, "形成环必须拒绝");
        assertEquals(0L, service.getDirectParent(1L), "1 仍是自然用户");
    }

    @Test
    void getAncestors_walksChainNearestToFarthest() {
        // 4 → 3 → 2 → 1（自然用户）
        service.bindParent(2L, 1L, 1L);
        service.bindParent(3L, 2L, 2L);
        service.bindParent(4L, 3L, 3L);

        List<Long> chain = service.getAncestors(4L, 10);

        assertEquals(Arrays.asList(3L, 2L, 1L), chain);
    }

    @Test
    void getAncestors_terminatesAtNaturalUser() {
        service.bindParent(2L, 1L, 1L);
        // 1 是自然用户

        assertEquals(Collections.singletonList(1L), service.getAncestors(2L, 10));
        assertEquals(Collections.emptyList(), service.getAncestors(1L, 10));
    }

    @Test
    void getAncestors_caps_atMaxDepth() {
        service.bindParent(2L, 1L, 1L);
        service.bindParent(3L, 2L, 2L);
        service.bindParent(4L, 3L, 3L);

        // maxDepth=2 应只返 2 层
        assertEquals(Arrays.asList(3L, 2L), service.getAncestors(4L, 2));
    }

    @Test
    void countDirectChildren_returnsZero_whenNoChildren() {
        when(mapper.selectListByParentUserId(99L)).thenReturn(Collections.emptyList());
        assertEquals(0, service.countDirectChildren(99L));
    }

    @Test
    void countDirectChildren_returnsCount() {
        when(mapper.selectListByParentUserId(1L)).thenReturn(Arrays.asList(
                new ShopUserReferralDO(),
                new ShopUserReferralDO(),
                new ShopUserReferralDO()));
        assertEquals(3, service.countDirectChildren(1L));
    }

    // ========== v6 严格语义：仅"首次进店带 inviter"才绑（依赖 member_shop_rel） ==========

    @Test
    void bindParent_v6Strict_noRel_bindsAndCreatesRel() {
        Long tenantId = 100L;
        TenantContextHolder.setTenantId(tenantId);
        when(memberShopRelService.getByUserAndTenant(eq(2L), eq(tenantId))).thenReturn(null);

        boolean ok = service.bindParent(2L, 1L, 100L);

        assertTrue(ok, "首次进店（rel 不存在）应绑定成功");
        assertEquals(1L, service.getDirectParent(2L));
        // 同时同步建 member_shop_rel
        verify(memberShopRelService).getOrCreateWithReferrer(eq(2L), eq(tenantId), eq(1L));
    }

    @Test
    void bindParent_v6Strict_existingNaturalRel_rejected() {
        // 用户之前自然进过店：rel 已存在但 referrer_user_id 是 null
        Long tenantId = 100L;
        TenantContextHolder.setTenantId(tenantId);
        MemberShopRelDO rel = MemberShopRelDO.builder()
                .userId(2L).tenantId(tenantId).referrerUserId(null).build();
        when(memberShopRelService.getByUserAndTenant(eq(2L), eq(tenantId))).thenReturn(rel);

        boolean ok = service.bindParent(2L, 1L, 100L);

        assertFalse(ok, "之前自然进过店的不能补绑上下级");
        assertNull(store.get(2L), "shop_user_referral 不应被写入");
        verify(memberShopRelService, never()).getOrCreateWithReferrer(any(), any(), any());
    }

    @Test
    void bindParent_v6Strict_existingRelWithDifferentReferrer_rejected() {
        Long tenantId = 100L;
        TenantContextHolder.setTenantId(tenantId);
        MemberShopRelDO rel = MemberShopRelDO.builder()
                .userId(2L).tenantId(tenantId).referrerUserId(99L).build();   // 已绑给 99
        when(memberShopRelService.getByUserAndTenant(eq(2L), eq(tenantId))).thenReturn(rel);

        boolean ok = service.bindParent(2L, 1L, 100L);   // 拟换绑给 1 → 拒

        assertFalse(ok);
        assertNull(store.get(2L));
    }

    @Test
    void bindParent_v6Strict_existingRelWithMatchingReferrer_synced() {
        // 视为 visit 流程已先绑了 rel.referrer=1，bindParent 仅同步写 shop_user_referral
        Long tenantId = 100L;
        TenantContextHolder.setTenantId(tenantId);
        MemberShopRelDO rel = MemberShopRelDO.builder()
                .userId(2L).tenantId(tenantId).referrerUserId(1L).build();
        when(memberShopRelService.getByUserAndTenant(eq(2L), eq(tenantId))).thenReturn(rel);

        boolean ok = service.bindParent(2L, 1L, 100L);

        assertTrue(ok, "rel.referrer 与 parent 一致应通过校验");
        assertEquals(1L, store.get(2L).getParentUserId());
        // 已存在 rel，不再次创建
        verify(memberShopRelService, never()).getOrCreateWithReferrer(any(), any(), any());
    }

    @Test
    void isNatural_returnsTrue_whenUnknownOrZeroParent() {
        // 没记录 → 自然
        assertTrue(service.isNatural(999L));

        // parent = 0 → 自然
        ShopUserReferralDO r = ShopUserReferralDO.builder().userId(7L).parentUserId(0L).build();
        store.put(7L, r);
        assertTrue(service.isNatural(7L));

        // parent > 0 → 非自然
        service.bindParent(8L, 1L, 1L);
        assertFalse(service.isNatural(8L));
    }

}
