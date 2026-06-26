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
