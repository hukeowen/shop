package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantDashboardRespVO;
import cn.iocoder.yudao.module.merchant.service.MerchantDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "商户小程序 - 首页数据看板")
@RestController
@RequestMapping("/merchant/mini/dashboard")
@Validated
public class AppMerchantDashboardController {

    @Resource
    private MerchantDashboardService merchantDashboardService;

    @GetMapping("/summary")
    @Operation(summary = "获取首页数据看板")
    public CommonResult<AppMerchantDashboardRespVO> getDashboard() {
        return success(merchantDashboardService.getDashboard());
    }

}
