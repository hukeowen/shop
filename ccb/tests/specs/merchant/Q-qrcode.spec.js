// M-Q 商户二维码 + 分享
import { test, expect } from '@playwright/test';

test.describe('M-Q 二维码 / 分享', () => {

  test('M-Q-01 店铺二维码页 — 图片是否真渲染', async ({ page }) => {
    // 监听网络拿到 qrcode 接口和 /qr 请求结果
    const apiResp = [];
    page.on('response', (resp) => {
      const url = resp.url();
      if (/shop\/qrcode|\/qr\?/.test(url)) {
        apiResp.push({ url, status: resp.status(), ct: resp.headers()['content-type'] });
      }
    });

    const consoleMsgs = [];
    page.on('console', (msg) => consoleMsgs.push(`[${msg.type()}] ${msg.text()}`));
    page.on('pageerror', (err) => consoleMsgs.push(`[pageerror] ${err.message}`));

    await page.goto('/m/#/pages/me/qrcode');
    await page.waitForTimeout(5000);
    expect(page.url()).not.toContain('merchant-login');

    // 找页面上所有 img/uni-image 元素
    const imgInfo = await page.evaluate(() => {
      const all = [...document.querySelectorAll('img, uni-image img, [class*="qr"]')];
      return all.slice(0, 10).map((el) => ({
        tag: el.tagName,
        src: el.getAttribute('src') || el.style?.backgroundImage || '',
        nw: el.naturalWidth || 0,
        nh: el.naturalHeight || 0,
        cls: el.className || '',
      }));
    });
    console.log(`M-Q-01 image 元素数=${imgInfo.length}`);
    for (const i of imgInfo) {
      console.log(`  tag=${i.tag} cls="${i.cls}" nw=${i.nw}x${i.nh} src=${i.src.slice(0, 80)}`);
    }

    console.log(`M-Q-01 二维码相关接口响应:`);
    for (const r of apiResp) {
      console.log(`  ${r.status} ${r.ct} ${r.url.slice(0, 100)}`);
    }

    if (consoleMsgs.length) {
      console.log(`M-Q-01 控制台 ${consoleMsgs.length} 条:`);
      consoleMsgs.slice(0, 10).forEach((m) => console.log(`  ${m.slice(0, 150)}`));
    }

    await page.screenshot({ path: 'test-results/M-Q-01-shop-qrcode.png', fullPage: true });

    // 找一个 nw>0 的 image（真渲染了）
    const realImg = imgInfo.find((i) => i.nw > 0);
    if (!realImg) {
      console.log(`✗ 没有真正渲染的二维码图（所有 image natural=0）`);
    } else {
      console.log(`✓ 二维码已渲染 ${realImg.nw}x${realImg.nh}`);
    }
  });

  test('M-Q-02 sidecar /qr 端点 PNG 可访问', async ({ request }) => {
    const r = await request.get('/qr?text=https%3A%2F%2Fwww.doupaidoudian.com%2Fm%2Fshop-home&w=320&m=1');
    expect(r.ok()).toBeTruthy();
    const ct = r.headers()['content-type'];
    expect(ct).toMatch(/image\/(png|svg)/);
    console.log(`✓ M-Q-02 /qr ${ct} ${(await r.body()).length} bytes`);
  });

  test('M-Q-03 sidecar /qr center 参数（SVG 中心叠店铺名）', async ({ request }) => {
    const r = await request.get('/qr?text=https%3A%2F%2Fwww.doupaidoudian.com&w=320&m=1&center=' + encodeURIComponent('水果店'));
    expect(r.ok()).toBeTruthy();
    const ct = r.headers()['content-type'];
    expect(ct).toContain('svg');
    const body = await r.text();
    expect(body, 'SVG 应含中心 text 标签').toMatch(/<text[^>]*>.*<\/text>/);
    expect(body, 'SVG 应含店铺名「水果店」').toContain('水果店');
    console.log(`✓ M-Q-03 SVG 含店铺名 ${body.length} bytes`);
  });
});
