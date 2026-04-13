import request from '@/sheep/request';

export default {
  // 创建AI视频任务
  create: (data) => request({ url: '/video/app/task/create', method: 'POST', data }),
  // 获取视频任务详情
  get: (id) => request({ url: '/video/app/task/get', method: 'GET', params: { id } }),
  // 发布视频到抖音
  publishDouyin: (id) => request({ url: '/video/app/task/publish-douyin', method: 'POST', params: { id } }),
  // 获取抖音授权URL
  getDouyinOAuthUrl: (merchantId, redirectUri) =>
    request({ url: '/video/douyin/oauth/url', method: 'GET', params: { merchantId, redirectUri } }),
};
