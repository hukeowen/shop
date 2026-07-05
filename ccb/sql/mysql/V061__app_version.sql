-- V061 App 自动升级：app_version 全局表 + 「平台运营」下 App版本管理菜单。幂等。
--   商户端 App 启动比对 versionCode，服务端更高则弹窗升级（可强更）。
--   表无 tenant_id（全局一套 APK），已在 application.yaml yudao.tenant.ignore-tables 登记 app_version。

CREATE TABLE IF NOT EXISTS `app_version` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `platform`      varchar(16)  NOT NULL DEFAULT 'android' COMMENT '平台 android/ios',
  `version_name`  varchar(32)  NOT NULL COMMENT '版本名，如 1.0.2',
  `version_code`  int          NOT NULL COMMENT '版本号（单调递增，比对用）',
  `download_url`  varchar(512) NOT NULL DEFAULT '' COMMENT 'APK 下载地址',
  `update_log`    varchar(2048)         DEFAULT '' COMMENT '更新说明',
  `force_update`  bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否强制更新',
  `file_size`     bigint                DEFAULT NULL COMMENT 'APK 大小（字节）',
  `status`        tinyint      NOT NULL DEFAULT 0 COMMENT '状态 0发布 1停用',
  `remark`        varchar(255)          DEFAULT '' COMMENT '备注',
  `creator`       varchar(64)           DEFAULT '' COMMENT '创建者',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`       varchar(64)           DEFAULT '' COMMENT '更新者',
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_platform_code` (`platform`, `version_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 版本（自动升级）';

-- 菜单：挂到「平台运营」(6500) 下
INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
VALUES
  (6590,'App版本管理','merchant:app-version:query',2,9,6500,'app-version','ep:cellphone','platform/appVersion/index','PlatformAppVersion',0,b'1',b'1',b'0','1',NOW(),'1',NOW(),b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),component=VALUES(component),component_name=VALUES(component_name),parent_id=VALUES(parent_id),deleted=b'0';

INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,creator,create_time,updater,update_time,deleted)
VALUES
  (6591,'新增版本','merchant:app-version:create',3,1,6590,'','','',0,b'1',b'1','1',NOW(),'1',NOW(),b'0'),
  (6592,'修改版本','merchant:app-version:update',3,2,6590,'','','',0,b'1',b'1','1',NOW(),'1',NOW(),b'0'),
  (6593,'删除版本','merchant:app-version:delete',3,3,6590,'','','',0,b'1',b'1','1',NOW(),'1',NOW(),b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),deleted=b'0';
