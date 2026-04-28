package cn.iocoder.yudao.module.merchant.service.promo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoWithdrawDO;

import java.util.List;

/**
 * 推广积分提现申请服务（v6 第一节"双积分"中"线下提现"链路）。
 *
 * 状态机：
 *   PENDING  → APPROVED   商户审批通过（待线下打款）
 *   PENDING  → REJECTED   商户驳回，原扣减积分原路退还（WITHDRAW_REFUND 流水）
 *   APPROVED → PAID       商户线下打款完成后标记
 *   其它跳转一律拒绝
 *
 * 业务规则：
 *   - 申请时校验：amount ≥ 商户配置 withdrawThreshold；余额充足；同用户不能同时多笔活跃
 *   - 申请成功立即扣减推广积分（避免审批中余额被刷走）；驳回时退还
 */
public interface WithdrawService {

    /**
     * 用户申请提现。
     *
     * @return 新建的申请记录（status=PENDING）
     * @throws IllegalStateException 余额不足 / 重复申请 / 低于门槛
     */
    ShopPromoWithdrawDO apply(Long userId, long amount);

    /** 商户审批通过。状态 PENDING → APPROVED。 */
    void approve(Long applyId, Long processorId, String remark);

    /** 商户驳回。状态 PENDING → REJECTED，并退还推广积分。 */
    void reject(Long applyId, Long processorId, String remark);

    /** 商户线下打款后标记。状态 APPROVED → PAID。 */
    void markPaid(Long applyId, Long processorId, String remark);

    /** 当前用户的提现申请列表（倒序）。 */
    List<ShopPromoWithdrawDO> listByUserId(Long userId);

    /** 商户审批端列表分页（status 可选过滤）。 */
    PageResult<ShopPromoWithdrawDO> page(String status, PageParam pageParam);

}
