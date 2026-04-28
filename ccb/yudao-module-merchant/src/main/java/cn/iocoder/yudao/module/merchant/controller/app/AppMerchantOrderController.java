package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantOrderRespVO;
import cn.iocoder.yudao.module.merchant.event.OrderOfflineConfirmedEvent;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderDeliveryReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderPageReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderQueryService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 商户小程序 - 订单管理（含扫码核销）
 */
@Tag(name = "商户小程序 - 订单管理")
@RestController
@RequestMapping("/merchant/mini/order")
@Validated
public class AppMerchantOrderController {

    @Resource
    private TradeOrderQueryService tradeOrderQueryService;
    @Resource
    private TradeOrderUpdateService tradeOrderUpdateService;
    @Resource
    private TradeOrderMapper tradeOrderMapper;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    // ==================== #19 订单列表 + 发货 ====================

    @GetMapping("/page")
    @Operation(summary = "分页查询订单列表")
    public CommonResult<PageResult<AppMerchantOrderRespVO>> getOrderPage(@Valid TradeOrderPageReqVO pageReqVO) {
        PageResult<TradeOrderDO> pageResult = tradeOrderQueryService.getOrderPage(pageReqVO);
        if (pageResult.getList().isEmpty()) {
            return success(PageResult.empty());
        }
        List<Long> orderIds = pageResult.getList().stream().map(TradeOrderDO::getId).collect(Collectors.toList());
        List<TradeOrderItemDO> allItems = tradeOrderQueryService.getOrderItemListByOrderId(orderIds);
        Map<Long, List<TradeOrderItemDO>> itemsByOrderId = allItems.stream()
                .collect(Collectors.groupingBy(TradeOrderItemDO::getOrderId));
        List<AppMerchantOrderRespVO> voList = pageResult.getList().stream()
                .map(order -> toRespVO(order, itemsByOrderId.getOrDefault(order.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
        return success(new PageResult<>(voList, pageResult.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "获取订单详情")
    @Parameter(name = "id", description = "订单编号", required = true)
    public CommonResult<AppMerchantOrderRespVO> getOrder(@RequestParam("id") Long id) {
        TradeOrderDO order = tradeOrderQueryService.getOrder(id);
        if (order == null) {
            return success(null);
        }
        List<TradeOrderItemDO> items = tradeOrderQueryService.getOrderItemListByOrderId(order.getId());
        return success(toRespVO(order, items));
    }

    @PostMapping("/delivery")
    @Operation(summary = "快递发货")
    public CommonResult<Boolean> deliveryOrder(@Valid @RequestBody TradeOrderDeliveryReqVO deliveryReqVO) {
        tradeOrderUpdateService.deliveryOrder(deliveryReqVO);
        return success(true);
    }

    /**
     * 商户主动确认送达（设计 8.4 节）：
     * 适用于同城配送 / 自营配送场景，商户在确认商品已送达客户手中后调用，
     * 立即把订单推进到"已收货"状态（COMPLETED 由 trade 模块在 receiveOrder 内决定）。
     *
     * 实现：以订单买家身份调 receiveOrderByMember —— 等价于"代用户确认收货"。
     * 仅在 status = 20(DELIVERED 已发货) 时生效。
     */
    @PostMapping("/confirm-delivered")
    @Operation(summary = "商户确认已送达（同城配送 / 自营配送场景）")
    @Parameter(name = "id", description = "订单编号", required = true)
    public CommonResult<Boolean> confirmDelivered(@RequestParam("id") Long id) {
        TradeOrderDO order = tradeOrderQueryService.getOrder(id);
        if (order == null) {
            throw exception0(400, "订单不存在");
        }
        if (!Integer.valueOf(20).equals(order.getStatus())) {
            throw exception0(400, "订单状态不是已发货，无法确认送达");
        }
        tradeOrderUpdateService.receiveOrderByMember(order.getUserId(), id);
        return success(true);
    }

    // ==================== #18 扫码核销（自提） ====================

    @GetMapping("/get-by-verify-code")
    @Operation(summary = "通过核销码查询订单（扫码或手动输入）")
    @Parameter(name = "pickUpVerifyCode", description = "自提核销码（8位数字）", required = true)
    public CommonResult<TradeOrderDO> getOrderByVerifyCode(
            @RequestParam("pickUpVerifyCode") String pickUpVerifyCode) {
        return success(tradeOrderUpdateService.getByPickUpVerifyCode(pickUpVerifyCode));
    }

    @PutMapping("/pick-up-verify")
    @Operation(summary = "核销自提订单（通过核销码）")
    @Parameter(name = "pickUpVerifyCode", description = "自提核销码", required = true)
    public CommonResult<Boolean> pickUpByVerifyCode(
            @RequestParam("pickUpVerifyCode") String pickUpVerifyCode) {
        tradeOrderUpdateService.pickUpOrderByAdmin(SecurityFrameworkUtils.getLoginUserId(), pickUpVerifyCode);
        return success(true);
    }

    @PutMapping("/pick-up-by-id")
    @Operation(summary = "核销自提订单（通过订单ID）")
    @Parameter(name = "id", description = "订单编号", required = true)
    public CommonResult<Boolean> pickUpById(@RequestParam("id") Long id) {
        tradeOrderUpdateService.pickUpOrderByAdmin(SecurityFrameworkUtils.getLoginUserId(), id);
        return success(true);
    }

    // ==================== #37 到店付款 - 商户确认收款 ====================

    @PostMapping("/offline-confirm")
    @Operation(summary = "确认到店付款收款")
    @Parameter(name = "id", description = "订单编号", required = true)
    public CommonResult<Boolean> offlineConfirm(@RequestParam("id") Long id) {
        TradeOrderDO order = tradeOrderQueryService.getOrder(id);
        if (order == null) {
            throw exception0(400, "订单不存在");
        }
        if (!Integer.valueOf(10).equals(order.getStatus())) {
            throw exception0(400, "订单状态不是待发货，无法确认收款");
        }
        if (Boolean.TRUE.equals(order.getPayStatus())) {
            throw exception0(400, "订单已支付，请勿重复确认");
        }
        // 更新 payStatus=true、payTime=now
        TradeOrderDO update = new TradeOrderDO();
        update.setId(id);
        update.setPayStatus(Boolean.TRUE);
        update.setPayTime(LocalDateTime.now());
        tradeOrderMapper.updateById(update);
        // 发布到店收款确认事件
        eventPublisher.publishEvent(new OrderOfflineConfirmedEvent(
                this, id,
                TenantContextHolder.getTenantId(),
                order.getUserId(),
                order.getPayPrice()));
        return success(true);
    }

    private AppMerchantOrderRespVO toRespVO(TradeOrderDO order, List<TradeOrderItemDO> items) {
        AppMerchantOrderRespVO vo = new AppMerchantOrderRespVO();
        vo.setId(order.getId());
        vo.setNo(order.getNo());
        vo.setStatus(order.getStatus());
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverMobile(order.getReceiverMobile());
        vo.setReceiverDetailAddress(order.getReceiverDetailAddress());
        vo.setPayPrice(order.getPayPrice());
        vo.setTotalPrice(order.getTotalPrice());
        vo.setProductCount(order.getProductCount());
        vo.setUserRemark(order.getUserRemark());
        vo.setDeliveryType(order.getDeliveryType());
        vo.setPickUpVerifyCode(order.getPickUpVerifyCode());
        vo.setPayStatus(order.getPayStatus());
        vo.setCreateTime(order.getCreateTime());
        List<AppMerchantOrderRespVO.Item> itemVOs = items.stream().map(item -> {
            AppMerchantOrderRespVO.Item i = new AppMerchantOrderRespVO.Item();
            i.setSpuName(item.getSpuName());
            i.setSkuName(item.getProperties() == null || item.getProperties().isEmpty() ? item.getSpuName()
                    : item.getProperties().stream()
                            .map(p -> p.getPropertyName() + ":" + p.getValueName())
                            .collect(Collectors.joining(" ")));
            i.setPrice(item.getPrice());
            i.setCount(item.getCount());
            i.setPicUrl(item.getPicUrl());
            return i;
        }).collect(Collectors.toList());
        vo.setItems(itemVOs);
        return vo;
    }

}
