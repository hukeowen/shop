# 摊小二营销系统 v8 — 最终文档（对照真实代码）

> **本文档与代码一致性日期：2026-05-21**
> 已替代 `marketing-system-v6.md` 与 `marketing-system-v8.md`（早期版）。
> 任何冲突一律以**本文档 + 真实代码**为准；不要再回头看老 doc。
> 本文每一节都标了对应代码文件，便于反查。

---

## 1. 系统总览

四类奖励 + 一类抵扣 + 一类提现，全部按 (user, spu) / (user, tenant) 维度独立，per-tenant 终生绑定上下级。

```
落地分享码 → 登录后 flush per-tenant 绑定 → 下单时立即抵扣 +
  支付完成回调 afterPayOrder：
    1. 消费积分入账        (PromoPointService.addConsumePoint)
    2. 推 N 反 1 状态机     (PromoQueueService.handleOrderPaidV8)
         包含：buyer 自购 + parent 首贡献 + 自然推队首 + 本单立即抵扣
    3. 团队极差奖          (CommissionService.handleOrderPaidV8)
    4. 星级奖池入池        (PromoPoolService.depositIfEnabledV8)
    5. 升星                (StarService.handleOrderPaidV8)
```

商户后台手动触发**奖池结算**（按 SPU 独立），把池余额按规则发给目标星级用户。
用户可申请**推广积分提现**（门槛过审 → PENDING → APPROVED → PAID）。

---

## 2. 关键数据表

| 表 | 维度 | 关键字段 / 索引 |
|---|---|---|
| `shop_user_referral` | (tenant, user) | UNIQUE(tenant, user)；parent_user_id, bound_at |
| `shop_queue_position` | (tenant, user, spu) | state ∈ {IN_PROGRESS, COMPLETED}; accumulated_count |
| `shop_queue_event` | 流水 | event_type (ACTIVATE/SELF_PROGRESS/REFERRAL_*/QUEUE/EXIT/POOL_DEPOSIT_V8) |
| `shop_referral_contribution` | (parent, child, spu) | UNIQUE 强约束 — 每对每 spu 首贡献仅触发一次 |
| `shop_user_star` | (tenant, user, spu) | 双身份：spu_id=0 行存全局余额；spu_id>0 行存 (user, spu) 星级 |
| `product_promo_config` | (tenant, spu) | 商品级营销规则（v8 核心） |
| `shop_promo_config` | (tenant) 单条 | 商户级开关 + v6/v7 兜底字段 |
| `spu_star_pool` | (tenant, spu) | 池余额 pool_balance + total_in + total_out |
| `spu_star_pool_settle_record` | 历次结算 | poolBefore/After, distributed, rules_snapshot |
| `spu_star_pool_payout_item` | 中奖明细 | (settle_id, user_id, star, mode, amount) |
| `shop_promo_record` | 推广积分流水 | UNIQUE(user, source_type, source_id) 幂等键 |
| `shop_consume_point_record` | 消费积分流水 | UNIQUE(user, source_type, source_id) |
| `shop_promo_deduction_record` | 抵扣审计 | UNIQUE(order, user, spu) — 兼任 v8 状态机幂等键 |
| `shop_promo_withdraw` | 提现申请 | status ∈ {PENDING, APPROVED, REJECTED, PAID} |
| `member_shop_rel` | (tenant, user) | referrer_user_id（前端"会员推荐关系"展示） |

**ShopUserStarDO 双身份要点**：所有"余额读写"用 `(user, spu_id=0)` 行；所有"星级 / 升星"用 `(user, spu_id>0)` 行。MyBatis 接口分别用 `selectByUserId(userId)` 和 `selectByUserAndSpu(userId, spuId)`。

---

## 3. 配置层级

### 3.1 商品级 `product_promo_config`（v8 核心）— 每 SPU 独立

代码：`ProductPromoConfigDO.java`

