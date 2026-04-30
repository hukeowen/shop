package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantDashboardRespVO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商户首页数据看板 Service 实现
 *
 * <p>所有查询均在当前租户上下文下执行（由 TenantBaseDO 自动过滤 tenant_id）</p>
 *
 * <p><b>跨模块 Mapper 注入说明</b>：本项目为单体应用（YudaoServerApplication 以
 * {@code scanBasePackages = "cn.iocoder.yudao.module"} 扫描所有模块），所有模块的
 * Mapper、Service Bean 均注册在同一个 Spring ApplicationContext 中，因此直接注入
 * trade/product/member 模块的 Mapper 是合法且安全的，无需通过跨进程 RPC。</p>
 */
@Service
@Slf4j
public class MerchantDashboardServiceImpl implements MerchantDashboardService {

    /** 订单状态：待发货 */
    private static final int ORDER_STATUS_UNDELIVERED = 10;
    /** 配送方式：自提 */
    private static final int DELIVERY_TYPE_PICK_UP = 2;
    /** 售后状态：申请中 */
    private static final int AFTER_SALE_STATUS_APPLY = 10;
    /** SPU状态：上架 */
    private static final int SPU_STATUS_ENABLE = 1;

    @Resource
    private TradeOrderMapper tradeOrderMapper;
    @Resource
    private TradeOrderItemMapper tradeOrderItemMapper;
    @Resource
    private AfterSaleMapper afterSaleMapper;
    @Resource
    private ProductSpuMapper productSpuMapper;
    @Resource
    private MemberUserMapper memberUserMapper;

    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("MM-dd");

    @Override
    public AppMerchantDashboardRespVO getDashboard() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = LocalDateTime.of(today, LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(today, LocalTime.MAX);
        LocalDate trendStartDate = today.minusDays(6); // 含今天共 7 天
        LocalDateTime trendStart = LocalDateTime.of(trendStartDate, LocalTime.MIN);
        LocalDate topStartDate = today.minusDays(29);  // 近 30 天
        LocalDateTime topStart = LocalDateTime.of(topStartDate, LocalTime.MIN);

        Map<String, long[]> trendByDate = queryTrend(trendStart, todayEnd);
        List<String> labels = new ArrayList<>(7);
        List<Long> orderCounts = new ArrayList<>(7);
        List<Long> sales = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate d = trendStartDate.plusDays(i);
            String key = d.toString(); // yyyy-MM-dd
            labels.add(d.format(LABEL_FMT));
            long[] row = trendByDate.getOrDefault(key, new long[]{0L, 0L});
            orderCounts.add(row[0]);
            sales.add(row[1]);
        }

