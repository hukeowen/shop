# 摊小二 营销体系 v6 — 开发进度档

> 配套文档：[marketing-system-v6.md](./marketing-system-v6.md)（已签字定稿的需求）
>
> 截止 2026-04-28，**5 + 4 + 4 + 1 个开发批次**已完成；代码侧尾巴清完，剩余仅部署联调。

---

## 一、阶段总览

| 批次 | 范围 | 状态 |
|---|---|---|
| W1 | SQL DDL（11 张表）+ 商户/商品营销配置后端 + uniapp 配置页 | ✅ 完成 |
| W2 | 双积分账本 + 推荐链 + 队列三机制（直推/插队/自然推） | ✅ 完成 |
| W3 | 团队极差递减 + 星级评定（终生制） | ✅ 完成 |
| W4-1/2 | 积分池累积 + 立即结算（FULL/LOTTERY × ALL/STAR） | ✅ 完成 |
| W4-3 | cron 自动结算 | ✅ 完成（并入 N6） |
| W5 | 提现申请 + 商户审批 + 状态机（PENDING/APPROVED/REJECTED/PAID）| ✅ 完成 |
| W6 | 订单 Handler 串通 + 权限种子 + 5.4 跑例集成测试 | ✅ 完成 |
| FIX-1..5 | 第一轮 code-review 修复（原子余额、commission 订单级、queue 重放幂等、convert 幂等键、池/账户并发兜底） | ✅ 完成 |
| FIX-A..D | 第二轮 code-review 修复（审批权限隔离、池余额原子化、Award JSON、withdraw 状态机原子化） | ✅ 完成 |
| N1 | 推荐链绑定接入（POST /referral/bind） | ✅ 完成 |
| N2 | 用户钱包页（双积分余额 + 流水 + 转换） | ✅ 完成 |
| N3 | 用户提现申请页 | ✅ 完成 |
| N4 | 商户提现审批页 | ✅ 完成 |
| N5 | promo-config 加"立即结算"+ 池历史 UI | ✅ 完成 |
| N6 | quartz cron 自动结算（PromoPoolSettleJob，每小时扫描） | ✅ 完成 |
| N7 | 用户落地页 inviter 解析 + 自动 bind（shop-home + user-home + utils/referral.js） | ✅ 完成 |
| N8 | PromoConfig 加 poolSettleMode 字段（FULL/LOTTERY，cron 用） | ✅ 完成 |
| N9 | PromoPoolSettleJob 单测（10 cases，cron 决策 + 多租户 + 失败隔离） | ✅ 完成 |
| N10 | shop-home 兼容 `?inviter=` query alias | ✅ 完成 |
| N11 | `docs/merchant-promo-guide.md` 商户使用手册 | ✅ 完成 |
| N12 | `scripts/smoke-test-promo.sh` 部署后烟雾测试 | ✅ 完成 |
| FIX-E | `convertPromoToConsume` 等 4 个余额方法改用 `SELECT … FOR UPDATE` 锁行 + 本地算 newBalance，杜绝 balanceAfter 并发滞后 | ✅ 完成（已在 Git Bash 内 mvn 96/96 回归） |

---

## 二、测试覆盖

最新一次完整跑：**96 / 96 全绿**（`mvn -pl yudao-module-merchant -am test`，FIX-E 后已在 Git Bash 内回归，仍 96/96）。

| 测试类 | 测试数 | 关键场景 |
|---|---|---|
| `PromoConfigServiceImplTest` | 4 | 默认值兜底 / upsert |
| `ProductPromoConfigServiceImplTest` | 6 | 商品级配置 + 批量映射 |
| `ReferralServiceImplTest` | 10 | 终生绑定 + 防自绑 + 防环 + 链路上溯 + maxDepth |
| `PromoPointServiceImplTest` | 10 | 原子入账 / 扣减 / 幂等 / 余额不足 / convert 幂等键 |
| `StarServiceImplTest` | 9 | 升星 / 终生制不降级 / 多级跨升 |
| `PromoQueueServiceImplTest` | 7 | **v6 文档 5.4 节 10 步跑例** + 重放幂等 + 边界 |
| `CommissionServiceImplTest` | 6 | 极差递减 / "5星之上 5星=0%" / 跳过低星 |
| `PromoPoolServiceImplTest` | 6 | 入池累积 + 重放幂等 + ratio=0 / disabled 静默 |
| `PoolSettlementServiceImplTest` | 9 | FULL/LOTTERY × ALL/STAR 4 模式 + 边界 |
| `WithdrawServiceImplTest` | 12 | 门槛 / 余额 / 互斥 / 状态机 / 驳回退还 / 并发 conflict |
| `MerchantPromoOrderHandlerTest` | 7 | 5 步串通 + 多 item commission 总额 + per-item 异常隔离 |
| `PromoPoolSettleJobTest` | 10 | cron 窗口判断（已到期/未到期/历史 NULL/解析失败）+ FULL/LOTTERY/非法兜底 + 多租户失败隔离 |

