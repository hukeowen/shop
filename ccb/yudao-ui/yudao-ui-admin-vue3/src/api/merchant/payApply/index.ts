import request from '@/config/axios'

export interface ShopPayApplyVO {
  id: number
  tenantId: number
  shopName: string
  tlMchId?: string
  payApplyStatus: number
  payApplyRejectReason?: string
  onlinePayEnabled: boolean
  createTime: Date
}

export interface PayApplyAuditReqVO {
  shopId: number
  approved: boolean
  rejectReason?: string
}

export interface PayApplyPageReqVO {
  pageNo: number
  pageSize: number
  status?: number
}

const BASE = '/merchant/shop'

export const getPayApplyPage = (params: PayApplyPageReqVO) =>
  request.get({ url: `${BASE}/pay-apply/page`, params })

export const auditPayApply = (data: PayApplyAuditReqVO) =>
  request.put({ url: `${BASE}/pay-apply/audit`, data })
