# 摊小二平台 开发交接文档

> 本文档用于在新机器/新人接手时**无缝对接**当前进度。按顺序读一遍能直接跑起来并继续往下开发。
>
> **最近更新**：2026-04-23，主线 commit `e52c1fb`（Phase 0 全部收尾）
> **当前分支**：`main`
> **仓库远端**：`github.com/hukeowen/shop`

---

## 0. 快速开始（新机器 30 分钟上手）

### 0.1 必备版本

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | **8**（严格） | Spring Boot 2.7.18 要求；用 17/21 会在 `javax.*` 上崩 |
| Maven | 3.8+ | `mvn -v` 验证，POM 用 `${revision}` + flatten-maven-plugin |
| Node | >= 16 | 前端 uni-app 要求 |
| pnpm | >= 8.6.0 | **强制 pnpm**，`npm i` / `yarn` 在 monorepo 会崩 |
| MySQL | 5.7 或 8.0 | 默认 `127.0.0.1:3306` / `root/root` / db `ruoyi-vue-pro` |
| Redis | 5.0+ | 默认 `127.0.0.1:16379`（**注意端口不是 6379**） |
| FFmpeg | 4.4+ | sidecar 自带 `ffmpeg-static`，本机无需额外装；字体要装 PingFang（macOS 系统自带） |

### 0.2 克隆 & 基础依赖

```bash
git clone git@github.com:hukeowen/shop.git
cd shop/ccb                              # 所有命令以这个为 cwd
mvn -DskipTests -q install               # 后端依赖预热（5-10 分钟首次）

cd yudao-ui/yudao-ui-merchant-uniapp
pnpm install                             # 商户小程序（活跃）

cd ../yudao-ui-admin-vue3
pnpm install                             # 平台 PC 后台（活跃）
```

其他 `yudao-ui-admin-vue2/vben/uniapp`、`yudao-ui-mall-uniapp` 都是**僵尸仓库**，不要动。

### 0.3 数据库初始化

在 MySQL 里建库 `ruoyi-vue-pro`，按顺序跑 SQL：

```bash
cd sql/mysql
mysql -h127.0.0.1 -uroot -proot ruoyi-vue-pro < ruoyi-vue-pro.sql   # 基础 yudao + 菜单 + 字典 seed
mysql ... < mall.sql                      # 商城基础表
mysql ... < mp.sql                        # 微信公众号
mysql ... < member_pay.sql                # 会员支付
mysql ... < merchant.sql                  # 商户入驻
mysql ... < video.sql                     # AI 视频模块
mysql ... < merchant_invite_code.sql      # ⚠️ Phase 0.2 新增：商户邀请码 + openid 列
mysql ... < ai_video_package.sql          # ⚠️ Phase 0.3 新增：配额/套餐/订单
# quartz.sql 按需（local profile 默认排除 Quartz 自动配置，不用）
# v2_business_tables.sql / fix_*.sql 看 header 说明按需增量
```

所有 Phase 0 新增的 SQL 脚本都**幂等**（`CREATE IF NOT EXISTS` + `information_schema` 守护列），重复跑不会炸。

### 0.4 环境变量

**关键原则**：
- `VITE_` 前缀 = 会打进浏览器 bundle，**不能放敏感 key**
- 无前缀 = 只给 Vite sidecar（Node 服务端）读，不会进 bundle
- Spring Boot 的 key 从 **系统环境变量** 读，不从任何 `.env` 文件读

#### 后端 `yudao-server` 环境变量（必填）

