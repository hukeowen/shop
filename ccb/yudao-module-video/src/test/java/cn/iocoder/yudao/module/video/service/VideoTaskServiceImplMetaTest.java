package cn.iocoder.yudao.module.video.service;

import cn.iocoder.yudao.module.video.dal.dataobject.VideoTaskDO;
import cn.iocoder.yudao.module.video.dal.mysql.VideoTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 验证 {@link VideoTaskServiceImpl#updateTaskMeta} 业务规则：
 * 仅覆盖 patch 中非空字段、failReason 截断 200 字符、不存在时静默跳过、
 * Integer 字段 0 是合法值。
 */
class VideoTaskServiceImplMetaTest {

    private VideoTaskServiceImpl service;
    private VideoTaskMapper mapper;
    private final Map<Long, VideoTaskDO> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        mapper = mock(VideoTaskMapper.class);
        when(mapper.selectById(any())).thenAnswer(inv -> {
            Object id = inv.getArgument(0);
            if (id instanceof Long) return store.get(id);
            if (id instanceof Number) return store.get(((Number) id).longValue());
            return null;
        });
        when(mapper.updateById(any(VideoTaskDO.class))).thenAnswer(inv -> {
            VideoTaskDO patch = inv.getArgument(0);
            VideoTaskDO existed = store.get(patch.getId());
            if (existed == null) return 0;
            // 模拟 MyBatis Plus 的"非 null 才更新"行为：把 patch 的非空字段拷到 existed
            if (patch.getTitle() != null) existed.setTitle(patch.getTitle());
            if (patch.getDescription() != null) existed.setDescription(patch.getDescription());
            if (patch.getImageUrls() != null) existed.setImageUrls(patch.getImageUrls());
            if (patch.getBgmUrl() != null) existed.setBgmUrl(patch.getBgmUrl());
            if (patch.getTtsAudioUrl() != null) existed.setTtsAudioUrl(patch.getTtsAudioUrl());
            if (patch.getVideoUrl() != null) existed.setVideoUrl(patch.getVideoUrl());
            if (patch.getDuration() != null) existed.setDuration(patch.getDuration());
            if (patch.getStatus() != null) existed.setStatus(patch.getStatus());
            if (patch.getFailReason() != null) existed.setFailReason(patch.getFailReason());
            if (patch.getBgmStyle() != null) existed.setBgmStyle(patch.getBgmStyle());
            if (patch.getPosterUrl() != null) existed.setPosterUrl(patch.getPosterUrl());
            if (patch.getVoiceKey() != null) existed.setVoiceKey(patch.getVoiceKey());
            if (patch.getRatio() != null) existed.setRatio(patch.getRatio());
            if (patch.getCoverUrl() != null) existed.setCoverUrl(patch.getCoverUrl());
            return 1;
        });

        service = new VideoTaskServiceImpl();
        ReflectionTestUtils.setField(service, "videoTaskMapper", mapper);
    }

    private VideoTaskDO seed(Long id) {
        VideoTaskDO t = VideoTaskDO.builder()
                .id(id).merchantId(1L).userId(2L)
                .title("orig title").description("orig desc")
                .imageUrls(Arrays.asList("u1", "u2", "u3"))
                .status(1).douyinPublishStatus(0)
                .voiceKey("cancan").ratio("9:16")
                .build();
        store.put(id, t);
        return t;
    }

    @Test
    void updateTaskMeta_overridesAllNewFields() {
        seed(100L);
        VideoTaskDO patch = VideoTaskDO.builder()
                .bgmStyle("street_food_yelling")
                .posterUrl("https://oss/poster.jpg")
                .voiceKey("qingxin")
                .ratio("16:9")
                .coverUrl("https://oss/cover.jpg")
                .build();
        service.updateTaskMeta(100L, patch);

        VideoTaskDO got = store.get(100L);
        assertEquals("street_food_yelling", got.getBgmStyle());
        assertEquals("https://oss/poster.jpg", got.getPosterUrl());
        assertEquals("qingxin", got.getVoiceKey());
        assertEquals("16:9", got.getRatio());
        assertEquals("https://oss/cover.jpg", got.getCoverUrl());
        // 原字段保留
        assertEquals("orig title", got.getTitle());
    }

    @Test
    void updateTaskMeta_nullFieldsDoNotOverride() {
        seed(100L);
        // patch 全 null（除 id）→ 不应改任何字段，且 dirty=false 不应触发 update
        service.updateTaskMeta(100L, new VideoTaskDO());
        VideoTaskDO got = store.get(100L);
        assertEquals("orig title", got.getTitle());
        assertEquals("cancan", got.getVoiceKey());
        // verify mapper.updateById 没被调（dirty=false 短路）
        verify(mapper, never()).updateById(any(VideoTaskDO.class));
    }

    @Test
    void updateTaskMeta_emptyTitle_isIgnored() {
        seed(100L);
        VideoTaskDO patch = VideoTaskDO.builder().title("").build();
        service.updateTaskMeta(100L, patch);
        assertEquals("orig title", store.get(100L).getTitle(), "空 title 不应覆盖");
    }

    @Test
    void updateTaskMeta_statusZero_isAccepted() {
        seed(100L);
        VideoTaskDO patch = VideoTaskDO.builder().status(0).build();
        service.updateTaskMeta(100L, patch);
        assertEquals(0, store.get(100L).getStatus().intValue(), "Integer 0 是合法值，应被覆盖");
    }

    @Test
    void updateTaskMeta_longFailReason_isTruncatedTo200() {
        seed(100L);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 250; i++) sb.append('x');
        VideoTaskDO patch = VideoTaskDO.builder().failReason(sb.toString()).build();
        service.updateTaskMeta(100L, patch);
        assertEquals(200, store.get(100L).getFailReason().length());
    }

    @Test
    void updateTaskMeta_nonexistent_isNoop() {
        // 没 seed
        VideoTaskDO patch = VideoTaskDO.builder().bgmStyle("x").build();
        service.updateTaskMeta(999L, patch);
        verify(mapper, never()).updateById(any(VideoTaskDO.class));
    }

    @Test
    void updateTaskMeta_invalidId_isNoop() {
        service.updateTaskMeta(null, VideoTaskDO.builder().bgmStyle("x").build());
        service.updateTaskMeta(0L, VideoTaskDO.builder().bgmStyle("x").build());
        service.updateTaskMeta(100L, null);
        verify(mapper, never()).selectById(any());
        verify(mapper, never()).updateById(any(VideoTaskDO.class));
    }

    @Test
    void updateTaskMeta_partialOverride_mixedFields() {
        seed(100L);
        // 同时改 title + bgmStyle，其它保留
        VideoTaskDO patch = VideoTaskDO.builder()
                .title("new title")
                .bgmStyle("trendy_pop")
                .build();
        service.updateTaskMeta(100L, patch);
        VideoTaskDO got = store.get(100L);
        assertEquals("new title", got.getTitle());
        assertEquals("trendy_pop", got.getBgmStyle());
        // 没 patch 的保留
        assertEquals("orig desc", got.getDescription());
        assertEquals("cancan", got.getVoiceKey());
        assertEquals(Arrays.asList("u1", "u2", "u3"), got.getImageUrls());
    }

    @Test
    void updateTaskMeta_emptyImageUrls_doesNotOverride() {
        seed(100L);
        // 前端误传空数组不应清掉 image_urls（schema NOT NULL）
        VideoTaskDO patch = VideoTaskDO.builder()
                .imageUrls(java.util.Collections.emptyList())
                .build();
        service.updateTaskMeta(100L, patch);
        assertEquals(Arrays.asList("u1", "u2", "u3"), store.get(100L).getImageUrls());
    }

    @Test
    void updateTaskMeta_videoUrlEmptyString_overrides() {
        // 注意：videoUrl="" 不是 null，按 partial 语义会覆盖；这是预期行为（用户手动清 URL）
        seed(100L);
        VideoTaskDO patch = VideoTaskDO.builder().videoUrl("").build();
        service.updateTaskMeta(100L, patch);
        assertEquals("", store.get(100L).getVideoUrl());
    }
}
