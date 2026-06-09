-- =====================================================================
-- v045 线下转账收款（商户未开通在线支付通道时的兜底收款链路）
--
-- 背景：部分商户无法开通通联在线支付通道，只能走"线下转账"——
--   1) 商户在「店铺设置」上传 微信 / 支付宝 收款码
--   2) 顾客下单后看码付款，并上传付款凭证截图
--   3) 商户在订单详情核对凭证后，手动「确认收款」（复用既有 offline-confirm）
--
-- 改动：
--   A. shop_info 增加两列收款码 URL
--   B. 新增 shop_offline_payment 表（按 order_id 一条，记录顾客凭证 + 状态机）
--
-- 幂等：可重复执行（列/表存在时 IF NOT EXISTS 跳过）。
-- =====================================================================

-- A. 店铺收款码 -------------------------------------------------------
ALTER TABLE `shop_info`
    ADD COLUMN IF NOT EXISTS `wechat_pay_qr_url` varchar(512) NULL COMMENT '微信收款码图片 URL（线下转账用）' AFTER `tl_sign_type`,
    ADD COLUMN IF NOT EXISTS `alipay_pay_qr_url` varchar(512) NULL COMMENT '支付宝收款码图片 URL（线下转账用）' AFTER `wechat_pay_qr_url`;

-- B. 线下转账收款记录 -------------------------------------------------
CREATE TABLE IF NOT EXISTS `shop_offline_payment` (
    `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`      bigint        NOT NULL COMMENT '交易订单 ID（trade_order.id，全局唯一）',
    `user_id`       bigint        NOT NULL COMMENT '买家用户 ID',
    `pay_price`     int           NOT NULL DEFAULT 0 COMMENT '应付金额（分）',
    `proof_url`     varchar(512)  NULL COMMENT '顾客上传的付款凭证截图 URL',
    `pay_channel`   varchar(32)   NULL COMMENT '顾客付款渠道：wechat / alipay',
    `buyer_remark`  varchar(255)  NULL COMMENT '顾客备注（如转账后四位 / 留言）',
    `status`        tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0=待付款上传 1=已上传待确认 2=商户已确认 3=商户驳回',
    `submit_time`   datetime      NULL COMMENT '顾客提交凭证时间',
    `confirm_time`  datetime      NULL COMMENT '商户确认收款时间',
    `reject_reason` varchar(255)  NULL COMMENT '商户驳回原因',
    `creator`       varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`       varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`     bigint        NOT NULL DEFAULT 0 COMMENT '租户编号（商户 tenant）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_id` (`order_id`),
    KEY `idx_tenant_status` (`tenant_id`, `status`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='线下转账收款记录';
