package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppAiVideoConfirmReqVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppAiVideoCreateReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.AiVideoTaskDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.TenantSubscriptionDO;

/**
 * AI 成片任务 Service 接口
 */
public interface AiVideoTaskService {

    /**
     * 创建 AI 成片任务（检查配额 + 插入任务记录 + 触发文案生成）
     *
     * @param reqVO    请求参数
     * @param tenantId 当前租户 ID
     * @param userId   当前用户 ID
     * @return 任务 ID
     */
    Long createTask(AppAiVideoCreateReqVO reqVO, Long tenantId, Long userId);

    /**
     * 获取任务详情（含归属校验）
     */
    AiVideoTaskDO getTask(Long id, Long userId);

    /**
     * 确认文案并触发视频合成
     */
    void confirmCopywriting(AppAiVideoConfirmReqVO reqVO, Long userId);

    /**
     * LLM 文案生成完成回调（由 video 模块监听器在 Ark LLM 返回后调用）。
     *
     * <p>写入 {@code ai_copywriting} 字段，状态从 {@code 1=文案生成中} 推进到 {@code 2=等待用户确认}。
     * 用乐观并发（仅 status=1 时才推进），避免重复回调覆盖已确认的文案。</p>
     *
     * @param taskId      任务 ID
     * @param copywriting LLM 生成的逐句文案（非空）
     */
    void onCopywritingGenerated(Long taskId, java.util.List<String> copywriting);

    /**
     * LLM 文案生成失败回调。
     *
     * <p>状态置为 {@code 5=失败}，记录失败原因；不扣配额。</p>
     */
    void onCopywritingFailed(Long taskId, String failReason);

    /**
     * 视频生成完成回调（原子扣减配额，幂等处理）
     *
     * @param taskId    任务 ID
     * @param success   是否成功
     * @param videoUrl  生成视频 URL（成功时必传）
     * @param coverUrl  封面图 URL
     * @param failReason 失败原因（失败时必传）
     */
    void onTaskComplete(Long taskId, Boolean success, String videoUrl, String coverUrl, String failReason);

    /**
     * 分页查询当前用户的历史任务
     */
    PageResult<AiVideoTaskDO> getTaskPage(Long userId, PageParam pageParam);

    /**
     * 查询配额（剩余 / 已用）
     */
    TenantSubscriptionDO getQuota(Long tenantId);

}
