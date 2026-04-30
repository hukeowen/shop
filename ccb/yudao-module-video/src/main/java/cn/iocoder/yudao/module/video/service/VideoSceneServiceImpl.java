package cn.iocoder.yudao.module.video.service;

import cn.iocoder.yudao.module.video.dal.dataobject.VideoSceneDO;
import cn.iocoder.yudao.module.video.dal.mysql.VideoSceneMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 分镜 Service 实现。
 *
 * <p>所有方法都依赖 (taskId, sceneIndex) 联合唯一索引（V014）做幂等保证：
 * UPSERT 用"先 select 再 insert/update"两步；并发场景靠唯一索引兜底（重复 insert
 * 抛 DuplicateKeyException，捕获后转 update）。</p>
 */
@Service
@Slf4j
public class VideoSceneServiceImpl implements VideoSceneService {

    @Resource
    private VideoSceneMapper sceneMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<VideoSceneDO> saveScenes(Long taskId, List<VideoSceneDO> scenes) {
        if (taskId == null || taskId <= 0) {
            throw new IllegalArgumentException("taskId 非法");
        }
        if (scenes == null || scenes.isEmpty()) {
            return new ArrayList<>();
        }
        List<VideoSceneDO> out = new ArrayList<>(scenes.size());
        for (VideoSceneDO s : scenes) {
            if (s == null) continue;
            // 强制 task_id 一致（避免前端误传别的 task）
            s.setTaskId(taskId);
            if (s.getSceneIndex() == null) {
                throw new IllegalArgumentException("sceneIndex 不能为空");
            }
            VideoSceneDO existed = sceneMapper.selectByTaskAndIndex(taskId, s.getSceneIndex());
            if (existed == null) {
                // status 缺省 pending，避免 NOT NULL 列入空
                if (s.getStatus() == null || s.getStatus().isEmpty()) {
                    s.setStatus("pending");
                }
                if (s.getIsEndCard() == null) {
                    s.setIsEndCard(false);
                }
                sceneMapper.insert(s);
                out.add(s);
            } else {
                // 用前端传来的字段 patch 已存在的行；id 复用，避免 UNIQUE 冲突
                s.setId(existed.getId());
                // 保留 createTime（updateById 会自动改 updateTime）
                sceneMapper.updateById(s);
                out.add(s);
            }
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateScenePartial(Long taskId, int sceneIndex, VideoSceneDO patch) {
        if (taskId == null || taskId <= 0 || patch == null) {
            return;
        }
        VideoSceneDO existed = sceneMapper.selectByTaskAndIndex(taskId, sceneIndex);
        if (existed == null) {
            log.warn("[updateScenePartial] task={} sceneIndex={} 不存在，跳过", taskId, sceneIndex);
            return;
        }
        // 仅覆盖非空字段（典型 patch：只更新 status / clipUrl / clipTaskId / failReason）
        if (patch.getStatus() != null && !patch.getStatus().isEmpty()) existed.setStatus(patch.getStatus());
        if (patch.getClipTaskId() != null) existed.setClipTaskId(patch.getClipTaskId());
        if (patch.getClipUrl() != null) existed.setClipUrl(patch.getClipUrl());
        if (patch.getAudioUrl() != null) existed.setAudioUrl(patch.getAudioUrl());
        if (patch.getFailReason() != null) existed.setFailReason(patch.getFailReason());
        if (patch.getStartImageUrl() != null) existed.setStartImageUrl(patch.getStartImageUrl());
        // narration / visualPrompt / imageSummary 也允许 partial 改（用户在 confirm 页改完台词重新提交）
        if (patch.getNarration() != null) existed.setNarration(patch.getNarration());
        if (patch.getVisualPrompt() != null) existed.setVisualPrompt(patch.getVisualPrompt());
        if (patch.getImageSummary() != null) existed.setImageSummary(patch.getImageSummary());
        if (patch.getImgIdx() != null) existed.setImgIdx(patch.getImgIdx());
        if (patch.getDuration() != null) existed.setDuration(patch.getDuration());
        if (patch.getIsEndCard() != null) existed.setIsEndCard(patch.getIsEndCard());
        sceneMapper.updateById(existed);
    }

    @Override
    public List<VideoSceneDO> listByTaskId(Long taskId) {
        if (taskId == null || taskId <= 0) return new ArrayList<>();
        return sceneMapper.selectListByTaskId(taskId);
    }

    @Override
    public VideoSceneDO getByTaskAndIndex(Long taskId, int sceneIndex) {
        if (taskId == null || taskId <= 0) return null;
        return sceneMapper.selectByTaskAndIndex(taskId, sceneIndex);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByTaskId(Long taskId) {
        if (taskId == null || taskId <= 0) return 0;
        return sceneMapper.deleteByTaskId(taskId);
    }
}
