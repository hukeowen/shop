# 摊小二 SaaS 订阅 + 通联支付落地文档

> 状态：v1 起草（2026-05-11）
> 作者：huliang
> 上下文：本仓在用户授权下做"一商户一通联账号"改造，同时引入 SaaS 订阅模式。

## 1. 已完成（commit f260318 起）

### 1.1 一商户一通联账号
| 改动 | 文件 | 说明 |
|---|---|---|
| TlpayCredential 数据类 | AllinpayCashierService.java | 承载 cusId/appId/RSA 密钥/notifyUrl 等所有凭据字段 |
| platformCredential() | 同上 | 全局 props 凭据 — 套餐订单用 |
| merchantCredentialForTenant(tenantId) | 同上 | 按商户 shop_info.tl_xxx 加载凭据（AES 解密）— 商品订单用 |
| signWithCredential / verifyWithCredential | 同上 | 替代写死 props 的旧 signWith/verifyWith |
| doBuildCashierForm(reqsn, trxamt, body, ua, cred) | 同上 | 共用核心，接 credential 参数 |
| buildCashierForm(packageOrderId, ua) | 同上 | 套餐包装，传 platformCredential |
| buildCashierFormForTrade(tradeOrderId, ua) | 同上 | 商品包装，传 merchantCredentialForTenant |
| queryByReqsn(reqsn) / queryByReqsn(reqsn, cred) | 同上 | 按 reqsn T 前缀自动路由凭据 |
| handlePayNotify 按 reqsn 路由凭据验签 | 同上 | 通联回调路径：T 开头 → trade 商户凭据；纯数字 → package 全局凭据 |
| TradeOrderAllinpayService | 新建 | trade 业务标已支付适配器：先标 pay_order SUCCESS（CAS）→ 调 yudao trade.updateOrderPaid → 触发 afterPayOrder v8 推 N 反 1 |
| TradeOrderAllinpayPollingService | 新建 | 轮询兜底 5/15/25/35/60/120s + 扫描 @Scheduled 2 分钟。集群安全用 Redisson 锁 |
| AppMerchantCheckoutController.submit | 改造 | 提单成功后自动调 buildCashierFormForTrade 拿 cashierUrl 一并返 + schedulePolling |
| POST /checkout/cashier-link | 新增 | user-order"立即付款"重新拿支付链接 |
| 前端 checkout.vue + user-order/list.vue | 改造 | 拿到 cashierUrl 直接 location.href 跳通联 |

### 1.2 端到端集成验证
- 用户下单 → checkout/submit 返 cashierUrl ✓
- 用商户级凭据签名（cusId=56165105331VE5Z）✓
- POST 通联（test-vsp.allinpay.com）→ SSL handshake 失败（JDK 8 + 通联 test 沙箱 cipher 兼容性问题；生产环境 vsp.allinpay.com 应可用）
- 通联回调路径已就位（按 reqsn T 前缀路由）
- 轮询兜底已挂调度 + 集群锁

### 1.3 测试数据
shop_info(id=4, tenant=162, shopName=新店1) 已写入测试通联凭据：
```
tl_enabled = true
tl_mch_id = 56165105331VE5Z
tl_app_id = 00240592
tl_sign_type = RSA
tl_rsa_private_key = (PKCS#8 PEM, AES 加密落库)
tl_rsa_public_key = NULL  ⚠️ 测试参数.txt 未提供 RSA 公钥
```

---

## 2. 待实施需求（用户 2026-05-11 提出）

### 2.1 SaaS 订阅体系

**商业模式**：摊小二系统本身收费（SaaS 模式）。商户按年订阅，享受不同功能档位。

| 服务包 | 价格 | 功能 | 赠送 AI 视频 |
|---|---|---|---|
| 基础包（298） | ¥298/年 | 订单系统 + 推 N 反 1 | 10 条 |
| 全功能包（1688） | ¥1688/年 | 基础 + 团队 + 星级 + 奖池 | 30 条 |

**新商户注册**：免费 30 天试用（推荐定级为 1688 全功能，让用户先体验完整功能）。

