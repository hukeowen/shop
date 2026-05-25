# 客小二（用户端 H5 / 小程序 / APP）

基于 `docs/prototype/user-h5-v9-wow.html` 的全新用户端 uniapp 工程。
同栈打包 H5（部署 `/u/`）+ 微信小程序 + Android APP。

## 设计

- 视觉令牌见 `src/uni.scss`，与 v9-wow 原型完全对齐。
- 主色：暖橙 `#FF6B35`；强调色：金 `#D4920A`；玩法卡用 mint / purple 区分。
- 21 屏覆盖：首页、附近、中奖榜、我的、店铺、商品、购物车、结算、订单、推 N 反 1 队列、钱包、提现、邀请、优惠券、收藏、地址、登录、推广/消费积分、榜一排名、支付完成。
- 全局组件：`NavBar`、`BottomNav`（首页 / 附近 / 中奖榜 / 我的 4 入口）、`AwardModal`（派奖到账弹窗）、`EmptyState`。

## 开发

```bash
pnpm install
pnpm dev:h5             # 浏览器调试，端口 5181
pnpm dev:mp-weixin      # 微信小程序（用微信开发者工具导入 dist/dev/mp-weixin）
pnpm dev:app-android    # APP（HBuilderX 真机或离线打包）

pnpm build:h5           # 产物 dist/build/h5/  → 服务器 rsync 到 /opt/tanxiaer/u/
pnpm build:mp-weixin
pnpm build:app-android
```

dev 模式 vite 代理 `/app-api` → `http://localhost:48080`（与后端 yudao-server 一致）。

## 部署

- **H5**：nginx `location ^~ /u/`，root `/opt/tanxiaer/u/`，`try_files $uri $uri/ /u/index.html`。
- **微信小程序**：上传 `dist/build/mp-weixin/` 到微信公众平台，appid 写在 `manifest.json -> mp-weixin.appid`。
- **Android APP**：`manifest.json -> app-plus.distribute.android` 已配 21 - 30 SDK + ARM v7/v8 + 网络/定位/相机/存储权限；HBuilderX 云打包或离线打包。

## API 约定

- 后端 `/app-api/**`（CommonResult<T>）；C 端按 `tenant-id` header 路由到具体店租户。
- 跨店浏览：未带 `tenant-id` → 后端按 token tenant fallback。
- 401 / `code=401` → 自动跳 `/pages/login/index`（首页/附近/中奖榜/搜索/商品详情/店铺主页等列表页例外，允许匿名浏览）。

## 与商户端对比

| | merchant uniapp (`/m/`) | user uniapp (`/u/`) |
|---|---|---|
| 受众 | 商户后台 | C 端消费者 |
| 关键功能 | AI 上架 / 订单核销 / 报表 | 下单 / 推广 / 提现 / 中奖榜 |
| 端 | H5 + 微信小程序 | H5 + 微信小程序 + Android APP |
| dev 端口 | 5180 | 5181 |
