package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantFunnelRespVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantHeatmapRespVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantMemberItemRespVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantProductRankRespVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantSalesStatsRespVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MemberShopRelDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MemberShopRelMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.MemberWithdrawApplyMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoDeductionRecordMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopPromoRecordMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopQueueEventMapper;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class MerchantStatsServiceImpl implements MerchantStatsService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Resource
    private TradeOrderMapper tradeOrderMapper;
    @Resource
    private cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper tradeOrderItemMapper;
    @Resource
    private AfterSaleMapper afterSaleMapper;
    @Resource
    private ShopPromoRecordMapper promoRecordMapper;
    @Resource
    private ShopPromoDeductionRecordMapper deductionRecordMapper;
    @Resource
    private ShopQueueEventMapper queueEventMapper;
    @Resource
    private MemberShopRelMapper memberShopRelMapper;
    @Resource
    private ShopInfoMapper shopInfoMapper;
    @Resource
    private MemberWithdrawApplyMapper memberWithdrawApplyMapper;
    @Resource
    private cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper memberUserMapper;
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserStarMapper shopUserStarMapper;
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserReferralMapper shopUserReferralMapper;
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopQueuePositionMapper shopQueuePositionMapper;
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopReferralContributionMapper shopReferralContributionMapper;

    @Override
    public AppMerchantSalesStatsRespVO getSalesStats(String period) {
        if (period == null || period.isEmpty()) period = "month";
        LocalDate today = LocalDate.now();
        LocalDate periodStart;
        LocalDate periodEnd = today;
        String label;
        switch (period) {
            case "day":
                periodStart = today;
                label = today.toString();
                break;
            case "week":
                periodStart = today.with(DayOfWeek.MONDAY);
                label = periodStart + " ~ " + today;
                break;
            case "year":
                periodStart = today.withDayOfYear(1);
                label = String.valueOf(today.getYear());
                break;
            case "month":
            default:
                periodStart = today.withDayOfMonth(1);
                label = today.format(MONTH_FMT);
                period = "month";
                break;
        }
        LocalDateTime start = LocalDateTime.of(periodStart, LocalTime.MIN);
        LocalDateTime end = LocalDateTime.of(periodEnd, LocalTime.MAX);

        // 总览
        long[] orderAgg = aggregateOrders(start, end);
        long salesAmount = orderAgg[0];
        long actualPayAmount = orderAgg[1];
        long orderCount = orderAgg[2];
        long promoIssued = sumPromoRecord(start, end);
        long poolDeposit = sumQueueEvent(start, end, "POOL_DEPOSIT_V8");
        long refundAmount = sumRefund(start, end);
        // 净收入 = 实付 - 退款。推广积分是商户内部营销让利凭证，不计入财务净收入扣项
        long netIncome = actualPayAmount - refundAmount;

        // 资金分布
        long pendingBalance = sumMemberShopRelBalance();
        long withdrawnAmount = sumWithdrawn();
        long pendingWithdrawCount = countPendingWithdraw();
        long shopBalance = getShopBalance();

        // 趋势
        List<String> labels = new ArrayList<>();
        List<Long> trendSales = new ArrayList<>();
        List<Long> trendActualPay = new ArrayList<>();
        buildTrend(period, periodStart, periodEnd, labels, trendSales, trendActualPay);

        // 客户洞察
        long[] customer = customerInsights(start, end);
        long avgOrderValue = orderCount > 0 ? actualPayAmount / orderCount : 0;
        double repurchaseRate = customer[2] > 0 ? (customer[3] * 100.0 / customer[2]) : 0.0;

        return AppMerchantSalesStatsRespVO.builder()
                .period(period)
                .periodLabel(label)
                .salesAmount(salesAmount)
                .actualPayAmount(actualPayAmount)
                .promoIssued(promoIssued)
                .poolDeposit(poolDeposit)
                .refundAmount(refundAmount)
                .netIncome(netIncome)
                .orderCount(orderCount)
                .pendingBalance(pendingBalance)
                .withdrawnAmount(withdrawnAmount)
                .pendingWithdrawCount(pendingWithdrawCount)
                .shopBalance(shopBalance)
                .trendLabels(labels)
                .trendSales(trendSales)
                .trendActualPay(trendActualPay)
                .avgOrderValue(avgOrderValue)
                .repurchaseRate(repurchaseRate)
                .newCustomerOrders(customer[0])
                .oldCustomerOrders(customer[1])
                .referralOrderRatio(customer[4] > 0 ? (customer[5] * 100.0 / customer[4]) : 0.0)
                .build();
    }

    /**
     * 一次性聚合订单：[salesAmount, actualPayAmount, orderCount]
     * salesAmount = sum(price * count)（按 trade_order_item 定价合计）
     * actualPayAmount = sum(pay_price)（订单实付）
     * status > 0 排除未支付
     */
    private long[] aggregateOrders(LocalDateTime start, LocalDateTime end) {
        QueryWrapper<TradeOrderDO> w = new QueryWrapper<>();
        w.select("IFNULL(SUM(pay_price),0) AS actual",
                "IFNULL(SUM(total_price),0) AS sales",
                "COUNT(*) AS cnt");
        w.between("create_time", start, end);
        w.gt("status", 0);
        List<Map<String, Object>> rows = tradeOrderMapper.selectMaps(w);
        if (rows == null || rows.isEmpty()) return new long[]{0, 0, 0};
        Map<String, Object> r = rows.get(0);
        long actual = parseLong(r.get("actual"));
        long sales = parseLong(r.get("sales"));
        long cnt = parseLong(r.get("cnt"));
        return new long[]{ sales, actual, cnt };
    }

    private long sumPromoRecord(LocalDateTime start, LocalDateTime end) {
        QueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoRecordDO> w = new QueryWrapper<>();
        w.select("IFNULL(SUM(amount),0) AS s");
        w.between("create_time", start, end);
        w.gt("amount", 0);
        List<Map<String, Object>> rows = promoRecordMapper.selectMaps(w);
        if (rows == null || rows.isEmpty()) return 0L;
        return parseLong(rows.get(0).get("s"));
    }

    private long sumQueueEvent(LocalDateTime start, LocalDateTime end, String type) {
        QueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueueEventDO> w = new QueryWrapper<>();
        w.select("IFNULL(SUM(amount),0) AS s");
        w.between("create_time", start, end);
        w.eq("event_type", type);
        List<Map<String, Object>> rows = queueEventMapper.selectMaps(w);
        if (rows == null || rows.isEmpty()) return 0L;
        return parseLong(rows.get(0).get("s"));
    }

    private long sumRefund(LocalDateTime start, LocalDateTime end) {
        QueryWrapper<AfterSaleDO> w = new QueryWrapper<>();
        w.select("IFNULL(SUM(refund_price),0) AS s");
        w.between("create_time", start, end);
        w.eq("status", 30); // STATUS_AGREE_REFUND（已退款）
        List<Map<String, Object>> rows = afterSaleMapper.selectMaps(w);
        if (rows == null || rows.isEmpty()) return 0L;
        return parseLong(rows.get(0).get("s"));
    }

    private long sumMemberShopRelBalance() {
        QueryWrapper<MemberShopRelDO> w = new QueryWrapper<>();
        w.select("IFNULL(SUM(balance),0) AS s");
        List<Map<String, Object>> rows = memberShopRelMapper.selectMaps(w);
        if (rows == null || rows.isEmpty()) return 0L;
        return parseLong(rows.get(0).get("s"));
    }

    private long sumWithdrawn() {
        try {
            QueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.MemberWithdrawApplyDO> w = new QueryWrapper<>();
            w.select("IFNULL(SUM(amount),0) AS s");
            w.eq("status", 20); // 已支付
            List<Map<String, Object>> rows = memberWithdrawApplyMapper.selectMaps(w);
            if (rows == null || rows.isEmpty()) return 0L;
            return parseLong(rows.get(0).get("s"));
        } catch (Exception e) {
            // 不静默吞：警告级 log，避免对账数据失真却无察觉
            log.warn("[sumWithdrawn] member_withdraw_apply 查询失败，返回 0（请人工核对）: {}", e.getMessage(), e);
            return 0L;
        }
    }

    private long countPendingWithdraw() {
        try {
            return memberWithdrawApplyMapper.selectCount(
                new LambdaQueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.MemberWithdrawApplyDO>()
                    .eq(cn.iocoder.yudao.module.merchant.dal.dataobject.MemberWithdrawApplyDO::getStatus, 0));
        } catch (Exception e) {
            log.warn("[countPendingWithdraw] member_withdraw_apply 查询失败，返回 0: {}", e.getMessage(), e);
            return 0L;
        }
    }

    private long getShopBalance() {
        try {
            cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO si = shopInfoMapper.selectOne(
                new LambdaQueryWrapper<>());
            return si == null || si.getBalance() == null ? 0L : si.getBalance();
        } catch (Exception e) {
            return 0L;
        }
    }

    private void buildTrend(String period, LocalDate periodStart, LocalDate periodEnd,
                             List<String> labels, List<Long> trendSales, List<Long> trendActualPay) {
        // 决定 trend 粒度
        boolean byMonth = "year".equals(period);
        int barCount;
        if (byMonth) {
            barCount = 12;
            for (int i = 0; i < barCount; i++) {
                LocalDate d = LocalDate.of(periodStart.getYear(), i + 1, 1);
                labels.add(String.format("%02d", i + 1) + "月");
            }
        } else {
            // day / week / month 都按日展开
            long days = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
            barCount = (int) Math.min(31, Math.max(1, days));
            for (int i = 0; i < barCount; i++) {
                LocalDate d = periodStart.plusDays(i);
                labels.add(d.format(DAY_FMT));
            }
        }
        // 一次性查所有日期
        QueryWrapper<TradeOrderDO> w = new QueryWrapper<>();
        if (byMonth) {
            w.select("DATE_FORMAT(create_time, '%Y-%m') AS d",
                    "IFNULL(SUM(total_price),0) AS sales",
                    "IFNULL(SUM(pay_price),0) AS actual");
            w.between("create_time", LocalDateTime.of(periodStart, LocalTime.MIN), LocalDateTime.of(periodEnd, LocalTime.MAX));
            w.gt("status", 0);
            w.groupBy("DATE_FORMAT(create_time, '%Y-%m')");
        } else {
            w.select("DATE(create_time) AS d",
                    "IFNULL(SUM(total_price),0) AS sales",
                    "IFNULL(SUM(pay_price),0) AS actual");
            w.between("create_time", LocalDateTime.of(periodStart, LocalTime.MIN), LocalDateTime.of(periodEnd, LocalTime.MAX));
            w.gt("status", 0);
            w.groupBy("DATE(create_time)");
        }
        List<Map<String, Object>> rows = tradeOrderMapper.selectMaps(w);
        Map<String, long[]> byKey = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> r : rows) {
                String d = r.get("d") == null ? "" : r.get("d").toString();
                if (d.length() > 10) d = d.substring(0, 10);
                byKey.put(d, new long[]{ parseLong(r.get("sales")), parseLong(r.get("actual")) });
            }
        }
        for (int i = 0; i < barCount; i++) {
            String key;
            if (byMonth) {
                key = periodStart.getYear() + "-" + String.format("%02d", i + 1);
            } else {
                key = periodStart.plusDays(i).toString();
            }
            long[] v = byKey.getOrDefault(key, new long[]{0L, 0L});
            trendSales.add(v[0]);
            trendActualPay.add(v[1]);
        }
    }

    /**
     * 客户洞察聚合：[newOrderCount, oldOrderCount, totalCustomers, repurchaseCustomers, totalOrders, referralOrders]
     * 严格定义：
     *   - 新客订单 = order.user_id 在该周期内首次下单（在该周期之前未下过任何已支付订单）
     *   - 老客订单 = 在该周期之前已下过订单的用户在本周期下的订单
     *   - 复购客户数 = 同一周期内下单 ≥ 2 次的用户（不再混入"老客"）
     *   - 推荐订单 = 该用户在 shop_user_referral 有 parent_user_id > 0
     *
     * 性能优化：用 SQL GROUP BY user_id + COUNT 直接聚合，不再把所有订单 user_id 拉到内存。
     */
    private long[] customerInsights(LocalDateTime start, LocalDateTime end) {
        // 1. SQL 直接 GROUP BY user_id 拿每用户在周期内的下单数（避免内存 group by）
        QueryWrapper<TradeOrderDO> w = new QueryWrapper<>();
        w.select("user_id AS uid", "COUNT(*) AS cnt");
        w.between("create_time", start, end);
        w.gt("status", 0);
        w.groupBy("user_id");
        List<Map<String, Object>> rows = tradeOrderMapper.selectMaps(w);
        if (rows == null || rows.isEmpty()) {
            return new long[]{0, 0, 0, 0, 0, 0};
        }
        Map<Long, Integer> userOrderCount = new HashMap<>(rows.size() * 2);
        for (Map<String, Object> r : rows) {
            userOrderCount.put(parseLong(r.get("uid")), (int) parseLong(r.get("cnt")));
        }
        long totalCustomers = userOrderCount.size();

        // 2. 老客 = 周期前已下过订单的 user
        QueryWrapper<TradeOrderDO> historyW = new QueryWrapper<>();
        historyW.select("DISTINCT user_id");
        historyW.lt("create_time", start);
        historyW.gt("status", 0);
        historyW.in("user_id", userOrderCount.keySet());
        List<Map<String, Object>> hRows = tradeOrderMapper.selectMaps(historyW);
        java.util.Set<Long> historyUsers = new java.util.HashSet<>();
        if (hRows != null) for (Map<String, Object> r : hRows) {
            historyUsers.add(parseLong(r.get("user_id")));
        }

        long newOrders = 0, oldOrders = 0, totalOrders = 0, repurchase = 0;
        for (Map.Entry<Long, Integer> e : userOrderCount.entrySet()) {
            int c = e.getValue();
            totalOrders += c;
            boolean isOld = historyUsers.contains(e.getKey());
            if (isOld) {
                oldOrders += c;
            } else {
                newOrders += 1;
                oldOrders += (c - 1);
            }
            // 严格复购：仅同周期下单 ≥ 2 次（"老客"是另一个独立指标）
            if (c >= 2) repurchase++;
        }

        // 3. 推荐订单：直接 @Resource 注入的 mapper（失败启动期就报错，不再静默吞）
        QueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserReferralDO> rw = new QueryWrapper<>();
        rw.select("user_id");
        rw.in("user_id", userOrderCount.keySet());
        rw.gt("parent_user_id", 0);
        List<Map<String, Object>> rRows = shopUserReferralMapper.selectMaps(rw);
        java.util.Set<Long> referralUsers = new java.util.HashSet<>();
        if (rRows != null) for (Map<String, Object> r : rRows) {
            referralUsers.add(parseLong(r.get("user_id")));
        }
        long referralOrders = 0;
        for (Long u : userOrderCount.keySet()) {
            if (referralUsers.contains(u)) referralOrders += userOrderCount.get(u);
        }

        return new long[]{ newOrders, oldOrders, totalCustomers, repurchase, totalOrders, referralOrders };
    }

    private long parseLong(Object o) {
        if (o == null) return 0L;
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return 0L; }
    }

    // =============== 商品排行 ===============

    @Override
    public List<AppMerchantProductRankRespVO> getProductRank(String period, String sort, int limit) {
        if (limit <= 0) limit = 20;
        if (limit > 100) limit = 100;
        LocalDate today = LocalDate.now();
        LocalDate periodStart;
        switch (period == null ? "month" : period) {
            case "day":   periodStart = today; break;
            case "week":  periodStart = today.with(DayOfWeek.MONDAY); break;
            case "year":  periodStart = today.withDayOfYear(1); break;
            case "month":
            default:      periodStart = today.withDayOfMonth(1); break;
        }
        LocalDateTime start = LocalDateTime.of(periodStart, LocalTime.MIN);
        LocalDateTime end = LocalDateTime.of(today, LocalTime.MAX);
        // HIGH-1 修：先查 status > 0（已支付）的订单 id 列表，再用 IN 限制 trade_order_item，
        // 避免未付款订单虚增销量
        QueryWrapper<TradeOrderDO> ow = new QueryWrapper<>();
        ow.select("id");
        ow.between("create_time", start, end);
        ow.gt("status", 0);
        List<Map<String, Object>> orderRows = tradeOrderMapper.selectMaps(ow);
        List<AppMerchantProductRankRespVO> list = new ArrayList<>();
        if (orderRows == null || orderRows.isEmpty()) return list;
        java.util.Set<Long> orderIds = new java.util.HashSet<>(orderRows.size());
        for (Map<String, Object> r : orderRows) orderIds.add(parseLong(r.get("id")));

        String orderByCol = "amount".equals(sort) ? "salesAmount" : "salesCount";
        QueryWrapper<cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO> w = new QueryWrapper<>();
        w.select("spu_id AS spuId",
                "MAX(spu_name) AS spuName",
                "MAX(pic_url) AS picUrl",
                "IFNULL(SUM(count),0) AS salesCount",
                "IFNULL(SUM(price * count),0) AS salesAmount",
                "IFNULL(SUM(pay_price),0) AS actualPay");
        w.in("order_id", orderIds);
        w.groupBy("spu_id");
        w.orderByDesc(orderByCol);
        // limit 已 clamp 到 [1, 100]，安全
        w.last("LIMIT " + limit);
        List<Map<String, Object>> rows = tradeOrderItemMapper.selectMaps(w);
        if (rows == null) return list;
        int rank = 1;
        for (Map<String, Object> r : rows) {
            list.add(AppMerchantProductRankRespVO.builder()
                .rank(rank++)
                .spuId(parseLong(r.get("spuId")))
                .name(r.get("spuName") == null ? null : r.get("spuName").toString())
                .picUrl(r.get("picUrl") == null ? null : r.get("picUrl").toString())
                .salesCount(parseLong(r.get("salesCount")))
                .salesAmount(parseLong(r.get("salesAmount")))
                .actualPayAmount(parseLong(r.get("actualPay")))
                .build());
        }
        return list;
    }

    // =============== 店铺会员列表 ===============

    @Override
    public List<AppMerchantMemberItemRespVO> listMembers(int pageNo, int pageSize) {
        if (pageNo <= 0) pageNo = 1;
        if (pageSize <= 0 || pageSize > 100) pageSize = 20;
        int offset = (pageNo - 1) * pageSize;
        // 1. 拉本店所有会员（按加入时间倒序）
        List<MemberShopRelDO> rels = memberShopRelMapper.selectList(
            new LambdaQueryWrapper<MemberShopRelDO>()
                .orderByDesc(MemberShopRelDO::getCreateTime)
                .last("LIMIT " + offset + ", " + pageSize));
        if (rels == null || rels.isEmpty()) return new java.util.ArrayList<>();
        java.util.Set<Long> userIds = new java.util.HashSet<>();
        for (MemberShopRelDO r : rels) userIds.add(r.getUserId());

        // 2. 拉 member_user（取 mobile / nickname）— member_user 是平台级表（@TenantIgnore）
        Map<Long, cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO> userMap = new HashMap<>();
        cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.executeIgnore(() -> {
            List<cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO> users = memberUserMapper.selectBatchIds(userIds);
            if (users != null) for (cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO u : users)
                userMap.put(u.getId(), u);
        });

        // 3. 拉每个 user 的下单数 + 总金额
        Map<Long, long[]> orderByUser = new HashMap<>();
        QueryWrapper<TradeOrderDO> ow = new QueryWrapper<>();
        ow.select("user_id AS uid", "COUNT(*) AS cnt", "IFNULL(SUM(pay_price),0) AS amt");
        ow.in("user_id", userIds);
        ow.gt("status", 0);
        ow.groupBy("user_id");
        List<Map<String, Object>> oRows = tradeOrderMapper.selectMaps(ow);
        if (oRows != null) for (Map<String, Object> r : oRows) {
            orderByUser.put(parseLong(r.get("uid")), new long[]{ parseLong(r.get("cnt")), parseLong(r.get("amt")) });
        }

        // 4. 拉 referral 关系
        Map<Long, Long> parentByUser = new HashMap<>();
        Map<Long, Long> invitedCountByUser = new HashMap<>();
        QueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserReferralDO> rw = new QueryWrapper<>();
        rw.in("user_id", userIds);
        List<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserReferralDO> refs = shopUserReferralMapper.selectList(rw);
        if (refs != null) for (cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserReferralDO ref : refs) {
            parentByUser.put(ref.getUserId(), ref.getParentUserId());
        }
        // 邀请数：parent_user_id ∈ userIds 的行数（本店 referral 表）
        QueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserReferralDO> iw = new QueryWrapper<>();
        iw.select("parent_user_id AS pid", "COUNT(*) AS cnt");
        iw.in("parent_user_id", userIds);
        iw.gt("parent_user_id", 0);
        iw.groupBy("parent_user_id");
        List<Map<String, Object>> iRows = shopUserReferralMapper.selectMaps(iw);
        if (iRows != null) for (Map<String, Object> r : iRows) {
            invitedCountByUser.put(parseLong(r.get("pid")), parseLong(r.get("cnt")));
        }

        // 5. 拉 user_star spu_id=0（全局账户）
        Map<Long, cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO> starByUser = new HashMap<>();
        QueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO> sw = new QueryWrapper<>();
        sw.in("user_id", userIds);
        sw.eq("spu_id", 0);
        List<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO> stars = shopUserStarMapper.selectList(sw);
        if (stars != null) for (cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO s : stars) {
            starByUser.put(s.getUserId(), s);
        }

        // 6. 组装
        List<AppMerchantMemberItemRespVO> result = new ArrayList<>();
        for (MemberShopRelDO r : rels) {
            cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO u = userMap.get(r.getUserId());
            long[] orderInfo = orderByUser.getOrDefault(r.getUserId(), new long[]{0L, 0L});
            cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO star = starByUser.get(r.getUserId());
            String mobile = u != null && u.getMobile() != null ? maskMobile(u.getMobile()) : "";
            result.add(AppMerchantMemberItemRespVO.builder()
                .userId(r.getUserId())
                .mobile(mobile)
                .nickname(u != null ? u.getNickname() : "")
                .joinedAt(r.getCreateTime())
                .balance(r.getBalance() == null ? 0L : Long.valueOf(r.getBalance()))
                .promoPointBalance(star == null || star.getPromoPointBalance() == null ? 0L : star.getPromoPointBalance())
                .consumePointBalance(star == null || star.getConsumePointBalance() == null ? 0L : star.getConsumePointBalance())
                .currentStar(star == null ? 0 : (star.getCurrentStar() == null ? 0 : star.getCurrentStar()))
                .orderCount(orderInfo[0])
                .orderAmount(orderInfo[1])
                .parentUserId(parentByUser.get(r.getUserId()))
                .invitedCount(invitedCountByUser.getOrDefault(r.getUserId(), 0L))
                .build());
        }
        return result;
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) return mobile;
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    // =============== 时段热力图 ===============

    @Override
    public AppMerchantHeatmapRespVO getHourlyHeatmap(String period) {
        if (period == null) period = "month";
        LocalDate today = LocalDate.now();
        LocalDate start;
        switch (period) {
            case "day":   start = today; break;
            case "week":  start = today.with(DayOfWeek.MONDAY); break;
            case "month":
            default:      start = today.withDayOfMonth(1); period = "month"; break;
        }
        QueryWrapper<TradeOrderDO> w = new QueryWrapper<>();
        w.select("HOUR(create_time) AS h", "COUNT(*) AS c");
        w.between("create_time", LocalDateTime.of(start, LocalTime.MIN), LocalDateTime.of(today, LocalTime.MAX));
        w.gt("status", 0);
        w.groupBy("HOUR(create_time)");
        List<Map<String, Object>> rows = tradeOrderMapper.selectMaps(w);
        long[] hourly = new long[24];
        if (rows != null) for (Map<String, Object> r : rows) {
            int h = Integer.parseInt(r.get("h").toString());
            if (h >= 0 && h < 24) hourly[h] = parseLong(r.get("c"));
        }
        List<Long> hourlyList = new ArrayList<>(24);
        long total = 0;
        for (int i = 0; i < 24; i++) { hourlyList.add(hourly[i]); total += hourly[i]; }
        return AppMerchantHeatmapRespVO.builder()
            .period(period).hourly(hourlyList).totalOrders(total).build();
    }

    // =============== 推 N 反 1 漏斗 ===============

    @Override
    public AppMerchantFunnelRespVO getReferralFunnel(Long spuId) {
        // 用 Supplier 每次构造新 wrapper，避免依赖 mybatis-plus 的 clone() 跨版本不稳定
        java.util.function.Supplier<LambdaQueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueuePositionDO>> base = () -> {
            LambdaQueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueuePositionDO> w = new LambdaQueryWrapper<>();
            if (spuId != null) w.eq(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueuePositionDO::getSpuId, spuId);
            return w;
        };

        long activated = shopQueuePositionMapper.selectCount(base.get());
        long completed = shopQueuePositionMapper.selectCount(base.get()
            .eq(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueuePositionDO::getState, "COMPLETED"));
        long inProgress = shopQueuePositionMapper.selectCount(base.get()
            .eq(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueuePositionDO::getState, "IN_PROGRESS"));

        LambdaQueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopReferralContributionDO> cw =
            new LambdaQueryWrapper<>();
        if (spuId != null) cw.eq(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopReferralContributionDO::getSpuId, spuId);
        long contribution = shopReferralContributionMapper.selectCount(cw);

        return AppMerchantFunnelRespVO.builder()
            .spuId(spuId)
            .activatedUsers(activated)
            .inProgressUsers(inProgress)
            .completedUsers(completed)
            .contributionCount(contribution)
            .build();
    }
}
