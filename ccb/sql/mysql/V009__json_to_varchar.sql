-- description: 把使用 IntegerListTypeHandler/LongListTypeHandler 的字段由 JSON 改 VARCHAR
-- author: huliang
-- date: 2026-04-15
--
-- 这些字段存"1,2,3"逗号分隔串，原 JSON 类型语义错（不是合法 JSON）。
-- 幂等：仅当列当前 DATA_TYPE='json' 才 ALTER；已经是 varchar 的跳过。

DROP PROCEDURE IF EXISTS `migrate_json_to_varchar`;
DELIMITER $$
CREATE PROCEDURE `migrate_json_to_varchar`(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_size INT,
    IN p_default VARCHAR(64),
    IN p_comment VARCHAR(255)
)
BEGIN
    DECLARE v_type VARCHAR(64);
    SELECT DATA_TYPE INTO v_type FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column;
    IF v_type = 'json' THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` MODIFY COLUMN `', p_column,
            '` VARCHAR(', p_size, ') DEFAULT ''', p_default, ''' COMMENT ''', p_comment, '''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL migrate_json_to_varchar('product_spu',                          'delivery_types',          256,   '',   '配送方式数组');
CALL migrate_json_to_varchar('trade_delivery_express_template_charge','area_ids',               10240, '',   '配送区域编号列表');
CALL migrate_json_to_varchar('trade_delivery_express_template_free', 'area_ids',                10240, '',   '配送区域编号列表');
CALL migrate_json_to_varchar('trade_delivery_pick_up_store',         'verify_user_ids',         10240, '',   '核销管理员编号数组');
CALL migrate_json_to_varchar('trade_config',                         'brokerage_withdraw_types', 256,  '',   '佣金提现方式');
CALL migrate_json_to_varchar('trade_order',                          'give_coupon_ids',         2048,  '',   '赠送优惠券编号数组');
CALL migrate_json_to_varchar('promotion_reward_activity',            'product_scope_values',    10240, '',   '商品范围编号数组');
CALL migrate_json_to_varchar('promotion_seckill_activity',           'config_ids',              512,   '',   '秒杀时段id');
CALL migrate_json_to_varchar('promotion_seckill_product',            'config_ids',              512,   '',   '秒杀时段id');
CALL migrate_json_to_varchar('promotion_coupon_template',            'product_scope_values',    10240, '',   '商品范围编号数组');
CALL migrate_json_to_varchar('promotion_coupon',                     'product_scope_values',    10240, '',   '商品范围编号数组');
CALL migrate_json_to_varchar('member_user',                          'tag_ids',                 512,   '',   '会员标签列表');
CALL migrate_json_to_varchar('mp_user',                              'tag_ids',                 512,   '[]', '标签编号数组');

DROP PROCEDURE `migrate_json_to_varchar`;
