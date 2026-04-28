# 客户演示 SOP（2026-04-29）

> 配套：`deploy.sh` 一键部署 / `marketing-system-v6.md` 营销需求 / `promo-engine-progress.md` 开发进度

## 一、上场前 30 分钟（运维一次性）

```bash
# 1) 在干净 CentOS 7.9 ECS 上
git clone https://github.com/hukeowen/shop.git /opt/tanxiaer/repo
cd /opt/tanxiaer/repo/ccb
cp .env.example .env
vim .env   # ⚠️ 必填 4 个：MYSQL_ROOT_PASS / DB_PASS / REDIS_PASS / MERCHANT_INTERNAL_TOKEN
           #    AI 视频要演示则补 ARK_API_KEY 等

# 2) 一条命令完成 装包 → DB 初始化 → 后端 build → 前端 build →
#    Nginx 反代（避免跨域）→ systemd 启服务
sudo bash deploy.sh
```

完成后部署脚本会打印：
- 三个入口（官网 / H5 / PC 后台）
- 演示账号 / 邀请码（`DEMO20260428`）
- 7 步演示链路

阿里云 ECS 安全组放行 80/443，把 `SERVER_NAME` 改成真实域名（演示用 IP 也可）。

## 二、自检清单（部署完跑一次）

```bash
# 后端活着
curl -s http://localhost:48080/admin-api/system/auth/captcha | head -c 100

# H5 入口能加载
curl -sI http://${PUBLIC_IP}/m/ | head -3

# PC 后台能加载
curl -sI http://${PUBLIC_IP}/admin/ | head -3

# Nginx 反代正常（避免跨域）
curl -s http://${PUBLIC_IP}/admin-api/system/auth/captcha | head -c 80

# 营销引擎 SQL 已 seed
mysql -uroot -p"${MYSQL_ROOT_PASS}" -D ruoyi-vue-pro -e \
  "SELECT id,name,type FROM system_menu WHERE id BETWEEN 6100 AND 6109;"
mysql -uroot -p"${MYSQL_ROOT_PASS}" -D ruoyi-vue-pro -e \
  "SELECT code,enabled FROM merchant_invite_code WHERE code='DEMO20260428';"
mysql -uroot -p"${MYSQL_ROOT_PASS}" -D ruoyi-vue-pro -e \
  "SELECT id,name,status FROM infra_job WHERE id=6200;"
```

预期：
- 后端 captcha 端点返 JSON
- H5 / PC 入口返 200
- 10 条 menu / 1 条邀请码 / status=0 的 Job

## 二·B、演示前 1 分钟开启 mock 支付渠道（必做）

> 这一步 deploy.sh **不会自动做** — yudao 默认不预 seed pay_app / pay_channel
> 实例，避免破坏既有支付配置。客户没真实微信支付的情况下，演示「用户下单 → 支付 → 营销引擎触发」必须开 mock 渠道。

PC 后台（`http://<HOST>/admin/`，admin/admin123）：

1. **创建支付应用**：左侧「支付管理 → 应用信息 → 新增」
   - 应用标识 `demo`，名称 `演示`，状态 `开启`
   - 回调地址全部填 `http://localhost:48080/admin-api/pay/notify/order/${channelId}`
2. **添加 mock 支付渠道**：选刚创建的应用 → 点「渠道」→ 新增
   - 渠道：`模拟支付（mock）`
   - 状态 `开启`，费率 0
   - 配置 JSON：`{}`（mock 不需要任何 KEY）
3. **关联到交易**：左侧「交易中心 → 配置 → 交易设置」
   - 选刚创建的支付应用为默认 `payAppId`
   - 保存

完成后客户在 H5 下单 → 选「模拟支付」→ 自动成功 → 触发 `MerchantPromoOrderHandler.afterPayOrder` → 营销引擎跑 5 步全跑通。

> 演示完毕：进 pay_channel 把 mock 渠道 status 改成 0 关闭，避免被真实用户白嫖订单。

## 三、演示链路（按顺序走）

> 所有 URL 把 `<HOST>` 替换为真实域名 / IP。

### 0. PC 后台总览（管理员视角）

打开 `http://<HOST>/admin/`
- 账号：`admin` / `admin123`
- 左侧菜单看到「营销引擎 → 商户营销配置 / 商品营销配置 / 推广积分提现」三页
- 在「商户营销配置」修改极差比例 / 升星门槛保存一次，证明 PUT 流程通

### 1. 用户从手机号 + 密码登录

打开 `http://<HOST>/m/#/pages/login/index`
- 输手机号 `13900000001` + 任意 ≥6 位密码（比如 `demo123`）
- 点「登录 / 注册」→ 后端首次输入即注册（不发短信、不验真）
- 跳到首页

✅ 预期：toast「登录成功」+ 进入「开始逛店 / 我是商户去申请」选择页

### 2. 申请开通商户（自动审核通过）

- 在登录后页面点「我是商户，去申请」
- 输入邀请码 `DEMO20260428`
- 点「申请开通」

✅ 预期：toast「商户申请成功」+ 后台数据库里：
- `merchant_info` 多一行 `status=1（APPROVED）`
- 该用户 `member_user.id` 关联此 merchant
- 用户 H5 跳到商户首页（左下角看到"摊小二商户"标识）

### 3. AI 极简发布商品（自动上架）

