-- V035 平台商户初始化（tenant_id=999）
--
-- 用户授权（2026-05-11）：
--   手机号 15928962028 / 密码 yhzc123456
--   永久 + 全功能（service_expire_at=NULL, is_platform=1, service_package_level=PLATFORM）
--   通联凭据来自 docs/测试参数.txt：
--     cusId=56165105331VE5Z / appId=00240592 / signType=SM2
--     SM2 私钥 + SM2 公钥（PEM）+ RSA 私钥（备用）
--
-- 注意：tlRsaPrivateKey/tlSm2PrivateKey/tlSm2PublicKey 字段在 ShopInfoDO 上标了
-- @EncryptTypeHandler，需要 AES 加密后落库。本 SQL 留空，由 PlatformMerchantInitializer
-- 启动时检测并写入（用 hutool SecureUtil.aes 同样的 key）。
--
-- 本 SQL 只建：
--   1. system_tenant (id=999, name=摊小二平台)
--   2. member_user (mobile=15928962028, password=BCrypt(yhzc123456))
--   3. merchant_info (id=999, tenant=999, is_platform=1, service_expire_at=NULL)
--   4. shop_info (tenant=999, shop_name=摊小二平台, status=1)
-- 通联凭据 + 上架服务包 SPU 由 Java initializer 完成。

SET NAMES utf8mb4;

-- 1. system_tenant —— 平台商户租户
INSERT IGNORE INTO system_tenant (id, name, contact_name, contact_mobile, status, websites, package_id, expire_time, account_count, creator, create_time, updater, update_time, deleted)
VALUES (999, '摊小二平台', '平台运营', '15928962028', 0, '', 0, '2099-12-31 23:59:59', 100, '1', NOW(), '1', NOW(), b'0');

-- 2. member_user —— 平台商户账号（密码后续 java initializer 重写为 BCrypt）
--    password 字段先占位（NULL 或空），由 PlatformMerchantInitializer 用 passwordEncoder.encode 重新写
INSERT IGNORE INTO member_user (id, mobile, status, create_time, update_time, tenant_id, deleted)
VALUES (999, '15928962028', 0, NOW(), NOW(), 0, b'0');

-- 3. merchant_info —— 平台商户（永久 + 全功能）
INSERT IGNORE INTO merchant_info (id, name, contact_name, contact_phone, status, user_id, tenant_id, is_platform, service_expire_at, service_package_level, video_quota_remaining, create_time, update_time, deleted)
VALUES (999, '摊小二平台', '平台运营', '15928962028', 1, 999, 999, b'1', NULL, 'PLATFORM', 99999, NOW(), NOW(), b'0');

-- 4. shop_info —— 平台商户的店铺（通联凭据由 java initializer 加密写入）
INSERT IGNORE INTO shop_info (id, tenant_id, shop_name, status, avg_rating, sales_30d, balance, online_pay_enabled, pay_apply_status, tl_enabled, tl_sign_type, create_time, update_time, deleted)
VALUES (999, 999, '摊小二平台', 1, 5.0, 0, 0, b'1', 2, b'1', 'SM2', NOW(), NOW(), b'0');