**到期处理**：
- 服务到期 → 商户后台所有功能锁死，登录弹"续费"模态框
- 续费付款成功 → `service_expire_at += 365 天`，`ai_video_quota_remaining += 赠送条数`
- 同时持有 1688 + 298：优先 1688 功能；1688 到期回 298；298 也到期回锁死

**配置可调**：
- 服务包价格 / 赠送条数后台可改（admin Vue3 加配置页）
- 平台商户（特殊商户）永不过期 + 全功能（演示 / 内部用）

### 2.2 数据模型

#### 2.2.1 merchant_info 加字段
```sql
ALTER TABLE merchant_info ADD COLUMN service_expire_at DATETIME NULL
  COMMENT '服务到期时间（NULL = 永久 / 平台商户）';
ALTER TABLE merchant_info ADD COLUMN service_package_level VARCHAR(16) DEFAULT 'TRIAL'
  COMMENT '当前生效套餐：TRIAL=试用 / BASIC=298 / PRO=1688 / PLATFORM=平台商户';
ALTER TABLE merchant_info ADD COLUMN is_platform BIT(1) NOT NULL DEFAULT b'0'
  COMMENT '是否平台商户（永久 + 全功能）';
```

#### 2.2.2 新表 saas_package_config（套餐配置，后台可改）
```sql
CREATE TABLE saas_package_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  level VARCHAR(16) NOT NULL UNIQUE COMMENT 'BASIC / PRO',
  name VARCHAR(64) NOT NULL,
  price_fen INT NOT NULL COMMENT '年费（分）',
  duration_days INT NOT NULL DEFAULT 365 COMMENT '一次购买的天数',
  ai_video_grant INT NOT NULL DEFAULT 0 COMMENT '赠送 AI 视频次数',
  features VARCHAR(512) COMMENT 'JSON 数组：可用功能 key 列表',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0=上架 1=下架',
  -- 标准审计字段
  ...
);

-- 种子数据
INSERT INTO saas_package_config (level, name, price_fen, duration_days, ai_video_grant, features) VALUES
  ('BASIC', '基础包', 29800, 365, 10, '["order","tuijian"]'),
  ('PRO', '全功能包', 168800, 365, 30, '["order","tuijian","team","star","pool"]');
```

#### 2.2.3 新表 merchant_subscription_order（订阅订单）
```sql
CREATE TABLE merchant_subscription_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL COMMENT 'merchant_info.id',
  level VARCHAR(16) NOT NULL COMMENT '购买的套餐档位',
  price_fen INT NOT NULL,
  duration_days INT NOT NULL,
  ai_video_grant INT NOT NULL,
  pay_status TINYINT NOT NULL DEFAULT 0 COMMENT '0=WAITING 1=PAID',
  pay_at DATETIME NULL,
  tl_reqsn VARCHAR(32) COMMENT '通联交易 reqsn（用 S 前缀区分 trade/package）',
  expire_extended_to DATETIME NULL COMMENT '付款后扩展到的新到期时间',
  ...
);
```

### 2.3 后端实施清单

#### 2.3.1 平台商户初始化（V034__platform_merchant_seed.sql）
```sql
-- 1. 建 system_tenant（id=999 给平台商户用）
INSERT IGNORE INTO system_tenant (id, name, status, package_id, expire_time, ...) VALUES (999, '摊小二平台', 0, ...);

-- 2. 建 member_user（手机号 18800000000）
INSERT IGNORE INTO member_user (id, mobile, password, ...) VALUES (999, '18800000000', ...);

-- 3. 建 merchant_info（关联 user_id=999, tenant_id=999）+ is_platform=1
INSERT IGNORE INTO merchant_info (id, name, contact_name, contact_phone, status, user_id, tenant_id, is_platform, service_package_level)
  VALUES (999, '摊小二平台', '平台运营', '18800000000', 1, 999, 999, b'1', 'PLATFORM');

-- 4. 建 shop_info + 写入通联凭据
INSERT IGNORE INTO shop_info (id, tenant_id, shop_name, status, tl_enabled, tl_mch_id, tl_app_id, tl_sign_type, tl_rsa_private_key, tl_rsa_public_key)
  VALUES (999, 999, '摊小二平台', 1, b'1', '56165105331VE5Z', '00240592', 'RSA', '<AES 加密的私钥>', '<AES 加密的公钥>');

-- 5. 上架 2 个服务包 SPU（属于平台商户租户 999）
INSERT INTO product_spu (id, name, status, price, ..., tenant_id) VALUES
  (9999801, '摊小二·基础包·298 元/年', 1, 29800, ..., 999),
  (9999802, '摊小二·全功能包·1688 元/年', 1, 168800, ..., 999);
INSERT INTO product_sku ...;
```

