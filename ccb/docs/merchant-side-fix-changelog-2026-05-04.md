# 商户端跨租户隔离修复清单（2026-05-04）

> **本次提交核心问题**：所有商户登录后看到的是同一个 admin 租户（tenant_id=1）的数据，互相能查/改/删对方的商品、订单、营销账户、提现记录。
> **根因**：登录入口（password-login / wx-mini-login / apply-merchant / bind-phone / switch-role）方法上挂着 `@TenantIgnore`，调 `issueToken()` 时当前线程 TenantContextHolder 处于 ignore 状态。yudao 的 `OAuth2TokenServiceImpl.createAccessToken` 把这个空上下文写到 `oauth2_access_token.tenant_id`，后续 `TenantSecurityWebFilter` 解析 token 拿到的 tenantId 落空，再 fallback 到 `member_user.tenant_id=1`（默认 admin tenant）→ 所有商户共享 tenant_id=1 → 跨租户。
> **影响**：从「商户多租户 SaaS」退化成「单租户多商户混合」。**这是历史遗留 bug，不是本次会话引入的**。

---

## 后端修复（yudao-module-merchant）

### 1. `AppUnifiedAuthController.java` — 修 token 签发上下文（**核心修复**）

新增工具方法 `issueTokenForMerchant(userId, merchantTenantId)`，在 `TenantUtils.execute(merchantTenantId, ...)` 上下文里调 `issueToken`，让 `oauth2_access_token.tenant_id` 落到正确的商户租户。

替换 5 处签发点：
| 接口 | 原代码 | 新代码 |
|---|---|---|
| `wx-mini-login` | `issueToken(member.getId())` | `issueTokenForMerchant(member.getId(), merchant?.getTenantId())` |
| `password-login` | 同上 | 同上 |
| `bind-phone` | 同上 | 同上 |
| `apply-merchant` | `issueToken(userId)` | `issueTokenForMerchant(userId, merchant.getTenantId())`，并补 `TenantUtils.executeIgnore` 跨租户取回 merchant |
| `switch-role` | 同上 | 切到 `merchant` 角色用 `merchant.getTenantId()`，切回 `member` fallback null |
| `applyMerchantBySms`（幂等返回分支） | `issueToken(member.getId())` | `issueTokenForMerchant(member.getId(), existed.getTenantId())` |

> `applyMerchantBySms` 主分支（line 504）原本就在 `TenantUtils.execute(merchant.getTenantId(), ...)` 里签 token，正确的，未改。

### 2. `AppLoginRespVO.java` — 增字段
- 新增 `private Long tenantId` 字段（前端落 storage 用）。

### 3. `MerchantServiceImpl.createMerchantFromMember()` — 老逻辑无需改
经审计已正确：调 `createTenantDirectly()` 直接 INSERT system_tenant 拿独立 tenantId，再用 `TenantUtils.execute(tenantId, () -> merchantMapper.insert(merchant))` 把新 merchant 写到独立租户。`shop_info / tenant_subscription / pay_app / pay_channel` 也按 tenantId 隔离。

---

## 前端修复（yudao-ui-merchant-uniapp）

### 1. `pages/merchant-login/index.vue`、`pages/login/index.vue`
登录成功后 `uni.setStorageSync('tenantId', resp.tenantId)`，后续 request.js 自动从 storage 读 tenant-id 写入 header。

### 2. 商户端 4 个主页加 `<RoleTabBar />`
- `pages/index/index.vue`（工作台）— `current="/pages/index/index"`
- `pages/ai-video/index.vue`（成片）— `current="/pages/ai-video/index"`
- `pages/order/list.vue`（订单）— `current="/pages/order/list"`
- `pages/me/index.vue`（我的）— `current="/pages/me/index"`

`pages.json` 早已注册 easycom 自定义映射 `RoleTabBar: "@/components/RoleTabBar.vue"`。

### 3. `pages/me/qrcode.vue`、`pages/shop-home/index.vue`、`pages/user-me/invite.vue`
QR 链接使用 `location.origin` 绝对路径（绕过 uniapp H5 base path 把 `/qr` 解析成 `/m/qr` 的问题）。

---

## 数据库迁移

**用户已确认会清库重跑**，不写迁移 SQL。新流程下：
- `merchant_info.tenant_id` 自动是独立 tenantId（`createTenantDirectly` 拿到的）
- `oauth2_access_token.tenant_id` 自动是独立 tenantId（本次修复）
- 所有 `TenantBaseDO` 的子类 SELECT/UPDATE/DELETE 自动 WHERE tenant_id=?

如果之后改主意要保留历史数据，需要手写 SQL：
```sql
-- 把 tenant_id=1 的历史 merchant 拆到独立 tenant（每商户建一行 system_tenant，再批量 UPDATE）
-- ⚠ 必须确保 product_spu / trade_order / shop_info 等关联表一起改，否则数据错乱
```

