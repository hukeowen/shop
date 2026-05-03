-- =============================================================================
-- V021: 平台 AI 视频套餐支付应用 seed
--
-- 解决 1021013003 "套餐支付应用未配置"：MerchantPackageOrderServiceImpl
-- 需要 yudao.merchant.package.pay-app-id 指向一个有效的 pay_app 行。
--
-- 固定 id=10001、固定 app_key=tanxiaer-package，跟 application-prod.yaml 对齐。
-- 幂等：DELETE+INSERT。
--
-- pay_app 是 yudao pay 模块的"支付应用"概念，本身只是一个壳；具体支付走哪个
-- 渠道（微信 / 通联 / 支付宝）由 pay_channel 配。本次平台套餐用通联 H5 收银台，
-- 所以预 seed 一条 pay_channel(code='allinpay_h5')；该 channel 配置的具体
-- 通联参数（appid / merch_no / md5key / RSA 私钥）都从 yaml 读，不入库。
-- =============================================================================

-- 1. pay_app（支付应用：套餐购买专用）
DELETE FROM `pay_app` WHERE id = 10001;
INSERT INTO `pay_app`
  (`id`, `name`, `status`, `remark`, `app_key`,
   `order_notify_url`, `refund_notify_url`, `transfer_notify_url`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
  (10001, 'AI 视频套餐', 0, '平台 AI 视频套餐购买，钱进平台账户',
   'tanxiaer-package',
   '/admin-api/merchant/admin-api/pay/notify/order',
   '/admin-api/merchant/admin-api/pay/notify/refund',
   '/admin-api/merchant/admin-api/pay/notify/transfer',
   'system', NOW(), 'system', NOW(), b'0', 1);

-- 2. pay_channel（占位行：让 pay_order 创建有 channel 可挂；
--    通联 H5 收银台不走 yudao 标准 PayClient，参数都在 yaml，code 为 allinpay_h5）
DELETE FROM `pay_channel` WHERE app_id = 10001 AND code = 'allinpay_h5';
INSERT INTO `pay_channel`
  (`code`, `status`, `remark`, `fee_rate`, `app_id`,
   `config`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
  ('allinpay_h5', 0, '通联收付通 H5 收银台（参数从 yaml 读取，不入库）',
   0, 10001, '{}', 'system', NOW(), 'system', NOW(), b'0', 1);
