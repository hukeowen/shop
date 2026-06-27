-- =====================================================================
-- V059 套餐商品（平台店 999 的 SPU 99001/99002）数据校正 + 配推3反1
--   背景：V042 把套餐做成 999 店商品，但商品价格/名称与 saas_package_config 不同步
--   （商品 ¥298/¥1688 + 旧名，配置 ¥598/¥1580 旺铺/旗舰版），且推N返1 残缺(N=1,ratios=[0])。
--   本迁移：① 同步商品/SKU 价格+名称 ② 配真正的「推3反1」(N=3，三人各 1/3，累计反约一份套餐)
--   注：幂等 UPDATE；固定 ID 仅对生产已存在的套餐商品生效。
-- =====================================================================
SET NAMES utf8mb4;

-- ① 同步套餐商品 SPU 价格 + 名称（与 saas_package_config 一致）
UPDATE `product_spu` SET name='拓小二旺铺版', price=59800,  market_price=59800  WHERE id=99001;
UPDATE `product_spu` SET name='拓小二旗舰版', price=158000, market_price=158000 WHERE id=99002;

-- ② 同步 SKU 价格（买家实付以 SKU 价为准）
UPDATE `product_sku` SET price=59800  WHERE id=99001 AND spu_id=99001;
UPDATE `product_sku` SET price=158000 WHERE id=99002 AND spu_id=99002;

-- ③ 配「推3反1」：推荐人推 3 个商户各买套餐 → 累计反约一份套餐金额到推广积分
--    tuijian_ratios 三段各 ~1/3（和=100）；direct_rate 保留（队列 COMPLETED 后的直推%）
UPDATE `product_promo_config`
   SET tuijian_enabled=1, tuijian_n=3, tuijian_ratios='[33.33,33.33,33.34]'
 WHERE spu_id IN (99001, 99002);

-- ④ 修 V042 套餐商品的 3 个建单阻断 bug（否则走交易订单 createOrder 报错/拒单）：
--    a) delivery_types 应为纯整数 '2'(自提)，V042 误写成 JSON '[2]' → trade 读商品 NumberFormatException
UPDATE `product_spu` SET delivery_types='2' WHERE id IN (99001,99002) AND delivery_types='[2]';
--    b) 套餐商品需「上架」(status=1) 才能下单；V042 建成 0(下架)
UPDATE `product_spu` SET status=1 WHERE id IN (99001,99002);
--    c) 平台店 999 缺 app_key='mall' 的支付应用（每个租户都有，999 漏建）→ 建支付单报「App 不存在」
INSERT INTO `pay_app` (app_key,name,status,remark,order_notify_url,refund_notify_url,transfer_notify_url,tenant_id,creator,create_time,updater,update_time,deleted)
SELECT 'mall','商城支付应用（平台SaaS套餐）',0,'平台套餐收款','','','',999,'admin',NOW(),'admin',NOW(),0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pay_app WHERE tenant_id=999 AND app_key='mall' AND deleted=0);
