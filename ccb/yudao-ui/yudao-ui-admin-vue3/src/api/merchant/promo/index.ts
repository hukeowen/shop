import request from '@/config/axios'

// ==================== 商户级营销配置 ====================

export interface PromoConfigVO {
  starLevelCount: number
  commissionRates: string
  starUpgradeRules: string
  pointConversionRatio: number
  withdrawThreshold: number
  poolEnabled: boolean
  poolRatio: number
  poolEligibleStars: string
  poolDistributeMode: string
  poolSettleCron: string
  poolLotteryRatio: number
  poolSettleMode: string
}

export const getPromoConfig = () =>
  request.get({ url: '/merchant/promo/config' })

export const savePromoConfig = (data: PromoConfigVO) =>
  request.put({ url: '/merchant/promo/config', data })

// ==================== 商品级营销配置 ====================

export interface ProductPromoConfigVO {
  spuId: number
  consumePointRatio: number
  tuijianEnabled: boolean
  tuijianN: number
  tuijianRatios: string
  poolEnabled: boolean
}

export const getProductPromoConfig = (spuId: number) =>
  request.get({ url: '/merchant/promo/product-config', params: { spuId } })

export const saveProductPromoConfig = (data: ProductPromoConfigVO) =>
  request.put({ url: '/merchant/promo/product-config', data })

// ==================== 推广积分提现审批 ====================

export interface PromoWithdrawVO {
  id: number
  userId: number
  amount: number
  status: string
  applyAt: string
  processedAt?: string
  processorId?: number
  processorRemark?: string
}

export interface PromoWithdrawPageReqVO {
  pageNo: number
  pageSize: number
  status?: string
}

export const pagePromoWithdraw = (params: PromoWithdrawPageReqVO) =>
  request.get({ url: '/merchant/promo/withdraw/page', params })

export const approvePromoWithdraw = (id: number, remark?: string) =>
  request.post({ url: '/merchant/promo/withdraw/approve', params: { id, remark } })

export const rejectPromoWithdraw = (id: number, remark?: string) =>
  request.post({ url: '/merchant/promo/withdraw/reject', params: { id, remark } })

export const markPaidPromoWithdraw = (id: number, remark?: string) =>
  request.post({ url: '/merchant/promo/withdraw/mark-paid', params: { id, remark } })
