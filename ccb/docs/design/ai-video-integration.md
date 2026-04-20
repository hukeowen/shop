# AI 成片功能：前端对接说明

> 更新于 2026-04-20 / 后端状态：生产级可用（待接真实 Ark / TTS Key）

## 一、后端改造概要

商户端 AI 成片走 **三段式异步流程**，全部由 merchant 模块暴露，video 模块内部消化：

```
[创建任务 create]                           [确认文案 confirm]                       [下载/发布]
 status=1 文案生成中 ──[豆包 LLM]──> 2 等待确认 ──[用户编辑]──> 3 视频合成中 ──[Seedance]──> 4 完成
                        失败 → 5                               失败 → 5
```

`1→2`、`3→4` 都是 **@Async + @TransactionalEventListener(AFTER_COMMIT)**，前端靠轮询 `/get` 拿状态。

- 文案：火山方舟 `doubao-1-5-pro-32k` (OpenAI 兼容 `/chat/completions`)
- 视频：火山方舟 `doubao-seedance-1-0-pro-250528` 图生视频（字节当前最强）
- 配音：豆包 TTS（旁路上传，不阻塞主流程）
- 产物持久化：通过 `infra.FileApi` 落自有 OSS，Seedance 临时 URL 在 24h 内转存

## 二、前端调用时序

### 1. 创建任务

```http
POST /admin-api/merchant/mini/ai-video/create
Header: tenant-id: ${tenantId}, Authorization: Bearer ${token}
Body: {
  "imageUrls": ["https://oss/.../a.jpg", ".../b.jpg", ".../c.jpg"],
  "userDescription": "我是卖烤地瓜的，烤箱现烤，香甜软糯，5 块钱一个"
}
→ 200 { "code":0, "data": 12345 }   // taskId
```

立刻返回 `taskId`，后端异步调豆包 LLM 生成文案。

### 2. 轮询文案（约 3–10 秒）

```http
GET /admin-api/merchant/mini/ai-video/get?id=12345
→ 200 { "data": { "status": 1, ... } }          // 文案生成中
→ 200 { "data": { "status": 2, "aiCopywriting": ["..","..",..], ... } }  // 可确认
→ 200 { "data": { "status": 5, "failReason": "..." } }                    // 失败
```

建议前端：`status=1` 时每 2 秒轮询一次，最多 30 秒；仍为 1 视为超时提示"稍后重试"。

### 3. 用户编辑 + 确认文案

展示 `aiCopywriting` 数组，每句一行可删/改。点击"生成视频"：

```http
POST /admin-api/merchant/mini/ai-video/confirm
Body: {
  "taskId": 12345,
  "finalCopywriting": ["编辑后的第1句", "第2句", ...],  // 可选：为空则沿用 AI 原版
  "bgmId": null                                        // 可选
}
→ 200 { "code":0, "data": true }
```

### 4. 轮询视频（约 2–5 分钟）

```http
GET /admin-api/merchant/mini/ai-video/get?id=12345
→ status=3 (合成中) → status=4 (完成，videoUrl/coverUrl 有值) → status=5 (失败)
```

Seedance 图生视频典型 90–180 秒，生产建议每 5 秒轮询一次、最多 15 分钟。

### 5. 下载 / 发布抖音

- `videoUrl` 指向自有 OSS，可直接下载/分享
- 发布抖音见下节

### 6. 抖音授权 + 发布

授权 URL（H5 跳转）：

```http
GET /admin-api/merchant/mini/ai-video/douyin/auth-url?redirectUri=...
→ 返回 https://open.douyin.com/platform/oauth/connect?...
```

授权完成后商户表的 `douyin_access_token` 已写入，发布：

```http
POST /admin-api/merchant/mini/ai-video/douyin/publish?taskId=12345
→ 200 { "code":0, "data": true }
```

## 三、配额查询

```http
GET /admin-api/merchant/mini/ai-video/quota
→ 200 { "data": {"aiVideoQuota": 10, "aiVideoUsed": 3, ...} }
```

`aiVideoUsed < aiVideoQuota` 时允许创建；`onTaskComplete` 成功时才扣减（原子 CAS）。

`POST /buy-quota` 目前抛 `AI_VIDEO_PAY_NOT_READY`，待支付系统对接。

## 四、失败状态

所有失败 (`status=5`) 都带 `failReason`（≤200 字）。常见类型：

| failReason 关键字 | 原因 | 用户提示 |
|---|---|---|
| `LLM 生成文案为空` / `AI 文案生成失败` | 豆包 API 故障/key 未配 | "文案生成失败，稍后重试" |
| `Seedance 生成失败` | Seedance 审核/素材/配额问题 | "视频生成失败，换图重试" |
| `视频大小超限` | Seedance 产物 > 200MB | 联系平台 |
| `ark-api-key 未配置` | 运维问题 | 联系平台 |

## 五、后端运维

**必配环境变量**（否则启动时 `IllegalStateException` 或运行时失败）：

```bash
export ARK_API_KEY=...                    # 火山方舟 API Key（LLM + Seedance 共用）
export VOLCANO_APP_ID=...                 # 豆包 TTS
export VOLCANO_ACCESS_TOKEN=...           # 豆包 TTS
export MERCHANT_INTERNAL_TOKEN=...        # ≥16 位，服务内部回调鉴权
export DOUYIN_CLIENT_KEY=...              # 抖音开放平台（发布需要）
export DOUYIN_CLIENT_SECRET=...
```

可选覆盖：

```bash
export DOUBAO_LLM_MODEL=ep-20240301-xxxxx # 推理接入点；默认用模型名 doubao-1-5-pro-32k-250115
export SEEDANCE_MODEL=doubao-seedance-1-0-pro-250528
```

**OSS**：依赖 infra 模块的 `FileApi`，需预先在 `infra_file_config` 表里配置默认存储桶（S3/OSS/MinIO 均可）。

**费用预警**：Seedance Pro 当前官方约 ¥3–¥5/10 秒，LLM 单次 < ¥0.02；建议在 Ark 控制台设置单日额度上限防刷。

## 六、前端落地清单（商户端 uniapp）

当前仓库 `yudao-ui` 下**没有商户端 uniapp**。当前只有：
- `yudao-ui-admin-vue3` — 平台管理后台（可加 AI 成片审核页）
- `yudao-ui-mall-uniapp` — 用户 C 端

商户端需新建一个 uniapp 工程（或复用 mall-uniapp 的工程脚手架），主要页面：

1. `pages/ai-video/create.vue` — 拍照选图 + 描述输入（→ POST `/create`）
2. `pages/ai-video/confirm.vue` — 展示文案逐句卡片 + 编辑 + "生成视频"（→ POST `/confirm`）
3. `pages/ai-video/detail.vue` — 视频预览 + 下载 + "发布到抖音"
4. `pages/ai-video/history.vue` — 历史列表（→ `/page`）
5. `pages/ai-video/quota.vue` — 配额展示 + 购买入口（`/quota` / `/buy-quota`）

所有接口前缀 `/admin-api/merchant/mini/ai-video/...`，携带 `tenant-id` Header 和商户管理员 JWT。
