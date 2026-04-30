package cn.iocoder.yudao.module.video.service;

import cn.iocoder.yudao.module.video.dal.dataobject.VideoSceneDO;
import cn.iocoder.yudao.module.video.dal.mysql.VideoSceneMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link VideoSceneServiceImpl} 的 UPSERT / partial update / cascade delete 等业务规则。
 *
 * <p>用 in-memory map 模拟 mapper —— 不打 DB，纯逻辑覆盖。</p>
 */
class VideoSceneServiceImplTest {

    private VideoSceneServiceImpl service;
    private VideoSceneMapper mapper;
    private final Map<Long, VideoSceneDO> store = new HashMap<>(); // id -> DO
    private final AtomicLong autoId = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        mapper = mock(VideoSceneMapper.class);

        // selectByTaskAndIndex
        when(mapper.selectByTaskAndIndex(any(), any())).thenAnswer(inv -> {
            Long taskId = inv.getArgument(0);
            Integer sceneIndex = inv.getArgument(1);
            return store.values().stream()
                    .filter(s -> taskId.equals(s.getTaskId()))
                    .filter(s -> sceneIndex.equals(s.getSceneIndex()))
                    .findFirst().orElse(null);
        });
        when(mapper.selectListByTaskId(any())).thenAnswer(inv -> {
            Long taskId = inv.getArgument(0);
            List<VideoSceneDO> result = new ArrayList<>();
            for (VideoSceneDO s : store.values()) {
                if (taskId.equals(s.getTaskId())) result.add(s);
            }
            result.sort((a, b) -> Integer.compare(
                    a.getSceneIndex() == null ? 0 : a.getSceneIndex(),
                    b.getSceneIndex() == null ? 0 : b.getSceneIndex()));
            return result;
        });
        when(mapper.deleteByTaskId(any())).thenAnswer(inv -> {
            Long taskId = inv.getArgument(0);
            int n = 0;
            List<Long> idsToDel = new ArrayList<>();
            for (VideoSceneDO s : store.values()) {
                if (taskId.equals(s.getTaskId())) { idsToDel.add(s.getId()); n++; }
            }
            for (Long id : idsToDel) store.remove(id);
            return n;
        });
        when(mapper.insert(any(VideoSceneDO.class))).thenAnswer(inv -> {
            VideoSceneDO s = inv.getArgument(0);
            s.setId(autoId.getAndIncrement());
            store.put(s.getId(), s);
            return 1;
        });
        when(mapper.updateById(any(VideoSceneDO.class))).thenAnswer(inv -> {
            VideoSceneDO s = inv.getArgument(0);
            store.put(s.getId(), s);
            return 1;
        });
        when(mapper.selectByClipTaskId(eq("clip-x"))).thenAnswer(inv -> {
            for (VideoSceneDO s : store.values()) {
                if ("clip-x".equals(s.getClipTaskId())) return s;
            }
            return null;
        });

