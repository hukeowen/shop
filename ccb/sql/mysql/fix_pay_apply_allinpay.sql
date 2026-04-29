-- Phase 1.9: 通联进件业务流水号字段 + 状态 4=通联进件中
-- shop_info 表追加 tl_open_order_id 列（幂等）
--
-- 用途：审核通过后调通联开户 API → 拿 outOrderId 存这里 → 通联异步回调按
-- outOrderId 反查店铺 → 写真 tlMchId / tlMchKey
--
-- pay_apply_status 状态机：
--   0 未申请
--   1 审核中（商户提交进件资质）
--   2 已开通（通联回调写真 tlMchId 后置位）
--   3 已驳回（PC 后台驳回 / 通联拒绝）
--   4 通联进件中（PC 后台审核通过，等通联开户回调）

DROP PROCEDURE IF EXISTS `shop_info_add_col_allinpay`;
DELIMITER $$
CREATE PROCEDURE `shop_info_add_col_allinpay`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'tl_open_order_id') THEN
        ALTER TABLE `shop_info` ADD COLUMN `tl_open_order_id` VARCHAR(64) NULL DEFAULT NULL COMMENT '通联进件业务流水号（outOrderId）';
        ALTER TABLE `shop_info` ADD INDEX `idx_tl_open_order_id` (`tl_open_order_id`);
    END IF;
END$$
DELIMITER ;
CALL `shop_info_add_col_allinpay`();
DROP PROCEDURE `shop_info_add_col_allinpay`;
