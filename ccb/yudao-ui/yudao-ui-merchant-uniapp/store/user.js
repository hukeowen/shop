import { defineStore } from 'pinia';
import { smsLogin, logout as apiLogout } from '../api/auth.js';

export const useUserStore = defineStore('user', {
  state: () => ({
    token: uni.getStorageSync('token') || '',
    tenantId: uni.getStorageSync('tenantId') || 0,
    user: uni.getStorageSync('user') || null,
    shop: uni.getStorageSync('shop') || null,
  }),
  getters: {
    loggedIn: (s) => !!s.token,
  },
  actions: {
    async loginBySms({ mobile, code }) {
      const res = await smsLogin({ mobile, code });
      this.token = res.token;
      this.tenantId = res.tenantId;
      this.user = res.user;
      this.shop = res.shop;
      uni.setStorageSync('token', res.token);
      uni.setStorageSync('tenantId', res.tenantId);
      uni.setStorageSync('user', res.user);
      uni.setStorageSync('shop', res.shop);
      return res;
    },
    async logout() {
      await apiLogout();
      this.token = '';
      this.tenantId = 0;
      this.user = null;
      this.shop = null;
      uni.removeStorageSync('token');
      uni.removeStorageSync('tenantId');
      uni.removeStorageSync('user');
      uni.removeStorageSync('shop');
    },
  },
});