| 字段 | 含义 |
|---|---|
| `consume_point_ratio` | 消费积分倍率（每元返多少积分，单位为分） |
| `tuijian_enabled` | 是否启用推 N 反 1 |
| `tuijian_n` | N 值 |
| `tuijian_ratios` | N 个比例 JSON：`[30,30,40]`（共 100%） |
| `direct_rate` | 直推/间推奖比例 (%)；COMPLETED 期 buyer 自购 + parent 首贡献用 |
| `star_count` | 星级数量（0 = 关闭团队极差） |
| `star_ratios` | 各星极差比例 JSON：`[1,2,3]` |
| `star_upgrade_rules` | 升星规则 JSON（详见 §7.5） |
| `pool_ratio` | 入池比例 (%) |
| `pool_dist_rules` | 池分发规则 JSON（详见 §7.4） |
| `pool_enabled` | v6 兼容字段；v8 用 poolRatio>0 判定 |

### 3.2 商户级 `shop_promo_config`（每商户单条，多数为兜底）

代码：`PromoConfigServiceImpl.java`

| 字段 | 含义 | 状态 |
|---|---|---|
| `natural_push_enabled` | 自然推开关（ON = 无 parent buyer 触发"队首拿奖"） | **v8 仍在用** |
| `direct_commission_ratio` | direct_rate 的商户级 fallback（商品级缺省时回落） | 兜底 |
| `point_conversion_ratio` | 推广积分 → 消费积分 转换比例 | **在用**（§9.3） |
| `withdraw_threshold` | 提现最低门槛（分） | **在用**（§9.4） |
| `consume_point_redeem_ratio` | 消费积分抵扣换算比 (1 积分 = N 分钱) | **在用**（§8） |
| `commission_rates / star_upgrade_rules` | v6/v7 商户级极差与升星 | **已废弃**（v8 用商品级） |
| `pool_*` | v6/v7 商户级池字段 | **已废弃**（v8 用 spu_star_pool） |

---

## 4. 推荐链 per-tenant 终生绑定

代码：`ReferralServiceImpl.bindParent`

### 4.1 触发入口
**唯一**：分享链接 `https://ke.doupaidoudian.com/m/shop-home?tenantId=171&inviter=99017`

### 4.2 前端落地（`src/App.vue` + `src/utils/referral.js`）

```
captureLandingInviter()
  ↓ 写 localStorage { inviter, tenantId, ts }  (key=promo:pendingBind)
captureRedirect() 识别 ?tenantId= → 返 'shop-share'
  ↓ location.hash = '#/pages/shop-home/index?tenantId=&inviter='
  ↓ uni.reLaunch (同步抢路由，防默认首页竞争)
shop-home / login 后任意时机:
  flushPendingReferrer(userId, tenantId?)
    ↓ POST /referral/bind?inviterUserId=X  header tenant-id=171
```

### 4.3 后端绑定判定（6 条必须全过）

```java
1. userId != parentUserId                       // 防自绑
2. TenantContextHolder.tenantId > 0             // tenant 上下文必须明确
3. shop_user_referral(user, tenant) 不存在       // per-tenant 终生：绑过就锁
4. shop_queue_position(parent, current_tenant) 存在
                                                // parent 资格：必须在该店买过推 N 反 1 商品
5. member_shop_rel(user, tenant) 不存在
   或 referrer == parent                        // 首次进店：用户从未访问/下单过该店才算拉新
                                                // 已自然访问过（referrer=null）→ 拒（按用户原话）
6. !hasAncestor(parent → ... 50 层, user)        // 防环
```

成功 → `INSERT shop_user_referral` + 若 rel 不存在 `getOrCreateWithReferrer(user, tenant, parent)`

### 4.4 边界

| 场景 | 结果 |
|---|---|
| 新用户首次点分享码 → 注册 → 进店 → 触发 flush | parent 激活则绑，未激活吞 |
| 老用户从未进过该店 → 点分享码 → 触发 flush | 同上 |
| 老用户该店已绑过 parent | 跳过（per-tenant 终生不可改） |
| **老用户已自然进店（无 parent）→ 点别人分享码** | **拒绝**（"已不算拉新"，符合用户原话）|
| 老用户已绑同一 parent | 幂等成功 |
| parent 在该店没买过推 N 反 1 商品 | 拒绝吞奖 |
| 同一用户在不同店 | 互不影响，可各有不同 parent |

