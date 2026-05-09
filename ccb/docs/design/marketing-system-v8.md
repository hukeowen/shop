# 摊小二营销系统 v8 设计文档

最终版本，落地依据。任何代码实现与本文档不一致都视为 bug。

---

## 0. 与 v7 的核心差异

| 维度 | v7 | v8 |
|---|---|---|
| 多件订单 | 一单算 1 步进度 | **每件单独触发**状态机 |
| 抵扣 | 无 | **本单产生积分立即抵扣本单**（按 SPU 独立、整件取整） |
| parent 首贡献奖 | 按 paidAmount × directRate%（订单总额） | **按 1 件价封顶**（不论 child 几件） |
| 团队极差奖 | 商户级配置，星级跟用户走（跨商品共用） | **商品级配置**，星级按 (user, spu) 独立 |
| 升星规则 | 商户级 starUpgradeRules | **商品级**，按 (user, spu) 独立计算 directCount + teamSalesAmount |
| 星级奖池 | 商户级 | **商品级**，按 SPU 独立累池 |

---

## 1. 概念分类

### 1.1 用户分类
- **自然用户**：进店时 `shop_user_referral.parent_user_id` 为空（没人邀请他）
- **被邀请用户**：通过邀请链接进店，有 parent

### 1.2 用户在某商品上的状态（user, spu 维度）
- `shop_queue_position.state ∈ {IN_PROGRESS, COMPLETED}`
- `shop_queue_position.accumulated_count ∈ [0, N]`
- `shop_user_star.current_star_on_spu` ∈ [0, starCount]（按 (user, spu) 独立）

---

## 2. 配置层级

### 2.1 商品级配置（`product_promo_config`，每个 SPU 独立）

| 字段 | 含义 |
|---|---|
| `tuijian_enabled` | 是否启用推 N 反 1 |
| `tuijian_n` | 推 N 反 1 的 N 值 |
| `tuijian_ratios` | 推 N 反 1 各位返奖比例 JSON 数组（共 100%），如 `[30,30,40]` |
| `direct_rate` | 直推/间推奖比例（%）；buyer 完成 N 后自购返；parent COMPLETED 期 1 件价 ×此比例 |
| `star_count` | 星级数量（0 = 不启用团队极差奖） |
| `star_ratios` | 各星级团队极差返奖比例 JSON 数组（%），如 `[1, 2, 3]` |
| `star_upgrade_rules` | 升星规则 JSON：`[{"star":1,"directCount":2,"teamSales":30000}, ...]`（teamSales 单位：分） |
| `pool_ratio` | 星级奖池入池比例（%） |
| `consume_point_ratio` | 每元返多少消费积分 |

### 2.2 商户级配置（`shop_promo_config`）

| 字段 | 含义 |
|---|---|
| `natural_push_enabled` | 自然推开关；ON 时无 parent buyer 触发"队首拿奖"机制 |
| `direct_commission_ratio` | 已**废弃**（v7 遗留），新代码读商品级 `direct_rate` |
| `star_level_count` / `commission_rates` / `star_upgrade_rules` | 已**废弃**（v6/v7 遗留），新代码读商品级配置 |

---

## 3. 奖励规则（4 类奖独立并行 + 抵扣）

### 3.1 推 N 反 1 期（IN_PROGRESS, cumulated < N）

**buyer 自购**（每件循环触发）：
- 第 1 件首单（buyer 在该 spu 之前没有 queue）→ ACTIVATE，cumulated=0，**不返奖**
- 第 2 ~ N+1 件 → cumulated++，每件返 `单件价 × ratios[cumulated_before++]`

**parent 首贡献**（UNIQUE 1 次）：
- 任何用户作 parent（含自然用户）：返 `1 × 单件价 × ratios[parent.cumulated_at_trigger]`
- parent 必须已激活该 spu（buyerPos 存在），否则**完全跳过**（吞奖）
- contribution(parent, child, spu) UNIQUE：每对 (parent, child, spu) 仅触发 1 次

