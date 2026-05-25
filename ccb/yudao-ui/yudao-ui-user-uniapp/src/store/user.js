import { defineStore } from 'pinia';

const KEY = 'kexiaoer-user-store-v1';

function load() {
  try {
    if (typeof localStorage !== 'undefined') {
      const raw = localStorage.getItem(KEY);
      if (raw) return JSON.parse(raw);
    }
  } catch {}
  return {};
}

function persist(state) {
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(KEY, JSON.stringify({
        token: state.token,
        refreshToken: state.refreshToken,
        expiresTime: state.expiresTime,
        userId: state.userId,
        phone: state.phone,
        nickname: state.nickname,
        avatar: state.avatar,
        tenantId: state.tenantId,
        roles: state.roles,
      }));
    }
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
      this.token = payload.token || '';
      this.refreshToken = payload.refreshToken || '';
      this.expiresTime = payload.expiresTime || 0;
      this.userId = payload.userId || 0;
      this.phone = payload.phone || payload.mobile || '';
      this.nickname = payload.nickname || '';
      this.avatar = payload.avatar || '';
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
      try { if (typeof localStorage !== 'undefined') localStorage.removeItem(KEY); } catch {}
      try { uni.removeStorageSync('token'); } catch {}
    },
  },
});