---

## 5. 订单 afterPayOrder 全流程

代码：`MerchantPromoOrderHandler.afterPayOrder`

```java
for (TradeOrderItemDO item : orderItems) {
    Long spuId = item.getSpuId();
    int qty = item.getCount();
    long paidAmount = item.getPayPrice();   // 抵扣后实付（分）
    int unitPrice = item.getPrice();        // 商品定价（分），不变

    ProductPromoConfigDO config = productPromoConfigService.getBySpuId(spuId);

    // 1. 消费积分入账
    long consumePoints = (paidAmount / 100) × config.consumePointRatio  (RoundingMode.DOWN)
    promoPointService.addConsumePoint(buyer, consumePoints, "CONSUME", orderId, ...);

    // 2. 推 N 反 1 状态机 + parent 首贡献 + 自然推 + 本单立即抵扣
    if (tuijianEnabled) {
        long produced = promoQueueService.previewProducedForOrder(config, buyer, spu, unitPrice, qty);
        int k = min(produced / unitPrice, qty);
        promoQueueService.handleOrderPaidV8(config, buyer, spu, unitPrice, qty, k, orderId);
    }

    // 3. 团队极差奖
    commissionService.handleOrderPaidV8(config, buyer, spu, paidAmount, orderId);

    // 4. 入池
    promoPoolService.depositIfEnabledV8(config, spu, paidAmount, orderId);

    // 5. 升星
    if (tuijianEnabled) {
        starService.handleOrderPaidV8(config, buyer, spu, qty, paidAmount);
    }
}
```

幂等：每步内部按 `(user, source_type, source_id)` 三元组防重；状态机额外用 `shop_promo_deduction_record(order, user, spu)` 兜底。

> **同时**：handler 还会自动 `getOrCreateWithReferrer(buyer, tenant, parentFromReferralChain)` 帮 buyer 自动入店（建 member_shop_rel），并 `shopInfoMapper.incrementSales30d(tenant, qty)` 累计销量。

---

## 6. 奖励详解

### 6.1 推 N 反 1 状态机 — `PromoQueueServiceImpl.handleOrderPaidV8`

**buyer 自购（applyBuyerLoopV8 按件循环）**

| 状态 | 件 | 行为 |
|---|---|---|
| 首单首件 | 1 | ACTIVATE → state=IN_PROGRESS, cumulated=0，**不返奖** |
| IN_PROGRESS 第 2 ~ N+1 件 | 每件 | 返 `unitPrice × ratios[cumulated_before]`，cumulated++ |
| 到 N | — | state → COMPLETED 永久终态，写 EXIT 事件 |
| COMPLETED 期每件 | — | 返 `unitPrice × directRate%` |

**精度修复**：`previewProducedForOrder` 累加 ratio 后**最后一次** round_down。早期每件 round_down 累积丢余数（实测：unitPrice=10、ratios=[25,25,25,25]，4 件 round_down 后只剩 8 分而非 10 分，导致"推 4 反 1 要买 7 件才抵 1 件"）。

**parent 首贡献（handleParentRewardV8，UNIQUE per (parent, child, spu)）**

- parent.shop_queue_position 不存在 → **完全跳过吞奖**（不上溯）
- parent COMPLETED：返 `unitPrice × directRate%` （v8 区别 v7：**1 件价封顶**，不论 child 几件）
- parent IN_PROGRESS：返 `unitPrice × ratios[parent.cumulated]`，parent.cumulated++
- 写 `shop_referral_contribution` UNIQUE 强约束兜底

**自然推队首（handleNaturalPushV8）**

