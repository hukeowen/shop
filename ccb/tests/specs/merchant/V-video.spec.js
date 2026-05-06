// M-V AI 视频：配额查询 / 任务列表 / 各页面可访问
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

test.describe('M-V AI 视频', () => {

  test('M-V-01 配额接口可调（路径 /quota 不是 /quota/get）', async ({ request }) => {
    const r = await request.get('/app-api/merchant/mini/ai-video/quota', {
      headers: { 'Authorization': `Bearer ${TOKEN}`, 'tenant-id': TENANT_ID },
    });
    const json = await r.json();
    expect(json.code, JSON.stringify(json)).toBe(0);
    const data = json.data || {};
    console.log(`✓ M-V-01 配额 used=${data.aiVideoUsed} quota=${data.aiVideoQuota} packId=${data.packageId}`);
  });

  test('M-V-02 任务列表接口', async ({ request }) => {
    const r = await request.get('/app-api/video/app/task/my-page', {
      headers: { 'Authorization': `Bearer ${TOKEN}`, 'tenant-id': TENANT_ID },
    });
    const json = await r.json();
    expect(json.code, JSON.stringify(json)).toBe(0);
    const list = Array.isArray(json.data) ? json.data : (json.data?.list || []);
    console.log(`✓ M-V-02 任务列表 ${list.length} 条`);
  });

  test('M-V-10 ai-video 入口页可访问', async ({ page }) => {
    await page.goto('/m/#/pages/ai-video/index');
    await page.waitForTimeout(3000);
    expect(page.url()).not.toContain('merchant-login');
    await page.screenshot({ path: 'test-results/M-V-10-ai-video-index.png', fullPage: true });
  });

  test('M-V-11 创建页可访问', async ({ page }) => {
    await page.goto('/m/#/pages/ai-video/create');
    await page.waitForTimeout(2500);
    expect(page.url()).not.toContain('merchant-login');
    await page.screenshot({ path: 'test-results/M-V-11-create.png', fullPage: true });
  });

  test('M-V-12 历史页可访问', async ({ page }) => {
    await page.goto('/m/#/pages/ai-video/history');
    await page.waitForTimeout(2500);
    expect(page.url()).not.toContain('merchant-login');
  });

  test('M-V-13 配额购买页可访问', async ({ page }) => {
    await page.goto('/m/#/pages/ai-video/quota');
    await page.waitForTimeout(2500);
    expect(page.url()).not.toContain('merchant-login');
    await page.screenshot({ path: 'test-results/M-V-13-quota.png', fullPage: true });
  });

  test('M-V-14 配额订单列表页可访问', async ({ page }) => {
    await page.goto('/m/#/pages/ai-video/package-orders');
    await page.waitForTimeout(2500);
    expect(page.url()).not.toContain('merchant-login');
  });
});
