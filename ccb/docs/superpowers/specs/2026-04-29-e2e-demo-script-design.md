# 摊小二 e2e 演示剧本设计（甲方现场版）

> 版本：v1 · 日期：2026-04-29
> 演示日：2026-05-01（后天）
> 验收对象：甲方
> 整理：brainstorming + 4 轮 agent 核查 + 31 commit 经手作者

---

## 0. 一图看懂

```
搭台子(15-25min)→ 商户开通 → 商品发布 + AI 视频生成(后台跑) →
  甲方手机扫码进店 → 浏览 → 下单付款 → ★ 营销引擎闭环 → 规则讲解收尾
```

剧本骨架 2（双角色双手机，甲方亲手扫码 + 下单 → 看自己动作触发商户那边数字跳动）。

---

## 1. 演示前 30 分钟（你自己做完）

### 1.1 ECS 准备
- 干净 CentOS 7.9 阿里云 ECS（4G+ 内存够用，admin-vue3 已瘦身 60% 不会 OOM）
- 阿里云控制台 → 安全组放行 80 / 443
- 域名 `www.doupaidoudian.com` A 记录指向 ECS 公网 IP
- root SSH 已可登

### 1.2 仓库 + .env
```bash
ssh root@<ECS_IP>
cd ~
wget https://raw.githubusercontent.com/hukeowen/shop/main/ccb/deploy.sh
wget https://raw.githubusercontent.com/hukeowen/shop/main/ccb/.env.example
chmod +x deploy.sh
cp .env.example .env
vim .env
```

填入：
```
MYSQL_ROOT_PASS=Demo1234!
DB_PASS=Demo1234!
REDIS_PASS=Demo1234!
MERCHANT_INTERNAL_TOKEN=tanxiaoer-internal-token-2026
ARK_API_KEY=<火山方舟 ApiKey，控制台 console.volcengine.com/ark>
VOLCANO_ACCESS_TOKEN=<同上 ApiKey；新版统一鉴权>
TTS_ACCESS_TOKEN=<同上>
VOLCANO_APP_ID=<火山豆包语音 AppID>
JIMENG_AK=<火山 IAM AccessKey>
JIMENG_SK=<火山 IAM SecretKey>
TOS_AK=<同 JIMENG_AK 复用一对火山 IAM AK/SK>
TOS_SK=<同 JIMENG_SK>
SERVER_NAME=www.doupaidoudian.com
GITHUB_PROXY=https://gh-proxy.com/
```

### 1.3 火山控制台权限自检
应用 AppID `1907208238` 必须勾三权限：
- 火山方舟 → **豆包 LLM**（文案生成）
- 火山方舟 → **Seedance**（图生视频）
- 语音技术 → **语音合成大模型 V3**（TTS）

任一缺则当天演示卡，控制台 30s 加权限即可。

### 1.4 备份 fallback 视频
ECS 跑 deploy 之前，**自己本机预录一份完整 AI 视频成片**（90s mp4），上传到 OSS 公开链接 `https://demo.doupaidoudian.com/fallback.mp4`。万一现场 Seedance 抽风超时，直接换链接演示。

### 1.5 设备就位
- **笔记本投影**到大屏：4 tab 浏览器
  - tab 1：`http://<ECS_IP>/admin/`（PC 后台）
  - tab 2：`http://<ECS_IP>/m/`（你手机扮商户用，桌面端先开着不动）
  - tab 3：投屏二维码（gen 后展示给甲方扫）
  - tab 4：Swagger `http://<ECS_IP>:48080/doc.html`（应急查 API）
  - 终端 1 个：`tail -f /opt/tanxiaer/logs/stdout.log`（出问题快速定位）
- **你手机**：扮商户 A
- **甲方手机**：扮用户 B（提前和甲方 align，"待会儿请你扫码"）
- 两手机连**任意 4G / WiFi**（域名走公网，无需同网段）

### 1.6 PC 后台 mock 支付（部署完后 1 分钟做）
admin / admin123 登录 → 支付管理：
1. 应用信息 → 新增「demo」应用，回调地址全填 `http://localhost:48080/admin-api/pay/notify/order/${channelId}`
2. 该应用 → 渠道 → 新增「**模拟支付**」状态开启 配置 `{}`
3. 交易中心 → 配置 → 默认 payAppId 选 demo