- 触发条件：buyer 无 parent + `naturalPushEnabled = ON` + buyer **首件**（queueMapper.selectByUserAndSpu 之前不存在）
- 队首 = `selectQueueHead(spuId)` = FIFO 最早入队的 IN_PROGRESS 用户（用户确认："之前设计是对的"）
- head 拿 `unitPrice × ratios[head.cumulated]`，head.cumulated++（满 N 后 COMPLETED）

**积分入账（buyer 自购）**

```java
// SELF_BATCH 一次入全额 produced（流水透明）
promoPointService.addPromoPoint(buyer, produced, "SELF_BATCH", orderId, ...);

// ORDER_DEDUCT 反向扣回本单已抵扣部分（避免双倍）
long autoDeduct = min(deductCount × unitPrice, produced);
promoPointService.deductPromoPoint(buyer, autoDeduct, "ORDER_DEDUCT", orderId, ...);
```

净到账 = produced - deductCount × unitPrice（若全抵则 0）。

### 6.2 团队极差奖 — `CommissionServiceImpl.handleOrderPaidV8`

```java
lastStar = 0

// 步 1: buyer 自己
if buyerStar in [1, ratios.size()]:
    buyer 拿 paidAmount × ratios[buyerStar - 1] / 100   (round down)
    lastStar = buyerStar

// 步 2: 沿推荐链上溯 50 层
for ancestorId in referralService.getAncestors(buyer, 50):
    s = ancestor.star_on_spu
    if s in [1, ratios.size()] && s > lastStar:        // 严格大于才拿
        ancestor 拿 paidAmount × ratios[s - 1] / 100    // 按自己星级取整额，非差额
        lastStar = s
```

- 基数：item.payPrice = 抵扣后实付
- 无 UNIQUE，每订单触发
- 入账 sourceType = "COMMISSION"，sourceId = orderId

**示例**：A=3, B=2, C=1，D 买 1 件 paid=¥100
- D.star=0 → 跳
- C.star=1>0 → +1%×100=¥1, lastStar=1
- B.star=2>1 → +2%×100=¥2, lastStar=2
- A.star=3>2 → +3%×100=¥3, lastStar=3

**反例**：A=1, B=1 → buyer B 自己拿 ¥1, lastStar=1；A.star=1≤1 跳。
A=2, B=3 → buyer B 拿 ¥3, lastStar=3；A.star=2≤3 跳。

### 6.3 星级奖池

**入池 — `PromoPoolServiceImpl.depositIfEnabledV8`**

```java
deposit = paidAmount × poolRatio / 100  (round down)
spu_star_pool: pool_balance += deposit, total_in += deposit
写 shop_queue_event(type=POOL_DEPOSIT_V8) 做幂等
```

幂等键：`(order, spu, SYSTEM_BENEFICIARY=-1, POOL_DEPOSIT_V8)`

**结算 — `SpuPoolSettleServiceImpl.settle(spuId, remark)`**

商户后台手动触发（admin controller `SpuPoolSettleController` / API `/admin-api/merchant/spu-pool/settle`）。

```java
@Transactional
1. 读 ProductPromoConfig.poolDistRules JSON
   [{"star":N, "ratio":pct, "mode":"EQUAL|LOTTERY", "winners":?}]
2. SELECT ... FOR UPDATE 锁池行
3. 预插 spu_star_pool_settle_record（拿 settleId）
4. for rule in rules:
       allocation = poolBefore × ratio / 100
       candidates = shop_user_star WHERE spu_id=? AND current_star=star
       if candidates 空 → 整段留池
       winners = (mode=EQUAL ? candidates : LOTTERY shuffle.take(winnersCount))
       perUser = allocation / winners.size()       // 整除零头留池
       for winner:
           addPromoPoint(winner, perUser, "POOL_V8", settleId, ...)
           插 spu_star_pool_payout_item
       remainder -= perUser × winners.size()
5. spu_star_pool: pool_balance -= distributed, total_out += distributed
6. 回填 settle_record (after / distributed / seed)
```

