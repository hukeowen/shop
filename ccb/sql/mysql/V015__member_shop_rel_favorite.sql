-- description: member_shop_rel 加 favorite 列（C 端店铺收藏夹）
-- author: huliang
-- date: 2026-05-02

DROP PROCEDURE IF EXISTS `_v015_alter_member_shop_rel`;
DELIMITER $$
CREATE PROCEDURE `_v015_alter_member_shop_rel`()
BEGIN
    DECLARE _exists INT;
    SELECT COUNT(*) INTO _exists FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member_shop_rel' AND COLUMN_NAME = 'favorite';
    IF _exists = 0 THEN
        ALTER TABLE `member_shop_rel`
            ADD COLUMN `favorite` BIT(1) NOT NULL DEFAULT b'0' COMMENT 'C 端店铺收藏（0=否 1=是）',
            ADD INDEX `idx_user_favorite` (`user_id`, `favorite`);
    END IF;
END$$
DELIMITER ;
CALL `_v015_alter_member_shop_rel`();
DROP PROCEDURE `_v015_alter_member_shop_rel`;
