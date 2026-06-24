import { get, post } from '@/utils/request.js';

// 营销配置（按 tenant-id header 决定店）
export const getPromoConfig = (tenantId) =>
  get('/app-api/merchant/mini/promo/config', { tenantId });

// 我的账户（跨店聚合 / 单店：通过 tenantId 决定）
//   tenantId 不传 → 跨店聚合（钱包/我的）
//   tenantId 传 → 单店（checkout 抵扣展示）
export const getAccount = (tenantId) =>
  get('/app-api/merchant/mini/promo/account', tenantId ? { tenantId } : {});

// 推广积分流水（分页倒序）— 传 tenantId 则只看该店；不传 = 跨店
export const listPromoRecords = (pageNo = 1, pageSize = 20, tenantId) =>
  get(`/app-api/merchant/mini/promo/promo-records?pageNo=${pageNo}&pageSize=${pageSize}`, tenantId ? { tenantId } : {});

// 消费积分流水（分页倒序）— 同上
export const listConsumeRecords = (pageNo = 1, pageSize = 20, tenantId) =>
  get(`/app-api/merchant/mini/promo/consume-records?pageNo=${pageNo}&pageSize=${pageSize}`, tenantId ? { tenantId } : {});

// 按店推广积分兑付申请（amount 单位分；tenantId 决定扣哪家店的积分 + 哪家商户审批）
export const applyPromoWithdraw = (amount, tenantId) =>
  post(`/app-api/merchant/mini/withdraw/apply?amount=${amount}&tenantId=${tenantId}`, null, { tenantId });

// 我的兑付申请列表（按店）
export const listMyPromoWithdraws = () =>
  get('/app-api/merchant/mini/withdraw/my-list');

// 确认已收款（PAID → COMPLETED）
export const confirmWithdrawReceived = (id) =>
  post(`/app-api/merchant/mini/withdraw/confirm-received?id=${id}`, null);

// 我的所有队列（跨店聚合，QUEUEING 状态）
export const listMyQueues = () =>
  get('/app-api/merchant/mini/promo/my-queues');

// 邀请资格（只有完成购买「推 N 反 1」商品的店才可分享）
// 返回 { eligible:boolean, shops:[{tenantId, shopName, queueingCount, completedCount}] }
export const getInviteEligibility = () =>
  get('/app-api/merchant/mini/promo/invite-eligibility');

// 我的推荐人（按当前 tenant）
export const getReferralParent = (tenantId) =>
  get('/app-api/merchant/mini/promo/referral/parent', { tenantId });

// 我的下级人数（按当前 tenant）
export const getMyChildrenCount = (tenantId) =>
  get('/app-api/merchant/mini/promo/referral/my-children-count', { tenantId });

// 绑定推荐关系
export const bindReferral = (inviterUserId, tenantId) =>
  post(`/app-api/merchant/mini/promo/referral/bind?inviterUserId=${inviterUserId}`, null, { tenantId });

// 我的 SPU 星级列表（按当前 tenant）
export const listMySpuStars = (tenantId) =>
  get('/app-api/merchant/mini/promo/my-spu-stars', { tenantId });

// 推广积分 → 消费积分
export const convertPromoToConsume = (promoAmount, idempotencyKey) =>
  post(`/app-api/merchant/mini/promo/convert?promoAmount=${promoAmount}&idempotencyKey=${idempotencyKey}`);

// === 以下需要后端新增 ===

// 中奖公榜（实时滚动 / 列表） — TODO: 后端待加
export const listWinners = (tenantId, limit = 50) =>
  get(`/app-api/merchant/mini/promo/winners?limit=${limit}`, tenantId ? { tenantId } : {});

// 首页 ticker（最新中奖滚动条）— TODO: 后端待加
export const listWinnersTicker = (limit = 8) =>
  get(`/app-api/merchant/mini/promo/winners-ticker?limit=${limit}`);

// 今日入账汇总（今日合计 ¥X + N 分）— TODO: 后端待加
export const getTodayStat = (tenantId) =>
  get('/app-api/merchant/mini/promo/today-stat', tenantId ? { tenantId } : {});