- 残值（无人段 / 整除零头）留池给下次
- 幂等：同 settleId 二次入 add 因三元组冲突直接返 false

### 6.4 升星 — `StarServiceImpl.handleOrderPaidV8`

```java
bumpTeamSalesV8(buyer, spu, qty, paidAmount)
for ancestor in getAncestors(buyer, 50):
    bumpTeamSalesV8(ancestor, spu, qty, paidAmount)

// bumpTeamSalesV8 内：
getOrCreateBySpu(user, spu)
addTeamSalesBySpu(user, spu, qty, paidAmount)
attemptUpgradeV8(user, spu, rules)
```

**attemptUpgradeV8 链式判定**

rule JSON：`[{"star":1, "directCount":2, "requiredCount":2, "requiredStar":0, "teamSales":30000}, ...]`

```java
target = 当前星级
while (target < rules.size()) {
    r = rules[target]
    needStar = r.requiredStar > 0 ? r.requiredStar : target  // 默认 = 目标星 - 1
    needCount = r.requiredCount ?? r.directCount

    histo = countDirectChildrenByStar(user, spu)  // 实时拉所有直推下级 + 查每个在该 spu 的星级
    matched = histo[needStar..].sum()             // ≥ needStar 星的直推下级数

    if matched >= needCount && teamSalesAmount >= r.teamSales:
        target++
    else break
}
upgradeStarIfHigherBySpu(user, spu, target)  // conditional UPDATE，只升不降
```

- **链式语义**：升 1 星 = 任意付费下级 ≥ X 个；升 2 星 = 1 星下级 ≥ X 个；升 3 星 = 2 星下级 ≥ X 个…
- **direct_count 字段不影响升星**（保留兼容，但 attemptUpgradeV8 走实时 histo）
- 升星是 conditional UPDATE，并发安全

> **⚠️ 已知 gap**：`StarServiceImpl.handleReferralBoundV8` 写了但 `ReferralServiceImpl.bindParent` **没调用**。当前 direct_count_per_spu 字段不会因绑定自动 +1。但因为升星走实时 histo 算法（不依赖 direct_count 字段），实际功能不受影响 — bind 发生后，等 child 在该 spu 下了首单时，handleOrderPaidV8 会经 bumpTeamSalesV8 创建 child 的 (child, spu) 行，下次 attemptUpgradeV8 实时算 histo 就能看到 child（哪怕 0 星也会被统计进 needStar=0）。

---

## 7. 本单立即抵扣（v8 核心）

代码：checkout `AppMerchantCheckoutController.submit` + handler `PromoQueueServiceImpl.handleOrderPaidV8`

### 7.1 checkout 抵扣顺序（每步保留 ≥ 1 分）

```
1. 店铺余额          balanceFen → deductBalanceForOrder → updateOrderPrice(-balanceFen)
2. 消费积分          consumePointDeductFen → deductConsumePoint("REDEEM", orderId)
                     按 ratio 反推积分数：points = ceil(deductFen / consumePointRedeemRatio)
3. 推广积分          promoPointDeductFen → deductPromoPoint("REDEEM_ORDER", orderId)
4. v8 推 N 反 1     按每个 SPU 行 previewProducedForOrder → K=floor(produced/unitPrice)
                     totalDeductFen = Σ K × unitPrice
5. 优惠券          minAmount 校验 → markUsedAtomic → updateOrderPrice
```

每步用 `tradeOrderUpdateService.updateOrderPrice(adjustPrice=-deductFen)` 同步改 trade_order + trade_order_item + pay_order，保证 item.payPrice 反映抵扣后实付。

### 7.2 全额抵扣免支付路径

若上面 5 步走完 `finalPayPrice ≤ 1`：
- 直接 `tradeOrderMapper.updateById(payStatus=true, status=30, payTime=now)`
- 发 `OrderOfflineConfirmedEvent` → `OrderPaidListener` 异步跑 `merchantPromoOrderHandler.afterPayOrder`（营销副作用）
- 跳过通联收银台
- 不跑 trade 内置 handlers（库存、积分、等级），防 NPE 污染主事务

