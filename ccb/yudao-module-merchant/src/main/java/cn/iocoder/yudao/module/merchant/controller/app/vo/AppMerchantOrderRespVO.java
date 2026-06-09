package cn.iocoder.yudao.module.merchant.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "商户小程序 - 订单响应 VO（含订单明细）")
@Data
public class AppMerchantOrderRespVO {

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单编号")
    private String no;

    @Schema(description = "订单状态：0=待付款 10=待发货 20=待核销 30=已完成 40=已取消")
    private Integer status;

    @Schema(description = "收货人姓名")
    private String receiverName;

    @Schema(description = "收货人手机号")
    private String receiverMobile;

    @Schema(description = "收货详细地址")
    private String receiverDetailAddress;

    @Schema(description = "订单实付金额（分）")
    private Integer payPrice;

    @Schema(description = "商品总价（分）")
    private Integer totalPrice;

    @Schema(description = "商品数量")
    private Integer productCount;

    @Schema(description = "买家备注")
    private String userRemark;

    @Schema(description = "配送方式：1=快递 2=自提")
    private Integer deliveryType;

    @Schema(description = "自提核销码")
    private String pickUpVerifyCode;

    @Schema(description = "是否已支付")
    private Boolean payStatus;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    // ========== 抵扣明细 (商家端 / 用户端订单详情都用) ==========
    @Schema(description = "店铺余额抵扣（分）")
    private Integer balanceDeductFen;

    @Schema(description = "消费积分抵扣金额（分）")
    private Integer consumePointDeductFen;

    @Schema(description = "消费积分扣减数量")
    private Long consumePointUsed;

    @Schema(description = "推广积分抵扣金额（分；1 积分 = 1 分钱）")
    private Integer promoPointRedeemFen;

    @Schema(description = "推 N 反 1 自动抵扣金额（分）")
    private Integer promoAutoDeductFen;

    @Schema(description = "推 N 反 1 自动抵扣件数")
    private Integer promoAutoDeductCount;

    @Schema(description = "优惠券抵扣（分）")
    private Integer couponDeductFen;

    // ========== 线下转账收款（商户未开通在线支付时） ==========
    @Schema(description = "是否线下转账单")
    private Boolean offlinePay;

    @Schema(description = "线下收款状态：0=待付款上传 1=已上传待确认 2=已确认 3=已驳回")
    private Integer offlinePayStatus;

    @Schema(description = "顾客上传的付款凭证截图 URL")
    private String offlineProofUrl;

    @Schema(description = "顾客付款渠道：wechat / alipay")
    private String offlinePayChannel;

    @Schema(description = "顾客付款备注")
    private String offlineBuyerRemark;

    @Schema(description = "顾客提交凭证时间")
    private LocalDateTime offlineSubmitTime;

    @Schema(description = "订单明细")
    private List<Item> items;

    @Data
    public static class Item {
        @Schema(description = "商品名称")
        private String spuName;

        @Schema(description = "SKU 名称")
        private String skuName;

        @Schema(description = "单价（分）")
        private Integer price;

        @Schema(description = "数量")
        private Integer count;

        @Schema(description = "商品图片")
        private String picUrl;
    }
}
