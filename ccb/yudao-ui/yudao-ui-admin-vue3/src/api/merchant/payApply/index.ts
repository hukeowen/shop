import request from '@/config/axios'

export interface ShopPayApplyVO {
  id: number
  tenantId: number
  shopName: string
  tlMchId?: string
  payApplyStatus: number
  payApplyRejectReason?: string
  onlinePayEnabled: boolean
  idCardFrontKey?: string
  idCardBackKey?: string
  businessLicenseKey?: string
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

/** 给 KYC 资质 TOS key 签发 1h 临时 GET URL（审核员预览时调） */
export const signKycKey = (key: string, ttl = 3600): Promise<{ url: string }> =>
  request.get({ url: `${BASE}/pay-apply/kyc-sign?key=${encodeURIComponent(key)}&ttl=${ttl}` })
