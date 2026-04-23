package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo.AiVideoQuotaLogPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantVideoQuotaLogDO;

import java.util.Date;
import java.util.List;

/**
 * 商户 AI 视频配额流水 Service（Phase 0.3.1）。
 *
 * <p>仅提供查询与 insertLog 原子写入——<em>不</em>修改 merchant_info。
 * 调用方（{@code MerchantService#increaseVideoQuota / decreaseVideoQuota}）在同一事务内先改余量再写本表。</p>
 */
public interface MerchantVideoQuotaLogService {

    /**
     * 插入一条流水。由上层事务持有者在原子扣减/增加之后调用。
     *
     * <p>依赖 {@code merchant_video_quota_log.(biz_type, biz_id)} 的 UNIQUE 约束做幂等保护：</p>
     * <ul>
     *     <li>返回 {@code true}——新插入成功；</li>
     *     <li>返回 {@code false}——同一 {@code (biz_type, biz_id)} 已存在（支付回调重试等），调用方应据此决定是否回滚上层事务。</li>
     * </ul>
     */
    boolean insertLog(MerchantVideoQuotaLogDO log);

    /** Admin 分页。 */
    PageResult<MerchantVideoQuotaLogDO> getLogPage(AiVideoQuotaLogPageReqVO reqVO);

    /** App 分页：商户只能看自己的流水。 */
    PageResult<MerchantVideoQuotaLogDO> getLogPageByMerchant(Long merchantId, Integer bizType,
                                                             AiVideoQuotaLogPageReqVO pageParam);

    /**
     * 扫描 VIDEO_GEN 方向的扣减流水，找出<em>未能成功绑定 taskId</em> 且也未被 VIDEO_REFUND 回补的条目，
     * 视为潜在"丢失的扣减"。供 {@code VideoQuotaReconcileJob} 告警。
     *
     * @param start 起始时间（含）
     * @param end   结束时间（含）
     * @return 疑似丢失扣减流水（最多 100 条，按 {@code create_time DESC}）
     */
    List<MerchantVideoQuotaLogDO> findOrphanDebits(Date start, Date end);

    /**
     * BFF 提交成功后，把即梦返回的 taskId 追加到预扣流水的 remark 上，完成审计链。
     * SQL 使用 {@code UPDATE ... LIMIT 1}，最多命中 1 行。
     *
     * @param merchantId 商户 ID
     * @param bizId      预扣时写入的临时 UUID
     * @param taskId     即梦返回的 task_id
     * @return 受影响行数
     */
    int appendTaskIdToRemark(Long merchantId, String bizId, String taskId);

}
