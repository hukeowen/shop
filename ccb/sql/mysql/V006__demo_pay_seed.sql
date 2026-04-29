-- =============================================================================
-- 演示用 mock 支付渠道自动 seed
--
-- 解决 SOP 第 II·B 节"演示前 1 分钟开 mock 支付"的手工步骤遗忘问题。
--
-- yudao trade 模块下单时按 yudao.trade.order.payAppKey="mall" 找 pay_app；
-- pay_app + pay_channel 都是 TenantBaseDO，按当前租户过滤。
-- 演示阶段商户租户 + admin 租户都需要能下单付款，因此每个新建租户都要有
-- app_key='mall' 的 pay_app + 一条 mock 渠道。
--
-- 实现：本脚本仅在 admin 租户（tenant_id=1）seed 一份 mall pay_app + mock 渠道；
-- 商户租户开通时（MerchantApplyServiceImpl / MerchantServiceImpl.createMerchantFromMember）
-- 由代码层调 tenantService.createTenant 自动从模板复制（yudao 默认行为）。
--
-- 上线生产：
--   1. UPDATE pay_channel SET status=1 WHERE code='mock' AND deleted=b'0';  -- 关 mock
--   2. PC 后台 → 支付管理 → 渠道 → 新增微信/支付宝真实渠道
-- =============================================================================

-- ----------------------------
-- 1. pay_app 应用：app_key='mall' 对应 yudao.trade.order.payAppKey
-- ----------------------------
INSERT IGNORE INTO `pay_app`
  (`id`, `app_key`, `name`, `status`, `remark`,
   `order_notify_url`, `refund_notify_url`, `transfer_notify_url`,
   `tenant_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (1, 'mall', '商城支付应用（演示用）', 0, '演示阶段 mock 渠道；上线前替换为微信/支付宝真实渠道',
   '', '', '',
   1, 'admin', NOW(), 'admin', NOW(), b'0');

-- ----------------------------
-- 2. pay_channel mock 渠道：关联到上面 pay_app id=1，config={} 即可（mock 不需要 KEY）
-- ----------------------------
INSERT IGNORE INTO `pay_channel`
  (`id`, `code`, `status`, `fee_rate`, `remark`, `app_id`, `config`,
   `tenant_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (1, 'mock', 0, 0, '演示用模拟支付（返回结果都是成功，方便日常流畅）',
   1, '{}',
   1, 'admin', NOW(), 'admin', NOW(), b'0');

-- ----------------------------
-- 备注：
--   - 商户租户开通时由 tenantService.createTenant 走租户模板初始化，
--     默认 yudao 会复制 admin 租户的 pay_app / pay_channel（不同版本行为略异，
--     如商户租户登录后看不到 mall 应用，可手工在 PC 后台 → 支付管理 切换租户复制一份）。
--   - mock 渠道的 PayClient 实现见 yudao-module-pay/.../mock/MockPayClient.java
--     注释直接写"模拟支付返回结果都是成功，方便大家日常流畅"。
-- =============================================================================