### 7.3 v8 抵扣审计

每条 SPU 行写一条 `shop_promo_deduction_record(order, item, user, spu, unitPrice, totalCount, produced, deductCount, actualPaid)`。**同时兼任** handleOrderPaidV8 的幂等键 — `existsByOrderUserSpu(orderId, user, spu)` 命中则整段跳过。

---

## 8. 双积分账本

代码：`PromoPointServiceImpl`

### 8.1 存储
- 都存在 `shop_user_star (user, spu_id=0)` 行：`promo_point_balance` + `consume_point_balance`
- 跨商品共享，per-tenant

### 8.2 写入路径

| 来源 | sourceType | 推广 | 消费 |
|---|---|:---:|:---:|
| 下单返消费积分 | CONSUME | | + |
| 推 N 反 1 buyer 自购 | SELF_BATCH | + | |
| 推 N 反 1 本单抵扣反扣 | ORDER_DEDUCT | − | |
| parent 首贡献 IN_PROGRESS | REFERRAL_PROGRESS | + | |
| parent 首贡献 COMPLETED | REFERRAL_COMMISSION | + | |
| 自然推队首 | QUEUE | + | |
| 团队极差奖 | COMMISSION | + | |
| 奖池结算 | POOL_V8 | + | |
| 下单消费积分抵扣 | REDEEM | | − |
| 下单推广积分抵扣 | REDEEM_ORDER | − | |
| 推→消转换 | CONVERT | − | + |
| 提现申请 | WITHDRAW | − | |
| 提现驳回退还 | WITHDRAW_REFUND | + | |

每条流水 `balance_after = oldBalance + delta`（本地计算，SELECT FOR UPDATE 锁行 + UPDATE col+=delta 原子）。

幂等：流水插入前查 `(user, source_type, source_id)` 三元组，已存在返 false 安全跳过。

### 8.3 转换（推→消）

`PromoPointServiceImpl.convertPromoToConsume(userId, promoAmount, idempotencyKey)`
- 必传 idempotencyKey（CONVERT 两侧共用）
- `consumeAmount = promoAmount × PromoConfig.pointConversionRatio` (round down)
- deductPromoPoint("CONVERT", key) + addConsumePoint("CONVERT", key)

### 8.4 提现 — `WithdrawServiceImpl`

```
apply(userId, amount):
  门槛: amount >= PromoConfig.withdrawThreshold
  互斥: user 没有 PENDING / APPROVED 的活跃申请
  余额: promoPointBalance >= amount
  INSERT shop_promo_withdraw status=PENDING
  deductPromoPoint("WITHDRAW", recordId)  ← 即时扣减防消费

approve(applyId, processor):  transitionStatus PENDING → APPROVED  (CAS)
reject(applyId, processor):   transitionStatus PENDING → REJECTED
                              addPromoPoint("WITHDRAW_REFUND", applyId)  退还
markPaid(applyId, processor): transitionStatus APPROVED → PAID
```

所有状态机跳转用 conditional UPDATE，rows!=1 报"非法跳转"。

---

## 9. 完整事件演练（hand-trace 已对账）

### 设定（同老 doc 第九节）
```
spu1: 单价 ¥100, 推 3 反 1, ratios=[30,30,40], direct_rate=10%
star_count=3, star_ratios=[1,2,3]
star_upgrade_rules: [{1星, 2推, ¥300}, {2星, 3推, ¥900}, {3星, 5推, ¥2700}]
pool_ratio=1%, naturalPushEnabled=ON

链：A → B → C → D
自然：E, F
G parent=H（H 未激活）
```

### 11 步全奖核算