**自然推队首**：
- 触发条件：buyer 无 parent + `naturalPushEnabled = ON`
- 仅在 buyer **首件触发**时（buyer 在该 spu 之前没有 queue）：
  - 找 spu 队首（IN_PROGRESS 中最早的 user）
  - 队首拿 `1 × 单件价 × ratios[head.cumulated]`
  - head.cumulated++（满 N 后 head COMPLETED）

### 3.2 直推/间推期（COMPLETED, cumulated == N）

**buyer 自购**（任何用户都享受，含自然用户）：
- 每件返 `单件价 × directRate%`

**parent 首贡献**（UNIQUE 1 次）：
- 任何用户作 parent（含自然用户）：返 `1 × 单件价 × directRate%`

### 3.3 团队极差奖（每订单 SPU 行触发，独立于推 N 反 1）

**算法**：沿 buyer 上链就近递增
```
lastStar = 0
for p in [buyer.parent → parent.parent → ... 最多 50 层]:
    if p.star_on_spu > lastStar:
        p 拿 starRatios[p.star_on_spu - 1] × paidAmount
        lastStar = p.star_on_spu
    else:
        跳过该 p（继续向上找下一个）
```

- **触发基数**：订单 spu 行 `paidAmount`（**抵扣后实付金额**）
- **无 UNIQUE 限制**：每次订单都触发
- **buyer 自己**也可能拿（如果他自己星级 > 0），按相同规则

### 3.4 星级奖池（按商品独立累池）

- 每订单 spu 行 `paidAmount × poolRatio` 入 `spu_star_pool`
- 池内分发规则：暂不实现（先入池，规则后定）

---

## 4. 抵扣规则（v8 核心新增）

### 4.1 触发时机
- **checkout/submit 阶段**：用户提交订单时立即计算抵扣 + 调整 `trade_order.payPrice`
- 用户付的就是抵扣后金额

### 4.2 算法

```
对订单中每个 SPU 行（spu_id, count, unitPrice）：
    produced = previewProducedForOrder(buyer, spu, unitPrice, count)
    K = floor(produced / unitPrice)        // 抵扣件数
    K = min(K, count)                      // 不超总件数
    deductFen += K × unitPrice

trade.updateOrderPrice(adjustPrice = -deductFen)
```

### 4.3 抵扣对象
- **仅 buyer 自己自购在阶段 3.1/3.2 产生的积分**参与抵扣
- 极差奖、入池金额、parent 首贡献奖**不参与抵扣**（直接进各自接收人账户）

### 4.4 抵扣按 SPU 独立
- spu A 产生的积分**只能**抵扣 spu A 那部分件数
- 多 SPU 订单各算各的

### 4.5 抵扣后实际写入

afterPayOrder 触发时：
- buyer 自购积分**全部**入余额（包括被抵扣的部分）
- 用户少付的金额（K × unitPrice）相当于"用积分换"
- 实际效果：余额净变化 = produced - K × unitPrice（剩余入余额）

---

## 5. 升星规则（按商品独立）

### 5.1 触发时机
- 每次订单 afterPayOrder 时：
  - buyer 在该 spu 上的 directCount / teamSalesAmount 可能变化
  - parent / 上链上级在该 spu 上的 directCount / teamSalesAmount 也可能变化
- 满足任一星级条件 → 立即升星（终生制，永不降）

### 5.2 升星条件
- **directCount**：在该商品上**直接邀请**且**买过该商品**的下级人数
- **teamSalesAmount**：我向下所有层级在该商品上的实付累计（分）

### 5.3 数据存储
- `shop_user_star` 表 `(user_id, spu_id)` 双主键维度
  - `direct_count`：该用户在该商品上的直推下级数
  - `team_sales_count`：该用户团队链路在该商品上的销售件数
  - `team_sales_amount`：该用户团队链路在该商品上的销售实付（分）
  - `current_star`：当前星级
