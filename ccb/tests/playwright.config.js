// Playwright 全量自动化配置
// 跑：cd ccb/tests && npx playwright test
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './specs',
  timeout: 180_000,           // 单个用例最长 3 分钟（AI 视频用例需要）
  expect: { timeout: 15_000 },
  fullyParallel: false,       // 串行：避免商户/用户互相影响 + 后端串行锁触发排队
  retries: 1,                 // 失败重试 1 次（网络抖动）
  workers: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'report' }]],

  use: {
    baseURL: process.env.BASE_URL || 'https://www.doupaidoudian.com',
    headless: process.env.HEADLESS === '1',  // 默认有头方便看
    channel: 'chrome',                       // 复用系统 Chrome（省 Chromium 下载）
    viewport: { width: 414, height: 896 },   // iPhone 11
    locale: 'zh-CN',
    geolocation: { latitude: 31.23, longitude: 121.47 }, // 上海
    permissions: ['geolocation', 'clipboard-read', 'clipboard-write'],
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    trace: 'retain-on-failure',
  },

  projects: [
    // setup 项目：跑商户/用户登录，把登录态存到 storageState
    // 注：setup 文件在 tests/ 根，需要单独 testDir 不走 ./specs
    { name: 'setup-merchant', testDir: '.', testMatch: /merchant\.setup\.js/ },
    { name: 'setup-user',     testDir: '.', testMatch: /user\.setup\.js/ },

    // 商户端测试（依赖 setup-merchant）
    {
      name: 'merchant',
      testDir: './specs/merchant',
      use: { storageState: '.auth/merchant.json' },
      dependencies: ['setup-merchant'],
    },

    // 用户端测试（不依赖 setup-user — A-auth 要测真实登录跳转，全新 session）
    {
      name: 'user',
      testDir: './specs/user',
    },
  ],
});
