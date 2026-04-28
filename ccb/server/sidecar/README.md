# tanxiaoer-sidecar

独立 Node express 服务，承载摊小二的"AI 视频后处理 + OSS 上传 + TTS + 抖音发布"能力。

## 为什么单独成进程

`yudao-ui-merchant-uniapp/vite.config.js` 里原有同名 sidecar，但仅在 `vite dev` 时
作为 vite plugin 的 middleware 挂载；`pnpm build:h5` 后产物是纯静态文件，sidecar
不存在，生产环境会 404。把它抽到独立进程，由 systemd 守护、nginx 反代过来，
让"AI 视频成片植入二维码"等关键能力在生产环境也工作。

## 路由（默认监听 `127.0.0.1:8081`）

| 路径 | 方法 | 用途 |
|---|---|---|
| `/healthz` | GET | 心跳检查 |
| `/oss/upload` | POST | base64 → 火山 TOS S3，公网 URL 返回 |
| `/tts/volc` | POST | 豆包 openspeech v3 流式 TTS |
| `/tts/edge` | POST | Microsoft Edge TTS（备用，需 `npm i msedge-tts`） |
| `/vproxy?url=` | GET | 视频反代加 CORS（给浏览器 canvas 抽帧用） |
| `/video/merge` | POST | 多片段视频 concat（720x1280 25fps 标准化） |
| `/video/mux` | POST | 主视频 + TTS 音轨 + 抖音风字幕 → TOS |
| **`/video/endcard`** | POST | **★ 端卡：商品图 + 居中大商户二维码 + 店名 + TTS → TOS** |
| `/douyin/auth-url` | GET | 抖音 OAuth URL |
| `/douyin/oauth-callback` | GET | OAuth 回调（兑换 access_token） |
| `/douyin/publish` | POST | 上传 + 发布抖音视频 |
| `/jimeng?action=` | POST | @deprecated 即梦签名代理（保留） |

## 必填环境变量

```
TOS_AK=<火山对象存储 AccessKey>            # 或 JIMENG_AK 兼容
TOS_SK=<火山对象存储 SecretKey>            # 或 JIMENG_SK 兼容
TOS_BUCKET=tanxiaoer                        # 默认 tanxiaoer
TOS_REGION=cn-beijing                       # 默认 cn-beijing
VOLCANO_ACCESS_TOKEN=<豆包 TTS Token>       # 或 TTS_ACCESS_TOKEN
DOUYIN_CLIENT_KEY=...                       # 抖音发布需要
DOUYIN_CLIENT_SECRET=...
JIMENG_AK / JIMENG_SK                       # 即梦签名（@deprecated 但保留）
SIDECAR_PORT=8081                           # 默认 8081
```

`.env` 会从 `server/sidecar/.env` 或仓库根 `.env` 读取（`deploy.sh` 自动塞）。

## 启动

```bash
cd server/sidecar
npm ci --omit=dev    # 或 pnpm i --prod
node index.js
```

生产环境由 deploy.sh 自动起 systemd unit `tanxiaoer-sidecar.service`。
