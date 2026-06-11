-- =====================================================================
-- v047 在线支付开通（通联商户进件 merchantapi/add）补全【全类型】必填字段
--
-- 在 v046 基础上，按文档 1413 补齐进件全类型（个人/个体户/企业/其他组织/事业单位
-- + 对私/对公）所需字段：行业、区划、负责人、控股股东、企业经营信息、业务联系人、
-- 结算人、对公账户、以及各类证件照 TOS key。
--
-- 敏感字段（控股股东证件号 holder_id_no / 结算人证件号 settle_id_no）AES 加密存储，
-- 列宽放宽到 255 容纳密文。
--
-- 兼容 MySQL 8.0，存储过程守卫幂等，可重复执行。
-- =====================================================================

DROP PROCEDURE IF EXISTS `_v047_add_cols`;
DELIMITER $$
CREATE PROCEDURE `_v047_add_cols`()
BEGIN
    DECLARE _exist INT;

    -- 通用加列：列不存在才加
    -- 行业 / 客服电话 / 证件类型 / 经营地址 / 区划
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='mcc_id') THEN
        ALTER TABLE `shop_info` ADD COLUMN `mcc_id` varchar(16) NULL COMMENT '所属行业 mccid（通联附录8.4）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='service_phone') THEN
        ALTER TABLE `shop_info` ADD COLUMN `service_phone` varchar(32) NULL COMMENT '客服电话';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='legal_id_type') THEN
        ALTER TABLE `shop_info` ADD COLUMN `legal_id_type` varchar(8) NULL DEFAULT '01' COMMENT '法人证件类型 01身份证/03护照/...';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='bus_address') THEN
        ALTER TABLE `shop_info` ADD COLUMN `bus_address` varchar(255) NULL COMMENT '经营地址';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='district_code') THEN
        ALTER TABLE `shop_info` ADD COLUMN `district_code` varchar(16) NULL COMMENT '所在区代码（通联附录8.2 / 国标区划）';
    END IF;
    -- 负责人 / 结算方式 / 卡折类型
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='contact_person') THEN
        ALTER TABLE `shop_info` ADD COLUMN `contact_person` varchar(32) NULL COMMENT '商户负责人';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='contact_phone') THEN
        ALTER TABLE `shop_info` ADD COLUMN `contact_phone` varchar(32) NULL COMMENT '负责人电话';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='clear_mode') THEN
        ALTER TABLE `shop_info` ADD COLUMN `clear_mode` varchar(4) NULL DEFAULT '1' COMMENT '结算方式 0自主提现/1银行卡/2电子账户';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='acct_tp') THEN
        ALTER TABLE `shop_info` ADD COLUMN `acct_tp` varchar(4) NULL DEFAULT '00' COMMENT '卡折类型 00借记卡/01存折';
    END IF;
    -- 控股股东（企业/个体户）
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='holder_name') THEN
        ALTER TABLE `shop_info` ADD COLUMN `holder_name` varchar(64) NULL COMMENT '控股股东姓名';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='holder_id_no') THEN
        ALTER TABLE `shop_info` ADD COLUMN `holder_id_no` varchar(255) NULL COMMENT '控股股东证件号（AES 加密）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='holder_expire') THEN
        ALTER TABLE `shop_info` ADD COLUMN `holder_expire` varchar(16) NULL COMMENT '控股股东证件有效期';
    END IF;
    -- 企业经营信息（枚举）
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='register_fund') THEN
        ALTER TABLE `shop_info` ADD COLUMN `register_fund` varchar(4) NULL COMMENT '注册资本档 1-5';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='staff_total') THEN
        ALTER TABLE `shop_info` ADD COLUMN `staff_total` varchar(4) NULL COMMENT '员工人数档 1-5';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='operate_limit') THEN
        ALTER TABLE `shop_info` ADD COLUMN `operate_limit` varchar(4) NULL COMMENT '经营区域 1城区/2郊区/3边远';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='inspect') THEN
        ALTER TABLE `shop_info` ADD COLUMN `inspect` varchar(4) NULL COMMENT '经营地段 1商业区/2工业区/3住宅区';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='thr_cert_flag') THEN
        ALTER TABLE `shop_info` ADD COLUMN `thr_cert_flag` varchar(4) NULL DEFAULT '1' COMMENT '是否三证合一 0否/1是';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='organ_code') THEN
        ALTER TABLE `shop_info` ADD COLUMN `organ_code` varchar(64) NULL COMMENT '组织机构代码证号（三证不合一）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='organ_expire') THEN
        ALTER TABLE `shop_info` ADD COLUMN `organ_expire` varchar(16) NULL COMMENT '组织机构代码证有效期';
    END IF;
    -- 业务联系人（非个人）
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='bus_contact_person') THEN
        ALTER TABLE `shop_info` ADD COLUMN `bus_contact_person` varchar(32) NULL COMMENT '业务联系人';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='bus_contact_tel') THEN
        ALTER TABLE `shop_info` ADD COLUMN `bus_contact_tel` varchar(32) NULL COMMENT '业务联系人电话';
    END IF;
    -- 结算人（对私）/ 对公账户
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='settle_id_no') THEN
        ALTER TABLE `shop_info` ADD COLUMN `settle_id_no` varchar(255) NULL COMMENT '结算人证件号（对私，AES 加密）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='pub_acct_info') THEN
        ALTER TABLE `shop_info` ADD COLUMN `pub_acct_info` varchar(255) NULL COMMENT '对公账户信息';
    END IF;
    -- 证件照 TOS key
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='legal_hold_pic_key') THEN
        ALTER TABLE `shop_info` ADD COLUMN `legal_hold_pic_key` varchar(512) NULL COMMENT '经营者手持身份证照 TOS key（个人）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='biz_place_pic_key') THEN
        ALTER TABLE `shop_info` ADD COLUMN `biz_place_pic_key` varchar(512) NULL COMMENT '经营场所证明照 TOS key（个人）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='settle_bank_pic_key') THEN
        ALTER TABLE `shop_info` ADD COLUMN `settle_bank_pic_key` varchar(512) NULL COMMENT '结算账户照 TOS key（对私）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='acct_license_pic_key') THEN
        ALTER TABLE `shop_info` ADD COLUMN `acct_license_pic_key` varchar(512) NULL COMMENT '对公账户照 TOS key（对公）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='person_head_pic_key') THEN
        ALTER TABLE `shop_info` ADD COLUMN `person_head_pic_key` varchar(512) NULL COMMENT '经营者与门头合照 TOS key（非个人）';
    END IF;
END$$
DELIMITER ;
CALL `_v047_add_cols`();
DROP PROCEDURE IF EXISTS `_v047_add_cols`;