| 变量 | 用途 | 如何获取 |
|---|---|---|
| `ARK_API_KEY` | 火山方舟 LLM + Seedance | [方舟控制台 API Key 管理](https://console.volcengine.com/ark) |
| `JIMENG_AK` / `JIMENG_SK` | 即梦 AI 图生视频（HMAC-SHA256 签名） | 火山视觉控制台 → 访问密钥 |
| `VOLCANO_APP_ID` / `VOLCANO_ACCESS_TOKEN` | 豆包 TTS | [openspeech 控制台](https://console.volcengine.com/speech/service) |
| `VOLCANO_AK` / `VOLCANO_SK` | 老智能创作接口（兼容保留） | 同上 |
| `DOUYIN_CLIENT_KEY` / `DOUYIN_CLIENT_SECRET` | 抖音开放平台 | [developer.open-douyin.com](https://developer.open-douyin.com) → 应用 |
| `MERCHANT_INTERNAL_TOKEN` | 内部服务回调鉴权（≥16 字符） | 自己生成，如 `openssl rand -hex 20` |
| `WECHAT_MINI_APP_ID` / `WECHAT_MINI_APP_SECRET` | 统一小程序登录 | 微信小程序后台 → 开发管理 |
| `MERCHANT_PACKAGE_PAY_APP_ID` | 套餐支付 PayApp 主键 | 登录 admin PC 后台 → `/pay/app` 管理端创建后取 ID |

**不配置的后果**：
- 缺 `ARK_API_KEY` → AI 文案接口返 `ARK_CHAT_FAILED`
- 缺 `JIMENG_AK/SK` → 即梦 submit 报 401
- 缺 `MERCHANT_INTERNAL_TOKEN` 或 < 16 字符 → **应用无法启动**（`AppMerchantAiVideoController.initInternalToken` 强制校验）
- 缺 `WECHAT_MINI_APP_*` → `/app-api/app/auth/wx-mini-login` 抛 `WX_LOGIN_FAILED`
- 缺 `MERCHANT_PACKAGE_PAY_APP_ID` → `/purchase` 接口抛 `PAY_APP_ID_NOT_CONFIGURED`

#### 前端 sidecar `.env.local`（拷贝自 `.env.local.example`）

```bash
cd yudao-ui/yudao-ui-merchant-uniapp
cp .env.local.example .env.local         # 基础模板
# 编辑 .env.local，填入：
#   · 无前缀变量（sidecar 服务端用，不会泄露）：
#     JIMENG_AK / JIMENG_SK
#     TOS_AK / TOS_SK / TOS_BUCKET / TOS_REGION
#     TTS_ACCESS_TOKEN / TTS_RESOURCE_ID
#     DOUYIN_CLIENT_KEY / DOUYIN_CLIENT_SECRET
#   · VITE_ 前缀（非敏感配置）：
#     VITE_ARK_LLM_MODEL / VISION_MODEL / VIDEO_MODEL / VIDEO_*
```

**敏感 key 不要 VITE_ 前缀**。历史版本用过 `VITE_ARK_API_KEY` 等，**已经全部下线到后端 BFF**，前端没有任何直连第三方 API 的路径。

### 0.5 启动顺序

```bash
# Terminal 1：后端
cd shop/ccb
HTTPS_PROXY=http://127.0.0.1:7897 \      # ⚠️ 若本机靠 Clash 上网，必须配代理，否则 OSS/火山连不上
ARK_API_KEY=xxx JIMENG_AK=xxx ... \      # 所有必填 env
mvn -pl yudao-server spring-boot:run
# 启动成功监听 48080

# Terminal 2：商户小程序（dev）
cd shop/ccb/yudao-ui/yudao-ui-merchant-uniapp
HTTPS_PROXY=http://127.0.0.1:7897 \
pnpm dev:h5                              # H5 模式，监听 5180
# 微信小程序模式：pnpm dev:mp-weixin → 用微信开发者工具打开 dist/dev/mp-weixin

# Terminal 3（可选）：平台 PC 后台
cd shop/ccb/yudao-ui/yudao-ui-admin-vue3
pnpm dev                                 # 监听 80 或 8080，看 vite.config
```

### 0.6 冒烟验证

```bash
# 后端存活
curl -s http://localhost:48080/admin-api/system/captcha/get-enable | head -c 100
# → 应返 {"code":0,"data":{...}}

# 商户小程序可访问
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:5180/
# → 200

# sidecar 端点挂载（看 pnpm dev:h5 输出）
# [sidecar] /oss/upload /tts/volc /tts/edge /vproxy /video/* /douyin/* /jimeng 已挂载

# BFF 鉴权生效（未登录应 401）
curl -s -X POST http://localhost:48080/app-api/merchant/mini/ai-video/bff/tts \
  -H "Content-Type: application/json" -d '{"text":"x","voice":"x"}' | head -c 100
# → 401 或 "needLogin"
```

---

## 1. 项目概览

### 1.1 产品形态
摊小二是面向**小微商户**的小程序 SaaS 平台。核心链路：
- 商户入驻（扫运营邀请码 → `getPhoneNumber` → 落库）
- AI 视频一键生成（7 元/条或套餐，用于抖音带货）
- 商户开店接单（三种配送：自提/送货上门/物流）
- 用户扫码进店自动成为会员 + 推 N 反 1 + 商户级积分

### 1.2 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot **2.7.18** / JDK **8** / Maven 多模块 / MyBatis Plus / Spring Security OAuth2 opaque token |
| 缓存 | Redis |
| DB | MySQL 5.7/8.0（多租户：`tenant_id` 列） |
| 前端 App | uni-app（Vue 3 + Vite 5） |
| 前端 PC | Vue 3 + Element Plus（yudao-ui-admin-vue3 现成壳） |
| AI | 火山方舟（豆包 LLM）+ 即梦 AI + openspeech TTS |
| 支付 | yudao-module-pay 框架（WX / 支付宝 / 钱包，通联 TBD） |

### 1.3 关键约束

- **JDK 8** 是硬约束（`javax.*` 依赖）
- `yudao.tenant.enable=true`：所有 `extends TenantBaseDO` 的表自动带 `tenant_id` 过滤
- **商户 = tenant**：`MerchantDO extends TenantBaseDO`，租户级隔离
- **统一小程序**：一个微信 appid，同套前端代码内按 JWT role 分流（商户端 / 用户端）
- **FFmpeg 合片仍在 Vite sidecar**（dev-only）：`/video/mux`, `/video/merge`, `/video/endcard`；生产版要搬到后端 Worker 服务（见 TODO 清单）

---

## 2. 目录结构

```
ccb/
├── pom.xml                                ← Maven 根 POM，revision=2026.01-jdk8-SNAPSHOT
├── yudao-dependencies/                    ← BOM，所有第三方版本锁定在这里
├── yudao-framework/
│   ├── yudao-common/                      ← ErrorCode / CommonResult / BaseDO / 各种 *Api 跨模块接口
│   ├── yudao-spring-boot-starter-*/       ← web/security/mybatis/redis/mq/job/tenant/data-permission
├── yudao-module-infra/                    ← codegen / 文件 / API 日志
├── yudao-module-system/                   ← 用户 / 菜单 / 字典 / OAuth2 token / 租户
├── yudao-module-member/                   ← 会员（全局级，不是商户级！）
├── yudao-module-mp/                       ← 微信公众号
├── yudao-module-pay/                      ← 支付中心（PayOrder / PayApp / PayChannel）
├── yudao-module-mall/
│   ├── yudao-module-product/
│   ├── yudao-module-promotion/
│   ├── yudao-module-trade/                ← 订单
│   ├── yudao-module-trade-api/
│   └── yudao-module-statistics/
├── yudao-module-merchant/                 ← ⭐ 本项目自研：商户入驻 / 邀请码 / 套餐订单 / 配额
├── yudao-module-video/                    ← ⭐ 本项目自研：AI 视频 BFF + 抖音授权发布
├── yudao-server/                          ← 唯一的 @SpringBootApplication，其他模块自动被扫
├── yudao-ui/
│   ├── yudao-ui-admin-vue3/               ← 平台 PC 后台（活跃，但缺 merchant/video/套餐管理页）
│   ├── yudao-ui-merchant-uniapp/          ← ⭐ 商户小程序（活跃，将来承载商户端 + 用户端角色分流）
│   ├── yudao-ui-mall-uniapp/              ← 空壳（Phase 2 会被填实）
│   └── yudao-ui-admin-vue2/vben/uniapp/   ← 僵尸，不动
├── sql/mysql/                             ← 所有 DDL 和迁移脚本
└── docs/                                  ← PDF 资料 + 本文档
```

### 2.1 Spring Boot 模块依赖图

```
yudao-dependencies（BOM）
yudao-framework/yudao-common
yudao-framework/yudao-spring-boot-starter-*
yudao-module-infra
yudao-module-system → infra
yudao-module-member → system
yudao-module-mp → system
yudao-module-pay → system
yudao-module-mall/* → product / promotion / trade / trade-api / statistics
yudao-module-merchant → system + trade + product + member + pay  ⭐ 新依赖 pay（Phase 0.3.3）
yudao-module-video → 监听 merchant 事件；新增商户 JWT BFF
yudao-server → 引入所有模块
```

被**注释掉**不参与编译的模块：`yudao-module-bpm/report/crm/erp/iot/mes/ai`。如需用在根 `pom.xml` + `yudao-server/pom.xml` 同时解注释。

### 2.2 每个业务模块内部包布局（约定）

```
cn.iocoder.yudao.module.{name}
├── controller/
│   ├── admin/                ← /admin-api/**（管理后台）
│   │   └── vo/
│   ├── app/                  ← /app-api/**（C 端小程序 / 商户端小程序）
│   │   └── vo/
├── dal/
│   ├── dataobject/           ← MyBatis Plus DO，extends BaseDO 或 TenantBaseDO
│   └── mysql/                ← Mapper interface（XML 在 resources/mapper 下）
├── service/
├── enums/
├── event/                    ← Spring ApplicationEvent
├── job/                      ← 定时任务
├── framework/                ← 模块私有 @Configuration
└── client/                   ← ⭐ 本项目新增：第三方 HTTP 客户端（Ark / Jimeng / TTS 等）
```

---

## 3. 已锁定的产品决策（不再改）

| # | 决策 | 锁定时间 |
|---|---|---|
| 1 | 单 appid / 单小程序 / 一套前端代码，按 role claim 分流 | 2026-04-23 |
| 2 | 微信 `wx.getPhoneNumber` 快速验证绑手机（0.03 元/次）；无短信通道 | 2026-04-23 |
| 3 | 商户邀请码 **per-operator**（`merchant_invite_code` 表带 `operator_user_id`） | 2026-04-23 |
| 4 | AI 视频 **7 元 / 条**（15s），**生成前预扣**（失败自动回补） | 2026-04-23 |
| 5 | 套餐由平台 PC 后台创建管理（`ai_video_package` 表） | 2026-04-23 |
| 6 | 配送方式三选（自提 / 送货上门 / 物流），商户级配置 | 2026-04-23 |
| 7 | 推 N 反 1 + 积分**按商户独立**（`MemberShopRelDO` 新表待建） | 2026-04-23 |
| 8 | 可提现余额 → 积分 **单向**转换，反向禁止 | 2026-04-23 |
| 9 | 通联支付 `TL_PAY` 商户开关制（`PayChannelEnum` 枚举待加） | 2026-04-23 |
| 10 | FFmpeg 合片 dev 留 Vite sidecar，生产搬后端 Worker | 2026-04-23 |

---

## 4. 开发路线图 & 当前进度

### 4.1 Phase 0 — 基础设施 & 安全 ✅ **全部完成**

| 子阶段 | 内容 | Commit | 状态 |
|---|---|---|---|
| 0.1 | Spring Boot BFF：Ark / Jimeng / TTS 后端代理（商户 JWT 保护） | `7edaae7` | ✅ |
| 0.2 后端 | 微信小程序真登录 + 邀请码体系 + 角色解析（Redis activeRole） | `750578d` | ✅ |
| 0.2 前端 | wx.login + getPhoneNumber 真接入；6 个 AI 文件迁 BFF；删 `VITE_*_KEY` | `7716605` | ✅ |
| 0.2 修 | 代码审查必修：@TenantIgnore / UNIQUE index / @ToString.Exclude / Ark 模型白名单 | `cfa9478` | ✅ |
| 0.3.1/2 | 配额数据模型（`ai_video_package` / `merchant_video_quota_log`）+ BFF 原子扣减 | `d1f308d` | ✅ |
| 0.3.1/2 修 | 审查必修：uk_biz UNIQUE 幂等键 + 告警 Job + taskId 审计链 | `f7f48f8` | ✅ |
| 0.3.3 | 套餐购买 + pay 模块 HTTP 回调闭环 | `d916e4e` | ✅ |
| 0.3.3 修 | 审查必修：payOrder 二次校验 + CAS 幂等 + quotaLogId 审计 | `e52c1fb` | ✅ |

### 4.2 Phase 0 剩余（前端闭环）⏳

| 子阶段 | 内容 | 预估 |
|---|---|---|
| 0.3.4 | **平台 PC 后台套餐管理页**（`yudao-ui-admin-vue3` 下新增 `views/merchant/aiVideoPackage/*`） | 1-2 天 |
| 0.3.5 | **商户小程序套餐购买页**（`pages/quota/index.vue` + `pages/quota/buy.vue`，JSAPI 调起） | 1-2 天 |

### 4.3 Phase 1+ — 商户端业务对接

| Phase | 内容 | 预估 |
|---|---|---|
| 1.1 | 商品管理（`/admin-api/product/spu/*`）+ 商户端编辑页接通；补 `giveIntegral` / `subCommissionType` 字段 | 3-4 天 |
| 1.2 | 订单管理（接单 / 发货 / 自提核销）对 `/admin-api/trade/order/*` | 4-5 天 |
| 1.3 | AI 成片任务落库 `AiVideoTaskDO`（前端 store → 真实 DB） | 2-3 天 |
| 1.4 | 返佣 / 积分设置页（`ShopBrokerageConfigDO`） | 2 天 |
| 1.5 | 会员列表（按消费倒序） | 1 天 |
| 1.6 | 商户提现审核（`MerchantWithdrawApply` + 凭证上传） | 1-2 天 |

### 4.4 Phase 2 — 用户端小程序

在 **同一 uni-app repo** 内新增 `pages/user-home/*` / `pages/shop-home/*` / `pages/cart/*` / `pages/checkout/*`。按 `userStore.activeRole === 'member'` 分流。

**关键业务**：
- 扫码进店 `?shopId=&ref=` → 调 `/app-api/member/bind-shop` → 落 `MemberShopRelDO`（新表）
- 结算页积分 + 在线支付混合抵扣
- 余额 → 积分 `/convert`（单向）

预估 3-4 周。

### 4.5 Phase 3 — 核心业务逻辑（跟 Phase 2 并行）

| Phase | 内容 |
|---|---|
| 3.1 | `MemberShopRelDO`（user × merchant，balance / points / firstVisitAt / referrerUserId） |
| 3.2 | `OrderPaidEvent` 监听：按 `ShopBrokerageConfig.returnAmount` 给 referrer 记账 |
| 3.3 | 按商品 `giveIntegral` 给买家加点数 |
| 3.4 | 通联支付 TL-Pay：`PayChannelEnum` 加枚举 + `PayClient` 实现 + 商户开关 |

### 4.6 Phase 4 — PC 后台补齐

基于 `yudao-ui-admin-vue3` 加：
- 商户入驻审核
- 商户提现二审
- 全平台推 N 反 1 数据大盘
- 视频 / 商品违规下架
- BD 邀请码归属统计

### 4.7 Phase 5 — 上架准备

- 微信小程序 appid 申请 + 类目（电商 / 零售）审核
- ICP 备案 + 业务域名（回调 HTTPS）
- 抖音开放平台正式发布
- 通联商户号 + 回调验签
- 压测 + 灰度

---

## 5. 已落地模块详解

### 5.1 AI 视频 BFF（Phase 0.1）

**路径**：`/app-api/merchant/mini/ai-video/bff/**`，所有端点**商户 JWT 保护** + `getMerchantOrThrow()`。

| 端点 | 方法 | 入参 | 行为 |
|---|---|---|---|
| `/ark/chat` | POST | 原 Ark chat JSON | 白名单校验 `model` + temperature ∈ [0,1] → 注入 `Authorization: Bearer ${ARK_API_KEY}` → 透传 |
| `/jimeng/submit` | POST | `{imageUrl, prompt, frames: 121\|241, seed}` | **预扣 1 条配额** → HMAC-SHA256 SigV4 签名 → 调 `CVSync2AsyncSubmitTask` → **失败回补** + 审计链回写 `taskId` |
| `/jimeng/query` | POST | `{taskId}` | 签名调 `CVSync2AsyncGetResult` |
| `/tts` | POST | `{text, voice}` | openspeech v3 NDJSON 流 → base64 拼接 → `audio/mpeg` 字节 |

**关键类**：
- `AppMerchantAiVideoBffController.java`
- `client/ArkBffClient.java`（指数退避重试 3 次 + key 脱敏）
- `client/JimengBffClient.java`（SigV4 签名链 SK→shortDate→region→service→'request'）
- `client/TtsBffClient.java`

### 5.2 微信小程序统一登录（Phase 0.2）

**路径**：`/app-api/app/auth/**`

| 端点 | 方法 | 说明 |
|---|---|---|
| `/wx-mini-login` | POST | `{code}` → `jscode2session` 换 openid → 查/建 MemberUser → 查 Merchant → 返 `{token, roles, openid, phone?, merchantId?, userId, activeRole}` |
| `/bind-phone` | POST | `{encryptedData, iv}` → Redis 取 sessionKey → AES-128-CBC/PKCS7 解密 |
| `/apply-merchant` | POST | `{inviteCode, encryptedData?, iv?}` → `validateAndConsume(code)` → `createMerchantFromMember` → JWT 增 `merchant` role |
| `/switch-role` | POST | `{role}` → Redis 写 activeRole TTL 7 天 |
| `/me` | GET | 返 roles / activeRole / phone / merchantId / userId / openid |

**JWT 设计**：**沿用 yudao 的 OAuth2 opaque token**（不自签 JWT 以避免改 starter）。`roles` / `activeRole` 不塞 token，由 `/me` 接口 + `ActiveRoleCache`（Redis）承载。

**所有方法带 `@TenantIgnore`**：因为 `member_user` 是全局表。

**并发保护**：`member_user.mini_app_open_id` UNIQUE 索引 + `INSERT IGNORE`，防并发注册重复。

### 5.3 邀请码体系（Phase 0.2）

**表**：`merchant_invite_code (code, operator_user_id, usage_limit, used_count, enabled, ...)`

**核心方法**：`MerchantInviteCodeService.validateAndConsume(code)`
- 单条 `UPDATE ... WHERE code=? AND enabled=1 AND (usage_limit=-1 OR used_count<usage_limit)` 原子自增
- `affected == 0` 抛 `INVITE_CODE_NOT_FOUND/EXHAUSTED/DISABLED`

**后台 CRUD**：`/admin-api/merchant/invite-code/*`（权限 `merchant:invite-code:*`）

### 5.4 视频配额与流水（Phase 0.3.1/2）

**表**：
- `merchant_info.video_quota_remaining`（列）
- `merchant_video_quota_log (merchant_id, quota_change, quota_after, biz_type, biz_id, remark)`
  - **`UNIQUE uk_biz(biz_type, biz_id)`** ← 支付回调幂等的核心约束

**枚举** `VideoQuotaBizTypeEnum`：
```
1 PACKAGE_PURCHASE   支付回调加量
2 VIDEO_GEN          BFF 扣减
3 VIDEO_REFUND       失败回补
4 MANUAL_ADJUST      后台手工调整
```

**API**：
- `MerchantService.increaseVideoQuota(merchantId, delta, bizType, bizId, remark) → QuotaChangeResult{after, logId}`
- `decreaseVideoQuota(...)` 同，内部 SQL `UPDATE ... WHERE video_quota_remaining >= #{delta}` 保证不透支
- 两方法都 `@Transactional(REQUIRES_NEW)` + `@Transactional(rollbackFor = Exception.class)`：配额变更和调用方主事务解耦
- **写 log 在 UPDATE 之后**：`insertLog` 冲突抛异常自动回滚整个 REQUIRES_NEW 事务

**告警任务**：`VideoQuotaReconcileJob.alertOrphanDebits()` `@Scheduled(cron="0 */5 * * * ?")`
- 扫 15-60 分钟前的 VIDEO_GEN 流水，无对应 VIDEO_REFUND 且 `remark` 无 `taskId=xxx` → 视为潜在丢失，记 `log.error` 告警
- **不自动回补**，人工判断

**App API**：`/app-api/merchant/mini/video-quota/`
- `GET /me` 当前配额
- `GET /packages` 在售套餐
- `GET /logs` 当前商户流水
- `POST /packages/{id}/purchase` 创建套餐订单 → 返 `{packageOrderId, payOrderId, channelCode}`
- `POST /pay-callback` ← **pay 模块回调专用，`@PermitAll`，但内部有二次校验**

**Admin API**：`/admin-api/merchant/ai-video/package/*`（权限 `merchant:ai-video-package:*`）

### 5.5 套餐订单 & 支付回调（Phase 0.3.3）

**表**：`merchant_package_order (merchant_id, package_id, package_name, video_count, price, pay_order_id UNIQUE, pay_status, pay_time, quota_log_id)`

**购买流程**（`MerchantPackageOrderServiceImpl.createOrder`）：
1. `@Transactional`
2. `validatePackageAvailable` 查套餐
3. INSERT `merchant_package_order`（pay_status=0）
4. 从 MemberUserDO 拿 openid（后端取，不信前端）
5. 组 `PayOrderCreateReqDTO`（appKey / merchantOrderId / price / expireTime 2h）
6. `payOrderApi.createOrder` → `payOrderId`
7. UPDATE `merchant_package_order.pay_order_id`
8. 返前端

前端拿 `payOrderId` 调 `/app-api/pay/order/submit?channelCode=wx_lite&channelExtras={openid}` 拿 JSAPI 参数。

**回调流程**（`onPayCallback`）：
1. `@PermitAll`（pay 模块 HTTP 回调无签名——yudao 的现状，所以业务侧二次校验）
2. 按 `payOrderId` 查 `merchant_package_order`，非本业务直接忽略
3. **调 `payOrderApi.getOrder(payOrderId)` 反查真实状态**：
   - `status == SUCCESS(10)`
   - `price == order.price`（防金额篡改）
4. CAS 更新 `pay_status`：`UPDATE ... WHERE id=? AND pay_status=WAITING`，`affected=0` 幂等短路
5. 调 `merchantService.increaseVideoQuota(PACKAGE_PURCHASE, payOrderId)`
   - 幂等由 `merchant_video_quota_log.uk_biz(biz_type=1, biz_id=payOrderId)` 保证
6. 回写 `quota_log_id` 审计链

**关键：CAS 在前、配额在后**。如果配额失败回滚，CAS 也会撤销，pay 模块重试时能再进来。

---

## 6. 环境变量完整清单

### 6.1 后端 `yudao-server`（系统环境变量）

| 变量 | 必填 | 来源 | 备注 |
|---|---|---|---|
| `ARK_API_KEY` | ✅ | 方舟控制台 | 豆包 LLM |
| `JIMENG_AK` | ✅ | 火山视觉 | 即梦 AI AK |
| `JIMENG_SK` | ✅ | 火山视觉 | 即梦 AI SK（base64 签名值） |
| `VOLCANO_APP_ID` | ✅ | openspeech | TTS |
| `VOLCANO_ACCESS_TOKEN` | ✅ | openspeech | TTS token |
| `VOLCANO_AK` / `VOLCANO_SK` | ❌ | 兼容 | 老智能创作接口（主路径已切 Seedance/Jimeng） |
| `DOUYIN_CLIENT_KEY` | ⚠️ | 抖音开放平台 | 发布功能需要 |
| `DOUYIN_CLIENT_SECRET` | ⚠️ | 抖音开放平台 | 同上 |
| `MERCHANT_INTERNAL_TOKEN` | ✅ | 自生成 ≥16 字符 | 不配 app 启动失败 |
| `WECHAT_MINI_APP_ID` | ✅ | 微信小程序后台 | 登录用 |
| `WECHAT_MINI_APP_SECRET` | ✅ | 同上 | session_key 解手机号 |
| `MERCHANT_PACKAGE_PAY_APP_ID` | ⚠️ | admin `/pay/app` 创建 | 套餐支付；默认 0 时 `/purchase` 报错 |

**注入方式**：IDE Run Config 加 env，或者 shell `export`，或者 docker `-e`。**不要提交 .env 文件到 git**。

### 6.2 前端 `yudao-ui-merchant-uniapp/.env.local`

**非敏感（VITE_ 前缀，会进 bundle）**：
```
VITE_ARK_LLM_MODEL=doubao-1-5-pro-32k-250115
VITE_ARK_VISION_MODEL=doubao-1-5-vision-pro-32k-250115
VITE_ARK_VIDEO_MODEL=doubao-seedance-1-5-pro-251215
VITE_VIDEO_RESOLUTION=1080p
VITE_VIDEO_DURATION=10
VITE_VIDEO_RATIO=9:16
VITE_VIDEO_SCENES=3
VITE_TTS_PROVIDER=volc
VITE_TTS_VOICE=zh_male_beijingxiaoye_emo_v2_mars_bigtts
```

**sidecar 专用（无前缀，仅 Node 端可见）**：
```
JIMENG_AK=                # 即梦 AK（仅当本地用 sidecar /jimeng 回归才填；正式链路走后端 BFF）
JIMENG_SK=
TOS_AK=                   # 火山 TOS（sidecar /oss/upload /video/mux /video/endcard 用）
TOS_SK=
TOS_BUCKET=tanxiaoer
TOS_REGION=cn-beijing
TTS_ACCESS_TOKEN=         # sidecar 合片内部用（区别于后端 BFF）
TTS_RESOURCE_ID=volc.service_type.10029
DOUYIN_CLIENT_KEY=        # sidecar /douyin/* 用
DOUYIN_CLIENT_SECRET=
```

### 6.3 Spring Boot yaml 配置

关键文件：`yudao-server/src/main/resources/application-local.yaml` 和 `yudao-module-video/src/main/resources/application-video.yaml`

重要段：
```yaml
video:
  volcano-engine:
    ark-api-key: ${ARK_API_KEY:}
    jimeng-access-key: ${JIMENG_AK:}
    jimeng-secret-key: ${JIMENG_SK:}
    ark-allowed-models:          # Phase 0 审查新增：模型白名单防前端滥用
      - doubao-1-5-pro-32k-250115
      - doubao-1-5-vision-pro-32k-250115
      - doubao-seedance-1-0-pro-250528
    # ... 其他
  douyin:
    client-key: ${DOUYIN_CLIENT_KEY:}
    client-secret: ${DOUYIN_CLIENT_SECRET:}

merchant:
  internal-token: ${MERCHANT_INTERNAL_TOKEN:please-override-in-production-at-least-16-chars}

wechat:
  mini-app:
    app-id: ${WECHAT_MINI_APP_ID:}
    app-secret: ${WECHAT_MINI_APP_SECRET:}

yudao:
  merchant:
    package:
      pay-app-id: ${MERCHANT_PACKAGE_PAY_APP_ID:0}
```

---

## 7. 数据库 Schema（Phase 0 新增）

| 表 / 列 | 脚本 | 说明 |
|---|---|---|
| `merchant_invite_code` | `merchant_invite_code.sql` | BD 商户邀请码 |
| `merchant.open_id` | `merchant_invite_code.sql` | ALTER 增 + INDEX `idx_open_id` |
| `merchant.union_id` | 同上 | |
| `merchant.invite_code_id` | 同上 | 注册时用的邀请码 ID |
| `member_user.mini_app_open_id` | 同上 | **UNIQUE `uk_mini_app_open_id`** 防并发重复 |
| `ai_video_package` | `ai_video_package.sql` | 平台级套餐定义 |
| `merchant_video_quota_log` | 同上 | **UNIQUE `uk_biz(biz_type, biz_id)`** 幂等核心 |
| `merchant_info.video_quota_remaining` | 同上 | 配额字段（ALTER 加列，information_schema 守护） |
| `merchant_package_order` | 同上（追加段） | 业务订单（`uk_pay_order` UNIQUE） |

所有脚本 `CREATE TABLE IF NOT EXISTS` + 列增补前查 `information_schema.COLUMNS`，幂等可重跑。

---

## 8. Sidecar（Vite dev）端点速查

`yudao-ui-merchant-uniapp/vite.config.js` 挂载的 sidecar：

| 端点 | 方法 | 用途 | 线上替代 |
|---|---|---|---|
| `/oss/upload` | POST | 浏览器传图 → 火山 TOS | 同（或改走后端） |
| `/tts/volc` | POST | TTS 流式 | 后端 BFF `/tts` |
| `/tts/edge` | POST | 微软 Edge TTS fallback | 同 |
| `/vproxy` | GET | 视频反代（canvas 抽帧用） | 同 |
| `/video/merge` | POST | FFmpeg concat N 个 clip | Phase X Worker 服务 |
| `/video/mux` | POST | 字幕 + TTS 合流 | 同上 |
| `/video/endcard` | POST | 商品图+大二维码+店名 端卡 | 同上 |
| `/jimeng` | POST | **已 `@deprecated`**，走后端 BFF | 后端 BFF |
| `/douyin/auth-url` | GET | 抖音 OAuth 链接 | 后端 |
| `/douyin/oauth-callback` | GET | 抖音 OAuth 回调 | 后端 |
| `/douyin/publish` | POST | 下载合并 mp4 + 上传抖音 | 后端 Worker |

---

## 9. 常见坑 & 踩坑手册

### 9.1 `@TenantIgnore` 什么时候加？

**规则**：
- 方法**只查询/写入** `extends TenantBaseDO` 的表 → 不加（自动过滤）
- 方法需要访问 `extends BaseDO` 的**全局表**（如 `member_user`、`ai_video_package`、`merchant_video_quota_log`）→ **必须加**，否则 MP 拦截器会拼 `AND tenant_id = ?` 把记录过滤掉
- 跨租户的登录/注册流程 → 加

**判断诀窍**：看 DO 的父类。`BaseDO` 子类 + 没有 `tenantId` 字段 = 全局表。

### 9.2 `VITE_*` 前缀敏感性

**任何带 `VITE_` 前缀的 env 都会被 Vite 打进最终 bundle**。生产打包后 grep dist/ 能看到。所以：
- 敏感 key（AK/SK/Token）**不要** `VITE_` 前缀
- 无前缀的 env 只有 Node 端（sidecar / vite.config.js）能读，不会进 bundle

历史曾用 `VITE_ARK_API_KEY` 等泄露过 key，**Phase 0.2 已经全部下线**（密钥搬到后端 BFF）。

### 9.3 `application-local.yaml` vs `application-video.yaml`

- `application-local.yaml` 在 `yudao-server/src/main/resources`——整个应用的 local profile 配置
- `application-video.yaml` 在 `yudao-module-video/src/main/resources`——video 模块私有配置，被 spring.factories 自动加载

Phase 0 的新配置（ark / jimeng / tts / douyin）在 video 模块；登录相关（wechat.mini-app）在 server 的 local profile。

### 9.4 BFF 要求商户 JWT

`/app-api/merchant/mini/ai-video/bff/*` 所有端点都 `getMerchantOrThrow()`，意思是：
- 必须 JWT（商户 userId）
- 且该 userId 必须通过 `MerchantService.getMerchantByUserId` 查得到 MerchantDO

开发调试时若没完成商户注册流程，调 BFF 必返 401 / `MERCHANT_NOT_FOUND`。先走 `/wx-mini-login` + `/apply-merchant` 注册一个测试商户。

### 9.5 MySQL 端口 16379（Redis 同），不是 6379

`application-local.yaml` 里写死 `16379`，本机 Redis 启动时要 `redis-server --port 16379`。

### 9.6 Quartz 在 local profile 默认禁用

看 `application-local.yaml`：
```
spring.autoconfigure.exclude:
  - org.quartz.xxx...
```
定时任务不会自动跑（比如 `VideoQuotaReconcileJob`）。本地测定时任务要删掉 exclude 或改 profile。

### 9.7 circular-references 允许

`spring.main.allow-circular-references=true` 有意为之（审计流某些 controller ↔ service 互相依赖）。别改。

### 9.8 模块 `bpm` 等被注释掉

根 `pom.xml` + `yudao-server/pom.xml` 里有一堆 `<!-- <module>... -->`。不要随便解注释，那些模块可能有编译错误或版本冲突（来自 upstream yudao）。

---

## 10. 完整 Commit 历史（Phase 0）

```
e52c1fb fix(merchant+video): Phase 0.3.3 代码审查必修项
d916e4e feat(merchant): Phase 0.3.3 套餐支付集成（pay 模块回调闭环）
f7f48f8 fix(merchant+video): Phase 0.3 代码审查必修项
d1f308d feat(merchant+video): Phase 0.3.1+0.3.2 视频配额数据模型 + BFF 原子扣减
cfa9478 fix(merchant+video): Phase 0 代码审查必修项
7716605 feat(merchant-uniapp): Phase 0.2 前端 — 真登录 + BFF 迁移 + 密钥脱离 bundle
750578d feat(merchant): Phase 0.2 后端 — 微信小程序真登录 + 邀请码体系 + 角色解析
7edaae7 feat(video): Phase 0.1 AI 视频 BFF（Ark / Jimeng / TTS 后端代理）
```

在新机器跑 `git log --oneline -20` 应至少看到上面 8 条。

---

## 11. TODO 清单（接手后按优先级做）

### 🔴 阻断上线的
- [ ] Phase 0.3.4 PC 平台套餐管理页 + `/pay/app` 配置指引
- [ ] Phase 0.3.5 小程序商户端套餐购买页 + JSAPI 调起
- [ ] Phase 1.x 商户端全模块对接真实后端（当前商品 / 订单 / 会员仍是 mock）
- [ ] 微信小程序 appid 实际申请 + 类目审核
- [ ] 通联支付 `TL_PAY` 接入
- [ ] 用户端小程序（Phase 2）
- [ ] PC 后台商户审核 / 提现二审页（Phase 4）

### 🟡 生产加固
- [ ] `VideoQuotaReconcileJob` 接入真实告警渠道（飞书/钉钉/邮件），当前只打 `log.error`
- [ ] FFmpeg 合片搬后端 Worker（当前生产无法跑）
- [ ] pay 模块 `orderNotifyUrl` 回调**加签名校验**（yudao 现状无签，目前靠业务侧 `payOrderApi.getOrder` 二次校验；上产前可考虑 PR 补签名）
- [ ] 套餐退款流程（/update-refunded 反向扣减配额）未实现
- [ ] BFF 加**限流**（当前 openid 级或商户级，防盗刷 AI）
- [ ] 前端 `vite.config.js` sidecar 里 `/jimeng` handler 已 `@deprecated`，一期观察后删掉

### 🟢 工程优化
- [ ] 前端 `src/api/auth.js` 有死代码（旧 SMS 登录），Phase 0.2 后未清；下一次 sweep 时删
- [ ] `yudao-ui-merchant-uniapp` 名字不再准确（马上要承载用户端），将来改 `yudao-ui-tanxiaoer-uniapp` 或类似
- [ ] `VideoQuotaBizTypeEnum.of()` 有定义无调用，Phase 0.3.3 支付回调会用到（如还未使用，加进去）
- [ ] 代码审查发现的 quota_after 一致性（中级）：可以把 `selectById` 读 `quota_after` 改成 `UPDATE ... RETURNING`-like 模式，少一次 SELECT。当前事务内安全，只是多一次数据库往返。

---

## 12. 快速排错

| 症状 | 可能原因 | 修法 |
|---|---|---|
| 后端启动 `MERCHANT_INTERNAL_TOKEN` 异常 | 未配或 < 16 字符 | 环境变量 `export MERCHANT_INTERNAL_TOKEN=$(openssl rand -hex 20)` |
| 前端白屏 + `[proxy] ⚠️ 未设置 HTTPS_PROXY` | 本机靠 Clash 上网没配 | `HTTPS_PROXY=http://127.0.0.1:7897 pnpm dev:h5` |
| BFF 返 401 | 商户 JWT 无效 / 未注册商户 | 先调 `/wx-mini-login` + `/apply-merchant` |
| BFF Jimeng 签名失败 | JIMENG_AK/SK 未配或错 | 看后端日志 `HMAC-SHA256 ... Signature=` |
| `/pay-callback` 拒付 | payOrderApi.getOrder 反查 status != 10 | 确认 pay_order 表里该单确实是 SUCCESS |
| 配额永远 0 | 未跑 `ai_video_package.sql` | 按第 0.3 节跑迁移 |
| 支付成功但没加配额 | PayApp.orderNotifyUrl 没配或不通 | admin `/pay/app` 填对外可达 URL |
| 字幕乱码 | 中文字体缺失 | macOS 自带 PingFang；Linux 装 `fonts-noto-cjk` |

---

## 13. 联系与文档

- **仓库**：`github.com/hukeowen/shop`（`main` 分支）
- **PR 模板**：commit 标题用 `feat(scope): zh-cn desc` 格式（见历史）
- **需求基线 v1**：在上一次对话里确认（见 Git 历史），已冻结 10 条决策
- **抖音开放平台文档**：https://developer.open-douyin.com/docs
- **火山方舟**：https://www.volcengine.com/docs/82379
- **即梦 AI**：https://www.volcengine.com/docs/85128

---

**附：完整切片 Agent 指令**

当接手后需要 AI 协助继续开发时，可把本文档 + 具体 Phase 目标（如 "做 Phase 0.3.4 平台 PC 套餐管理页"）作为 context 给 AI。本项目采用 oh-my-claudecode 的 executor agent 模式批量做 Java / Vue 代码，做完立即代码审查 + 修必修项。每个 Phase commit 前都跑 `mvn -pl yudao-server -am compile -DskipTests` + 前端 `pnpm build:h5` 两个验证。
