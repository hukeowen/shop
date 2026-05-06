# 商户端 H5 全部页面 + API 清单

> **生成时间**：2026-05-04
> **范围**：`yudao-ui-merchant-uniapp`，仅商户端（不含 C 端用户/消费者）
> **目的**：用于跨租户隔离回归测试，每个 API 都要保证 token.tenantId 正确时只能查到本商户数据

## 商户端页面 → 调用的接口

| # | 页面路径 | 用途 | 调用的接口 |
|---|---|---|---|
| 1 | `pages/merchant-login/index` | 商户登录（密码） | `POST /app-api/app/auth/password-login` |
| 2 | `pages/merchant-apply/index` | 商户入驻（SMS 一键申请） | `POST /app-api/app/auth/send-sms-code`<br>`POST /app-api/app/auth/apply-merchant-by-sms` |
| 3 | `pages/index/index` | 商户工作台（dashboard） | `GET /app-api/merchant/mini/dashboard/summary` |
| 4 | `pages/ai-video/index` | AI 一键成片入口 | `GET /app-api/merchant/mini/video-quota/me`<br>`GET /app-api/merchant/mini/ai-video/page` |
| 5 | `pages/ai-video/create` | 创建视频（上传图） | `POST /app-api/merchant/mini/ai-video/bff/script/rich` |
| 6 | `pages/ai-video/confirm` | 确认文案 | `POST /app-api/merchant/mini/ai-video/bff/jimeng/submit`<br>`GET /app-api/merchant/mini/ai-video/bff/jimeng/result` |
| 7 | `pages/ai-video/detail` | 视频详情 | `GET /app-api/merchant/mini/ai-video/get` |
| 8 | `pages/ai-video/history` | 历史记录 | `GET /app-api/merchant/mini/ai-video/page` |
| 9 | `pages/ai-video/quota` | 配额购买 | `GET /app-api/merchant/mini/video-quota/packages`<br>`POST /app-api/merchant/mini/video-quota/packages/{id}/purchase` |
| 10 | `pages/ai-video/package-orders` | 配额订单 | `GET /app-api/merchant/mini/video-quota/orders` |
| 11 | `pages/order/list` | 订单列表 | `GET /app-api/merchant/mini/order/page` |
| 12 | `pages/order/detail` | 订单详情 | `GET /app-api/merchant/mini/order/get` |
| 13 | `pages/order/deliver` | 发货 | `POST /app-api/merchant/mini/order/deliver` |
| 14 | `pages/product/list` | 商品管理 | `GET /app-api/merchant/mini/product/page` |
| 15 | `pages/product/edit` | 上架商品 | `POST /app-api/merchant/mini/product/simple-create`<br>`PUT /app-api/merchant/mini/product/update`<br>`GET /app-api/product/category/list` |
| 16 | `pages/product/batch` | AI 识别上架 | `POST /app-api/merchant/mini/product-detect/*` |
| 17 | `pages/me/index` | 我的（商户） | `GET /app-api/app/auth/me` |
| 18 | `pages/me/shop-edit` | 编辑店铺 | `GET /app-api/merchant/mini/shop/info`<br>`PUT /app-api/merchant/mini/shop/info` |
| 19 | `pages/me/qrcode` | 店铺二维码 | `GET /app-api/merchant/mini/shop/info`<br>`GET /app-api/merchant/mini/shop/qrcode` |
| 20 | `pages/me/brokerage` | 返佣与积分 | `GET /app-api/merchant/mini/shop/...`(内部 BASE) |
| 21 | `pages/me/promo-config` | v6 营销配置 | `GET/PUT /app-api/merchant/mini/promo/config` |
| 22 | `pages/me/withdraw-approve` | 商户审批用户提现 | `/app-api/merchant/mini/withdraw/...`(内部 BASE) |
| 23 | `pages/me/pay-apply` | 在线支付开通 | `/app-api/merchant/mini/shop/...` |
| 24 | `pages/me/help` | 帮助反馈 | `POST /app-api/merchant/mini/feedback/submit` |
| 25 | `pages/me/about` | 关于（静态） | — |
| 26 | `pages/member/list` | 会员消费排行 | `GET /app-api/merchant/mini/member/...` |
| 27 | `pages/withdraw/user-list` | 用户提现审核 | `/app-api/merchant/mini/withdraw/...` |
| 28 | `pages/withdraw/member-list` | 用户余额提现审核 | `/app-api/merchant/mini/withdraw/...` |
| 29 | `pages/withdraw/merchant-apply` | 商户提现申请 | `POST /app-api/merchant/mini/withdraw/apply`<br>`GET /app-api/merchant/mini/withdraw/my-list` |

