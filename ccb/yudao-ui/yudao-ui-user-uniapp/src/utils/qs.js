/**
 * 跨端 query string 拼接。
 *
 * 替代 `new URLSearchParams(params).toString()` —— 微信小程序运行时没有 URLSearchParams 全局，
 * 直接用会抛 ReferenceError，导致 GET 接口（附近好店 / 商品 / 优惠券 / 结算等）整条失败、列表空白。
 * 本函数在 H5 与小程序结果一致；跳过 undefined / null / 空字符串。
 *
 * @param {Record<string, any>} params
 * @returns {string} 形如 `a=1&b=2`（不含前导 ?）
 */
export function toQuery(params = {}) {
  const parts = [];
  Object.keys(params || {}).forEach((k) => {
    const v = params[k];
    if (v === undefined || v === null || v === '') return;
    parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(v)}`);
  });
  return parts.join('&');
}
