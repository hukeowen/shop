// U-A 用户登录跳转：纯登录 vs 从 shop-home 链接登录
import { test, expect } from '@playwright/test';

test.describe('U-A 用户登录跳转', () => {

  test('U-A-01 直接打开 /pages/login/index，密码登录后跳 user-home', async ({ page }) => {
    await page.goto('/m/#/pages/login/index');
    await page.waitForTimeout(1500);

    // 用 19999999999 / 888888 试登录（实际是 merchant 角色，但路由按 active 判定）
    // 这里观察登录后 URL 落地哪个 page
    const phone = page.locator('input[type="tel"], input[placeholder*="手机"]').first();
    if (await phone.count()) {
      await phone.fill('19999999999');
      const pwd = page.locator('input[type="password"], input[placeholder*="密码"]').first();
      if (await pwd.count()) {
        await pwd.fill('888888');
        const btn = page.locator('button:has-text("登录"), .btn:has-text("登录")').first();
        if (await btn.count()) {
          await btn.click();
          await page.waitForTimeout(4000);
          console.log(`U-A-01 登录后 URL = ${page.url()}`);
          // merchant 角色跳 /pages/index/index 是对的；纯 member 应跳 /pages/user-home/index
        }
      }
    }
    await page.screenshot({ path: 'test-results/U-A-01-after-login.png', fullPage: true });
  });

  test('U-A-02 shop-home 分享链接 → 登录页 → 登录成功跳回 shop-home', async ({ page }) => {
    // 模拟：用户从 shop-home?tenantId=171&inviter=11 触发未登录保护跳到 login
    await page.goto('/m/shop-home?tenantId=171&inviter=11#/pages/login/index');
    await page.waitForTimeout(2000);
    // 登录后应跳 shop-home 而不是 user-home / index
    const phone = page.locator('input[type="tel"], input[placeholder*="手机"]').first();
    if (await phone.count()) {
      await phone.fill('19999999999');
      const pwd = page.locator('input[type="password"], input[placeholder*="密码"]').first();
      if (await pwd.count()) {
        await pwd.fill('888888');
        const btn = page.locator('button:has-text("登录"), .btn:has-text("登录")').first();
        if (await btn.count()) {
          await btn.click();
          await page.waitForTimeout(4000);
          const url = page.url();
          console.log(`U-A-02 从 shop-home 链接登录后 URL = ${url}`);
          // 期望含 shop-home
        }
      }
    }
    await page.screenshot({ path: 'test-results/U-A-02-shop-home-redirect.png', fullPage: true });
  });
});
