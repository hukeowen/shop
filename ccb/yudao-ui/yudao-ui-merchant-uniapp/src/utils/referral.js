/**
 * 推荐链绑定工具（per-tenant 终生绑定）。
 *
 *   - savePending(inviter, tenantId)  把分享链接里的 inviter + 目标店 tenantId 暂存
 *   - flushPending(currentUserId, tenantId?) 用户登录后调一次，挂着的 inviter 落库
 *
 * 设计要点：
 *   1. 落地页不要求用户已登录 → 只能先存，登录后再 bind（后端校验 currentUser != inviter）
 *   2. 上下级关系**按店铺独立**：bindReferral 必须在目标店 tenant 上下文调
 *   3. 上级资格：后端校验 inviter 在该 tenant 已激活（买过推 N 反 1 商品）
 *   4. 终生绑定：一旦绑定（成功 / 失败 / 已绑）就清 pending；下次同上级想再绑别的店要重新走链接
 *   5. 不抛异常：落地 / 登录是高频路径，不能因为绑定失败阻塞 UI
 */
import { bindReferral } from '../api/promo.js';

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
    // 兼容旧版本（直接是数字字符串 = 旧的 inviter-only 存储）
    if (/^\d+$/.test(raw)) return { inviter: Number(raw), tenantId: 0 };
    return JSON.parse(raw);
  } catch { return null; }
}

function clearPending() {
  try {
    if (typeof localStorage !== 'undefined') localStorage.removeItem(KEY);
    uni.removeStorageSync(KEY);
    // 同时清理旧 key
    if (typeof localStorage !== 'undefined') localStorage.removeItem('promo:pendingInviter');
  } catch {}
}

/**
 * 把暂存的 inviter 真正绑定。
 *
 * @param {number} currentUserId  当前登录用户的 ID
 * @param {number} [overrideTenantId] 可选；优先用入参 tenantId（适用于 shop-home 已知目标店场景）
 * @returns {Promise<boolean>}    true = 已成功绑定（首次）；false = 跳过 / 已绑 / 资格不足
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
    // 没有目标 tenant 不能绑（per-tenant 绑定）— 保留 pending 等下次进店时再 flush
    return false;
  }
  try {
    const newlyBound = await bindReferral(pending.inviter, undefined, targetTenant);
    clearPending();
    return !!newlyBound;
  } catch {
    return false;
  }
}