后续甲方下单选「模拟支付」→ 自动成功 → 触发 `MerchantPromoOrderHandler.afterPayOrder` → 5 步营销引擎全跑。

### 1.7 演示前 1 分钟自检（curl 一行命令搞定）
```bash
curl -s http://<ECS_IP>/admin-api/system/auth/captcha | head -c 80     # 后端活
curl -sI http://<ECS_IP>/m/ | head -3                                  # H5 OK
curl -sI http://<ECS_IP>/admin/ | head -3                              # PC OK
curl -sf http://127.0.0.1:8081/healthz                                 # sidecar OK
mysql -uroot -p"$MYSQL_ROOT_PASS" -D ruoyi-vue-pro -e \
  "SELECT code,enabled FROM merchant_invite_code WHERE code='DEMO20260428';"
```
4 个 200 + 1 行邀请码 = 全绿，开始。

---

## 2. 演示剧本（六幕）

### 幕 1：搭台子（15-25 分钟，边 build 边讲）

**屏幕动作**：终端共享，跑
```bash
sudo bash deploy.sh
```

**你的话术（按 build 阶段对应）**：

| build 进度 | 时间 | 你讲什么 |
|---|---|---|
| 装 JDK / Maven / Node / Nginx / MySQL / Redis | 0-3 min | "摊小二是 SaaS 多租户架构，所有依赖一键装齐，目标是 1 台 4G ECS 也能跑" |
| 拉代码 + DB 初始化（11 张营销表 + seed 菜单 + 邀请码 + Job） | 3-5 min | "数据库初始化时自动 seed 营销引擎相关表 / 菜单权限 / Quartz 自动结算 Job，零人工" |
| `mvn package` 后端构建 | 5-12 min | "后端是 Spring Boot 2.7 + JDK 8 monolith，5 个核心模块（merchant/video/mall/system/pay），103 个单测覆盖营销引擎核心算法" |
| `vite build` admin-vue3 + uniapp H5 | 12-20 min | "前端 PC 后台用 Vue 3 + Vite 5 + Element Plus，商户/用户端用 uniapp（H5 + 微信小程序双端）" |
| sidecar npm install + ffmpeg | 20-22 min | "AI 视频后处理走独立 Node express 服务，ffmpeg 合成端卡 + 字幕，systemd 守护" |
| nginx 配置 + systemd 起服务 + 心跳就绪 | 22-25 min | "nginx 反向代理一站式：前端静态 + 后端 API + sidecar BFF，避免跨域" |

**预期屏幕末尾输出**：
```
🎉  摊小二部署完成！
官网首页:    http://<IP>/
商户/用户端: http://<IP>/m/
管理后台:    http://<IP>/admin/
[演示账号入口 + 邀请码 DEMO20260428]
```

**风险 mitigate**：
- 卡 `pnpm install` → 国内 npmmirror 已配，加 `--registry=https://registry.npmmirror.com` 已在脚本
- `mvn` 卡 → 阿里云镜像已配
- 任意一步 fail → `journalctl -u tanxiaer -n 80 --no-pager` 实时定位

---

### 幕 2：商户开通（你手机扮商户 A）

**演示动作**：

1. **PC 后台 mock 支付配置**（屏幕共享，1 分钟操作完）
   - 跟 1.6 节步骤走

2. **你手机打开** `https://www.doupaidoudian.com/m/`
   - 落地登录页 → 输入手机号 `13900000001` + 密码 `demo123` → 登录
   - 弹"是否开通商户"→ 输入邀请码 `DEMO20260428` → 申请开通
   - **甲方看到**：3 秒内秒变商户，无审核、无短信验证

3. **解释**：
   - "短信演示模式开了 888888 万能验证码（生产关掉走真实短信）"
   - "邀请码是平台运营给商户铺货的钩子，DEMO20260428 是无限次演示码"
   - "商户开通即建租户、试用 30 天、AI 视频套餐 1 次免费"