- `(user_id, spu_id=0)` 行专门承载用户全局账户：
  - `promo_point_balance`：推广积分余额（跨商品共享，分）
  - `consume_point_balance`：消费积分余额（跨商品共享，分）

---

## 6. 完整事件流（11 步示例）

### 设定
```
spu1：单价 ¥100，推 3 反 1，ratios=[30%, 30%, 40%]
direct_rate = 10%
star_count = 3，star_ratios = [1%, 2%, 3%]
star_upgrade_rules：1 星(2 推, ¥300)，2 星(3 推, ¥900)，3 星(5 推, ¥2700)
pool_ratio = 1%
naturalPushEnabled = ON

链：A → B → C → D
自然用户：E, F
G parent=H（H 未激活 spu1）
```

### 关键事件

#### T1：A 自然首买 4 件
- A 件 1 ACTIVATE，件 2-4 推进 30+30+40 = 100 → COMPLETED
- A 自购产生 100 → 抵扣 1 件 → 实付 ¥300
- A 余额 +0（产生 100 - 抵扣 100）
- 极差奖：A 无 parent，跳过
- 入池 spu1：¥300 × 1% = ¥3

#### T2：B 首买 4 件（parent=A，A 已 COMPLETED）
- B 自购同 A：实付 ¥300，余额 +0
- A 首贡献（COMPLETED 期）：1 × 100 × 10% = **+10** 到 A 余额
- contribution(A, B, spu1) 写入
- 极差奖：A.star_on_spu1=0 → 跳过
- 入池 +¥3

#### T3、T4：C/D 链式同样流程
- B/C 各拿 +10 首贡献
- 极差奖仍全 0 星 → 跳过

#### T5、T6：E/F 自然用户 + 自然推
- E 首单 → 队列没人 → 没人拿奖
- F 首单 → 找到队首 E（IN_PROGRESS cumulated=0）→ E 拿 1×100×30% = **+30**

#### T7：G 首单（parent=H 未激活）
- H 未激活 → 完全跳过，吞奖

#### T8：A 第 2 单 1 件（A COMPLETED 自然用户）
- A 自购完成期：100 × 10% = 10（< 100 不抵扣，入余额）
- A 余额 += 10

#### T9：B 二单 1 件（UNIQUE 拦给 A）
- B 自购完成期 +10
- A 不再拿（contribution 已存在）

#### T10：D 第 2 单 14 件（COMPLETED 大单）
- D 14 × 100 × 10% = 200 → 抵扣 2 件 → 实付 ¥1200，余额 +0
- C：UNIQUE 已存在 → 跳过
- 入池 ¥12

#### T11：E 第 2 单 5 件（IN_PROGRESS cumulated=1，跨阶段）
- 件 1 IN_PROGRESS cumulated 1→2 返 30
- 件 2 cumulated 2→3=N → COMPLETED 返 40
- 件 3-5 完成期 30
- 合计 100 → 抵 1 件，实付 ¥400，余额 +0

### 极差奖示例（假设 A=3 星，B=2 星，C=1 星，D 买 1 件 paidAmount=¥100）

| 上链 | lastStar | 计算 |
|---|---|---|
| C.star=1 > 0 | 1 | C 拿 1% × 100 = ¥1 |
| B.star=2 > 1 | 2 | B 拿 2% × 100 = ¥2 |
| A.star=3 > 2 | 3 | A 拿 3% × 100 = ¥3 |

### 极差奖反例

**A=1, B=1（同级）**：B 拿 1，A.star=1 ≤ 1 跳过。
**A=2, B=3（上低下高）**：B 拿 3，A.star=2 ≤ 3 跳过。

---

## 7. 最终积分汇总（仅 v8 推 N 反 1 + 直推奖部分；不含极差/池）

