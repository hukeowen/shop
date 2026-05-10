package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantFunnelRespVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantHeatmapRespVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantMemberItemRespVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantProductRankRespVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantSalesStatsRespVO;
import cn.iocoder.yudao.module.merchant.service.MerchantStatsService;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 商户小程序 - 销售统计 / 商品排行 / 客户洞察 等数据 API。
 *
 * 所有端点都依赖商户 token tenant 上下文，仅返该商户租户下的数据。
 */
@Tag(name = "商户小程序 - 销售统计")
@RestController
@RequestMapping("/merchant/mini/stats")
@Validated
public class AppMerchantStatsController {

    @Resource
    private MerchantStatsService merchantStatsService;

    @GetMapping("/sales")
    @Operation(summary = "销售统计主数据")
    public CommonResult<AppMerchantSalesStatsRespVO> getSalesStats(
            @Parameter(description = "周期：day / week / month / year", example = "month")
            @RequestParam(value = "period", defaultValue = "month") String period) {
        return success(merchantStatsService.getSalesStats(period));
    }

    @GetMapping("/product-rank")
    @Operation(summary = "商品销售排行")
    public CommonResult<List<AppMerchantProductRankRespVO>> getProductRank(
            @RequestParam(value = "period", defaultValue = "month") String period,
            @RequestParam(value = "sort", defaultValue = "count") String sort,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return success(merchantStatsService.getProductRank(period, sort, limit));
    }

    @GetMapping("/members")
    @Operation(summary = "店铺会员列表")
    public CommonResult<List<AppMerchantMemberItemRespVO>> listMembers(
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return success(merchantStatsService.listMembers(pageNo, pageSize));
    }

    @GetMapping("/hourly-heatmap")
    @Operation(summary = "时段订单热力图")
    public CommonResult<AppMerchantHeatmapRespVO> getHourlyHeatmap(
            @RequestParam(value = "period", defaultValue = "month") String period) {
        return success(merchantStatsService.getHourlyHeatmap(period));
    }

    @GetMapping("/referral-funnel")
    @Operation(summary = "推 N 反 1 漏斗")
    public CommonResult<AppMerchantFunnelRespVO> getReferralFunnel(
            @RequestParam(value = "spuId", required = false) Long spuId) {
        return success(merchantStatsService.getReferralFunnel(spuId));
    }

}
