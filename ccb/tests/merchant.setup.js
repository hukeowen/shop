// 商户登录 setup：跑一次 password-login 拿 token + 落 storageState
import { test as setup, expect } from '@playwright/test';

const MERCHANT_PHONE = process.env.MERCHANT_PHONE || '18888888888';
const MERCHANT_PASSWORD = process.env.MERCHANT_PASSWORD || '888888';

setup('商户登录拿 token', async ({ page, request }) => {
  // 直调 API 拿 token（绕过 UI 输入更稳）
  const r = await request.post('/app-api/app/auth/password-login', {
    headers: { 'tenant-id': '1' },
    data: { mobile: MERCHANT_PHONE, password: MERCHANT_PASSWORD },
  });
  expect(r.ok()).toBeTruthy();
  const json = await r.json();
  expect(json.code).toBe(0);
  const token = json.data.token;
  const userId = json.data.userId;
  const merchantId = json.data.merchantId;
  const tenantId = 1;

  // 把 token 注入 localStorage（前端约定 key）
  await page.goto('/m/');
  await page.evaluate(({ token, userId, merchantId, tenantId, phone, shopName }) => {
    localStorage.setItem('token', token);
    localStorage.setItem('user-store-v1', JSON.stringify({
      token, userId, merchantId, phone, shopName, activeRole: 'merchant',
    }));
    localStorage.setItem('tenantId', String(tenantId));
  }, {
    token, userId, merchantId, tenantId,
    phone: json.data.phone,
    shopName: json.data.shopName,
  });

  await page.context().storageState({ path: '.auth/merchant.json' });
  console.log(`✓ 商户登录 token=${token.slice(0,12)}... merchantId=${merchantId} shop=${json.data.shopName}`);
});