        return AppMerchantDashboardRespVO.builder()
                // 今日数据
                .todayOrderCount(countTodayOrders(todayStart, todayEnd))
                .todayOrderAmount(sumTodayOrderAmount(todayStart, todayEnd))
                .todayNewMemberCount(countTodayNewMembers(todayStart, todayEnd))
                // 待处理
                .pendingShipmentCount(countPendingShipment())
                .pendingVerifyCount(countPendingVerify())
                .pendingAfterSaleCount(countPendingAfterSale())
                // 经营概览
                .activeSpuCount(countActiveSpu())
                .totalMemberCount(countTotalMembers())
                // 7 天趋势
                .trendLabels(labels)
                .trendOrderCounts(orderCounts)
                .trendSalesAmount(sales)
                // 30 天 Top3
                .topProducts(queryTopProducts(topStart, todayEnd))
                .build();
    }

    /**
     * 查最近 N 天每日（订单数、销售额）。返回 key=yyyy-MM-dd → [count, sumPayPrice]，
     * status>0（已支付/已发货/已完成等），未支付订单不计入营业。
     */
    private Map<String, long[]> queryTrend(LocalDateTime start, LocalDateTime end) {
        QueryWrapper<cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO> w = new QueryWrapper<>();
        w.select("DATE(create_time) AS d", "COUNT(*) AS c", "IFNULL(SUM(pay_price),0) AS amt");
        w.between("create_time", start, end);
        w.gt("status", 0);
        w.groupBy("DATE(create_time)");
        List<Map<String, Object>> rows = tradeOrderMapper.selectMaps(w);
        Map<String, long[]> map = new HashMap<>();
        if (rows == null) return map;
        for (Map<String, Object> r : rows) {
            Object dObj = r.get("d");
            if (dObj == null) continue;
            String key = dObj.toString();
            // MySQL DATE 返 java.sql.Date，toString 即 yyyy-MM-dd；其他驱动可能直接是字符串
            if (key.length() > 10) key = key.substring(0, 10);
            long c = r.get("c") == null ? 0L : Long.parseLong(r.get("c").toString());
            long amt = r.get("amt") == null ? 0L : Long.parseLong(r.get("amt").toString());
            map.put(key, new long[]{c, amt});
        }
        return map;
    }

    /**
     * 近 30 天热销 Top3。按 trade_order_item.spu_id 聚合销量，order item 自带
     * spu_name / pic_url 快照，无需 join 商品库。tenant_id 由 TenantBaseDO 拦截器自动加。
     */
    private List<AppMerchantDashboardRespVO.TopProductVO> queryTopProducts(LocalDateTime start, LocalDateTime end) {
        QueryWrapper<cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO> w = new QueryWrapper<>();
        w.select("spu_id AS spuId",
                "MAX(spu_name) AS spuName",
                "MAX(pic_url) AS picUrl",
                "IFNULL(SUM(count),0) AS salesCount",
                "IFNULL(SUM(pay_price),0) AS salesAmount");
        w.between("create_time", start, end);
        w.groupBy("spu_id");
        w.orderByDesc("salesCount");
        w.last("LIMIT 3");
        List<Map<String, Object>> rows = tradeOrderItemMapper.selectMaps(w);
        List<AppMerchantDashboardRespVO.TopProductVO> list = new ArrayList<>(3);
        if (rows == null) return list;
        for (Map<String, Object> r : rows) {
            list.add(AppMerchantDashboardRespVO.TopProductVO.builder()
                    .spuId(r.get("spuId") == null ? null : Long.parseLong(r.get("spuId").toString()))
                    .name(r.get("spuName") == null ? null : r.get("spuName").toString())
                    .picUrl(r.get("picUrl") == null ? null : r.get("picUrl").toString())
                    .salesCount(r.get("salesCount") == null ? 0L : Long.parseLong(r.get("salesCount").toString()))
                    .salesAmount(r.get("salesAmount") == null ? 0L : Long.parseLong(r.get("salesAmount").toString()))
                    .build());
        }
        return list;
    }

    private Long countTodayOrders(LocalDateTime start, LocalDateTime end) {
        return tradeOrderMapper.selectCount(new LambdaQueryWrapper<cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO>()
                .between(cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO::getCreateTime, start, end)
                .gt(cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO::getStatus, 0)); // 排除未支付
    }

    private Long sumTodayOrderAmount(LocalDateTime start, LocalDateTime end) {
        // 使用 selectMaps 取 SUM
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO> wrappers =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        wrappers.select("IFNULL(SUM(pay_price), 0) as total");
        wrappers.between("create_time", start, end);
        wrappers.gt("status", 0);
        java.util.List<java.util.Map<String, Object>> maps = tradeOrderMapper.selectMaps(wrappers);
        if (maps != null && !maps.isEmpty() && maps.get(0).get("total") != null) {
            return Long.parseLong(maps.get(0).get("total").toString());
        }
        return 0L;
    }

    private Long countTodayNewMembers(LocalDateTime start, LocalDateTime end) {
        return memberUserMapper.selectCount(new LambdaQueryWrapper<cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO>()
                .between(cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO::getCreateTime, start, end));
    }

    private Long countPendingShipment() {
        return tradeOrderMapper.selectCount(new LambdaQueryWrapper<cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO>()
                .eq(cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO::getStatus, ORDER_STATUS_UNDELIVERED)
                .ne(cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO::getDeliveryType, DELIVERY_TYPE_PICK_UP));
    }

    private Long countPendingVerify() {
        return tradeOrderMapper.selectCount(new LambdaQueryWrapper<cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO>()
                .eq(cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO::getStatus, ORDER_STATUS_UNDELIVERED)
                .eq(cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO::getDeliveryType, DELIVERY_TYPE_PICK_UP));
    }

    private Long countPendingAfterSale() {
        return afterSaleMapper.selectCount(new LambdaQueryWrapper<cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleDO>()
                .eq(cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleDO::getStatus, AFTER_SALE_STATUS_APPLY));
    }

    private Long countActiveSpu() {
        return productSpuMapper.selectCount(new LambdaQueryWrapper<cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO>()
                .eq(cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO::getStatus, SPU_STATUS_ENABLE));
    }

    private Long countTotalMembers() {
        // 显式指定泛型，便于 MP 解析目标表；MemberUserDO 继承自 TenantBaseDO，
        // 会自动附加 tenant_id 过滤，返回当前租户下的会员数
        return memberUserMapper.selectCount(
                new LambdaQueryWrapper<cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO>());
    }

}
