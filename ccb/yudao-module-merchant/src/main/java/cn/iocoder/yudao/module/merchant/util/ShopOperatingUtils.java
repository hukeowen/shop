package cn.iocoder.yudao.module.merchant.util;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 店铺营业状态三层闸门判定。
 *
 * <p>规则（强 → 弱）：
 * <ol>
 *   <li>today_open_at == 今天      未达 → HIDDEN（用户侧不显示，不可下单）</li>
 *   <li>!manual_closed            打烊开关 ON → HIDDEN</li>
 *   <li>now ∈ business_hours_json 不在时间段 → OUTSIDE_HOURS（显示但权重末尾，可下单）</li>
 * </ol>
 *
 * <p>该判定结果**用户侧排序 + 下单校验**都用，保证前后端语义一致。
 *
 * <p>{@code business_hours_json} 格式 {@code {"start":"09:00","end":"22:00","days":[1,2,3,4,5,6,7]}}：
 * <ul>
 *   <li>{@code days}：ISO 周序（1=周一 … 7=周日）；空数组等同 1..7</li>
 *   <li>{@code start/end}：HH:mm；{@code end < start} 表示跨夜（如 18:00-02:00）</li>
 *   <li>整个 json 为 NULL → 视为 24/7</li>
 * </ul>
 */
public final class ShopOperatingUtils {

    public enum OperatingStatus {
        /** 已打卡 + 未主动打烊 + 在营业时间内：用户侧正常显示，权重高 */
        OPEN,
        /** 已打卡 + 未主动打烊 + 不在营业时间内：用户侧显示但排序末尾（茶叶店深夜场景） */
        OUTSIDE_HOURS,
        /** 未打卡 OR 主动打烊：用户侧不显示，不可下单 */
        HIDDEN,
    }

    private ShopOperatingUtils() {}

    /** 当前状态判定（基于服务器当前时间 {@code LocalDateTime.now()}）。 */
    public static OperatingStatus computeStatus(ShopInfoDO shop) {
        return computeStatus(shop, LocalDateTime.now());
    }

    /** 当前状态判定（指定时间，便于单测） */
    public static OperatingStatus computeStatus(ShopInfoDO shop, LocalDateTime now) {
        if (shop == null) {
            return OperatingStatus.HIDDEN;
        }
        // ❶ 今日打卡：今天点过吗？
        LocalDate today = now.toLocalDate();
        if (shop.getTodayOpenAt() == null || !shop.getTodayOpenAt().equals(today)) {
            return OperatingStatus.HIDDEN;
        }
        // ❷ 主动打烊：开关 ON → 隐藏
        if (Boolean.TRUE.equals(shop.getManualClosed())) {
            return OperatingStatus.HIDDEN;
        }
        // ❸ 营业时间：null 视为 24/7
        if (shop.getBusinessHoursJson() == null || shop.getBusinessHoursJson().trim().isEmpty()) {
            return OperatingStatus.OPEN;
        }
        return inBusinessHours(shop.getBusinessHoursJson(), now)
                ? OperatingStatus.OPEN : OperatingStatus.OUTSIDE_HOURS;
    }

    /** 是否可下单（OPEN / OUTSIDE_HOURS 都可；HIDDEN 不可）。 */
    public static boolean canOrder(ShopInfoDO shop) {
        return computeStatus(shop) != OperatingStatus.HIDDEN;
    }

    /** 用户侧列表显示用：用户能看到吗（HIDDEN 不显示）。 */
    public static boolean isVisibleToUser(ShopInfoDO shop) {
        return computeStatus(shop) != OperatingStatus.HIDDEN;
    }

    /**
     * 解析 business_hours_json 判断 now 是否在营业时段。
     * 容错：任何解析失败一律按"在营业时间内"处理（避免后端 bug 导致全站店铺误判为打烊）。
     */
    static boolean inBusinessHours(String json, LocalDateTime now) {
        try {
            JSONObject obj = JSONUtil.parseObj(json);
            String startStr = obj.getStr("start");
            String endStr = obj.getStr("end");
            if (startStr == null || endStr == null) return true;

            // days 校验：空数组 / 未传 → 视为每天
            JSONArray daysArr = obj.getJSONArray("days");
            if (daysArr != null && !daysArr.isEmpty()) {
                int isoDay = now.getDayOfWeek().getValue(); // 1..7
                boolean hit = false;
                for (Object d : daysArr) {
                    if (Integer.parseInt(d.toString()) == isoDay) { hit = true; break; }
                }
                if (!hit) return false;
            }

            LocalTime start = LocalTime.parse(startStr);
            LocalTime end = LocalTime.parse(endStr);
            LocalTime cur = now.toLocalTime();

            if (end.isAfter(start) || end.equals(start)) {
                // 同日时段（含全天 00:00-00:00 → 等价 24/7）
                if (end.equals(start)) return true;
                return !cur.isBefore(start) && cur.isBefore(end);
            } else {
                // 跨夜时段（如 18:00-02:00）：在 [start, 24:00) 或 [00:00, end)
                return !cur.isBefore(start) || cur.isBefore(end);
            }
        } catch (Exception e) {
            // JSON 损坏 → 当作"营业中"，宁可多显示一家店也不要全站暴雷
            return true;
        }
    }
}
