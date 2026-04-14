package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantDashboardRespVO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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
    private AfterSaleMapper afterSaleMapper;
    @Resource
    private ProductSpuMapper productSpuMapper;
    @Resource
    private MemberUserMapper memberUserMapper;

    @Override
    public AppMerchantDashboardRespVO getDashboard() {
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

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
                .build();
    }

    private Long countTodayOrders(LocalDateTime start, LocalDateTime end) {
        return tradeOrderMapper.selectCount(new LambdaQueryWrapper<cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO>()
                .between(cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO::getCreateTime, start, end)
                .gt(cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO::getStatus, 0)); // 排除未支付
    }

    private Long sumTodayOrderAmount(LocalDateTime start, LocalDateTime end) {
        // 使用 selectMaps 取 SUM
        var wrappers = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO>();
        wrappers.select("IFNULL(SUM(pay_price), 0) as total");
        wrappers.between("create_time", start, end);
        wrappers.gt("status", 0);
        var maps = tradeOrderMapper.selectMaps(wrappers);
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
        return memberUserMapper.selectCount(new LambdaQueryWrapper<>());
    }

}
