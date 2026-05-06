# 商户端接口租户隔离审计报告

> **执行时间**：2026-05-06
> **测试账号**：A=18888888888（老王烧烤 merchantId=4），B=19999999999（水果店 merchantId=5）
> **结果**：🚨 **租户隔离完全失效** — 两个商户登录后看到完全相同的 15 个商品；所有商品 `tenant_id=1`

## 实测证据

```
A token=3f0340eb7f... 老王烧烤 merchantId=4
B token=edf3ceda00... 水果店  merchantId=5

A 调 /app-api/merchant/mini/product/page → 15 条商品，全部 tenantId=1
B 调 /app-api/merchant/mini/product/page → 15 条商品，全部 tenantId=1
重叠 15/15 → A 能看到 B 创建的商品，B 能看到 A 创建的
```

## 根因（可能性，按 likelihood 排序）

### 1. **商户入驻时未真创建独立租户**（最可能）
- 用户期望：「一个商户后台就自动创建一个租户」
- 实际：所有商户共享 `tenant_id=1`（yudao 系统默认租户）
- 数据库验证（待确认）：`SELECT id, tenant_id, name FROM merchant_info LIMIT 10`

`AppUnifiedAuthController` 里有 `createTenant` 调用（line 446 注释提到 TenantUtils.execute），但**实际入驻流程是否真走 createTenant 待审计**。

### 2. JWT token 里 tenantId 解析有误
- 商户登录返回 `userId=9 merchantId=4`，但 response 没显示 tenantId
- 后端用 `user.getTenantId()` 解析 → 如果 system_users 表里 tenant_id=1，则永远查 tenant=1 数据

### 3. ProductSpuMapper 加 @TenantIgnore（最不可能）
- 已确认 `ProductSpuDO extends TenantBaseDO` ✓
- Service 层无 `@TenantIgnore` 注解
- 应自动走 TenantLineInnerInterceptor

## 商户端所有接口清单 + 隔离风险评估

> ✅ = TenantBaseDO 自动隔离（依赖 token tenantId 正确）
> ⚠ = 用 merchantId 显式过滤，跟租户无关，需审计
> ❌ = 公开接口跨租户可见，但本就是这个语义
> 🚨 = 实测/疑似有泄漏

### 认证 / 通用（这些路径通常 @TenantIgnore 是对的）
| 接口 | 用途 | 隔离 |
|---|---|---|
| `POST /app-api/app/auth/password-login` | 登录 | @TenantIgnore（按手机号跨租户找用户）|
| `POST /app-api/app/auth/send-sms-code` | 发短信 | @TenantIgnore |
| `POST /app-api/app/auth/me` | 当前用户信息 | JWT 解析 |
| `POST /app-api/app/auth/switch-role` | 角色切换 | JWT |
| `POST /app-api/app/auth/apply-merchant` | 商户入驻 | @TenantIgnore（创建过程） |
| `POST /app-api/app/auth/apply-merchant-by-sms` | 同上 | @TenantIgnore |
| `POST /app-api/app/auth/bind-phone` | 绑手机 | JWT |
| `POST /app-api/app/auth/wx-mini-login` | 微信登录 | @TenantIgnore |
| `GET /app-api/system/area/tree` | 行政区域 | 全局 |

### 🚨 商品（`/app-api/merchant/mini/product`）— **实测泄漏**
| 接口 | 实现 | 隔离风险 |
|---|---|---|
| `GET /merchant/mini/product/page` | `productSpuService.getSpuPage()` MyBatis 自动 tenant 过滤 | 🚨 **实测：A B 看到对方商品** |
| `GET /merchant/mini/product/get?id=X` | `productSpuService.getSpu(id)` | 🚨 推测同样泄漏 |
| `POST /merchant/mini/product/simple-create` | 入库 ProductSpuDO（TenantBaseDO） | tenantId 自动注入但**注入的是 token 的 tenant=1** |
| `PUT /merchant/mini/product/update-status` | 改状态 | 🚨 可能改别人商品 |
| `DELETE /merchant/mini/product/delete` | 删除 | 🚨 **可能删别人商品** ← 最危险 |
| `GET /app-api/product/category/list` | 全平台分类 | ❌ 公开（语义对，分类是平台级） |
| `GET /app-api/product/spu/page` | yudao mall 公开商品分页 | ❌ 公开（C 端用，跨店浏览） |

### 商户业务（`/app-api/merchant/mini/...`）— 全部依赖 tenant 正确
| 接口 | 用途 | 隔离 |
|---|---|---|
| `GET /merchant/mini/dashboard/summary` | 商户工作台数据 | ✅ TenantBaseDO 自动 |
| `GET /merchant/mini/shop/info` | 店铺信息 | ✅ |
| `GET /merchant/mini/shop/qrcode` | 店铺二维码 | ✅ |
| `GET /merchant/mini/order/page` | 订单列表 | 🚨 OrderDO 是 TenantBaseDO，但 token tenant=1 → 看到全平台订单 |
| `GET /merchant/mini/member` | 会员列表 | 🚨 同上 |
| `GET /merchant/mini/member-rel/list` | 会员关系 | 🚨 |
| `GET /merchant/mini/member-rel/my` | 我的关系 | ⚠ 看 SQL where 条件 |
| `GET /merchant/mini/withdraw/list` | 提现列表 | 🚨 |
| `POST /merchant/mini/withdraw/apply` | 商户申请提现 | 🚨 |

