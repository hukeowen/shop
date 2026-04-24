import request from '@/config/axios'

export interface AiVideoPackageVO {
  id?: number
  name: string
  description?: string
  videoCount: number
  price: number
  originalPrice?: number
  sort?: number
  status: number
  createTime?: Date
  updateTime?: Date
}

export interface AiVideoQuotaLogVO {
  id: number
  merchantId: number
  quotaChange: number
  quotaAfter: number
  bizType: number
  bizId?: string
  remark?: string
  createTime: Date
}

const BASE = '/merchant/ai-video/package'

export const getPackagePage = (params: PageParam) =>
  request.get({ url: `${BASE}/page`, params })

export const getPackage = (id: number) =>
  request.get({ url: `${BASE}/get?id=${id}` })

export const createPackage = (data: AiVideoPackageVO) =>
  request.post({ url: `${BASE}/create`, data })

export const updatePackage = (data: AiVideoPackageVO) =>
  request.put({ url: `${BASE}/update`, data })

export const deletePackage = (id: number) =>
  request.delete({ url: `${BASE}/delete?id=${id}` })

export const getQuotaLogPage = (params: PageParam) =>
  request.get({ url: `${BASE}/quota-log/page`, params })