| 事件 | 推 N 反 1 + 抵扣 | 首贡献 | 极差 | 入池 | 升星累计 |
|---|---|---|---|---|---|
| T1 A 自然 4 件 | A 实付¥300 余0 | — | A.star=0 → 0 | ¥3 | A.team+=¥300 |
| T2 B 4 件 p=A | B 实付¥300 余0 | A +¥10 | 全 0 → 0 | ¥3 | A.team=¥600, B.team=¥300 |
| T3 C 4 件 p=B | C 实付¥300 余0 | B +¥10 | 0 | ¥3 | C/B/A 各 +¥300 |
| T4 D 4 件 p=C | D 实付¥300 余0 | C +¥10 | 0 | ¥3 | D/C/B/A 各 +¥300 |
| T5 E 自然 1 件 | E ACTIVATE 实付¥100 余0 | — | 0 | ¥1 | E.team+=¥100 |
| T6 F 自然 1 件 | F ACTIVATE 实付¥100 余0；**自然推→ E +¥30** | — | 0 | ¥1 | F.team+=¥100 |
| T7 G 1 件 p=H 未激活 | G 实付¥100 余0 | H 吞奖 | 0 | ¥1 | G/H 各 +¥100 |
| T8 A 2 单 1 件 COMPLETED | A 余 +¥10（不抵） | — | 0 | ¥1 | A.team+=¥100 |
| T9 B 2 单 1 件 | B 余 +¥10；A UNIQUE 跳 | — | 0 | ¥1 | A/B 各 +¥100 |
| T10 D 2 单 14 件 | D 抵 2 件实付¥1200 余0 | UNIQUE 跳 | 0 | ¥12 | D/C/B/A 各 +¥1200 |
| T11 E 2 单 5 件跨阶段 | 件1¥30 件2¥40 件3-5¥30 合¥100 抵 1 实付¥400 余0 | — | 0 | ¥4 | E.team+=¥400 |

**池累计 = ¥33**（老 doc 写 ≈¥35 是估算，本数字以代码 round_down 算法为准）

**全程无人升星**：
- A teamSales 累 ¥2300 ≥ ¥300，但 direct = 1 < 2 → 0 星
- 11 步演练里 A 只有 B 一个直推，永远凑不齐 2 推

**全程无极差奖**（因没人升星）

### 极差验证（独立 hypothetical）
A=3, B=2, C=1，D 买 1 件 paid=¥100：
- buyer D star=0 → 跳；lastStar=0
- C(1)>0 → C +1%×100=¥1; lastStar=1
- B(2)>1 → B +2%×100=¥2; lastStar=2
- A(3)>2 → A +3%×100=¥3; lastStar=3

✅ 代码与设计一致

---

## 10. 前端入口

| 页面 | 路径 | 功能 |
|---|---|---|
| 店铺主页 | `/pages/shop-home/index?tenantId=&inviter=` | 分享落地、商品列表 |
| 商品详情 | `/pages/product/detail` | 加购、营销说明 |
| 结算页 | `/pages/checkout/index` | 三态抵扣（余额+消费+推广），可见 v8 自动抵扣 K 件 |
| 支付完成 | `/pages/order/pay-done` | 微信点金计划 |
| 我的钱包 | `/pages/user-me/wallet` | 双积分余额 |
| 推广流水 | `/pages/user-me/promo-records` | A/B/C/D 积分到账 |
| 消费流水 | `/pages/user-me/consume-records` | 消费积分变动 |
| 我的星级 | `/pages/user-me/star` | 已满足/未满足分组 + 各店星级列表 |
| 店铺星级 | `/pages/user-me/star-shop?tenantId=` | 该店每个 SPU 的升星规则 + 权益 + 分享码 |
| 分享码 | `/pages/me/qrcode` | 生成带 inviter+tenantId 的 H5 链接 + QR |
| 提现 | `/pages/user-me/withdraw` | 推广积分提现申请 |

**显示规范**：所有积分 UI 一律按 `(fen/100).toFixed(2) + " 积分"` 展示（"1 积分 = 1 元"），不能混用 ¥。

---

## 11. 后端 API（C 端）

代码：`AppMerchantPromoController`, `AppMerchantCheckoutController`