**屏幕证据**：
- 商户首页打开看到「[商户后台]」标识 + 营业天数倒计时
- PC 后台 → 租户管理 → 多了一个"摊小二商户"租户

**关键检查点**：
- ✅ `/app-api/app/auth/password-login` 返 200，token 已发
- ✅ `/app-api/app/auth/apply-merchant` 返 200，merchant_id 非空
- ✅ DB `merchant_info.status=1` (APPROVED)

---

### 幕 3：商品发布 + AI 视频中段穿插（90 秒）

**演示动作**：

1. **你手机**进商户首页 → 「极简发布商品」
2. **拍照** → 商品名「成都钵钵鸡」→ 价格 ¥18 → 立即上架
3. **甲方看到**：商品瞬间出现在管理列表，无审核
4. **点「AI 一键成片」**：选刚发的商品 → 一键生成
5. **进度条出现**（豆包文案 → Seedance 图生视频 → 端卡 + TTS + 字幕 mux），显示「预计 90 秒」

**等待这 90 秒里你讲什么（v6 营销规则讲解）**：

| 等待秒数 | 讲什么 |
|---|---|
| 0-15s | "AI 视频后台在干活——豆包 LLM 写文案、Seedance 图生视频、豆包 TTS 配音、ffmpeg 合成端卡（含商户分享码）" |
| 15-40s | "现在切到我们的杀手锏，营销引擎 v6（PC 后台 → 营销引擎 → 商户营销配置）" |
| | 屏幕切到 PC 后台 → 商户营销配置页：演示三组核心配置：<br>① 极差比例 `[1,2,3,4,5]` —— 5 星之上 5 星 = 0%<br>② 升星门槛 直推+团队销售双 AND<br>③ 推 N 反 1：每个商品 N 值 + N 个比例（如 N=3 比例 [30,30,40]） |
| 40-70s | "队列三机制：直推 / 自然推 / 插队（**画一张白板小图**）"<br>"积分池：实付 5% 入池，每月按星级瓜分" |
| 70-90s | 切回商户 H5 看进度条 → 视频生成完毕 |

6. **视频出来**：商户 H5 自动播放（带豆包女声配音 + 抖音风字幕 + 端卡中央大商户二维码 + "微信扫描二维码在线下单"提示文案）
7. **你说**："这个视频可以一键发布到抖音（演示账号没接，跳过这步）"

**屏幕证据**：
- 商户视频列表多一条 status=COMPLETED 的任务
- OSS 链接公开可播
- 配额从 1 → 0（CAS 防超卖）

**风险 mitigate**：
- 视频超时 > 3 分钟 → 直接换 fallback 链接（1.4 节预录）"我们之前的样片"
- ApiKey 权限不全 → 屏幕显示"AI 服务暂不可用"，跳过 AI 视频，走 fallback

---

### 幕 4：甲方手机扮用户 B 扫码进店

**演示动作**：

1. **你**：商户 H5 → 我的店铺二维码（页面显示前端 qrcode 库本地生成的 QR）
2. **笔记本投屏**到二维码 tab，**甲方掏手机扫**
3. 甲方手机跳到 `https://www.doupaidoudian.com/m/#/pages/shop-home?tenantId=<X>&inviter=<A的userId>`
4. 甲方看到店铺首页（店铺名 / 商品列表 / 营业时间）
5. 甲方点「成都钵钵鸡」→ 加入购物车 → 提示登录
6. **甲方手机**输入手机号 `13800000002` + 密码 `demo123` → 登录
7. **关键瞬间**：登录成功的那一刻，后台 `flushPendingReferrer()` 把暂存的 inviter A 落库 → A 与 B 在该店铺**首次进店时建立终生上下级**关系

**屏幕证据（你切到 PC 后台 / 终端验证）**：
- DB 多一行 `member_shop_rel(userId=<B>, tenantId=<X>, referrerUserId=<A>, firstVisitAt=...)`
- DB 多一行 `shop_user_referral(userId=<B>, parentUserId=<A>)`

