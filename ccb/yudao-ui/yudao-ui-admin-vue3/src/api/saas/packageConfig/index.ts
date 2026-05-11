import request from '@/config/axios'

export interface SaasPackageConfigVO {
  id?: number
  level: string
  name: string
  priceFen: number
  durationDays: number
  aiVideoGrant: number
  features?: string
  sort: number
  status: number
}

const BASE = '/merchant/saas/package'

export const listPackages = () => request.get({ url: `${BASE}/list` })

export const savePackage = (data: SaasPackageConfigVO) =>
  request.put({ url: `${BASE}/save`, data })

export const updatePackageStatus = (id: number, status: number) =>
  request.put({ url: `${BASE}/${id}/status?status=${status}` })
