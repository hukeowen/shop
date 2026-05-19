-- =============================================================================
-- V044：infra_api_access_log.operate_name 从 varchar(50) 扩到 varchar(255)
--
-- 原因：yudao 框架的 ApiAccessLogAspect 把 @Operation(summary=...) 写到这一列；
-- 上游 yudao 默认 50 字符够用，但本仓库不少新增 endpoint 的 summary 是中文长描述
-- （特别是 /promo/* 系列），UTF-8 三字节 × 30 字 已经超 90 bytes，触发
-- "Data truncation: Data too long for column 'operate_name'" → endpoint 500。
--
-- 影响：仅扩列，不破坏现有数据；行变长可忽略。
-- =============================================================================
SET NAMES utf8mb4;

ALTER TABLE infra_api_access_log
  MODIFY COLUMN operate_name VARCHAR(255) DEFAULT NULL COMMENT '操作名';
