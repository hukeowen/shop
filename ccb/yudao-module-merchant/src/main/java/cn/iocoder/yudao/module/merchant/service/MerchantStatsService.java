package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantFunnelRespVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantHeatmapRespVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantMemberItemRespVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantProductRankRespVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantSalesStatsRespVO;

import java.util.List;

/**
 * 商户销售统计服务（v8 新增）
 *
 * 所有方法都基于当前 tenant 上下文运行（依赖 TenantBaseDO 自动过滤）。
 */
public interface MerchantStatsService {

    /**
     * 销售统计主页数据。
     *
     * @param period day / week / month / year
     * @return 总览大数 + 资金分布 + 趋势 + 客户洞察
     */
    AppMerchantSalesStatsRespVO getSalesStats(String period);

    /** 商品销售排行（按 period 切粒度，按 sort=count|amount 排序，limit 默认 20） */
    List<AppMerchantProductRankRespVO> getProductRank(String period, String sort, int limit);

    /** 店铺会员列表（按加入时间倒序） */
    List<AppMerchantMemberItemRespVO> listMembers(int pageNo, int pageSize);

    /** 时段热力图（period 默认月） */
    AppMerchantHeatmapRespVO getHourlyHeatmap(String period);

    /** 推 N 反 1 漏斗（spuId=null 时全店聚合） */
    AppMerchantFunnelRespVO getReferralFunnel(Long spuId);

}