| 用户 | 类型 | 余额 | 实付 | 来源 |
|---|---|---|---|---|
| A | 自然 COMPLETED | 20 | ¥400 | T2 拿 B 首贡献 +10 + T8 自购 +10 |
| B | 被邀请 COMPLETED | 20 | ¥400 | T3 拿 C 首贡献 +10 + T9 自购 +10 |
| C | 被邀请 COMPLETED | 10 | ¥300 | T4 拿 D 首贡献 |
| D | 被邀请 COMPLETED | 0 | ¥1500 | 全抵扣 |
| E | 自然 COMPLETED | 30 | ¥500 | T6 队首 +30，T11 抵扣净 0 |
| F | 自然 COMPLETED | 0 | ¥300 | 全抵扣 |
| G | 被邀请 IN_PROGRESS | 0 | ¥100 | parent 未激活吞奖 |

spu1 池累计 ≈ ¥35（暂不发）

---

## 8. 数据库改动汇总（V028）

```sql
-- 商品级配置 4 个新字段
ALTER TABLE product_promo_config
  ADD direct_rate DECIMAL(5,2) DEFAULT 0.00,
  ADD star_count INT NOT NULL DEFAULT 0,
  ADD star_ratios VARCHAR(255),
  ADD star_upgrade_rules TEXT,
  ADD pool_ratio DECIMAL(5,2) DEFAULT 0.00;

-- 用户星级表 加 spu_id 维度（v7 老数据 spu_id=0 兼容）
ALTER TABLE shop_user_star
  ADD spu_id BIGINT NOT NULL DEFAULT 0,
  ADD team_sales_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE shop_user_star DROP INDEX uk_user_id,
  ADD UNIQUE KEY uk_tenant_user_spu (tenant_id, user_id, spu_id, deleted);

-- 新表：商品奖池
CREATE TABLE spu_star_pool (...);

-- 新表：抵扣流水
CREATE TABLE shop_promo_deduction_record (...);
```

---

## 9. 实现路线图（commit 粒度）

| 批次 | 内容 | 状态 |
|---|---|---|
| v8-1 | DB schema + Java DO + Mapper | ✅ `9ce9941` |
| v8-2 | 多件循环 + 抵扣 (preview + handleOrderPaidV8) + checkout 接通 | ✅ `1036645` |
| v8-3 | parent 首贡献按 1 件价封顶（含在 v8-2） | ✅ `1036645` |
| v8-4 | 团队极差奖按商品级 + 沿链就近递增 | ⬜ |
| v8-5 | 升星按 (user, spu) + 入池 | ⬜ |
| v8-6 | 前端：商品级配置 UI + checkout 抵扣展示 | ⬜ |
| v8-7 | 单测重写 + 真实订单 E2E 回归 | ⬜ |

---

## 10. 实现注意事项

### 10.1 余额表 vs 星级表的双重身份
`shop_user_star` 同时承载两种数据：
- `(user_id, spu_id=0)`：用户全局账户（promo_point_balance, consume_point_balance）
- `(user_id, spu_id>0)`：用户在该商品上的星级（current_star, direct_count, team_sales_count, team_sales_amount）

**约束**：所有"余额读写"必须用 `selectByUserId(userId)`（spu_id=0 行）；所有"星级 / 升星"必须用 `selectByUserAndSpu(userId, spuId)`（spu_id>0 行）。

### 10.2 抵扣的"一致性"
checkout 阶段调用 `previewProducedForOrder` 算 K，afterPayOrder 阶段再次调用同样函数算 K，两者结果**必须一致**（因为这期间 buyer queue 状态没变化）。如果 afterPayOrder 时 K 不同，说明有并发或 bug，写日志告警。

### 10.3 contribution UNIQUE
- 用 DB UNIQUE `uk_parent_child_spu (parent_user_id, child_user_id, spu_id, deleted)` 强约束
- 代码层 `contributionMapper.exists(parent, child, spu)` 预先检查
- 双重保险：DB 抛 DuplicateKeyException 也兜底

### 10.4 parent 上溯链的判定
`referralService.getAncestors(userId, 50)` 取 50 层（够深了）；推 N 反 1 仅看**直接 parent**，团队极差奖**沿全链上溯**。
