-- V039: 店铺营业状态三层闸门
--
-- 业务规则（强 → 弱）：
--   ❶ today_open_at == 今天      未达 → 用户侧不显示，无法下单
--   ❷ manual_closed = 0         主动打烊 → 用户侧不显示，无法下单
--   ❸ now ∈ business_hours_json 不在时间段 → 显示但排序末尾（茶叶店深夜场景）
--
-- 兼容：原 business_hours varchar 不删，admin/老接口继续读；新接口读 business_hours_json。

ALTER TABLE shop_info
    ADD COLUMN business_hours_json varchar(512) DEFAULT NULL
        COMMENT '营业时间 JSON：{"start":"09:00","end":"22:00","days":[1,2,3,4,5,6,7]}（days 1=周一 … 7=周日；未设置等同 24/7）',
    ADD COLUMN manual_closed bit(1) NOT NULL DEFAULT b'0'
        COMMENT '商户主动打烊开关：1=已打烊（用户侧不显示且不可下单，无视其他闸门）',
    ADD COLUMN today_open_at date DEFAULT NULL
        COMMENT '今日营业打卡日期：== CURRENT_DATE() 时算"今日已打卡"。商户每日进首页主动点"开始营业"后写入';

-- 默认值兼容：已存在的店全部视为"今日未打卡"，第一次进商户首页时引导打卡。
-- manual_closed 全部 = 0 (b'0')，没有打烊。
-- business_hours_json 全部 NULL → 用户侧解析 NULL 时视为 24/7（兼容老店）。
