-- description: AI 视频多幕分镜后端化第一步 — 建 video_scene 一对多表 + video_task 加 5 个元数据字段
-- author: huliang
-- date: 2026-05-01
--
-- 背景：之前 AI 视频任务的核心数据（多幕 scenes 数组、bgmStyle、posterUrl、voiceKey、ratio、
--       coverUrl）全在前端 localStorage / store.tasks，跨设备/换浏览器丢失，违反"全真数据"
--       交付红线。
--
-- 本次：
--   1) 新建 video_scene 表，每个任务的每幕分镜独立一行（一对多）
--   2) video_task 加 5 个字段：bgm_style / poster_url / voice_key / ratio / cover_url
--      （progress_done/total 不落库，运行时从 video_scene status 实时聚合）
--
-- 兼容：旧任务（V014 之前）在 video_scene 里没有记录，前端要兼容空 scenes 走"列表-only"展示。

-- ========== 1. video_task 加字段 ==========
DROP PROCEDURE IF EXISTS `_v014_alter_video_task`;
DELIMITER $$
CREATE PROCEDURE `_v014_alter_video_task`()
BEGIN
    DECLARE _exists INT;

    -- bgm_style
    SELECT COUNT(*) INTO _exists FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'video_task' AND COLUMN_NAME = 'bgm_style';
    IF _exists = 0 THEN
        ALTER TABLE `video_task` ADD COLUMN `bgm_style` VARCHAR(32) DEFAULT NULL
            COMMENT 'BGM 风格 key (street_food_yelling/cozy_explore/asmr_macro/elegant_tea/trendy_pop/emotional_story/none)';
    END IF;

    -- poster_url
    SELECT COUNT(*) INTO _exists FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'video_task' AND COLUMN_NAME = 'poster_url';
    IF _exists = 0 THEN
        ALTER TABLE `video_task` ADD COLUMN `poster_url` VARCHAR(512) DEFAULT NULL
            COMMENT '即梦 CV 端卡海报 URL（detail 页单独下载）';
    END IF;

    -- voice_key
    SELECT COUNT(*) INTO _exists FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'video_task' AND COLUMN_NAME = 'voice_key';
    IF _exists = 0 THEN
        ALTER TABLE `video_task` ADD COLUMN `voice_key` VARCHAR(32) DEFAULT NULL
            COMMENT 'TTS 音色 key (cancan / ...)';
    END IF;

    -- ratio
    SELECT COUNT(*) INTO _exists FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'video_task' AND COLUMN_NAME = 'ratio';
    IF _exists = 0 THEN
        ALTER TABLE `video_task` ADD COLUMN `ratio` VARCHAR(8) DEFAULT NULL
            COMMENT '画面比例 9:16 / 16:9 / 1:1';
    END IF;

    -- cover_url
    SELECT COUNT(*) INTO _exists FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'video_task' AND COLUMN_NAME = 'cover_url';
    IF _exists = 0 THEN
        ALTER TABLE `video_task` ADD COLUMN `cover_url` VARCHAR(512) DEFAULT NULL
            COMMENT '视频封面图 URL（一般取 image_urls[0]）';
    END IF;
END$$
DELIMITER ;
CALL `_v014_alter_video_task`();
DROP PROCEDURE `_v014_alter_video_task`;

-- ========== 2. 新建 video_scene 表 ==========
CREATE TABLE IF NOT EXISTS `video_scene` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分镜编号',
  `task_id` bigint NOT NULL COMMENT '所属任务 id (video_task.id)',
  `scene_index` int NOT NULL COMMENT '分镜序号（0 起，按播放顺序）',
  `img_idx` int NOT NULL COMMENT '该幕用第几张图（0 起，对应 video_task.image_urls）',
  `image_summary` varchar(255) DEFAULT NULL COMMENT '一句话亮点（中文，≤ 20 字）',
  `narration` varchar(255) DEFAULT NULL COMMENT '口播台词（TTS 念，≤ 36 字）',
  `visual_prompt` varchar(1024) DEFAULT NULL COMMENT 'Seedance 视觉 prompt（英文，含运镜+风格）',
  `start_image_url` varchar(1024) DEFAULT NULL COMMENT '该幕起始帧图片 URL（从 image_urls[img_idx] 来）',
  `clip_task_id` varchar(128) DEFAULT NULL COMMENT '即梦/Seedance 远程 task_id（轮询接管用）',
  `clip_url` varchar(1024) DEFAULT NULL COMMENT '该幕生成视频 URL（OSS）',
  `audio_url` varchar(1024) DEFAULT NULL COMMENT 'TTS 配音 URL（如有）',
  `duration` int DEFAULT NULL COMMENT '该幕时长（秒）',
  `is_end_card` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否端卡（最后一幕，跳过 Seedance 走 sidecar /video/endcard）',
  `status` varchar(32) NOT NULL DEFAULT 'pending' COMMENT '状态: pending/video_creating/video_running/audio_muxing/endcard_building/ready/video_failed',
  `fail_reason` varchar(512) DEFAULT NULL COMMENT '失败原因',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  -- (task_id, scene_index) 联合唯一：一个 task 内每个 scene_index 只能一条，幂等用
  UNIQUE KEY `uk_task_scene` (`task_id`, `scene_index`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_clip_task_id` (`clip_task_id`),
  KEY `idx_status` (`status`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI视频任务分镜表（一个 video_task 多个 scene）';
