// 分 → 元（保留 2 位，千分逗号）。默认 false，因 H5 模板已自带 ¥ 字面量，避免 ¥¥
export function fen2yuan(fen, withSign = false) {
  if (fen == null || fen === '') return withSign ? '¥0.00' : '0.00';
  const n = Number(fen) / 100;
  const s = n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return withSign ? `¥${s}` : s;
}

// "2026-05-22T16:14:04" / unix-ms → "05-22 16:14" / "今天 16:14"
export function fmtTime(t) {
  if (!t) return '';
  const d = typeof t === 'number' ? new Date(t) : new Date(String(t).replace('T', ' ').replace(/-/g, '/'));
  if (Number.isNaN(d.getTime())) return '';
  const now = new Date();
  const pad = (x) => String(x).padStart(2, '0');
  const sameDay = d.toDateString() === now.toDateString();
  if (sameDay) return `今天 ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

// 距离 m → 友好
export function fmtDistance(m) {
  if (m == null) return '';
  if (m < 1000) return `${Math.round(m)}m`;
  return `${(m / 1000).toFixed(1)}km`;
}
