package cn.iocoder.yudao.module.video.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.video.dal.dataobject.VideoSceneDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI 视频分镜 Mapper。
 *
 * <p>Schema 在 {@code (task_id, scene_index)} 上有 UNIQUE 约束（V014 加），所以
 * {@link #selectByTaskAndIndex} 用 selectOne 安全。</p>
 */
@Mapper
public interface VideoSceneMapper extends BaseMapperX<VideoSceneDO> {

    /** 按 task_id 取所有分镜（按 scene_index 升序），用户进 confirm/detail 都用 */
    default List<VideoSceneDO> selectListByTaskId(Long taskId) {
        return selectList(new LambdaQueryWrapperX<VideoSceneDO>()
                .eq(VideoSceneDO::getTaskId, taskId)
                .orderByAsc(VideoSceneDO::getSceneIndex));
    }

    /** 按 (taskId, sceneIndex) 唯一查一行，配合 UPSERT 用 */
    default VideoSceneDO selectByTaskAndIndex(Long taskId, Integer sceneIndex) {
        return selectOne(new LambdaQueryWrapperX<VideoSceneDO>()
                .eq(VideoSceneDO::getTaskId, taskId)
                .eq(VideoSceneDO::getSceneIndex, sceneIndex));
    }

    /** 按 clipTaskId 反查（远程 Seedance webhook / 轮询接管用） */
    default VideoSceneDO selectByClipTaskId(String clipTaskId) {
        if (clipTaskId == null || clipTaskId.isEmpty()) return null;
        List<VideoSceneDO> list = selectList(new LambdaQueryWrapperX<VideoSceneDO>()
                .eq(VideoSceneDO::getClipTaskId, clipTaskId)
                .orderByDesc(VideoSceneDO::getId)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 按 task_id 删全部（任务被删时级联清，用 deleted bit 软删）。
     * MyBatis Plus 自动转成 UPDATE deleted = 1。
     */
    default int deleteByTaskId(Long taskId) {
        return delete(new LambdaQueryWrapperX<VideoSceneDO>()
                .eq(VideoSceneDO::getTaskId, taskId));
    }
}
