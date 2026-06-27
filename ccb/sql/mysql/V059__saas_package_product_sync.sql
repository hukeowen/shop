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
