// M-A 商户认证：验证登录态有效 + 个人页可访问
import { test, expect } from '@playwright/test';

test('M-A-01 已登录访问商户工作台', async ({ page }) => {
  await page.goto('/m/#/pages/index/index');
  await page.waitForTimeout(2000);
  // 不应被弹回登录页
  expect(page.url(), '不应跳到 merchant-login').not.toContain('merchant-login');
});

test('M-A-02 商户「我的」页可访问', async ({ page }) => {
  await page.goto('/m/#/pages/me/index');
  await page.waitForTimeout(2500);
  // 截图作回归记录
  await page.screenshot({ path: 'test-results/M-A-02-me.png', fullPage: true });
  expect(page.url()).not.toContain('merchant-login');
});
