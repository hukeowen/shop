// 用户端登录 setup
import { test as setup, expect } from '@playwright/test';

const USER_PHONE = process.env.USER_PHONE || '13700137000';

setup('用户端登录（短信 demo 码）', async ({ page, request }) => {
  // 用户端走 /app-api/app/auth/sms-login（自动注册）
  const sendR = await request.post('/app-api/app/auth/send-sms-code', {
    headers: { 'tenant-id': '1' },
    data: { mobile: USER_PHONE, scene: 1 },
  }).catch(() => null);
  // demo 模式 send 端点可能不存在/可选；继续用固定码 888888 登录
  const r = await request.post('/app-api/app/auth/sms-login', {
    headers: { 'tenant-id': '1' },
    data: { mobile: USER_PHONE, code: '888888' },
  });
  if (!r.ok()) {
    console.log('user sms-login failed, body=', await r.text());
  }
  expect(r.ok()).toBeTruthy();
  const json = await r.json();
  if (json.code !== 0) {
    throw new Error(`user login non-zero code: ${JSON.stringify(json)}`);
  }
  const token = json.data.token;

  await page.goto('/m/');
  await page.evaluate(({ token, userId, phone }) => {
    localStorage.setItem('token', token);
    localStorage.setItem('user-store-v1', JSON.stringify({
      token, userId, phone, activeRole: 'user',
    }));
    localStorage.setItem('tenantId', '1');
  }, { token, userId: json.data.userId, phone: USER_PHONE });

  await page.context().storageState({ path: '.auth/user.json' });
  console.log(`✓ 用户登录 token=${token.slice(0,12)}... userId=${json.data.userId}`);
});
