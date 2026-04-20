# 摊小二商户端（yudao-ui-merchant-uniapp）

> 原型阶段 — UI 走 mock 数据，不依赖后端。

## 功能范围

- 登录（手机号 + 验证码）
- 首页 / 简单报表（今日订单、销售额、会员、趋势）
- **AI 一键成片**（核心）
  - 拍照 / 选图 → 输入描述 → 生成文案 → 编辑确认 → 生成视频 → 下载 / 发布抖音
  - 历史记录、配额购买入口
- 订单管理
  - 列表（按状态分 Tab：待发货 / 待核销 / 已完成）
  - 核销（扫码 + 手动输入核销码）
  - 发货（填快递单号）
- 我的（店铺信息、设置、退出）

## 技术栈

- uni-app 3 + Vue 3 + Vite 5（无 HBuilderX 依赖）
- Pinia（状态）+ uni.request 封装
- 纯 JS + SCSS，零第三方 UI 库（原型阶段自描样式）

## 本地启动（H5）

```bash
# 仓库根目录已用 pnpm 管理；本工程独立 npm/pnpm 均可
cd yudao-ui/yudao-ui-merchant-uniapp
pnpm install     # 或 npm install
pnpm dev:h5      # → http://localhost:5180
```

打开浏览器 `http://localhost:5180`，默认进登录页，任意手机号 + 任意 4 位验证码即可进入（mock）。

### 包版本说明

`@dcloudio/*` 版本每天滚动。如果 `pnpm install` 报 "No matching version"，
执行：

```bash
pnpm add -D @dcloudio/vite-plugin-uni@alpha
pnpm add @dcloudio/uni-app@alpha @dcloudio/uni-h5@alpha
```

让 pnpm 拉当前 alpha 最新版即可。

## 后端接入（下一轮）

所有接口以 `/admin-api/merchant/mini/**` 为前缀，Dev 环境通过 Vite 代理到
`http://localhost:48080`（见 `manifest.json` 的 `h5.devServer.proxy`）。

当前 `api/` 目录下所有函数用 mock，真实接入时去掉 `mockXxx` return，
放开注释里的 `request(...)` 即可。

## 小程序

`manifest.json` 里预留 `mp-weixin` 配置，补上 `appid` 后 `pnpm dev:mp-weixin`
即可用微信开发者工具打开 `dist/dev/mp-weixin`。