⚠️ **凭据加密**：插入时密钥用 SymmetricCrypto AES 加密（与 yudao mybatis-plus.encryptor.password 一致）。可以写个一次性初始化脚本（Java main 或 Bash + openssl）。

#### 2.3.2 业务服务
- `SaasSubscriptionService` — 续费下单 / 标支付成功 / 续期 / 检查过期
- `SaasPackageConfigService` — CRUD 套餐配置
- `MerchantServiceExpireFilter` — 拦截器：商户登录后查 service_expire_at，过期则返特定 errcode

#### 2.3.3 接口
- `GET /app-api/merchant/mini/saas/packages` — 列出可购套餐
- `POST /app-api/merchant/mini/saas/purchase?packageLevel=BASIC` — 创建订阅订单 + 调通联 cashier 返支付 URL（reqsn 用 S${subscriptionOrderId}）
- `GET /app-api/merchant/mini/saas/my-status` — 我的服务状态（level / expire_at / 是否过期 / 是否平台商户）

#### 2.3.4 通联回调路由扩展
- `handlePayNotify` 加 S 前缀路由：S 开头 → SaasSubscriptionService.markPaid → 商户 service_expire_at += duration_days

### 2.4 前端实施清单

#### 2.4.1 商户后台
- `pages/me/index.vue` — 显示服务到期时间 + "续费" 按钮 + 服务级别 chip
- `pages/me/subscription.vue` — 续费页（列套餐 → 选 → 调 purchase → 跳通联）
- `App.vue` / 路由守卫 — 商户每次进入后台前调 saas/my-status，过期则 reLaunch 续费页

#### 2.4.2 营销配置按 level 隐藏
`pages/me/promo-config.vue` 按当前商户的 service_package_level：
- BASIC: 隐藏团队 / 星级 / 奖池配置区
- PRO / PLATFORM: 全部显示

#### 2.4.3 用户侧商品详情按 level 显示
`pages/product/detail.vue` 按商品所属商户的 level 决定显示哪些营销 chip。

#### 2.4.4 admin Vue3 套餐配置
`views/saas/packageConfig/index.vue` — CRUD saas_package_config（价格 / 赠送条数可改）。

### 2.5 排期建议
| 阶段 | 内容 | 工作量 |
|---|---|---|
| P0（必做） | V033__saas_subscription.sql + V034__platform_merchant_seed.sql + 通联公钥写入 | 半天 |
| P1（必做） | SaasSubscriptionService + 4 个 API + 续费页 + 拦截器 | 1 天 |
| P2（必做） | 营销配置 / 商品详情按 level 隐藏 + admin 套餐配置页 | 半天 |
| P3（验证） | 端到端：商户购买 → 通联付款 → 回调 → service_expire_at 续 1 年 + AI 额度增 | 半天 |

---

## 3. 待你提供的信息

1. **通联 RSA 公钥**：测试参数.txt 只有商户私钥 + SM2 私钥/公钥。回调验签需要通联 RSA 公钥。
2. **平台商户手机号 / 密码**：默认建议 18800000000 / 123456，是否调整？
3. **新商户试用期套餐定级**：30 天试用是 TRIAL 还是 PRO 体验装？
4. **过期商户能否登录看订单 / 处理售后**？还是完全锁死？

---

## 4. 当前可立即使用

- 一商户一通联凭据架构 ✓
- 平台 admin 后台编辑商户通联配置 ✓
- trade 订单走商户独立通联 + 回调 + 轮询双兜底 ✓
- v8 推 N 反 1 链路（订单付款后 afterPayOrder 触发）✓

待通联 RSA 公钥到位 + 生产环境 SSL 配齐，整套即可在生产工作。
