import { defineStore } from 'pinia';

const KEY = 'kexiaoer-user-store-v1';

function load() {
  try {
    // #ifdef H5
    if (typeof localStorage !== 'undefined') {
      const raw = localStorage.getItem(KEY);
      if (raw) return JSON.parse(raw);
    }
    // #endif
    // #ifndef H5
    // 小程序 / APP 无 localStorage，用 uni 同步存储读回（保证重启后仍是登录态）
    const raw = uni.getStorageSync(KEY);
    if (raw) return typeof raw === 'string' ? JSON.parse(raw) : raw;
    // #endif
  } catch {}
  return {};
}

function persist(state) {
  const payload = JSON.stringify({
    token: state.token,
    refreshToken: state.refreshToken,
    expiresTime: state.expiresTime,
    userId: state.userId,
    phone: state.phone,
    nickname: state.nickname,
    avatar: state.avatar,
    tenantId: state.tenantId,
    roles: state.roles,
  });
  try {
    // #ifdef H5
    if (typeof localStorage !== 'undefined') localStorage.setItem(KEY, payload);
    // #endif
    // #ifndef H5
    uni.setStorageSync(KEY, payload);
    // #endif
  } catch {}
}

export const useUserStore = defineStore('user', {
  state: () => {
    const o = load();
    return {
      token: o.token || '',
      refreshToken: o.refreshToken || '',
      expiresTime: o.expiresTime || 0,
      userId: o.userId || 0,
      phone: o.phone || '',
      nickname: o.nickname || '',
      avatar: o.avatar || '',
      tenantId: o.tenantId || '',
      roles: o.roles || [],
    };
  },
  getters: {
    isLogin: (s) => !!s.token,
  },
  actions: {
    setLogin(payload) {
      // 兼容字段名：yudao /sms-login 返 accessToken，merchant /apply 返 token
      this.token = payload.accessToken || payload.token || '';
      this.refreshToken = payload.refreshToken || '';
      this.expiresTime = payload.expiresTime || 0;
      this.userId = payload.userId || 0;
      this.phone = payload.phone || payload.mobile || '';
      this.nickname = payload.nickname || '';
      this.avatar = payload.avatar || payload.avatar || '';
      this.tenantId = payload.tenantId || '';
      this.roles = payload.roles || [];
      persist(this.$state);
      try { uni.setStorageSync('token', this.token); } catch {}
      try { uni.setStorageSync('tenantId', this.tenantId); } catch {}
    },
    logout() {
      this.token = '';
      this.refreshToken = '';
      this.expiresTime = 0;
      this.userId = 0;
      this.phone = '';
      this.nickname = '';
      this.avatar = '';
      this.tenantId = '';
      this.roles = [];
      // #ifdef H5
      try { if (typeof localStorage !== 'undefined') localStorage.removeItem(KEY); } catch {}
      // #endif
      // #ifndef H5
      try { uni.removeStorageSync(KEY); } catch {}
      // #endif
      try { uni.removeStorageSync('token'); } catch {}
      try { uni.removeStorageSync('tenantId'); } catch {}
    },
  },
});