---

## 三、关键文件清单

### 后端（`yudao-module-merchant`）

**DAL（11 个 DO + 11 个 Mapper）**
- `dal/dataobject/promo/`：`PromoConfigDO`, `ProductPromoConfigDO`, `ShopUserReferralDO`, `ShopUserStarDO`, `ShopQueuePositionDO`, `ShopQueueEventDO`, `ShopPromoRecordDO`, `ShopConsumePointRecordDO`, `ShopPromoPoolDO`, `ShopPromoPoolRoundDO`, `ShopPromoWithdrawDO`
- `dal/mysql/promo/`：对应 11 个 Mapper；其中 `ShopUserStarMapper`、`ShopPromoPoolMapper`、`ShopPromoWithdrawMapper` 含原子 `@Update` 方法

**Service（10 个）**
- `service/promo/`：
  - `PromoConfigService` / `ProductPromoConfigService` — 商户级 / 商品级配置
  - `PromoPointService` — 双积分账本（原子余额变更 + 流水）
  - `ReferralService` — 推荐链（首绑 / 防环 / 上溯）
  - `PromoQueueService` — 直推 / 插队 / 自然推三机制 + A/B 层 + N 满出队
  - `CommissionService` — 团队极差递减
  - `StarService` — 星级评定（终生制 + 原子升星）
  - `PromoPoolService` — 入池累积（幂等 by 标记事件）
  - `PoolSettlementService` — 立即结算（FULL/LOTTERY × ALL/STAR）
  - `WithdrawService` — 提现 4 状态机
- `service/promo/handler/MerchantPromoOrderHandler` — `implements TradeOrderHandler` → `afterPayOrder` 自动串通 5 步引擎

**Controller**
- `controller/admin/promo/`：
  - `PromoConfigController` / `ProductPromoConfigController` — 管理后台 CRUD
  - `AdminWithdrawPromoController` — 提现审批（@PreAuthorize 守门）
- `controller/app/`：
  - `AppMerchantPromoController` — 配置 / 钱包 / 池 / 推荐链（uniapp 通用入口）
  - `AppMerchantWithdrawPromoController` — 用户申请 / 我的列表

### 前端（`yudao-ui/yudao-ui-merchant-uniapp/src`）

**API 客户端**
- `api/promo.js` — 全部端点封装（配置 / 商品 / 钱包 / 推荐链 / 池 / 提现）

**页面**
- 商户端：
  - `pages/me/promo-config.vue`（W1）— 商户级营销配置
  - `pages/me/withdraw-approve.vue`（N4）— 提现审批分 tab + 通过/驳回/标记打款
  - `pages/product/edit.vue`（W1）— 商品营销开关
- 用户端：
  - `pages/user-me/wallet.vue`（N2）— 双积分余额 + tab 切换流水 + 转换入口
  - `pages/user-me/withdraw.vue`（N3）— 申请提现 + 我的申请列表

**部署**
- `sql/mysql/marketing.sql` — 11 张表 DDL
- `sql/mysql/fix_marketing_seed.sql` — 7 个权限菜单（promo-config × 2 / product-promo-config × 2 / promo-withdraw × 2 + 父目录） + 超级管理员授权
- `deploy.sh` — `marketing.sql` 已加入 `SQL_FILES`；`fix_marketing_seed.sql` 走自动 `fix_*.sql` 路径，每次部署幂等执行

---

## 四、API 端点速查

### `/admin-api`（带 `@PreAuthorize`，PC 后台 + 商户 uniapp 都可调）
- `GET / PUT /merchant/promo/config` — 商户级营销配置
- `GET / PUT /merchant/promo/product-config?spuId=` — 商品级
- `GET /merchant/promo/withdraw/page?status=` — 提现申请分页
- `POST /merchant/promo/withdraw/approve|reject|mark-paid?id=&remark=` — 状态机操作

### `/app-api/merchant/mini/promo/`（JWT，无 `@PreAuthorize`）
- `GET / PUT config`、`product-config`
- `GET account` — 当前用户星级 + 双积分余额
- `GET promo-records` / `consume-records` — 流水分页
- `POST convert?promoAmount=&idempotencyKey=` — 推广 → 消费 转换
- `POST referral/bind?inviterUserId=&orderId=` — 首绑上级
- `GET referral/parent` — 查上级
- `GET pool/info` — 当前池余额
- `POST pool/settle?mode=FULL|LOTTERY` — 立即结算（写 round + 池清零 + 发奖）
- `GET pool/rounds` — 历史轮次