        service = new VideoSceneServiceImpl();
        ReflectionTestUtils.setField(service, "sceneMapper", mapper);
    }

    @Test
    void saveScenes_inserts_newScenes() {
        VideoSceneDO s0 = VideoSceneDO.builder()
                .sceneIndex(0).imgIdx(0).narration("第 1 句").visualPrompt("push-in macro")
                .build();
        VideoSceneDO s1 = VideoSceneDO.builder()
                .sceneIndex(1).imgIdx(1).narration("第 2 句").visualPrompt("pan slow motion")
                .isEndCard(false)
                .build();
        List<VideoSceneDO> out = service.saveScenes(100L, Arrays.asList(s0, s1));
        assertEquals(2, out.size());
        // status 默认 pending
        assertEquals("pending", out.get(0).getStatus());
        // task_id 强制覆盖
        assertEquals(100L, out.get(0).getTaskId());
        assertEquals(100L, out.get(1).getTaskId());
        // 写进了 store
        assertEquals(2, store.size());
        // isEndCard null → 默认 false
        assertEquals(false, out.get(0).getIsEndCard());
    }

    @Test
    void saveScenes_secondCall_isUpsert_notDuplicate() {
        // 先插一行
        VideoSceneDO s0 = VideoSceneDO.builder()
                .sceneIndex(0).imgIdx(0).narration("v1").visualPrompt("p1")
                .build();
        service.saveScenes(100L, Arrays.asList(s0));
        assertEquals(1, store.size());
        Long firstId = store.keySet().iterator().next();

        // 同 (taskId, sceneIndex) 再保存 → 应 update 不应 insert 新行
        VideoSceneDO s0v2 = VideoSceneDO.builder()
                .sceneIndex(0).imgIdx(0).narration("v2-edited").visualPrompt("p2-edited")
                .build();
        service.saveScenes(100L, Arrays.asList(s0v2));
        assertEquals(1, store.size(), "UPSERT 不应产生重复行");
        VideoSceneDO updated = store.get(firstId);
        assertEquals("v2-edited", updated.getNarration());
        assertEquals("p2-edited", updated.getVisualPrompt());
    }

    @Test
    void saveScenes_taskIdEnforced_evenIfPayloadMismatches() {
        // 前端传错 taskId 也要被强制覆盖
        VideoSceneDO s0 = VideoSceneDO.builder()
                .taskId(999L).sceneIndex(0).imgIdx(0).narration("x").visualPrompt("y")
                .build();
        List<VideoSceneDO> out = service.saveScenes(100L, Arrays.asList(s0));
        assertEquals(100L, out.get(0).getTaskId(), "taskId 必须被 service 强制覆盖");
    }

    @Test
    void saveScenes_emptyOrNull_returnsEmpty() {
        assertTrue(service.saveScenes(100L, null).isEmpty());
        assertTrue(service.saveScenes(100L, new ArrayList<>()).isEmpty());
    }

    @Test
    void saveScenes_invalidTaskId_throws() {
        VideoSceneDO s0 = VideoSceneDO.builder().sceneIndex(0).imgIdx(0).build();
        assertThrows(IllegalArgumentException.class,
                () -> service.saveScenes(0L, Arrays.asList(s0)));
        assertThrows(IllegalArgumentException.class,
                () -> service.saveScenes(null, Arrays.asList(s0)));
    }

    @Test
    void saveScenes_missingSceneIndex_throws() {
        VideoSceneDO s0 = VideoSceneDO.builder()
                .imgIdx(0).narration("x") // 缺 sceneIndex
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> service.saveScenes(100L, Arrays.asList(s0)));
    }

    @Test
    void updateScenePartial_only_overridesProvidedFields() {
        VideoSceneDO orig = VideoSceneDO.builder()
                .sceneIndex(0).imgIdx(0).narration("hello").visualPrompt("vp")
                .duration(10).isEndCard(false).status("pending")
                .build();
        service.saveScenes(100L, Arrays.asList(orig));

        VideoSceneDO patch = VideoSceneDO.builder()
                .status("ready").clipUrl("https://oss/clip.mp4")
                .build();
        service.updateScenePartial(100L, 0, patch);

        VideoSceneDO got = service.getByTaskAndIndex(100L, 0);
        assertEquals("ready", got.getStatus());
        assertEquals("https://oss/clip.mp4", got.getClipUrl());
        // 其它字段保留
        assertEquals("hello", got.getNarration());
        assertEquals("vp", got.getVisualPrompt());
        assertEquals(Integer.valueOf(10), got.getDuration());
    }

    @Test
    void updateScenePartial_nonexistent_isNoop() {
        // 没有任何记录直接 patch
        VideoSceneDO patch = VideoSceneDO.builder().status("ready").build();
        service.updateScenePartial(100L, 0, patch);
        assertNull(service.getByTaskAndIndex(100L, 0));
    }

    @Test
    void updateScenePartial_emptyStatus_doesNotOverwrite() {
        VideoSceneDO orig = VideoSceneDO.builder()
                .sceneIndex(0).imgIdx(0).narration("x").visualPrompt("vp")
                .status("video_running")
                .build();
        service.saveScenes(100L, Arrays.asList(orig));

        // patch 给 status="" → 不应覆盖之前的 video_running
        VideoSceneDO patch = VideoSceneDO.builder()
                .status("").clipTaskId("clip-x")
                .build();
        service.updateScenePartial(100L, 0, patch);

        VideoSceneDO got = service.getByTaskAndIndex(100L, 0);
        assertEquals("video_running", got.getStatus(), "空 status 不应覆盖");
        assertEquals("clip-x", got.getClipTaskId());
    }

    @Test
    void listByTaskId_returnsSortedAscBySceneIndex() {
        // 故意乱序 insert
        service.saveScenes(100L, Arrays.asList(
                VideoSceneDO.builder().sceneIndex(2).imgIdx(2).narration("c").build(),
                VideoSceneDO.builder().sceneIndex(0).imgIdx(0).narration("a").build(),
                VideoSceneDO.builder().sceneIndex(1).imgIdx(1).narration("b").build()
        ));
        List<VideoSceneDO> list = service.listByTaskId(100L);
        assertEquals(3, list.size());
        assertEquals("a", list.get(0).getNarration());
        assertEquals("b", list.get(1).getNarration());
        assertEquals("c", list.get(2).getNarration());
    }

    @Test
    void listByTaskId_invalidId_returnsEmpty() {
        assertTrue(service.listByTaskId(null).isEmpty());
        assertTrue(service.listByTaskId(0L).isEmpty());
    }

    @Test
    void deleteByTaskId_cascadesAllScenes() {
        service.saveScenes(100L, Arrays.asList(
                VideoSceneDO.builder().sceneIndex(0).imgIdx(0).narration("a").build(),
                VideoSceneDO.builder().sceneIndex(1).imgIdx(1).narration("b").build()
        ));
        // 别的 task 也插一条，不应被删
        service.saveScenes(200L, Arrays.asList(
                VideoSceneDO.builder().sceneIndex(0).imgIdx(0).narration("other").build()
        ));
        int n = service.deleteByTaskId(100L);
        assertEquals(2, n);
        assertTrue(service.listByTaskId(100L).isEmpty());
        // 其他 task 不受影响
        assertEquals(1, service.listByTaskId(200L).size());
    }

    @Test
    void deleteByTaskId_invalidId_returnsZero() {
        assertEquals(0, service.deleteByTaskId(null));
        assertEquals(0, service.deleteByTaskId(0L));
    }

    @Test
    void saveScenes_thenUpdate_keepsOriginalCreateTimeAndTaskId() {
        VideoSceneDO s0 = VideoSceneDO.builder()
                .sceneIndex(0).imgIdx(0).narration("v1").visualPrompt("p1")
                .build();
        service.saveScenes(100L, Arrays.asList(s0));
        Long originalId = store.values().iterator().next().getId();

        // 第二次保存（新对象但同 sceneIndex）
        VideoSceneDO s0v2 = VideoSceneDO.builder()
                .sceneIndex(0).imgIdx(2).narration("v2").visualPrompt("p2")
                .build();
        service.saveScenes(100L, Arrays.asList(s0v2));

        VideoSceneDO got = service.getByTaskAndIndex(100L, 0);
        assertEquals(originalId, got.getId(), "UPSERT 应复用同一行 id");
        assertEquals(100L, got.getTaskId());
        assertEquals(2, got.getImgIdx().intValue(), "imgIdx 已更新");
    }
}
