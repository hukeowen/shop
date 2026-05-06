// M-S 设置 / 我的页相关
import { test, expect } from '@playwright/test';
import fs from 'node:fs';

const TENANT_ID = '1';
function readToken() {
  try {
    const raw = JSON.parse(fs.readFileSync('.auth/merchant.json', 'utf-8'));
    return raw.origins?.[0]?.localStorage?.find((kv) => kv.name === 'token')?.value || '';
  } catch { return ''; }
}
const TOKEN = readToken();

test.describe('M-S 设置 / 个人页', () => {

  test('M-S-01 shop/info 接口可调', async ({ request }) => {
    const r = await request.get('/app-api/merchant/mini/shop/info', {
      headers: { 'Authorization': `Bearer ${TOKEN}`, 'tenant-id': TENANT_ID },
    });
    const json = await r.json();
    expect(json.code, JSON.stringify(json)).toBe(0);
    console.log(`✓ M-S-01 shop/info 店名=${json.data?.shopName} tenantId=${json.data?.tenantId}`);
  });

  test('M-S-02 dashboard/summary 接口可调', async ({ request }) => {
    const r = await request.get('/app-api/merchant/mini/dashboard/summary', {
      headers: { 'Authorization': `Bearer ${TOKEN}`, 'tenant-id': TENANT_ID },
    });
    const json = await r.json();
    expect(json.code, JSON.stringify(json)).toBe(0);
    console.log(`✓ M-S-02 dashboard/summary 字段: ${Object.keys(json.data || {}).join(',')}`);
  });

  test('M-S-10 编辑店铺页可访问', async ({ page }) => {
    await page.goto('/m/#/pages/me/shop-edit');
    await page.waitForTimeout(2500);
    expect(page.url()).not.toContain('merchant-login');
  });

  test('M-S-11 关于摊小二页', async ({ page }) => {
    await page.goto('/m/#/pages/me/about');
    await page.waitForTimeout(2000);
    expect(page.url()).not.toContain('merchant-login');
  });

  test('M-S-12 帮助与反馈页', async ({ page }) => {
    await page.goto('/m/#/pages/me/help');
    await page.waitForTimeout(2000);
    expect(page.url()).not.toContain('merchant-login');
  });

  test('M-S-13 营销配置 v6 页可访问', async ({ page }) => {
    await page.goto('/m/#/pages/me/promo-config');
    await page.waitForTimeout(2500);
    expect(page.url()).not.toContain('merchant-login');
    await page.screenshot({ path: 'test-results/M-S-13-promo-config.png', fullPage: true });
  });

  test('M-S-14 提现审批页', async ({ page }) => {
    await page.goto('/m/#/pages/me/withdraw-approve');
    await page.waitForTimeout(2500);
    expect(page.url()).not.toContain('merchant-login');
  });
});