**讲解**："注意这条规则——只有 B **首次**通过 A 的分享码进店才算 A 是 B 的上级。如果 B 之前自己来过这家店，再扫 A 的码不会绑——v6 严格语义已经做了 4 个单测覆盖"

**风险 mitigate**：
- 甲方手机不在同 WiFi → 没事，走公网域名
- 甲方扫码失败 → 笔记本浏览器复制 URL 给甲方手机（fallback 输 URL）

---

### 幕 5：用户下单 + 营销引擎闭环触发 ★高潮★

**演示动作**：

1. **甲方手机**：购物车 → 提交订单 → 选「模拟支付」→ 一秒成功
2. **你笔记本**同时打开 3 屏（你提前布置好的浏览器 tab）：
   - **左屏**：商户 A 钱包页 `pages/user-me/wallet.vue`（推广积分余额）
   - **中屏**：商户 A 订单列表 `pages/order/list.vue`
   - **右屏**：PC 后台「推广积分提现审批」页

3. **甲方支付成功的瞬间**：
   - **左屏数字跳动**：商户 A 推广积分 +X 分（直推奖第 1 位 30%）
   - **流水跳出新一行**：「直推奖 +XX 分」「队列返奖 +XX 分」（因为 A 也是自然进店队列首位）
   - **中屏**：商户订单列表多一条「待发货」订单
   - **右屏**（PC 后台）：暂时空（等下一步提现申请才会有）

4. **甲方亲眼看到**：他刚点的"支付"按钮 → 5 秒后商户那边数字跳了

**讲解**："这就是营销引擎的 5 步串通：1) 消费积分入账 2) 直推奖给 A 3) 团队极差递减（沿 A 上溯，A 是顶端就只发到 A） 4) 入星级积分池 5) A 的星级评定 / 升星检查 — 全部通过 Spring 应用事件驱动，事务一致 + 幂等 + 防超卖"

**屏幕证据（用 sql 验证给甲方看，可选）**：
```sql
SELECT * FROM shop_promo_record WHERE user_id=<A> ORDER BY id DESC LIMIT 5;
SELECT * FROM shop_queue_event WHERE source_order_id=<orderId>;
SELECT current_star, direct_count, team_sales_count FROM shop_user_star WHERE user_id=<A>;
```

5. **A 申请提现**（你手机商户 H5）：
   - 进 `pages/user-me/withdraw.vue`
   - 输入提现金额（满门槛 ¥100）
   - 提交申请
   - **右屏 PC 后台**立即出现一条 PENDING 提现单

6. **PC 后台审批**：
   - 选刚那条 → 通过 → 标记打款
   - **状态机**：PENDING → APPROVED → PAID
   - **左屏 A 钱包**：推广积分扣减、流水多一条「提现 -XX」

**关键检查点**：
- ✅ `shop_promo_record` 表新增 1+ 行（直推 / 队列 / 极差）
- ✅ `shop_promo_pool` 余额增加（按 poolRatio）
- ✅ `shop_user_star` 直推数 / 团队销售数累加
- ✅ `shop_promo_withdraw` 状态机走完 4 状态

---

### 幕 6：规则讲解 + Q&A 收尾

**讲解题纲（事后追溯，比事前讲规则更易懂）**：

1. **为什么甲方刚才点支付，A 钱包就数字跳了**？
   - `MerchantPromoOrderHandler.afterPayOrder` 监听 trade 模块支付完成事件
   - 5 步引擎幂等 + 原子，再点一次也不会重复发奖（`shop_queue_event` 唯一键防重）

2. **数据安全**：
   - 多租户行级隔离（`tenant_id` 字段 + MyBatis Plus 自动过滤）
   - 双积分余额改造（FIX-E）：`SELECT … FOR UPDATE` 锁行 + 本地算 newBalance，杜绝并发滞后
   - 提现状态机原子化（FIX-D）防双花

3. **运维就绪**：
   - 一键部署：`sudo bash deploy.sh`，4G 机器 25 分钟从 0 到上线
   - 单测覆盖：103/103 全绿（v6 文档 5.4 节 10 步跑例 + 边界 + 防重）
   - 监控：systemd 守护后端 + sidecar，自动重启
   - 自动结算：Quartz Job 6200 每小时按 cron 扫所有租户

