package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantDashboardRespVO;

/**
 * 商户首页数据看板 Service
 */
public interface MerchantDashboardService {

    /**
     * 获取当前租户的首页数据看板
     */
    AppMerchantDashboardRespVO getDashboard();

}