### `/app-api/merchant/mini/withdraw/`（JWT）
- `POST apply?amount=` — 用户申请（即时扣减积分）
- `GET my-list` — 我的申请

---

## 五、订单管线（自动触发）

下单支付完成（`TradeOrderUpdateService.updateOrderPaid`）→ 注入的 `tradeOrderHandlers` 列表里包含 `MerchantPromoOrderHandler` → `afterPayOrder`：

```
对每个 OrderItem：
  1. 消费积分入账（CONSUME，按商品配置 ratio）
  2. 直推 / 队列 / 自然推（按商品 tuijianEnabled，调 PromoQueueService）
  4. 入池（商品 + 商户 poolEnabled，按商户 poolRatio）
  5. 星级回算（参与商品份数计入 team_sales_count）

订单总额累计完成后，统一一次：
  3. 团队极差递减（按商户 commissionRates，沿推荐链发）
```

每步都通过 `(userId, sourceType, orderId)` / `(orderId, spuId, beneficiary, eventType)` 等三元 / 四元键做幂等。重放 / 多 SKU 同 SPU 不重复发奖。

---

## 六、暂停时的待办

| ID | 内容 | 影响 |
|---|---|---|
| ~~`convertPromoToConsume` 的 `balanceAfter`~~ | ✅ FIX-E 已修：改为 `SELECT … FOR UPDATE` 锁旧余额 + 原子 UPDATE + 本地 `newBalance = old + delta`；audit 字段严格等于本次写后值 | — |
| Job 启用 | `infra_job` 默认 status=2(STOP)；商户首次部署后需进后台 → 基础设施 → 定时任务，把"积分池自动结算 Job" 改成 NORMAL | 不启用则不会自动跑，但手动触发不受影响 |
| 推荐链 query 参数命名 | shop-home 现支持 `referrerUserId`（兼容旧）；如分享卡片用 `inviter` 需在前端额外读一次 | 当前命名一致即可 |
| 部署联调 | 跑一遍真实订单：A 推 B → B 下单 → 验证 A 拿直推奖 / B 进 B 层 / commission / pool deposit 全链路 | 仅人工验收 |

## 七、deploy 清单（一键发布的核对项）

- ✅ `marketing.sql` 在 `deploy.sh` 的 `SQL_FILES`
- ✅ `fix_marketing_seed.sql` 走 `fix_*.sql` 自动路径
- ✅ 商户 uniapp 配置页（promo-config / 商品营销开关）走 `/m/`
- ✅ 用户 uniapp 钱包 / 提现页都已注册到 `pages.json`
- ✅ 商户审批页 `me/withdraw-approve` 已注册
- ✅ promo-config 页底部已加"立即结算 / 池历史"运营区
- ✅ `infra_job` 已 seed `promoPoolSettleJob`（默认 STOP，商户后台启用即跑）
- ✅ `pnpm build:h5` 通过；`mvn -pl yudao-module-merchant -am compile` 通过
- ✅ 所有 96 单测绿
- ⚠️ 商户租户的 `system_tenant_package.menu_ids` 需要手工把 6100-6106 加进去，否则租户管理员看不到新菜单（fix_marketing_seed.sql 已注释说明，部署后 UI 配置一次即可）
- ⚠️ Quartz Job (id=6200) 默认 STOP；商户首次部署后到"基础设施 → 定时任务"启用，否则不会自动结算

---

## 八、留给下一轮的 prompt

> 项目状态见 `docs/design/promo-engine-progress.md`。营销引擎已开发完成（96/96 测试绿，H5 build 通过，3 轮 code-review 修完——FIX-1..5 / FIX-A..D / FIX-E），下一步是**部署联调**：
>
> 1. 跑 `bash deploy.sh` 把后端 + uniapp 都发到环境
> 2. PC 后台「角色 / 租户套餐」勾上菜单 ID 6100-6106
> 3. 「基础设施 → 定时任务」把 ID=6200 的 promoPoolSettleJob 启用
> 4. 真实订单走一遍：A 分享 → B 落地 shop-home?referrerUserId=A → B 登录 → B 下单 →
>    后端日志应见 referral bound + 引擎 5 步全跑 + A 拿直推奖
> 5. 切到 user-me/wallet 看 A 的双积分余额；切 me/withdraw-approve 走完审批闭环

---

_整理日期：2026-04-28（FIX-E 收尾 + Git Bash 内 mvn 96/96 回归通过）_
