import request from '@/config/axios'

export interface UserFeedbackVO {
  id: number
  tenantId: number
  userId: number
  category: string
  content: string
  contact?: string
  images?: string
  /** 0 待处理 / 1 处理中 / 2 已解决 / 3 已关闭 */
  status: number
  reply?: string
  repliedAt?: Date
  createTime: Date
}

export interface UserFeedbackPageReqVO {
  pageNo: number
  pageSize: number
  status?: number
  category?: string
  userId?: number
  /** 仅 tenantId=1 平台超管可用 */
  allTenants?: boolean
}

export const UserFeedbackApi = {
  page: (params: UserFeedbackPageReqVO) =>
    request.get({ url: '/merchant/user-feedback/page', params }),

  reply: (id: number, reply: string) =>
    request.put({ url: '/merchant/user-feedback/reply', params: { id, reply } }),

  updateStatus: (id: number, status: number) =>
    request.put({ url: '/merchant/user-feedback/status', params: { id, status } }),
}
