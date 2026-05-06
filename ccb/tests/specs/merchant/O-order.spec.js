// M-O 订单管理：列表 / 详情 / 状态分页
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

test.describe('M-O 订单', () => {

  test('M-O-01 订单列表接口', async ({ request }) => {
    const r = await request.get('/app-api/merchant/mini/order/page?pageNo=1&pageSize=20', {
      headers: { 'Authorization': `Bearer ${TOKEN}`, 'tenant-id': TENANT_ID },
    });
    const json = await r.json();
    expect(json.code, JSON.stringify(json)).toBe(0);
    const total = json.data?.total ?? json.data?.list?.length ?? 0;
    console.log(`✓ M-O-01 订单列表 total=${total}`);
  });

  test('M-O-10 订单列表页', async ({ page }) => {
    await page.goto('/m/#/pages/order/list');
    await page.waitForTimeout(2500);
    expect(page.url()).not.toContain('merchant-login');
    await page.screenshot({ path: 'test-results/M-O-10-order-list.png', fullPage: true });
  });
});
