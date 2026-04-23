import { defineStore } from 'pinia';
import { request } from '../api/request.js';

// 持久化到 localStorage（H5 / uni 通用；小程序下 localStorage 不可用时 fallback 到 uni.setStorageSync）
const STORAGE_KEY = 'user-store-v1';
const PERSISTED_FIELDS = [
  'token', 'refreshToken', 'openid', 'userId', 'merchantId', 'phone',
  'roles', 'activeRole', 'shop', 'tenantId', 'user',
];

function readPersisted() {
  try {
    if (typeof localStorage !== 'undefined') {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) return JSON.parse(raw) || {};
    }
  } catch {}
  try {
    const raw = uni.getStorageSync(STORAGE_KEY);
    if (raw) return typeof raw === 'string' ? JSON.parse(raw) : raw;
  } catch {}
  return {};
}

function writePersisted(state) {
  const pick = {};
  for (const k of PERSISTED_FIELDS) pick[k] = state[k];
  const serialized = JSON.stringify(pick);
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(STORAGE_KEY, serialized);
      return;
    }
  } catch {}
  try {
    uni.setStorageSync(STORAGE_KEY, serialized);
  } catch {}
}

function clearPersisted() {
  try {
    if (typeof localStorage !== 'undefined') localStorage.removeItem(STORAGE_KEY);
  } catch {}
  try {
    uni.removeStorageSync(STORAGE_KEY);
  } catch {}
}

const hydrated = readPersisted();

export const useUserStore = defineStore('user', {
  state: () => ({
    // Phase 0.2 身份字段
    token: hydrated.token || '',
    refreshToken: hydrated.refreshToken || '',
    openid: hydrated.openid || '',
    userId: hydrated.userId || 0,
    merchantId: hydrated.merchantId || 0,
    phone: hydrated.phone || '',
    roles: Array.isArray(hydrated.roles) ? hydrated.roles : [],
    activeRole: hydrated.activeRole || '',
    // 兼容老代码
    tenantId: hydrated.tenantId || 0,
    user: hydrated.user || null,
    shop: hydrated.shop || null,
  }),
  getters: {
    loggedIn: (s) => !!s.token,
    isMerchant: (s) => s.activeRole === 'merchant' || s.roles.includes('merchant'),
    isMember: (s) => s.activeRole === 'member' || s.roles.includes('member'),
    canSwitch: (s) => (s.roles || []).length >= 2,
  },
  actions: {
    /** 从持久化存储恢复（App.vue onLaunch 调一次即可） */
    hydrate() {
      const h = readPersisted();
      for (const k of PERSISTED_FIELDS) {
        if (h[k] !== undefined) this[k] = h[k];
      }
    },
    /** 主动落盘（在每个会修改身份态的 action 末尾调用） */
    persist() {
      writePersisted(this.$state);
    },

    /**
     * 微信小程序一键登录（先调 uni.login 拿 code，再换 token）
     * @returns {Promise<{token,roles,activeRole,phone,merchantId,userId,openid}>}
     */
    async wxMiniLogin() {
      const code = await new Promise((resolve, reject) => {
        try {
          uni.login({
            provider: 'weixin',
            success: (r) => (r && r.code ? resolve(r.code) : reject(new Error('微信未返回 code'))),
            fail: (e) => reject(new Error(e?.errMsg || '微信登录失败')),
          });
        } catch (e) {
          reject(e);
        }
      });
      const resp = await request({
        url: '/app-api/app/auth/wx-mini-login',
        method: 'POST',
        data: { code },
      });
      this.token = resp.token || '';
      this.refreshToken = resp.refreshToken || '';
      this.openid = resp.openid || '';
      this.userId = resp.userId || 0;
      this.merchantId = resp.merchantId || 0;
      this.phone = resp.phone || '';
      this.roles = Array.isArray(resp.roles) ? resp.roles : [];
      this.activeRole = resp.activeRole || (this.roles[0] || '');
      this.persist();
      return resp;
    },

    /**
     * 绑定手机号（接收 <button open-type="getPhoneNumber"> 的 getphonenumber 事件）
     */
    async bindPhoneByWxButton(e) {
      const detail = e?.detail || {};
      if (!detail.encryptedData || !detail.iv) {
        throw new Error('未授权手机号');
      }
      const resp = await request({
        url: '/app-api/app/auth/bind-phone',
        method: 'POST',
        data: { encryptedData: detail.encryptedData, iv: detail.iv },
      });
      this.phone = resp.phone || this.phone;
      this.persist();
      return resp;
    },

    /**
     * 申请成为商户。可选传入 getphonenumber 事件，一次性绑手机号
     */
    async applyMerchant(inviteCode, e) {
      const data = { inviteCode };
      const detail = e?.detail;
      if (detail?.encryptedData && detail?.iv) {
        data.encryptedData = detail.encryptedData;
        data.iv = detail.iv;
      }
      const resp = await request({
        url: '/app-api/app/auth/apply-merchant',
        method: 'POST',
        data,
      });
      if (resp.token) this.token = resp.token;
      this.merchantId = resp.merchantId || this.merchantId;
      if (Array.isArray(resp.roles)) this.roles = resp.roles;
      this.activeRole = 'merchant';
      this.persist();
      return resp;
    },

    /** 在 merchant / member 之间切换当前工作身份 */
    async switchRole(role) {
      const resp = await request({
        url: '/app-api/app/auth/switch-role',
        method: 'POST',
        data: { role },
      });
      if (resp.token) this.token = resp.token;
      this.activeRole = resp.activeRole || role;
      this.persist();
      return resp;
    },

    /** 刷新 me 信息（App 启动时 token 存在就 refresh 一次拿角色） */
    async refreshMe() {
      const me = await request({ url: '/app-api/app/auth/me', method: 'GET' });
      this.openid = me.openid || this.openid;
      this.userId = me.userId || this.userId;
      this.merchantId = me.merchantId || this.merchantId;
      this.phone = me.phone || this.phone;
      if (Array.isArray(me.roles)) this.roles = me.roles;
      this.activeRole = me.activeRole || this.activeRole;
      this.persist();
      return me;
    },

    logout() {
      this.$reset();
      clearPersisted();
    },
  },
});
