import request from '@/config/axios'

export interface TlpayConfigVO {
  id: number
  tenantId: number
  shopName: string
  tlEnabled: boolean
  tlMchId?: string
  tlAppId?: string
  tlSignType?: string
  tlNotifyUrl?: string
  /** 私钥不返明文，只返"是否已配置"布尔 */
  privateKeyConfigured: boolean
  publicKeyConfigured: boolean
  sm2PrivateKeyConfigured: boolean
  sm2PublicKeyConfigured: boolean
}

export interface TlpayConfigPageReqVO {
  pageNo: number
  pageSize: number
  shopName?: string
  enabled?: boolean
}

export interface TlpayConfigSaveReqVO {
  id: number
  tlEnabled?: boolean
  tlMchId?: string
  tlAppId?: string
  tlSignType?: string
  tlNotifyUrl?: string
  /** 空串 = 保留不变；__CLEAR__ = 主动清空；其它 = 覆盖 */
  tlRsaPrivateKey?: string
  tlRsaPublicKey?: string
  tlSm2PrivateKey?: string
  tlSm2PublicKey?: string
}

const BASE = '/merchant/tlpay'

export const getTlpayConfigPage = (params: TlpayConfigPageReqVO) =>
  request.get({ url: `${BASE}/page`, params })

export const getTlpayConfig = (id: number) =>
  request.get({ url: `${BASE}/get?id=${id}` })

export const saveTlpayConfig = (data: TlpayConfigSaveReqVO) =>
  request.put({ url: `${BASE}/save`, data })
