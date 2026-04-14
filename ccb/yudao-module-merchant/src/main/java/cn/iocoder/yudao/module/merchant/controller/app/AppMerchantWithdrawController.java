package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantWithdrawApplyDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.service.MerchantWithdrawService;
import cn.iocoder.yudao.module.trade.controller.admin.brokerage.vo.withdraw.BrokerageWithdrawPageReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.brokerage.BrokerageWithdrawDO;
import cn.iocoder.yudao.module.trade.enums.brokerage.BrokerageWithdrawStatusEnum;
import cn.iocoder.yudao.module.trade.service.brokerage.BrokerageWithdrawService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.servlet.ServletUtils.getClientIP;

/**
 * 商户小程序 - 提现管理
 * #24 用户提现审核（商户审核用户佣金提现）
 * #25 商户向平台申请提现
 */
@Tag(name = "商户小程序 - 提现管理")
@RestController
@RequestMapping("/merchant/mini/withdraw")
@Validated
public class AppMerchantWithdrawController {

    @Resource
    private BrokerageWithdrawService brokerageWithdrawService;
    @Resource
    private MerchantWithdrawService merchantWithdrawService;
    @Resource
    private ShopInfoMapper shopInfoMapper;

    // ==================== #24 用户提现审核 ====================

    @GetMapping("/user/page")
    @Operation(summary = "分页查询用户提现申请")
    public CommonResult<PageResult<BrokerageWithdrawDO>> getUserWithdrawPage(
            @Valid BrokerageWithdrawPageReqVO pageReqVO) {
        return success(brokerageWithdrawService.getBrokerageWithdrawPage(pageReqVO));
    }

    @PostMapping("/user/approve")
    @Operation(summary = "通过用户提现申请")
    @Parameter(name = "id", description = "提现申请ID", required = true)
    public CommonResult<Boolean> approveUserWithdraw(@RequestParam("id") Long id,
                                                     HttpServletRequest request) {
        brokerageWithdrawService.auditBrokerageWithdraw(id,
                BrokerageWithdrawStatusEnum.AUDIT_SUCCESS, null, getClientIP(request));
        return success(true);
    }

    @PostMapping("/user/reject")
    @Operation(summary = "驳回用户提现申请")
    public CommonResult<Boolean> rejectUserWithdraw(@RequestParam("id") Long id,
                                                    @RequestParam("reason") String reason,
                                                    HttpServletRequest request) {
        brokerageWithdrawService.auditBrokerageWithdraw(id,
                BrokerageWithdrawStatusEnum.AUDIT_FAIL, reason, getClientIP(request));
        return success(true);
    }

    // ==================== #25 商户向平台申请提现 ====================

    @PostMapping("/merchant/create")
    @Operation(summary = "商户向平台提交提现申请")
    public CommonResult<Boolean> createMerchantWithdraw(
            @RequestParam("amount") @NotNull Integer amount,
            @RequestParam("withdrawType") @NotNull Integer withdrawType,
            @RequestParam(value = "accountName", required = false) String accountName,
            @RequestParam(value = "accountNo", required = false) String accountNo,
            @RequestParam(value = "bankName", required = false) String bankName) {
        Long tenantId = TenantContextHolder.getTenantId();
        ShopInfoDO shopInfo = shopInfoMapper.selectByTenantId(tenantId);
        MerchantWithdrawApplyDO apply = MerchantWithdrawApplyDO.builder()
                .tenantId(tenantId)
                .shopName(shopInfo != null ? shopInfo.getShopName() : null)
                .amount(amount)
                .withdrawType(withdrawType)
                .accountName(accountName)
                .accountNo(accountNo)
                .bankName(bankName)
                .build();
        merchantWithdrawService.createWithdraw(apply);
        return success(true);
    }

}
