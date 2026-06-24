package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoWithdrawDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoWithdrawMapper;
import com.mzt.logapi.starter.annotation.LogRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.module.merchant.enums.MerchantLogRecordConstants.*;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 推广积分提现实现。
 */
@Service
@Slf4j
public class WithdrawServiceImpl implements WithdrawService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_PAID = "PAID";
    /** V044 商家用户双确认工作流：商家标记 PAID 后，用户确认收到 → COMPLETED */
    private static final String STATUS_COMPLETED = "COMPLETED";

    @Resource
    private ShopPromoWithdrawMapper withdrawMapper;
    @Resource
    private PromoConfigService promoConfigService;
    @Resource
    private PromoPointService promoPointService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = SHOP_WITHDRAW_TYPE, subType = SHOP_WITHDRAW_APPLY_SUB_TYPE,
            bizNo = "{{#_ret.id}}", success = SHOP_WITHDRAW_APPLY_SUCCESS)
    public ShopPromoWithdrawDO apply(Long userId, long amount) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("提现金额必须 > 0");
        }
        // 门槛校验
        PromoConfigDO config = promoConfigService.getConfig();
        Integer threshold = config == null ? 0 : (config.getWithdrawThreshold() == null ? 0 : config.getWithdrawThreshold());
        if (amount < threshold) {
            throw new IllegalStateException("低于提现门槛 " + threshold + " 分");
        }
        // 互斥：用户已有 PENDING / APPROVED 的活跃申请
        if (withdrawMapper.existsActiveByUserId(userId)) {
            throw new IllegalStateException("已有进行中的提现申请，请等审批完成或线下结算后再申请");
        }
        // 余额校验 + 即时扣减（保留 sourceId = 申请记录 ID 的语义；先建记录再扣减）
        ShopUserStarDO acct = promoPointService.getOrCreateAccount(userId);
        if (acct.getPromoPointBalance() < amount) {
            throw new IllegalStateException("推广积分余额不足，余额=" + acct.getPromoPointBalance()
                    + " 申请=" + amount);
        }
        ShopPromoWithdrawDO record = ShopPromoWithdrawDO.builder()
                .userId(userId)
                .amount(amount)
                .status(STATUS_PENDING)
                .applyAt(LocalDateTime.now())
                .build();
        withdrawMapper.insert(record);
        // 即时扣减积分（防止审批中被消费）
        promoPointService.deductPromoPoint(userId, amount, "WITHDRAW", record.getId(),
                "提现申请 #" + record.getId());
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopPromoWithdrawDO apply(Long userId, long amount, Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            return apply(userId, amount);
        }
        // 必须在目标店铺的租户上下文内执行：getConfig / 账户 / 扣减 / 插入 都按该店单租户。
        // 否则 @TenantIgnore 下 promo_config.selectOne 会跨店命中多行 → TooManyResultsException，
        // 且账户/扣减也会落到错误店铺。ShopPromoWithdrawDO 为 TenantBaseDO，insert 自动写 tenant_id。
        return cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.execute(tenantId,
                () -> apply(userId, amount));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = SHOP_WITHDRAW_TYPE, subType = SHOP_WITHDRAW_AUDIT_SUB_TYPE,
            bizNo = "{{#applyId}}", success = SHOP_WITHDRAW_APPROVE_SUCCESS)
    public void approve(Long applyId, Long processorId, String remark) {
        mustGet(applyId);
        // 原子状态机：仅当 PENDING 才转 APPROVED；并发下另一个事务先转过去会撞 0 行
        int rows = withdrawMapper.transitionStatus(applyId, STATUS_PENDING, STATUS_APPROVED, processorId, remark);
        if (rows != 1) {
            throw new IllegalStateException("状态机非法跳转：申请 #" + applyId + " 当前不是 PENDING（可能已被并发处理）");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = SHOP_WITHDRAW_TYPE, subType = SHOP_WITHDRAW_AUDIT_SUB_TYPE,
            bizNo = "{{#applyId}}", success = SHOP_WITHDRAW_REJECT_SUCCESS)
    public void reject(Long applyId, Long processorId, String remark) {
        ShopPromoWithdrawDO record = mustGet(applyId);
        int rows = withdrawMapper.transitionStatus(applyId, STATUS_PENDING, STATUS_REJECTED, processorId, remark);
        if (rows != 1) {
            throw new IllegalStateException("状态机非法跳转：申请 #" + applyId + " 当前不是 PENDING（可能已被并发处理）");
        }
        // 退还积分（独立流水：sourceType=WITHDRAW_REFUND，sourceId=applyId）
        promoPointService.addPromoPoint(record.getUserId(), record.getAmount(),
                "WITHDRAW_REFUND", applyId, "提现驳回退还 #" + applyId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = SHOP_WITHDRAW_TYPE, subType = SHOP_WITHDRAW_PAY_SUB_TYPE,
            bizNo = "{{#applyId}}", success = SHOP_WITHDRAW_PAY_SUCCESS)
    public void markPaid(Long applyId, Long processorId, String remark) {
        mustGet(applyId);
        int rows = withdrawMapper.transitionStatus(applyId, STATUS_APPROVED, STATUS_PAID, processorId, remark);
        if (rows != 1) {
            throw new IllegalStateException("状态机非法跳转：申请 #" + applyId + " 当前不是 APPROVED（可能已被并发处理）");
        }
    }

    @Override
    public List<ShopPromoWithdrawDO> listByUserId(Long userId) {
        return withdrawMapper.selectListByUserId(userId);
    }

    /**
     * V044：用户确认收款。
     * 状态机：PAID → COMPLETED；仅本人申请可调；幂等。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceived(Long applyId, Long userId) {
        ShopPromoWithdrawDO record = mustGet(applyId);
        if (!userId.equals(record.getUserId())) {
            throw new IllegalStateException("只能确认本人的提现申请");
        }
        int rows = withdrawMapper.transitionStatus(applyId, STATUS_PAID, STATUS_COMPLETED, userId,
                "用户确认已收款");
        if (rows != 1) {
            throw new IllegalStateException("当前状态不可确认（仅 PAID 状态可确认）：申请 #" + applyId);
        }
    }

    @Override
    public PageResult<ShopPromoWithdrawDO> page(String status, PageParam pageParam) {
        return withdrawMapper.selectPageByStatus(status, pageParam);
    }

    @Override
    public PageResult<ShopPromoWithdrawDO> pageByTenant(Long tenantId, String status, PageParam pageParam) {
        return withdrawMapper.selectPageByTenantAndStatus(tenantId, status, pageParam);
    }

    private ShopPromoWithdrawDO mustGet(Long applyId) {
        if (applyId == null) {
            throw new IllegalArgumentException("applyId 不能为空");
        }
        ShopPromoWithdrawDO record = withdrawMapper.selectById(applyId);
        if (record == null) {
            throw new IllegalStateException("提现申请不存在: " + applyId);
        }
        return record;
    }

    /**
     * 状态机校验：必须从 fromStatus 转到 toStatus。
     * 同状态二次操作（fromStatus = toStatus 也是当前状态）幂等命中 → throw（让 Controller 判 200 OK 还是错？）
     * 这里采用严格校验：只允许 PENDING→approve / PENDING→reject / APPROVED→markPaid。
     */
    private void requireStatus(ShopPromoWithdrawDO record, String expectedFrom, String settingTo) {
        String current = record.getStatus();
        if (!expectedFrom.equals(current)) {
            throw new IllegalStateException("状态机非法跳转：当前 " + current + "，期望从 "
                    + expectedFrom + " 转到 " + settingTo);
        }
    }

}