---

## 回归验证步骤

### A. SQL 验证（重启 + 注册两个新商户后跑）

```sql
-- 1. 看两个新商户是否各自独立租户
SELECT id, tenant_id, name, contact_phone FROM merchant_info
WHERE contact_phone IN ('13700137001','13700137002');
-- 期望：两条记录，tenant_id 不同

-- 2. 看商户登录 token 是否带正确 tenant_id（关键）
SELECT id, tenant_id, user_id, expires_time FROM oauth2_access_token
ORDER BY id DESC LIMIT 4;
-- 期望：两个商户的 token 各自的 tenant_id 不同，且都不是 1

-- 3. 看新建商品是否归属正确租户
SELECT id, tenant_id, name FROM product_spu ORDER BY id DESC LIMIT 4;
-- 期望：A 建的商品 tenant_id 与 A 的租户一致，B 同理
```

### B. HTTP 验证（手工 / E2E）

1. `POST /app-api/app/auth/send-sms-code` { mobile: '13700137001', scene: 50 } → 返 ok
2. `POST /app-api/app/auth/apply-merchant-by-sms` { mobile, code: '888888', shopName: '小张烧烤' } → 返 token A + tenantIdA
3. 同样 13700137002 注册 → token B + tenantIdB（应不等于 tenantIdA）
4. A 用 tokenA 调 `POST /app-api/merchant/mini/product/simple-create` 建商品 X
5. B 用 tokenB 调 `GET /app-api/merchant/mini/product/page` → **不应包含 X**
6. A 用 tokenA 调 `GET /app-api/merchant/mini/dashboard/summary` 看是否 0 订单（新租户）
7. A 调 `GET /app-api/merchant/mini/order/page` → 不应看到 admin tenant 的历史订单

### C. 浏览器验证

- 新窗口打开 `http://localhost:5180/m/#/pages/merchant-apply/index` 走入驻全程
- 登录后看 `localStorage.tenantId` 是否真实数字（不是 "1"）
- 看 Network → 任意商户 API 请求 header 里 `tenant-id` 是否带新租户 id

---

## 用户需手工执行的事项

1. **生产环境清库**：`TRUNCATE merchant_info; TRUNCATE oauth2_access_token; TRUNCATE product_spu;` 等关键表（顺序按外键约束跑）。**这是用户授权的破坏性操作，本会话只准备 SQL 不直接执行。**
2. 用 `git status` + `git diff` 复核我改的代码。
3. 若要回滚本次修复，用 `git restore <file>` 或 `git checkout HEAD -- <file>`，影响范围在改动文件内。

---

## 本次会话改了哪些文件

```
后端：
  yudao-module-merchant/src/main/java/cn/iocoder/yudao/module/merchant/controller/app/AppUnifiedAuthController.java
  yudao-module-merchant/src/main/java/cn/iocoder/yudao/module/merchant/controller/app/vo/auth/AppLoginRespVO.java

前端：
  yudao-ui/yudao-ui-merchant-uniapp/src/pages/merchant-login/index.vue
  yudao-ui/yudao-ui-merchant-uniapp/src/pages/login/index.vue
  yudao-ui/yudao-ui-merchant-uniapp/src/pages/index/index.vue
  yudao-ui/yudao-ui-merchant-uniapp/src/pages/ai-video/index.vue
  yudao-ui/yudao-ui-merchant-uniapp/src/pages/order/list.vue
  yudao-ui/yudao-ui-merchant-uniapp/src/pages/me/index.vue
  yudao-ui/yudao-ui-merchant-uniapp/src/pages/me/qrcode.vue
  yudao-ui/yudao-ui-merchant-uniapp/src/pages/shop-home/index.vue
  yudao-ui/yudao-ui-merchant-uniapp/src/pages/user-me/invite.vue

文档：
  docs/merchant-h5-api-inventory.md（商户端 H5 全部接口清单）
  docs/merchant-side-fix-changelog-2026-05-04.md（本文件）
```

---

## 风险评估

| 改动 | 风险等级 | 备注 |
|---|---|---|
| `issueTokenForMerchant` 新方法 | 低 | 新建 token 用正确 tenant_id；不影响已发出的 token（让用户重新登录即可） |
| 5 处签发点替换 | 中 | 必须每条路径都覆盖否则又泄漏。已 grep `issueToken\(` 复核（line 631 helper 实现 + 657 fallback + 660 lambda 调用 + 5 处业务调用 = 共 5 业务签发点全部改完） |
| RoleTabBar 加载 | 低 | 只是底部导航，组件早就在 `pages.json` easycom 注册 |
| 用户端入口暂未动 | — | 按用户指令「先改商户端」，user-home/user-order/user-me 等用户端 RoleTabBar import 留待下一阶段 |
