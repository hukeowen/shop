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
        long netIncome = actualPayAmount - refundAmount - promoIssued;

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
            log.debug("[sumWithdrawn] withdraw 表查询失败，返回 0: {}", e.getMessage());
            return 0L;
        }
    }

    private long countPendingWithdraw() {
        try {
            return memberWithdrawApplyMapper.selectCount(
                new LambdaQueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.MemberWithdrawApplyDO>()
                    .eq(cn.iocoder.yudao.module.merchant.dal.dataobject.MemberWithdrawApplyDO::getStatus, 0));
        } catch (Exception e) {
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
     * 简化算法：
     *   - 新客订单 = order.user_id 在该周期内首次下单
     *   - 老客订单 = 老用户重复下单（≥ 2 次）
     *   - 复购客户数 = 同一周期内下单 ≥ 2 次的用户
     *   - 推荐订单 = 该用户在 shop_user_referral 有 parent
     */
    private long[] customerInsights(LocalDateTime start, LocalDateTime end) {
        // 拉所有该周期订单的 user_id 列表
        QueryWrapper<TradeOrderDO> w = new QueryWrapper<>();
        w.select("user_id");
        w.between("create_time", start, end);
        w.gt("status", 0);
        List<Map<String, Object>> rows = tradeOrderMapper.selectMaps(w);
        Map<Long, Integer> userOrderCount = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> r : rows) {
                Long u = r.get("user_id") == null ? null : Long.parseLong(r.get("user_id").toString());
                if (u != null) userOrderCount.merge(u, 1, Integer::sum);
            }
        }
        long newOrders = 0, oldOrders = 0, totalCustomers = userOrderCount.size(), repurchase = 0;
        long totalOrders = 0, referralOrders = 0;
        if (!userOrderCount.isEmpty()) {
            // 老客 = 在该周期之前已经下过订单
            QueryWrapper<TradeOrderDO> historyW = new QueryWrapper<>();
            historyW.select("DISTINCT user_id");
            historyW.lt("create_time", start);
            historyW.gt("status", 0);
            historyW.in("user_id", userOrderCount.keySet());
            List<Map<String, Object>> hRows = tradeOrderMapper.selectMaps(historyW);
            java.util.Set<Long> historyUsers = new java.util.HashSet<>();
            if (hRows != null) for (Map<String, Object> r : hRows) {
                historyUsers.add(Long.parseLong(r.get("user_id").toString()));
            }
            for (Map.Entry<Long, Integer> e : userOrderCount.entrySet()) {
                int c = e.getValue();
                totalOrders += c;
                boolean isOld = historyUsers.contains(e.getKey());
                if (isOld) {
                    oldOrders += c;
                } else {
                    // 第 1 单是新客，剩余是老客
                    newOrders += 1;
                    oldOrders += (c - 1);
                }
                if (c >= 2 || isOld) repurchase++;
            }
            // 推荐订单：拉该周期所有订单 user_id ∈ shop_user_referral 有 parent 的
            QueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserReferralDO> rw = new QueryWrapper<>();
            rw.select("user_id");
            rw.in("user_id", userOrderCount.keySet());
            rw.gt("parent_user_id", 0);
            try {
                cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserReferralMapper rm =
                    cn.iocoder.yudao.framework.common.util.spring.SpringUtils.getBean(
                        cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserReferralMapper.class);
                List<Map<String, Object>> rRows = rm.selectMaps(rw);
                java.util.Set<Long> referralUsers = new java.util.HashSet<>();
                if (rRows != null) for (Map<String, Object> r : rRows) {
                    referralUsers.add(Long.parseLong(r.get("user_id").toString()));
                }
                for (Long u : userOrderCount.keySet()) {
                    if (referralUsers.contains(u)) referralOrders += userOrderCount.get(u);
                }
            } catch (Exception ignored) { /* SpringUtils 不可用就跳过 */ }
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
        String orderByCol = "amount".equals(sort) ? "salesAmount" : "salesCount";
        QueryWrapper<cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO> w = new QueryWrapper<>();
        w.select("spu_id AS spuId",
                "MAX(spu_name) AS spuName",
                "MAX(pic_url) AS picUrl",
                "IFNULL(SUM(count),0) AS salesCount",
                "IFNULL(SUM(price * count),0) AS salesAmount",
                "IFNULL(SUM(pay_price),0) AS actualPay");
        w.between("create_time", LocalDateTime.of(periodStart, LocalTime.MIN), LocalDateTime.of(today, LocalTime.MAX));
        w.groupBy("spu_id");
        w.orderByDesc(orderByCol);
        w.last("LIMIT " + limit);
        cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper im =
            cn.iocoder.yudao.framework.common.util.spring.SpringUtils.getBean(
                cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper.class);
        List<Map<String, Object>> rows = im.selectMaps(w);
        List<AppMerchantProductRankRespVO> list = new ArrayList<>();
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

        // 2. 拉 member_user（取 mobile / nickname）— 使用 SpringUtils
        Map<Long, cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO> userMap = new HashMap<>();
        try {
            cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper um =
                cn.iocoder.yudao.framework.common.util.spring.SpringUtils.getBean(
                    cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper.class);
            cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.executeIgnore(() -> {
                List<cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO> users = um.selectBatchIds(userIds);
                if (users != null) for (cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO u : users)
                    userMap.put(u.getId(), u);
            });
        } catch (Exception e) {
            log.warn("[listMembers] 拉 member_user 失败: {}", e.getMessage());
        }

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
        try {
            cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserReferralMapper rm =
                cn.iocoder.yudao.framework.common.util.spring.SpringUtils.getBean(
                    cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserReferralMapper.class);
            QueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserReferralDO> rw = new QueryWrapper<>();
            rw.in("user_id", userIds);
            List<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserReferralDO> refs = rm.selectList(rw);
            if (refs != null) for (cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserReferralDO ref : refs) {
                parentByUser.put(ref.getUserId(), ref.getParentUserId());
            }
            // 邀请数：parent_user_id ∈ userIds 的行数（本店 referral 表）
            QueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserReferralDO> iw = new QueryWrapper<>();
            iw.select("parent_user_id AS pid", "COUNT(*) AS cnt");
            iw.in("parent_user_id", userIds);
            iw.gt("parent_user_id", 0);
            iw.groupBy("parent_user_id");
            List<Map<String, Object>> iRows = rm.selectMaps(iw);
            if (iRows != null) for (Map<String, Object> r : iRows) {
                invitedCountByUser.put(parseLong(r.get("pid")), parseLong(r.get("cnt")));
            }
        } catch (Exception ignored) { }

        // 5. 拉 user_star spu_id=0（全局账户）
        Map<Long, cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO> starByUser = new HashMap<>();
        try {
            cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserStarMapper sm =
                cn.iocoder.yudao.framework.common.util.spring.SpringUtils.getBean(
                    cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopUserStarMapper.class);
            QueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO> sw = new QueryWrapper<>();
            sw.in("user_id", userIds);
            sw.eq("spu_id", 0);
            List<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO> stars = sm.selectList(sw);
            if (stars != null) for (cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO s : stars) {
                starByUser.put(s.getUserId(), s);
            }
        } catch (Exception ignored) { }

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
        cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopQueuePositionMapper qm =
            cn.iocoder.yudao.framework.common.util.spring.SpringUtils.getBean(
                cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopQueuePositionMapper.class);
        cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopReferralContributionMapper cm =
            cn.iocoder.yudao.framework.common.util.spring.SpringUtils.getBean(
                cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopReferralContributionMapper.class);
        // 各状态人数
        LambdaQueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueuePositionDO> base =
            new LambdaQueryWrapper<>();
        if (spuId != null) base.eq(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueuePositionDO::getSpuId, spuId);

        long activated = qm.selectCount(base.clone());
        long completed = qm.selectCount(base.clone()
            .eq(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueuePositionDO::getState, "COMPLETED"));
        long inProgress = qm.selectCount(base.clone()
            .eq(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopQueuePositionDO::getState, "IN_PROGRESS"));

        LambdaQueryWrapper<cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopReferralContributionDO> cw =
            new LambdaQueryWrapper<>();
        if (spuId != null) cw.eq(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopReferralContributionDO::getSpuId, spuId);
        long contribution = cm.selectCount(cw);

        return AppMerchantFunnelRespVO.builder()
            .spuId(spuId)
            .activatedUsers(activated)
            .inProgressUsers(inProgress)
            .completedUsers(completed)
            .contributionCount(contribution)
            .build();
    }
}
