/** 分 → 元，保留 2 位小数 */
export function fen2yuan(fen) {
  if (fen == null) return '0.00';
  return (fen / 100).toFixed(2);
}

/** AI 视频状态码 → 文案 & 颜色 */
export const AI_VIDEO_STATUS = {
  1: { text: '文案生成中', color: '#3B82F6' },
  2: { text: '待确认文案', color: '#F59E0B' },
  3: { text: '视频合成中', color: '#3B82F6' },
  4: { text: '已完成', color: '#10B981' },
  5: { text: '失败', color: '#EF4444' },
};

/** 订单状态码 → 文案 */
export const ORDER_STATUS = {
  10: { text: '待发货', color: '#F59E0B' },
  20: { text: '待核销', color: '#3B82F6' },
  30: { text: '已完成', color: '#10B981' },
};

/** 快递公司列表 */
export const EXPRESS_COMPANIES = [
  { code: 'SF', name: '顺丰速运' },
  { code: 'YTO', name: '圆通速递' },
  { code: 'ZTO', name: '中通快递' },
  { code: 'STO', name: '申通快递' },
  { code: 'YD', name: '韵达快递' },
  { code: 'JD', name: '京东物流' },
  { code: 'DBL', name: '德邦快递' },
  { code: 'MT', name: '美团闪送' },
];