| 路径 | 方法 | 功能 |
|---|---|---|
| `/merchant/mini/checkout/submit` | POST | 结算（5 步抵扣 + 创建订单 + 通联支付） |
| `/merchant/mini/checkout/cashier-link` | POST | 立即付款（重拿通联链接） |
| `/merchant/mini/promo/config` | GET / PUT | 商户营销配置 |
| `/merchant/mini/promo/product-config` | GET / PUT | 商品营销配置（按 spuId） |
| `/merchant/mini/promo/account` | GET | 当前用户星级 + 双积分余额（跨租户聚合） |
| `/merchant/mini/promo/promo-records` | GET | 推广积分流水 |
| `/merchant/mini/promo/consume-records` | GET | 消费积分流水 |
| `/merchant/mini/promo/convert` | POST | 推→消转换 |
| `/merchant/mini/promo/my-queues` | GET | 当前用户在所有商品队列中的位置 |
| `/merchant/mini/promo/my-spu-stars` | GET | 用户在指定店所有 SPU 的星级 |
| `/merchant/mini/promo/referral/bind` | POST | 绑定 parent（per-tenant） |
| `/merchant/mini/promo/referral/parent` | GET | 当前用户的直接 parent |
| `/merchant/mini/promo/pool/info` | GET | 商户级池 v7 兼容（已废弃） |
| `/merchant/mini/promo/spu-pool/balance` | GET | SPU 池余额 + 历史 in/out（商户） |
| `/merchant/promo/pool/balance` | GET | SPU 池余额（C 端公示，免登录） |
| `/merchant/promo/pool/latest-payouts` | GET | 最近一次结算名单（脱敏） |
| `/merchant/promo/pool/settle-records` | GET | 历次结算列表（C 端） |
| `/merchant/mini/withdraw/apply` | POST | 提现申请 |
| `/merchant/mini/withdraw/my-list` | GET | 我的提现申请 |
| `/merchant/mini/withdraw/page` | GET | 商户审批端分页 |
| `/merchant/mini/withdraw/approve|reject|mark-paid` | POST | 商户审批动作 |
| `/merchant/mini/invite-share/lookup` | GET | 分享码 → tenantId+inviterUserId 查询（PermitAll + TenantIgnore） |

---

## 12. 已知问题 / TODO

| 项 | 状态 | 说明 |
|---|---|---|
| `StarServiceImpl.handleReferralBoundV8` 写了未调 | 🟡 不影响功能 | 升星走实时 histo，不依赖 direct_count 字段 |
| 真实订单 E2E 回归 | ⬜ 待做 | 手 trace 已验，缺自动化测试覆盖 4 类奖 |
| 池结算「无人段」分发策略 | 🟡 当前留池 | 文档未明，按代码就是无候选直接 skip |
| `direct_count` 字段语义 | 🟡 兼容字段 | v6 用，v8 不用；保留避免改 schema |
| 旧 v7 接口 `handleOrderPaid` | 🟡 死代码 | 仍存在但 MerchantPromoOrderHandler 只走 V8 入口 |

---

## 13. 校验 checklist（改完代码必跑）

- [ ] `mvn -pl yudao-module-merchant test -Dtest=PromoQueueServiceImplTest` 单测过
- [ ] `mvn -pl yudao-module-merchant test -Dtest=SpuPoolSettleServiceImplTest` 单测过
- [ ] 真实订单：拉新 → 绑定 → 下单 → 验证 4 类奖 + 抵扣 + 升星轨迹
- [ ] checkout submit 看 5 步抵扣 resp 字段都填了（balanceDeductFen / consumePointDeductFen / promoPointRedeemFen / promoDeductFen / couponDeductFen / payPrice）
- [ ] 微信浏览器拉起支付看到的是微信支付（不是 Apple Pay）— UA 透传校验
- [ ] 全额抵扣场景免支付：trade_order.payStatus=true, status=30, 跳过通联
- [ ] 绑定 per-tenant：同用户两个店分别绑不同 parent 互不影响
- [ ] 绑定终生：第二次绑定同店不同 parent 应被拒
