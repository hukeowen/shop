# 摊小二商户端（yudao-ui-merchant-uniapp）

> 原型阶段。订单/商品/报表走 mock；**AI 一键成片走真实火山引擎**。

## 功能范围

- 登录（手机号 + 验证码，任意 4 位通过）
- 首页 / 简单报表
- **AI 一键成片**（真实调用 — 见下方接入说明）
  - 粘贴图片 URL / 选样图 → 输入描述 → 豆包 LLM 写文案 → 编辑确认
  - → Seedance 图生视频（720p / 5s / 9:16，约 90-180 秒）
  - → 豆包 TTS 配音（视频 + 音频双轨同步播放）
  - 下载视频 / 下载配音 / 发布抖音
- 商品管理（极简上架，主表单 4 项 + 高级折叠）
- 订单管理（核销 / 发货）
- 我的

## 技术栈

- uni-app 3 + Vue 3 + Vite 5（无 HBuilderX 依赖）
- Pinia + uni.request；纯 JS + SCSS

## 本地启动（H5）

```bash
cd yudao-ui/yudao-ui-merchant-uniapp
pnpm install            # 首次约 4 分钟
cp .env.local.example .env.local   # 不填 key 也能跑，AI 成片会报"未配置"
pnpm dev:h5             # → http://localhost:5180
```

任意手机号 + 任意 4 位验证码登录。

## ✨ AI 一键成片接入（5 分钟）

### 一、注册 + 拿 key

| 服务 | 必须 | 链接 | 拿到 |
|---|---|---|---|
| 火山引擎账号 | ✅ | https://www.volcengine.com/ | 实名 |
| 火山方舟（豆包 LLM + Seedance） | ✅ | https://console.volcengine.com/ark | `ARK_API_KEY` |
| 豆包 TTS（语音合成） | ✅ | https://console.volcengine.com/speech/service | `APP_ID` + `ACCESS_TOKEN` |
| 抖音开放平台 | 选 | https://developer.open-douyin.com/ | `CLIENT_KEY` + `CLIENT_SECRET` |

火山方舟操作：
1. 「模型广场」→ 开通 **豆包·1.5-pro-32k**（新用户 50 万 tokens 免费）
2. 「模型广场」→ 开通 **豆包·Seedance 1.0 pro**（按量计费，5s/720p ≈ ¥0.3-0.5）
3. 「API Key 管理」→ 创建 → 复制 sk-xxx

豆包 TTS 操作：
1. 进控制台开通「语音合成」
2. 「应用列表」→ 选你的应用 → 拿 `APP ID` 和 `Access Token`
3. 在「音色管理」给应用授权一个音色（如 `BV705_streaming` 甜美女声）

### 二、填 .env.local

```bash
VITE_ARK_API_KEY=sk-xxx
VITE_TTS_APP_ID=xxx
VITE_TTS_ACCESS_TOKEN=xxx
VITE_TTS_VOICE=BV705_streaming
```

### 三、重启 dev server

`Ctrl+C` 关掉之前的 `pnpm dev:h5`，重新执行（vite proxy + env 改动需要冷启动）。

### 四、跑通流程

1. 登录 → AI 成片 → 开始创作
2. 粘贴一个公网图片 URL，或点"选用样图"里的"烤地瓜 / 蛋糕 / 咖啡..."
3. 写一句描述（≥5 字）→ 开始生成
4. 等 3-8 秒看 LLM 文案 → 编辑后点"生成视频"
5. 等 90-180 秒（轮询条会动）→ 视频出来 + 自动配音播放

### 五、Seedance 图片限制

Seedance 接口要求**公网 https URL，不接受 base64 / blob**。摊主拍照后需先上传到任一图床（imgur / 微信公众号素材 / 阿里 OSS）拿 URL 再粘贴。原型阶段提供 5 张样图供朋友体验。

### 六、安全提醒

`VITE_*` 前缀的环境变量在浏览器源码中可见。**仅用于内部体验**，不要把带 key 的链接贴公网。
- 控制台→费用中心→设置「单日预算告警 ¥20」防刷
- 朋友体验完关掉 dev server 即下线

### 七、CORS 与代理

`vite.config.js` 已配两条代理：
- `/ark/*` → `https://ark.cn-beijing.volces.com`
- `/tts/*` → `https://openspeech.bytedance.com`

dev 模式下浏览器只对 `localhost:5180` 发请求，由 Vite 转发，**绕开 CORS**。生产部署需要换成自有后端中转或 Cloudflare Worker。

## 包版本说明

`@dcloudio/*` 版本每天滚动。装不上时执行：

```bash
pnpm add -D @dcloudio/vite-plugin-uni@vue3
pnpm add @dcloudio/uni-app@vue3 @dcloudio/uni-h5@vue3
```

## 业务接口（订单 / 商品）下一轮接入

所有接口以 `/admin-api/merchant/mini/**` 为前缀，已配 vite proxy 到
`http://localhost:48080`。`src/api/*.js` 当前用 mock，真实接入时把
`return mockDelay(...)` 换成 `return request({...})` 即可。

## 小程序

`manifest.json` 里预留 `mp-weixin` 配置，补上 `appid` 后 `pnpm dev:mp-weixin` 即可。
小程序端无 CORS 限制，但需在公众平台配置 request 合法域名白名单（`ark.cn-beijing.volces.com` / `openspeech.bytedance.com`）。
