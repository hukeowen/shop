import request from '@/config/axios'

// App 版本管理（平台运营 → App 自动升级）

export interface AppVersionVO {
  id?: number
  platform: string
  versionName: string
  versionCode: number
  downloadUrl: string
  updateLog?: string
  forceUpdate?: boolean
  fileSize?: number
  status?: number
  remark?: string
  createTime?: string
}

export const getAppVersionPage = (params: any) =>
  request.get({ url: '/merchant/app-version/page', params })

export const getAppVersion = (id: number) =>
  request.get({ url: `/merchant/app-version/get?id=${id}` })

export const createAppVersion = (data: AppVersionVO) =>
  request.post({ url: '/merchant/app-version/create', data })

export const updateAppVersion = (data: AppVersionVO) =>
  request.put({ url: '/merchant/app-version/update', data })

export const deleteAppVersion = (id: number) =>
  request.delete({ url: `/merchant/app-version/delete?id=${id}` })
