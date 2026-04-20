# 新机器 Claude Code 快速上手

> 目标：在新电脑上 5 分钟内让 Claude Code 读完上下文、知道手上在做什么、能接着干活。

## 1. 克隆 + 安装依赖

```bash
git clone <repo-url> ccb && cd ccb

# JDK 8 + Maven 3.8+（mvn -v 自检）
mvn -pl yudao-server -am install -DskipTests       # 首次拉依赖，约 3–8 分钟

# 前端（可选，后端工作不用装）
cd yudao-ui/yudao-ui-admin-vue3 && pnpm install && cd ../..
```

## 2. 本地基础设施

`yudao-server/src/main/resources/application-local.yaml` 默认期望：

| 服务 | 地址 | 备注 |
|---|---|---|
| MySQL 8 | `127.0.0.1:3306` db=`ruoyi-vue-pro` 用户/密码 `root/root` | 按 [SQL 初始化顺序](#sql-初始化顺序) 导数据 |
| Redis 7 | `127.0.0.1:16379` | 无密码 |
| RocketMQ/RabbitMQ/Kafka | 可选 | 默认流程不用 |

```bash
# docker-compose 起 MySQL + Redis 最小套
docker run -d --name ccb-mysql -e MYSQL_ROOT_PASSWORD=root -p 3306:3306 mysql:8.0
docker run -d --name ccb-redis -p 16379:6379 redis:7
```

### SQL 初始化顺序

按顺序 source 这些 SQL 到 `ruoyi-vue-pro`：

```
sql/mysql/ruoyi-vue-pro.sql        # 基础 yudao
sql/mysql/quartz.sql               # 可选（需要定时任务）
sql/mysql/mall.sql
sql/mysql/mp.sql
sql/mysql/member_pay.sql
sql/mysql/merchant.sql
sql/mysql/video.sql
sql/mysql/v2_business_tables.sql   # 本仓库关键：merchant_apply / shop_info / ai_video_task 等
sql/mysql/fix_tenant_id.sql        # 视情况
sql/mysql/fix_json_to_varchar.sql  # 视情况
```

## 3. 必配环境变量

以下是**启动前必须**设置的。未设置就启动，`AppMerchantAiVideoController.initInternalToken()` 会 fail-fast 拦截：

```bash
# 必填
export MERCHANT_INTERNAL_TOKEN=$(openssl rand -hex 24)   # ≥16 位，内部回调鉴权
export ARK_API_KEY=...            # 火山方舟 API Key，LLM + Seedance 共用
export VOLCANO_APP_ID=...         # 豆包 TTS
export VOLCANO_ACCESS_TOKEN=...

# 选填（对应抖音发布功能）
export DOUYIN_CLIENT_KEY=...
export DOUYIN_CLIENT_SECRET=...

# 可选（推理接入点或换模型）
export DOUBAO_LLM_MODEL=doubao-1-5-pro-32k-250115
export SEEDANCE_MODEL=doubao-seedance-1-0-pro-250528
```

本地调试短时用假值也能启动（internal-token 会拦），但 AI 成片调 Ark 会在 `onAiVideoTaskCreated` 里拿到 `ark-api-key 未配置` 然后 `status=5 失败`——前端可见失败态，不会卡死。

## 4. 启动

```bash
# 后端
mvn -pl yudao-server spring-boot:run
# → http://localhost:48080/swagger-ui

# 前端
cd yudao-ui/yudao-ui-admin-vue3 && pnpm dev
# → http://localhost:80 (Vite 默认，.env.local 内可查)
```

**商户端 uniapp 尚未建**。当前 `yudao-ui/` 只有：
- `yudao-ui-admin-vue3` 平台管理后台
- `yudao-ui-mall-uniapp` 用户 C 端小程序

商户小程序需要新建独立 uniapp 工程（参见 [AI 视频集成文档 §6](docs/design/ai-video-integration.md)）。

## 5. Claude Code 接手指引

Claude 第一次打开这个仓库，**按这个顺序读**就够了：

1. [`CLAUDE.md`](CLAUDE.md) — 架构地图、构建命令、模块图、多租户规则、事件驱动约定（常驻上下文）
2. [`docs/design/product-design.md`](docs/design/product-design.md) — 铺星 SaaS 商户平台 v1.0 完整产品设计
3. [`docs/design/progress.md`](docs/design/progress.md) — 40 项任务进度 + 2026-04-20 生产级改造记录
4. [`docs/design/ai-video-integration.md`](docs/design/ai-video-integration.md) — AI 成片产线前端对接时序 + 环境变量 + 失败语义
5. 本文件 — 机器准备、启动方式

## 6. 当前状态（截至 2026-04-20）

✅ **后端主功能 38/40 通路可用**（商户入驻、租户订阅、商户小程序、用户小程序、推广裂变、提现、抖音授权+发布、AI 成片产线）

🔄 **生产级改造已完成的关键链路**：AI 成片跑真实豆包 LLM + Seedance Pro 图生视频 + OSS 持久化，状态机 3 段式异步分步，余额原子扣减。见 `docs/design/progress.md` 顶部的"2026-04-20 生产级改造"。

⬜ **待完成**：
- `buy-quota` 待支付系统对接（抛 `AI_VIDEO_PAY_NOT_READY`）
- 商户端 uniapp 5 个页面（`docs/design/ai-video-integration.md §6`）
- LLM 文案分步流程 UI（后端已就位，status=2 时取 `aiCopywriting` 展示编辑）
- `#4 附近页` 按距离排序二次过滤（目前只做 `±0.1` 度粗筛）
- 老 admin-vue3 需要加"AI 成片审核 + 商户提现审核"页面（接口在 `MerchantWithdrawController` / 管理端 `MerchantApplyController`）

## 7. 本次会话未提交的改动

以下是 **2026-04-20 AI 视频生产级改造** 的文件清单，建议作为一个独立 commit：

**新增（6）**
```
CLAUDE.md
ONBOARDING.md
docs/design/ai-video-integration.md
yudao-module-video/src/main/java/cn/iocoder/yudao/module/video/service/CopywritingService.java
yudao-module-video/src/main/java/cn/iocoder/yudao/module/video/service/CopywritingServiceImpl.java
```

**修改（11）**
```
docs/design/progress.md
yudao-module-video/src/main/resources/application-video.yaml
yudao-module-video/src/main/java/cn/iocoder/yudao/module/video/config/VolcanoEngineProperties.java
yudao-module-video/src/main/java/cn/iocoder/yudao/module/video/service/VideoGenerateService.java
yudao-module-video/src/main/java/cn/iocoder/yudao/module/video/service/VideoGenerateServiceImpl.java
yudao-module-video/src/main/java/cn/iocoder/yudao/module/video/service/VideoTaskServiceImpl.java
yudao-module-video/src/main/java/cn/iocoder/yudao/module/video/listener/AiVideoTaskCreatedListener.java
yudao-module-merchant/src/main/java/cn/iocoder/yudao/module/merchant/service/AiVideoTaskService.java
yudao-module-merchant/src/main/java/cn/iocoder/yudao/module/merchant/service/AiVideoTaskServiceImpl.java
yudao-module-merchant/src/main/java/cn/iocoder/yudao/module/merchant/service/MerchantWithdrawServiceImpl.java
yudao-module-merchant/src/main/java/cn/iocoder/yudao/module/merchant/dal/mysql/ShopInfoMapper.java
```

**会话前已在工作区未跟踪（不是本次改）**
```
yudao-module-video/src/main/java/cn/iocoder/yudao/module/video/controller/app/AppMerchantDouyinController.java
```

### 提交命令

```bash
git add CLAUDE.md ONBOARDING.md docs/design/
git add yudao-module-video/ yudao-module-merchant/
git status                    # 人眼确认
git diff --staged | head -200 # 抽检
git commit -m "feat(ai-video): 生产级重构 AI 成片产线

- 接入真实豆包 LLM 文案生成（Ark /chat/completions）
- 视频生成切火山 Seedance Pro 图生视频 + OSS 持久化
- 状态机 3 段式异步：创建→LLM→确认→Seedance→完成
- 余额扣减改原子 SQL 谓词，杜绝并发负余额
- @Async 切线程后租户表读写全部 TenantUtils.executeIgnore
- 新增 CLAUDE.md / ONBOARDING.md / AI 视频集成说明"
```

**提交前人眼复核要点**：
- `application-video.yaml` 里没有硬编码密钥（全部 `${ENV:}` 占位）✓
- `ShopInfoMapper` 的两个 atomic SQL 都带 `tenant_id` 谓词 ✓
- `AiVideoTaskCreatedListener` 所有租户表读写都在 `TenantUtils.executeIgnore` 里 ✓

## 8. 后续接手的"第一件事"建议

按 ROI 排序，Claude 接手后可以直接问清方向再开干：

1. **（最高）前端商户端 uniapp 5 页** — 后端已就位，前端落地后 AI 视频功能即可对外
2. **（高）admin-vue3 管理端 AI 成片审核 + 商户提现审核页** — 后端接口齐全，只差 UI
3. **（中）配额购买支付流程** — 对接 pay 模块
4. **（中）用户端"附近商户"按距离排序** — 现在只粗筛经纬度 ±0.1
5. **（低）AI 视频步骤 4.4 背景音乐素材库 `ai_video_bgm`**