商户身份在 H5 进「商品发布」（`pages/product/edit`）
- 拍照 / 选图，输入名称、价格
- 提交

✅ 预期：商品落库 `product_spu.status=1（ENABLE）` 直接上架，
列表页可见。**无审核中间状态**。

### 4. AI 生成视频 + 发布抖音（依赖 KEY）

> ⚠️ 缺 `ARK_API_KEY` 时跳过此步；缺 `DOUYIN_*` 只演示到"生成完成"为止。

- 在 H5「AI 视频」入口选商品 → 一键生成
- 等待文案生成（豆包 LLM）→ 确认
- 等待图生视频（Seedance / 即梦）
- 完成后点「授权抖音」→ 跳转抖音 OAuth → 回跳后点「发布」

✅ 预期：抖音工作台看到新视频。

### 5. AI 套餐充值

- 商户进「AI 套餐」（H5：`pages/me/aivideo-package`）
- 看到剩余配额 + 在架套餐
- 选一个套餐点购买

> 演示无微信支付时：跳过支付，运维 ssh 上去执行
> ```sql
> UPDATE merchant_info SET video_quota_remaining=video_quota_remaining+10 WHERE id=<merchantId>;
> ```
> 当场刷新 H5，配额从 X → X+10。

### 6. 商户分享码（推广引擎入口）

- 商户 H5 进「我的店铺二维码」（`pages/me/qrcode`）
- 看到二维码图（后端字段空时**前端自动用 qrcode 库生成**）
- 链接形如 `http://<HOST>/m/shop-home?inviter=<userId>&tenantId=<tenantId>`

### 7. 用户扫码进店 + 下单（推广引擎触发）

- 让客户用 / 让另一台手机扫上一步的二维码
- 落地 `shop-home` 页 → 提示登录 → 输手机号 `13800000001` + 密码登录
- **关键**：登录瞬间「推荐链」自动落库（A 推 B 终生制绑定）
- 浏览商品 → 加入购物车 → 提交订单 → 微信支付（或 ssh 直接更新支付状态）

✅ 预期：支付完成 → 订单状态变「已支付」→ 营销引擎自动跑 5 步：
1. 消费积分入账（B）
2. 直推奖给 A
3. 团队极差递减（A 沿链上溯）
4. 入星级积分池
5. A / B 星级回算

### 8. 双端订单 + 推广积分流水

- **商户视角**（A 用户切回商户）：进「订单管理」可见 B 的订单
- **用户视角**（B 用户）：进「我的订单」看到自己的订单
- **用户钱包**（A 进 `pages/user-me/wallet`）：
  - 推广积分余额 ↑
  - 流水里看到「直推奖」「队列返奖」「团队极差」类目
- **我的队列**（A 进 `pages/user-me/my-queue`）：看到 A 在哪些商品队列、A/B 层、累计 N/maxN 进度
- **邀请好友**（A 进 `pages/user-me/invite`）：看到自己的邀请二维码（前端 qrcode 库本地生成）

### 9. 积分池立即结算（演示推广引擎闭环）

PC 后台或 H5 商户身份调：
```bash
curl -X POST -H "Authorization: Bearer <merchantToken>" \
  "http://<HOST>/app-api/merchant/mini/promo/pool/settle?mode=FULL"
```
✅ 预期：池余额清零，奖金按规则发到所有星级用户的推广积分。
随后到「积分池历史」（PC 后台或 H5）看到本次 round 记录。

### 10. 提现申请 + 审批

- 用户 B 进 `pages/user-me/withdraw` 申请提现 ¥100（满门槛）
- 商户 A 进 PC 后台「推广积分提现」→ 通过 / 驳回
- 通过后状态变 APPROVED；标记打款后变 PAID
- 驳回时推广积分**自动退还**

## 四、当场卡住时的兜底

| 现象 | 排查 |
|---|---|
| 一键脚本报错 | `journalctl -u tanxiaer -n 80 --no-pager` 看后端日志；`/tmp/maven-build.log` 看 mvn |
| H5 登录后白屏 | F12 看 console，多半是接口 401（token 过期）→ 退出重登 |
| PC 后台菜单不全 | 用 admin（id=1）登录确认；商户租户登录看不见时去「角色 / 租户套餐」勾 6100/6107/6108/6109 |
| AI 视频报错 | 看 `journalctl -u tanxiaer | grep ARK` —— 多半 ARK_API_KEY 没填或失效 |
| 推广积分没增加 | 1) 看支付是否真完成（`trade_order.status`）2) 看 `shop_promo_record` 表有没有新记录 |
| 营销引擎逻辑疑问 | 99/99 单测覆盖了 v6 5.4 节 10 步跑例 + 极差递减 + 池结算；mvn -pl yudao-module-merchant test 可重跑确认 |

## 五、演示完成后

1. 把演示用邀请码停用：
   ```sql
   UPDATE merchant_invite_code SET enabled=b'0' WHERE code='DEMO20260428';
   ```
2. 演示账号留存观察 1 周后清理：
   ```sql
   DELETE FROM member_user WHERE mobile LIKE '13800000%' OR mobile LIKE '13900000%';
   ```
3. 真实上线时把 SERVER_NAME 改域名 + 配 HTTPS：
   ```bash
   yum install -y certbot python2-certbot-nginx
   certbot --nginx -d your.domain.com
   ```
