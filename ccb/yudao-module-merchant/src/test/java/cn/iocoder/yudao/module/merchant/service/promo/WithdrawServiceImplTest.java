package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoWithdrawDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoWithdrawMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link WithdrawServiceImpl} 测试。
 *
 * 覆盖：
 *   - apply：门槛 / 余额 / 互斥 / 成功
 *   - approve / reject / markPaid 状态机校验
 *   - reject 退还积分
 */
class WithdrawServiceImplTest {

    private ShopPromoWithdrawMapper withdrawMapper;
    private PromoConfigService promoConfigService;
    private PromoPointService promoPointService;
    private WithdrawServiceImpl service;

    private final Map<Long, ShopPromoWithdrawDO> store = new HashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);
    private long balance;
    private final Map<String, Long> awarded = new HashMap<>();   // sourceType → amount

    @BeforeEach
    void setUp() {
        withdrawMapper = mock(ShopPromoWithdrawMapper.class);
        promoConfigService = mock(PromoConfigService.class);
        promoPointService = mock(PromoPointService.class);

        service = new WithdrawServiceImpl();
        ReflectionTestUtils.setField(service, "withdrawMapper", withdrawMapper);
        ReflectionTestUtils.setField(service, "promoConfigService", promoConfigService);
        ReflectionTestUtils.setField(service, "promoPointService", promoPointService);

        store.clear();
        awarded.clear();
        idSeq.set(1);
        balance = 100000L; // 默认 1000 元

        when(withdrawMapper.insert(any(ShopPromoWithdrawDO.class))).thenAnswer(inv -> {
            ShopPromoWithdrawDO r = inv.getArgument(0);
            r.setId(idSeq.getAndIncrement());
            store.put(r.getId(), r);
            return 1;
        });
        when(withdrawMapper.selectById(any())).thenAnswer(inv -> store.get(inv.<Long>getArgument(0)));
        // 原子状态机：仅当当前 status 匹配 expectedFrom 才转
        when(withdrawMapper.transitionStatus(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            String expectedFrom = inv.getArgument(1);
            String newStatus = inv.getArgument(2);
            Long processorId = inv.getArgument(3);
            String remark = inv.getArgument(4);
            ShopPromoWithdrawDO r = store.get(id);
            if (r == null || !expectedFrom.equals(r.getStatus())) {
                return 0;
            }
            r.setStatus(newStatus);
            r.setProcessorId(processorId);
            r.setProcessorRemark(remark);
            return 1;
        });

        when(promoConfigService.getConfig()).thenReturn(PromoConfigDO.builder()
                .withdrawThreshold(10000).build());  // 门槛 100 元

        when(promoPointService.getOrCreateAccount(any())).thenAnswer(inv -> ShopUserStarDO.builder()
                .userId(inv.getArgument(0)).promoPointBalance(balance).build());
        when(promoPointService.deductPromoPoint(any(), anyLong(), eq("WITHDRAW"), any(), any())).thenAnswer(inv -> {
            long amt = inv.getArgument(1);
            balance -= amt;
            awarded.merge("WITHDRAW", amt, Long::sum);
            return true;
        });
        when(promoPointService.addPromoPoint(any(), anyLong(), eq("WITHDRAW_REFUND"), any(), any())).thenAnswer(inv -> {
            long amt = inv.getArgument(1);
            balance += amt;
            awarded.merge("WITHDRAW_REFUND", amt, Long::sum);
            return true;
        });
    }

    @Test
    void apply_succeeds_andDeductsBalance() {
        when(withdrawMapper.existsActiveByUserId(1L)).thenReturn(false);

        ShopPromoWithdrawDO record = service.apply(1L, 50000L);

        assertNotNull(record);
        assertEquals("PENDING", record.getStatus());
        assertEquals(50000L, record.getAmount());
        assertEquals(50000L, awarded.get("WITHDRAW"));
        assertEquals(50000L, balance);
    }

    @Test
    void apply_rejected_belowThreshold() {
        when(withdrawMapper.existsActiveByUserId(1L)).thenReturn(false);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.apply(1L, 5000L)); // 50 元 < 100 元门槛
        assertTrue(ex.getMessage().contains("门槛"));
        verify(withdrawMapper, never()).insert(any(ShopPromoWithdrawDO.class));
    }

    @Test
    void apply_rejected_balanceInsufficient() {
        balance = 1000L;  // 只有 10 元余额，申请 100 元
        when(withdrawMapper.existsActiveByUserId(1L)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.apply(1L, 10000L));
        assertEquals(1000L, balance, "扣减失败前余额应保持");
    }

    @Test
    void apply_rejected_whenAlreadyHasActiveApply() {
        when(withdrawMapper.existsActiveByUserId(1L)).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.apply(1L, 50000L));
        assertTrue(ex.getMessage().contains("进行中"));
    }

    @Test
    void apply_rejected_zeroOrNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> service.apply(1L, 0L));
        assertThrows(IllegalArgumentException.class, () -> service.apply(1L, -100L));
    }

    @Test
    void approve_succeeds_fromPending() {
        ShopPromoWithdrawDO record = newPending(1L, 50000L);

        service.approve(record.getId(), 99L, "OK");

        ShopPromoWithdrawDO updated = store.get(record.getId());
        assertEquals("APPROVED", updated.getStatus());
        assertEquals(99L, updated.getProcessorId());
        assertEquals("OK", updated.getProcessorRemark());
    }

    @Test
    void approve_throws_whenNotPending() {
        ShopPromoWithdrawDO record = newPending(1L, 50000L);
        record.setStatus("APPROVED");

        assertThrows(IllegalStateException.class,
                () -> service.approve(record.getId(), 99L, "二次操作"));
    }

    @Test
    void reject_refundsBalance_andTransitions() {
        ShopPromoWithdrawDO record = newPending(1L, 50000L);
        long beforeBalance = balance;

        service.reject(record.getId(), 99L, "信息不全");

        ShopPromoWithdrawDO updated = store.get(record.getId());
        assertEquals("REJECTED", updated.getStatus());
        assertEquals(50000L, awarded.get("WITHDRAW_REFUND"), "驳回应退还原扣减金额");
        assertEquals(beforeBalance + 50000L, balance);
    }

    @Test
    void reject_throws_whenAlreadyApproved() {
        ShopPromoWithdrawDO record = newPending(1L, 50000L);
        record.setStatus("APPROVED");

        assertThrows(IllegalStateException.class,
                () -> service.reject(record.getId(), 99L, "改主意"));
        assertNull(awarded.get("WITHDRAW_REFUND"), "异常时不应退还");
    }

    @Test
    void markPaid_succeeds_fromApproved() {
        ShopPromoWithdrawDO record = newPending(1L, 50000L);
        record.setStatus("APPROVED");

        service.markPaid(record.getId(), 99L, "已转账");

        assertEquals("PAID", store.get(record.getId()).getStatus());
    }

    @Test
    void markPaid_throws_whenNotApproved() {
        ShopPromoWithdrawDO record = newPending(1L, 50000L);
        // status 还是 PENDING

        assertThrows(IllegalStateException.class,
                () -> service.markPaid(record.getId(), 99L, "跳步骤"));
    }

    @Test
    void mustGet_throws_whenIdMissingOrUnknown() {
        assertThrows(IllegalArgumentException.class,
                () -> service.approve(null, 99L, "x"));
        assertThrows(IllegalStateException.class,
                () -> service.approve(9999L, 99L, "x"));
    }

    private ShopPromoWithdrawDO newPending(Long userId, long amount) {
        when(withdrawMapper.existsActiveByUserId(userId)).thenReturn(false);
        return service.apply(userId, amount);
    }

}
