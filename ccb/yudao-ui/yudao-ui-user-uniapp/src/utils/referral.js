/**
 * 推荐关系绑定工具（per-tenant 终生绑定）。
 *
 * 使用流程（C 端用户）：
 *   1) 落地页（如 shop/home?tenantId=X&inviter=Y）首次访问：
 *      App.vue onLaunch 调 savePendingReferrer(Y, X)，把 inviter+tenantId 存 localStorage
 *   2) 用户登录 → login 成功后调 flushPendingReferrer(userId)
 *      或 shop/home onLoad → 已登录用户直接 flushPendingReferrer(userId, tenantId)
 *   3) 后端 bindReferral 接口校验：
 *      - 当前用户 != inviter
 *      - inviter 在该 tenant 已激活
 *      - 当前用户在该 tenant 首次绑定（已绑则拒绝补绑 = V6 严格语义）
 *
 * 设计要点：
 *   - 落地页不要求登录 → 先存 localStorage，登录后再 bind
 *   - 终生绑定：成功 / 失败 / 已绑 都清 pending；同上线再分享别的店要重新走链接
 *   - 不抛异常：登录是高频路径，绑定失败不阻塞 UI
 */
import { bindReferral } from '@/api/promo.js';

const KEY = 'promo:pendingBind';

export function savePendingReferrer(inviterUserId, tenantId) {
  if (!inviterUserId || Number(inviterUserId) <= 0) return;
  try {
    const payload = JSON.stringify({
      inviter: Number(inviterUserId),
      tenantId: tenantId ? Number(tenantId) : 0,
      ts: Date.now(),
    });
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(KEY, payload);
    } else {
      uni.setStorageSync(KEY, payload);
    }
  } catch {}
}

function readPending() {
  try {
    let raw;
    if (typeof localStorage !== 'undefined') {
      raw = localStorage.getItem(KEY);
    } else {
      raw = uni.getStorageSync(KEY);
    }
    if (!raw) return null;
    if (/^\d+$/.test(raw)) return { inviter: Number(raw), tenantId: 0 };
    return JSON.parse(raw);
  } catch { return null; }
}

function clearPending() {
  try {
    if (typeof localStorage !== 'undefined') localStorage.removeItem(KEY);
    uni.removeStorageSync(KEY);
  } catch {}
}

/**
 * 把暂存的 inviter 真正绑定。
 *
 * @param {number} currentUserId  当前登录用户 ID
 * @param {number} [overrideTenantId] 可选；shop/home 已知目标店时传入优先
 * @returns {Promise<boolean>}    true=首次成功绑定；false=跳过/已绑/资格不足
 */
export async function flushPendingReferrer(currentUserId, overrideTenantId) {
  if (!currentUserId) return false;
  const pending = readPending();
  if (!pending || !pending.inviter || pending.inviter === Number(currentUserId)) {
    clearPending();
    return false;
  }
  const targetTenant = overrideTenantId || pending.tenantId;
  if (!targetTenant || targetTenant <= 0) {
    // 没目标 tenant 不能绑（per-tenant 绑定）— 保留 pending 等下次进店时再 flush
    return false;
  }
  try {
    const newlyBound = await bindReferral(pending.inviter, targetTenant);
    clearPending();
    return !!newlyBound;
  } catch {
    clearPending();
    return false;
  }
}
