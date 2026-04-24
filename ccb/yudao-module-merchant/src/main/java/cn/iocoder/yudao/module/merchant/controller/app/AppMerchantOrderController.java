package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantOrderRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderDeliveryReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderPageReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderQueryService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
