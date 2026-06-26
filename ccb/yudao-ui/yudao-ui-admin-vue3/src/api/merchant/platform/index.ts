import request from '@/config/axios'

// 平台运营总览 —— 跨租户聚合（独立模块，不走 yudao 自带商城接口）

/** 所有店铺（租户ID+店铺名+状态）：筛选下拉 */
export const getPlatformShops = () => request.get({ url: '/merchant/platform/shops' })

/** 跨租户商品总览分页 */
export const getPlatformProductPage = (params: any) =>
  request.get({ url: '/merchant/platform/product/page', params })

/** 平台上/下架某店铺商品（0下架 1上架 4回收站） */
export const updatePlatformProductStatus = (id: number, status: number) =>
  request.put({ url: `/merchant/platform/product/update-status?id=${id}&status=${status}` })

/** 店铺套餐总览分页（每店当前套餐/到期/累计付费） */
export const getPlatformSubscriptionPage = (params: any) =>
  request.get({ url: '/merchant/platform/subscription/page', params })

/** 跨租户订单总览分页 */
export const getPlatformOrderPage = (params: any) =>
  request.get({ url: '/merchant/platform/order/page', params })

/** 平台数据概览 */
export const getPlatformStats = () => request.get({ url: '/merchant/platform/stats' })

/** 平台会员管理分页（按店铺查会员） */
export const getPlatformMemberPage = (params: any) =>
  request.get({ url: '/merchant/platform/member/page', params })

/** 店铺管理分页 */
export const getPlatformShopPage = (params: any) =>
  request.get({ url: '/merchant/platform/shop/page', params })
/** 改通联费率 */
export const updateShopRate = (id: number, tlFeeRate: string) =>
  request.put({ url: `/merchant/platform/shop/update-rate?id=${id}&tlFeeRate=${encodeURIComponent(tlFeeRate || '')}` })
/** 设置该店自动上架开关 */
export const updateShopAutoApprove = (id: number, autoApprove: number) =>
  request.put({ url: `/merchant/platform/shop/update-auto-approve?id=${id}&autoApprove=${autoApprove}` })
