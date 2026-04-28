package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoWithdrawDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoWithdrawMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Resource
    private ShopPromoWithdrawMapper withdrawMapper;
    @Resource
    private PromoConfigService promoConfigService;
    @Resource
    private PromoPointService promoPointService;

    @Override
    @Transactional(rollbackFor = Exception.class)
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

    @Override
    public PageResult<ShopPromoWithdrawDO> page(String status, PageParam pageParam) {
        return withdrawMapper.selectPageByStatus(status, pageParam);
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