4. **AI 能力**：
   - 文案：火山方舟豆包 LLM
   - 图生视频：Seedance（火山方舟）
   - TTS 配音：豆包语音合成大模型 V3 单向流式
   - 字幕：ffmpeg + libass 抖音风花字（fade + move 动画）
   - 端卡：商品图模糊背景 + 中央大商户分享码 + 店名 + TTS 提示文案

5. **下一步路线**：
   - 真实抖音账号接入（已实现完整 OAuth + 上传 + 发布链路，缺一个真应用）
   - 商户分享码自动印到物料（咖啡杯套 / 外卖包装 / 名片）
   - 甲方个性化需求（你 Q&A）

---

## 3. 演示后清理

1. 禁用演示邀请码：
   ```sql
   UPDATE merchant_invite_code SET enabled=b'0' WHERE code='DEMO20260428';
   ```
2. 清理演示账号（演示后 1 周观察期再删）：
   ```sql
   DELETE FROM member_user WHERE mobile IN ('13900000001','13800000002');
   ```
3. 关 mock 支付渠道（PC 后台支付管理 → 状态关闭）
4. 短信切回真实模式：`application-prod.yaml` 设 `yudao.sms-code.demo-mode: false`
5. password-login 端点上线后必须加 `@ConditionalOnProperty(yudao.demo.password-login-enabled)` 开关
   - **行动**：演示后第一周开发任务

---

## 4. 演示当天会出意外的 8 个高风险点 + mitigate

| # | 风险 | 触发条件 | 提前准备 | 应急话术 |
|---|---|---|---|---|
| 1 | ECS 安全组未放行 80 | 浏览器打不开 | 部署前阿里云控制台双确认 | "网络组设置中，1 分钟搞定" |
| 2 | 域名 DNS 未生效 | 浏览器 NS error | 提前 1 天解析 | "我用 IP 访问演示" |
| 3 | mvn / pnpm 国内拉包慢 | build 超 25 分钟 | `.env` 设 GITHUB_PROXY，镜像已配 | "网络高峰期，再等 5 分钟" |
| 4 | mock 支付未配 | 下单 → 没有可选支付方式 | 部署完立即按 1.6 配 | 切到 SQL 直接 update payStatus |
| 5 | AI 视频 > 3 分钟 | Seedance 抽风 | fallback 视频已录屏（1.4） | "我们之前生成的样片，今天网络慢，给您看一份高清版" |
| 6 | 甲方扫码进站不到 | 甲方手机网络差 / 二维码模糊 | 笔记本复制 URL 短链 | "请输入这个短链" |
| 7 | 营销引擎触发延迟 > 5 秒 | DB / Redis 卡 | 钱包页加 setInterval(2s) 自动刷新 | "刷新一下" |
| 8 | PC 后台菜单空 | 商户租户未关联 menu_ids | 用 admin (id=1) 演示 | "先用平台管理员视角，商户视角后续部署给你看" |

---

## 5. 验收清单（甲方走前确认 ✅）

- [ ] 商户从 0 到 1 秒开通（无审核、无短信验证）
- [ ] 极简商品发布（拍照 + 名 + 价 → 自动上架）
- [ ] AI 视频成片含豆包文案 + Seedance 视频 + TTS 配音 + 字幕 + 端卡 + 商户分享码
- [ ] 用户扫商户分享码进店首次绑定上下级
- [ ] 用户下单 mock 支付立即成功
- [ ] 商户钱包看到直推奖 + 队列返奖 + 团队极差 实时跳数
- [ ] 提现申请 → PC 后台 4 状态机审批 → 标记打款
- [ ] 多租户隔离（PC 后台租户列表 + DB tenant_id 字段）
- [ ] 一键部署能力（终端共享看 build 全程）

---

## 6. 文档版本

- v1（2026-04-29 23:55）：剧本 2 双角色双手机定稿
- 关联：`docs/演示SOP.md`（部署运维 SOP）/ `docs/design/promo-engine-progress.md`（v6 营销开发进度）/ `docs/design/marketing-system-v6.md`（v6 需求）