## 接口分类 + 隔离机制

### A. 认证类（@TenantIgnore — 跨租户根据手机号 / openid 找用户）
| 接口 | 隔离机制 |
|---|---|
| password-login | ✅ 已修：token 在 merchant.tenantId 上下文里签 |
| wx-mini-login | ✅ 已修：同上 |
| bind-phone | ✅ 已修：同上 |
| apply-merchant | ✅ 已修：同上 |
| apply-merchant-by-sms | ✅ 早就在 TenantUtils.execute 里签 |
| switch-role | ✅ 已修：切到 merchant 时用 merchant.tenantId 签 |
| me | 仅返 user/merchant 信息，不查租户表 |
| send-sms-code | 不涉及租户 |

### B. 商户业务（依赖 token.tenantId 走 TenantLineInnerInterceptor）
所有 DO 已 extends `TenantBaseDO`：
- `MerchantDO` ✅
- `ShopInfoDO` ✅（虽是 BaseDO 但 SQL 都按 tenant_id WHERE）
- `TenantSubscriptionDO` ✅
- `ProductSpuDO` / `ProductSkuDO` ✅（mall 模块原生）
- `TradeOrderDO` ✅（mall 模块原生）
- `MemberShopRelDO` ✅
- `WithdrawDO` ✅
- `PromoAccountDO` / `PromoQueueDO` / `PromoPoolDO` ✅
- `AiVideoTaskDO` / `VideoQuotaDO` ✅
- `MerchantFeedbackDO` ✅

⇒ **token.tenantId 修对后，TenantLineInnerInterceptor 自动给所有 SELECT/UPDATE/DELETE/INSERT 加 `WHERE tenant_id=?`，跨租户自动隔离。**

### C. 平台公共（@TenantIgnore，本就跨租户语义）
| 接口 | 说明 |
|---|---|
| `GET /app-api/product/category/list` | 全平台分类 |
| `GET /app-api/product/spu/page` | C 端跨店浏览（用户端用） |
| `GET /app-api/merchant/shop/public/info` | 店铺公开信息（不带敏感数据） |
| `GET /app-api/merchant/shop/public/list` | 附近店铺 |
| `GET /app-api/merchant/mini/user-shop/nearby` | 附近店铺 |
| `GET /app-api/system/area/tree` | 行政区域 |

### D. 用户端通用（C 端用户的，不在本次商户端验证）
- `/app-api/trade/cart/*`、`/app-api/trade/order/*`
- `/app-api/member/address/*`
- `/app-api/pay/order/submit`

## 修复后回归测试用例

E2E 步骤（本地或线上都可）：
1. 用 13700137001 走 send-sms-code → apply-merchant-by-sms 注册商户 A
2. 用 13700137002 走同样流程注册商户 B
3. SQL 验证：`SELECT id, tenant_id, name FROM merchant_info WHERE contact_phone IN ('13700137001','13700137002')` — 两条 tenant_id **必须不同**
4. SQL 验证：`SELECT id, tenant_id FROM oauth2_access_token ORDER BY id DESC LIMIT 2` — 两条 tenant_id **必须不同**（这次是关键修复点）
5. A 登录后调 `POST /app-api/merchant/mini/product/simple-create` 建商品 X；B 登录后建商品 Y
6. A 调 `GET /app-api/merchant/mini/product/page` — **只能看到 X，不能看到 Y**
7. A 调 `GET /app-api/merchant/mini/order/page`、`/dashboard/summary`、`/promo/account`、`/withdraw/my-list` — 都只能看到本租户数据
8. A 调 `DELETE /app-api/merchant/mini/product/delete?id=Y_id` — **必须 404 / 0 affected rows**

## 历史数据迁移（可选）

如果不删库重跑，需要写 SQL 把 tenant_id=1 的历史 merchant 拆到独立租户。**用户已确认删了重跑**，跳过此项。
