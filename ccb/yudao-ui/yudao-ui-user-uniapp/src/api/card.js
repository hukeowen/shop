import { get } from '@/utils/request.js';

// 我的卡包（跨店）：每张卡含 shopName/name/cardNo/remainCount/expireTime/effectiveStatus 等
export const listMyCards = () => get('/app-api/merchant/mini/card/my');

// 单张卡详情（出示码页）：含 verifyRecords
export const getMyCard = (id) => get(`/app-api/merchant/mini/card/get?id=${id}`);
