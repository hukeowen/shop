package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderDeliveryReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderPageReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderQueryService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

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
    public CommonResult<PageResult<TradeOrderDO>> getOrderPage(@Valid TradeOrderPageReqVO pageReqVO) {
        return success(tradeOrderQueryService.getOrderPage(pageReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获取订单详情")
    @Parameter(name = "id", description = "订单编号", required = true)
    public CommonResult<TradeOrderDO> getOrder(@RequestParam("id") Long id) {
        return success(tradeOrderQueryService.getOrder(id));
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

}
