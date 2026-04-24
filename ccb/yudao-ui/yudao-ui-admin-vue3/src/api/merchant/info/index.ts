import request from '@/config/axios'

export interface MerchantInfoVO {
  id: number
  name: string
  logo?: string
  contactName: string
  contactPhone: string
  licenseNo?: string
  licenseUrl?: string
  businessCategory?: string
  /** 0=待审核 1=已通过 2=已驳回 3=已禁用 */
  status: number
  rejectReason?: string
  auditTime?: Date
  createTime: Date
}

export interface MerchantPageReqVO {
  pageNo: number
  pageSize: number
  name?: string
  contactPhone?: string
  status?: number
}

export interface MerchantAuditReqVO {
  id: number
  approved: boolean
  rejectReason?: string
}

const BASE = '/merchant/info'

export const getMerchantPage = (params: MerchantPageReqVO) =>
  request.get({ url: `${BASE}/page`, params })

export const auditMerchant = (data: MerchantAuditReqVO) =>
  request.post({ url: `${BASE}/audit`, data })
