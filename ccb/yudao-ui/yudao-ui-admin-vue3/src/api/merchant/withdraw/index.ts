import request from '@/config/axios'

export interface WithdrawVO {
  id: number
  tenantId: number
  shopName: string
  amount: number
  withdrawType: number
  accountName: string
  accountNo: string
  bankName?: string
  status: number
  rejectReason?: string
  auditorId?: number
  auditTime?: Date
  voucherUrl?: string
  createTime: Date
}

export interface WithdrawAuditReqVO {
  id: number
  approved: boolean
  voucherUrl?: string
  rejectReason?: string
}

export interface WithdrawPageReqVO {
  pageNo: number
  pageSize: number
  status?: number
  tenantId?: number
}

const BASE = '/merchant/withdraw'

export const getWithdrawPage = (params: WithdrawPageReqVO) =>
  request.get({ url: `${BASE}/page`, params })

export const auditWithdraw = (data: WithdrawAuditReqVO) =>
  request.post({ url: `${BASE}/audit`, data })