### AI 视频（`/app-api/merchant/mini/ai-video`）
| 接口 | 隔离 |
|---|---|
| `GET /ai-video/quota` | ✅ TenantSubscription 按 tenant 隔离（但 tenant=1 时也是平台级） |
| `GET /ai-video/bff/jimeng/submit` | ✅ 用 merchantId 显式扣 quota |
| `GET /video-quota/me` | 🚨 看 token tenant=1 |
| `GET /video-quota/packages` | ❌ 平台套餐列表，公开对 |

### 营销 v6（`/app-api/merchant/mini/promo`）— 全租户敏感
| 接口 | 风险 |
|---|---|
| `GET /promo/account` 推广账户 | 🚨 |
| `GET /promo/config` 营销配置 | 🚨 |
| `GET /promo/promo-records` 流水 | 🚨 |
| `GET /promo/my-queues` 队列 | 🚨 |
| `GET /promo/pool/info` 资金池 | 🚨 |
| `POST /promo/pool/settle` 手动结算 | 🚨 **可能结算别人池子** |
| `GET /promo/referral/parent` 推荐人 | 🚨 |
| `POST /promo/referral/bind` 绑推荐 | ⚠ |
| `GET /promo/product-config` 商品营销 | 🚨 |
| `GET /promo/consume-records` 消费记录 | 🚨 |

### 用户端 / C 端（不是商户端但同前端）
| 接口 | 性质 |
|---|---|
| `GET /member/address/*` | 用户地址，按 userId 隔离 |
| `POST /trade/cart/*` | 用户购物车 |
| `POST /trade/order/*` | 用户订单 |
| `POST /pay/order/submit` | 支付 |

## 真因待验证（需 SQL 查询）

```sql
-- 1. 看 merchant_info 表所有商户的 tenant_id 是否都是 1
SELECT id, tenant_id, name FROM merchant_info ORDER BY id;

-- 2. 看 product_spu 表所有商品的 tenant_id
SELECT id, tenant_id, name FROM product_spu ORDER BY id DESC LIMIT 30;

-- 3. 看 system_tenant 表（yudao 租户主表）
SELECT id, name, status FROM system_tenant;

-- 4. 看 system_users 表里商户管理员的 tenant_id
SELECT id, mobile, tenant_id, status FROM system_users WHERE mobile IN
  ('18888888888', '19999999999');
```

## 修复方向（紧急）

### 方案 A：补全多租户（推荐，符合用户原始设计）
1. **入驻时真创建租户**：`AppUnifiedAuthController#applyMerchant` 调 `tenantService.createTenant()` 拿到新 tenantId，然后：
   - merchant_info.tenant_id = 新 tenantId
   - system_users.tenant_id = 新 tenantId（管理员账号在该租户下）
   - 该商户所有商品/订单/营销数据自然走新 tenant
2. **历史数据迁移**：把 tenant_id=1 的 merchant_info 拆分到独立租户（写迁移 SQL）
3. **登录 response 加 tenantId**：buildLoginResp 设 `vo.setTenantId(merchant.getTenantId())`，前端 setStorage 用真 tenantId
4. **关键 controller 加 `@PreAuthorize` + 跨租户检查**：getSpu/updateStatus/deleteSpu 必须额外校验 spu.merchantId == 当前 merchantId（防止伪造 id 越权）

### 方案 B：放弃严格租户隔离，全靠 merchantId 过滤（不推荐）
所有 controller 显式 WHERE merchant_id=currentMerchantId。改造工作量大且失去 yudao 多租户原生能力。

## 立刻能止血的最小改动

在 `AppMerchantProductController` 的 page/get/update/delete 里加 merchantId 校验，即使 tenant 失效也能挡：

```java
@GetMapping("/page")
public CommonResult<PageResult<ProductSpuDO>> getSpuPage(@Valid ProductSpuPageReqVO reqVO) {
    Long merchantId = currentMerchantId();
    reqVO.setMerchantId(merchantId);  // ← 显式过滤
    return success(productSpuService.getSpuPage(reqVO));
}
```

但 `ProductSpuPageReqVO` 不一定有 `merchantId` 字段，需要扩展。

---

**结论**：这不是"按需修一个 bug"能解决的，是**架构级补丁**。建议 1-2 天专项处理：
1. SQL 查 4 张关键表确认现状（5 分钟）
2. 改入驻流程真创建 tenant（4 小时）
3. 历史数据迁移 SQL（2 小时）
4. 关键接口加 merchantId 防越权（4 小时）
5. 自动化测试覆盖跨商户访问（2 小时）
