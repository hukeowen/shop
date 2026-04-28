/**
 * 推荐链绑定工具：
 *
 *   - savePending(inviterUserId)  把分享链接里的 inviter 暂存到 localStorage
 *   - flushPending(currentUserId) 用户登录后调一次，挂着的 inviter 就会落库；成功后清掉
 *
 * 设计要点：
 *   1. 落地页不要求用户已登录 → 只能先存，登录后再 bind（后端校验 currentUser != inviter）
 *   2. bindReferral 后端是终生绑定 + 防自绑 + 防环 → 重复调用幂等无副作用
 *   3. 不抛异常：落地 / 登录是高频路径，不能因为绑定失败阻塞 UI
 */
import { bindReferral } from '../api/promo.js';

const KEY = 'promo:pendingInviter';

export function savePendingReferrer(inviterUserId) {
  if (!inviterUserId || Number(inviterUserId) <= 0) return;
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(KEY, String(inviterUserId));
    } else {
      uni.setStorageSync(KEY, String(inviterUserId));
    }
  } catch {}
}

function readPending() {
  try {
    if (typeof localStorage !== 'undefined') {
      const v = localStorage.getItem(KEY);
      if (v) return Number(v);
    }
    const v = uni.getStorageSync(KEY);
    return v ? Number(v) : 0;
  } catch {
    return 0;
  }
}

function clearPending() {
  try {
    if (typeof localStorage !== 'undefined') localStorage.removeItem(KEY);
    uni.removeStorageSync(KEY);
  } catch {}
}

/**
 * 把暂存的 inviter 真正绑定。需在用户登录完成 / 拿到 userId 之后调一次。
 *
 * @param {number} currentUserId  当前登录用户的 ID（自绑会被后端拒）
 * @returns {Promise<boolean>}    true = 已成功绑定（首次）；false = 没有 pending 或绑定不生效
 */
export async function flushPendingReferrer(currentUserId) {
  if (!currentUserId) return false;
  const inviter = readPending();
  if (!inviter || inviter === Number(currentUserId)) {
    clearPending();
    return false;
  }
  try {
    const newlyBound = await bindReferral(inviter);
    // bindReferral 成功（true 或 false 都不再 pending）
    clearPending();
    return !!newlyBound;
  } catch {
    // 网络 / 后端异常 — 保留 pending，下次再尝试
    return false;
  }
}
