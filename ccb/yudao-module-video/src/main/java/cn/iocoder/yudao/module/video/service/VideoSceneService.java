package cn.iocoder.yudao.module.video.service;

import cn.iocoder.yudao.module.video.dal.dataobject.VideoSceneDO;

import java.util.List;

/**
 * AI 视频分镜 Service。
 *
 * <p>每个 {@link cn.iocoder.yudao.module.video.dal.dataobject.VideoTaskDO} 一对多
 * 个分镜。前端 confirmTask 时把 N 幕脚本批量落库；视频生成过程中每幕的
 * clipTaskId / clipUrl / status 也单独同步上来。</p>
 */
public interface VideoSceneService {

    /**
     * 批量保存分镜（UPSERT 语义）。
     *
     * <p>同一 (taskId, sceneIndex) 已存在则更新（用 selectByTaskAndIndex + updateById），
     * 不存在则 insert；列表里没有的 sceneIndex **不会**被删（前端如有需要先调
     * deleteSceneByTaskAndIndex 显式删）。</p>
     *
     * <p>典型用法：confirmTask 时把整段脚本一次性 saveAll，runClip 跑视频生成时每幕
     * 单独 update 自己的 clipTaskId/clipUrl/status。</p>
     *
     * @return 实际写入的分镜列表（含 id）
     */
    List<VideoSceneDO> saveScenes(Long taskId, List<VideoSceneDO> scenes);

    /** 单幕状态/产出更新（runClip 路径用，避免每次重写整段脚本） */
    void updateScenePartial(Long taskId, int sceneIndex, VideoSceneDO patch);

    /** 列出某 task 的所有分镜（按 sceneIndex 升序） */
    List<VideoSceneDO> listByTaskId(Long taskId);

    /** 单幕查询（前端断点续传 / 接管轮询用） */
    VideoSceneDO getByTaskAndIndex(Long taskId, int sceneIndex);

    /** 删某 task 的所有分镜（任务整删时用，软删） */
    int deleteByTaskId(Long taskId);
}
