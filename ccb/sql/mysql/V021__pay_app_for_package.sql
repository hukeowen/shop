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
-- notify_url 是 yudao pay 模块成功回调的 URL；本路径只在 wx_lite 走（allinpay_h5 跳过 pay_order）
DELETE FROM `pay_app` WHERE id = 10001;
INSERT INTO `pay_app`
  (`id`, `name`, `status`, `remark`, `app_key`,
   `order_notify_url`, `refund_notify_url`, `transfer_notify_url`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
  (10001, 'AI 视频套餐', 0, '平台 AI 视频套餐购买，钱进平台账户',
   'tanxiaer-package',
   'https://www.doupaidoudian.com/app-api/merchant/mini/video-quota/pay-callback',
   'https://www.doupaidoudian.com/app-api/merchant/mini/video-quota/pay-callback',
   'https://www.doupaidoudian.com/app-api/merchant/mini/video-quota/pay-callback',
   'system', NOW(), 'system', NOW(), b'0', 1);

-- 2. allinpay_h5 不是 yudao PayClient 渠道，不需要 seed pay_channel 行（之前的 config='{}'
--    被 PayClientConfigTypeHandler 反序列化时会撞 NPE）。pay 模块的 admin 渠道列表页因此
--    不会列出 allinpay_h5，但完全不影响业务流程 — allinpay_h5 由 AllinpayCashierService
--    独立桥接，跟 yudao PayClient 体系无关。
DELETE FROM `pay_channel` WHERE app_id = 10001 AND code = 'allinpay_h5';
