-- V040: 店铺行业类型（AI 视频精准定制用）
--
-- 用途：AI 视频 BFF 根据 business_type 注入不同行业的 cinematography prompt：
--   · bbq           烧烤夜市：sizzling oil / charcoal embers / smoke curling
--   · snack         小吃快餐：steam bursting / golden batter / hand wrapping
--   · drink         奶茶咖啡：condensation dripping / cheese foam pouring / latte art
--   · restaurant    正餐餐厅：plating shot / pour shot / steam reveal
--   · fruit         水果生鲜：knife cut / juice splash / fresh dew
--   · super         超市便利店：shelf dolly / fluorescent flicker / hand grabbing
--   · tea           茶叶酒水：tea leaf macro / pouring stream / steam curl
--   · tea_house     茶楼茶馆：bamboo screen / gaiwan ceremony / candle warm light
--   · bakery        烘焙甜品：dough kneading / golden crust / sugar scattering
--   · clothing      服装鞋帽：fabric flow / hanger rotation / texture macro
--   · massage       按摩 SPA：candle flicker / hand motion / aromatherapy steam
--   · beauty        美容美发：mirror reflection / brush motion / product macro
--   · other         其他：通用 cozy_explore 风格
--
-- 商户首次进 shop-edit 时弹引导选；未选 = NULL，BFF 走旧路径（仍可生成，但泛化）。

ALTER TABLE shop_info
    ADD COLUMN business_type varchar(32) DEFAULT NULL
        COMMENT '行业类型 key：bbq/snack/drink/restaurant/fruit/super/tea/tea_house/bakery/clothing/massage/beauty/other；NULL=未选';
