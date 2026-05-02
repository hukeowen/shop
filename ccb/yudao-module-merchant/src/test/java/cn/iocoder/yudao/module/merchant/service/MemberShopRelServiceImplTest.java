package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MemberOrderBalanceLogDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MemberOrderBalanceLogMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.MemberShopRelMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link MemberShopRelServiceImpl#deductBalanceForOrder} 覆盖：
 * - 幂等命中（已存在日志直接返回 false）
 * - 余额不足抛业务异常
 * - 正常扣减后写日志
 * - 并发 DuplicateKey 路径触发回滚（throw）
 * - amount ≤ 0 不动作
 */
class MemberShopRelServiceImplTest {

    private MemberShopRelServiceImpl service;
    private MemberShopRelMapper relMapper;
    private MemberOrderBalanceLogMapper logMapper;

    /** 用 (userId,tenantId,orderId) 三元组当 key 模拟唯一索引行为 */
    private final Map<String, MemberOrderBalanceLogDO> logsByKey = new HashMap<>();
    private final AtomicLong autoLogId = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        relMapper = mock(MemberShopRelMapper.class);
        logMapper = mock(MemberOrderBalanceLogMapper.class);

        // 模拟 selectByUserTenantOrder
        when(logMapper.selectByUserTenantOrder(any(), any(), any())).thenAnswer(inv ->
                logsByKey.get(key(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2))));

        // 模拟 insert：UNIQUE 检查
        when(logMapper.insert(any(MemberOrderBalanceLogDO.class))).thenAnswer(inv -> {
            MemberOrderBalanceLogDO row = inv.getArgument(0);
            String k = key(row.getUserId(), row.getTenantId(), row.getOrderId());
            if (logsByKey.containsKey(k)) {
                throw new DuplicateKeyException("UNIQUE conflict on " + k);
            }
            row.setId(autoLogId.getAndIncrement());
            logsByKey.put(k, row);
            return 1;
        });

        service = new MemberShopRelServiceImpl();
        ReflectionTestUtils.setField(service, "memberShopRelMapper", relMapper);
        ReflectionTestUtils.setField(service, "memberOrderBalanceLogMapper", logMapper);
    }

    private static String key(Object u, Object t, Object o) { return u + "|" + t + "|" + o; }

    @Test
    void deduct_idempotent_when_log_exists_returnsFalse_andDoesNotDeduct() {
        // 已存在历史记录
        logsByKey.put(key(1L, 100L, 5000L),
                MemberOrderBalanceLogDO.builder().userId(1L).tenantId(100L).orderId(5000L).amount(100).build());

        boolean done = service.deductBalanceForOrder(1L, 100L, 5000L, 100);

        assertFalse(done);
        // 余额扣减 mapper 不应被调用
        verify(relMapper, never()).deductBalance(any(), any(), anyInt());
        // insert 不应再被调用
        verify(logMapper, never()).insert(any(MemberOrderBalanceLogDO.class));
    }

    @Test
    void deduct_normal_path_deductsBalance_writesLog_returnsTrue() {
        when(relMapper.deductBalance(eq(1L), eq(100L), eq(50))).thenReturn(1);

        boolean done = service.deductBalanceForOrder(1L, 100L, 5000L, 50);

        assertTrue(done);
        verify(relMapper).deductBalance(1L, 100L, 50);
        assertNotNull(logsByKey.get(key(1L, 100L, 5000L)));
        assertEquals(50, logsByKey.get(key(1L, 100L, 5000L)).getAmount());
    }

    @Test
    void deduct_insufficient_balance_throwsServiceException() {
        when(relMapper.deductBalance(eq(1L), eq(100L), eq(99999))).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.deductBalanceForOrder(1L, 100L, 5000L, 99999));
        assertTrue(ex.getMessage().contains("余额不足"));
        // 不应该写日志
        assertNull(logsByKey.get(key(1L, 100L, 5000L)));
    }

    @Test
    void deduct_concurrent_duplicateKey_throwsServiceException_balanceRestoredByTxRollback() {
        when(relMapper.deductBalance(eq(1L), eq(100L), eq(50))).thenReturn(1);
        // 模拟并发：刚走完 selectOne=null + deduct，另一事务先插入了日志
        logsByKey.put(key(1L, 100L, 5000L),
                MemberOrderBalanceLogDO.builder().userId(1L).tenantId(100L).orderId(5000L).amount(50).build());
        // 让 selectOne 仍返回 null（模拟它在并发对手提交前就已经查过）
        when(logMapper.selectByUserTenantOrder(eq(1L), eq(100L), eq(5000L))).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.deductBalanceForOrder(1L, 100L, 5000L, 50));
        assertTrue(ex.getMessage().contains("已抵扣过"));
        // @Transactional 在外层回滚，相关资金保护由事务确保（mock 这里只验证抛 ServiceException）
    }

    @Test
    void deduct_zeroOrNegative_amount_returnsFalse_noop() {
        assertFalse(service.deductBalanceForOrder(1L, 100L, 5000L, 0));
        assertFalse(service.deductBalanceForOrder(1L, 100L, 5000L, -10));
        verify(relMapper, never()).deductBalance(any(), any(), anyInt());
        verify(logMapper, never()).insert(any(MemberOrderBalanceLogDO.class));
    }

}
