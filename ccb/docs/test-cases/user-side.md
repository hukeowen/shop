# 用户端 H5 测试用例（自动化友好）

> 路径：`https://www.doupaidoudian.com/m/#/...`
> 用户登录页：`/pages/login/index`
> C 端首页：`/pages/index/index`（注：与商户端共用，但导航不同）
> 店铺详情：`/pages/shop-home/index?tenantId=<id>`
>
> 用例 ID 格式：`U-<模块>-<序号>`，模块缩写：A=Auth、S=Shop、C=Cart、O=Order、M=MyAccount、I=Invite、N=Nearby、F=Favorite。

## U-A 用户登录

### U-A-01 短信验证码登录
- **前置**：手机号 `13700137000` 数据库无（首次登录会自动注册）
- **步骤**：
  1. 打开 `/m/#/pages/login/index`
  2. 输入手机号 + `888888` → 登录
- **预期**：跳 `/pages/user-home/index`，顶部显示「我的」+ 头像
- **断言**：URL 含 `pages/user-home`；localStorage 含 `token`

### U-A-02 登出后访问受限页 → 跳用户登录（不是商户登录）
- **步骤**：登出 → 直接访问 `/m/#/pages/user-me/wallet`
- **预期**：跳 `/pages/login/index`（**不是 merchant-login**）
- **断言**：URL 含 `login` 但不含 `merchant-login`

---

## U-S 逛店

### U-S-01 扫码进店（带 tenantId）
- **步骤**：打开 `/m/#/pages/shop-home/index?tenantId=2`
- **预期**：显示该店铺信息、商品列表、营业状态
- **断言**：page 含「店铺名」+ 至少 1 个商品卡

### U-S-02 通过推荐链接进店（带 inviter）
- **步骤**：`/m/#/pages/shop-home/index?tenantId=2&inviter=10001`
- **预期**：进店成功；后端 `member-rel/visit` 落库 referrerUserId=10001
- **断言**：localStorage 写入 `lastShopTenantId=2`、`lastShopName=...`

### U-S-03 收藏店铺
- 步骤：店铺页点「收藏」
- 预期：「我收藏的店铺」列表出现这家
- 断言：`/pages/user-me/favorites` 含该 tenantId 卡

### U-S-04 商品详情查看
- 步骤：商品列表点商品 → 商品详情
- 预期：显示主图、价格、规格、加购按钮

---

## U-C 购物车 / 下单

### U-C-01 加入购物车
- 步骤：商品详情点「加入购物车」
- 预期：购物车 badge +1；`/pages/cart/index` 含该商品

### U-C-02 结算下单（自提）
- **前置**：购物车有 1 个商品
- **步骤**：购物车 → 结算 → 选「自提」 → 提交
- **预期**：跳「等待商家发货」页；订单中心可见
- **断言**：`/pages/user-order/list` 第一条 status='待发货'

### U-C-03 结算下单（用余额抵扣）
- **前置**：用户余额 ≥ 商品价
- **步骤**：结算时勾「余额抵扣」→ 提交
- **预期**：余额扣减 + 订单 paidByBalance>0

---

## U-O 订单

### U-O-01 订单列表查看
- 步骤：`/pages/user-order/list`
- 预期：3 个 tab（待发货/已发货/已完成）+ 各自订单列表

### U-O-02 订单详情 + 物流
- 步骤：列表点订单 → 详情
- 预期：订单号、商品、金额、状态、商家备注

---

## U-M 我的账户

### U-M-01 余额与积分页
- 步骤：`/pages/user-me/shop-balance`
- 预期：显示余额（分→元）、积分、最近 10 条流水

### U-M-02 推广积分明细
- 步骤：`/pages/user-me/promo-records`
- 预期：列表显示推荐人/被推荐 + 时间 + 积分变动

### U-M-03 申请提现（用户余额）
- **前置**：余额 ≥ ¥10
- **步骤**：`/pages/user-me/withdraw` → 填金额 + 收款方式 → 提交
- **预期**：toast「提现申请已提交」+ 余额冻结对应金额
- **断言**：钱包页提现 tab 出现新申请 status='审批中'

### U-M-04 我的队列（v6 推 N 反 1）
- 步骤：`/pages/user-me/my-queue`
- 预期：显示当前队列层级 + A 层 / B 层人数

### U-M-05 店铺星级
- 步骤：`/pages/user-me/star`
- 预期：显示 5 星制评分 + 评价历史

---

## U-I 邀请好友

### U-I-01 邀请页二维码
- **前置**：localStorage 已有 `lastShopTenantId`（先逛过店）
- **步骤**：`/pages/user-me/invite`
- **预期**：显示带店铺名的二维码图片 + 复制链接按钮
- **断言**：`<image>` natural width > 0；图片 src 含 `/qr?text=...&center=...`

### U-I-02 复制邀请链接
- 步骤：点「复制链接」
- 预期：toast「已复制」；剪贴板含 `?inviter=<userId>`

---

## U-N 附近店铺

### U-N-01 附近列表
- **前置**：浏览器允许定位（自动化用 mock geolocation）
- **步骤**：`/pages/nearby/index`
- **预期**：按距离升序显示 ≥1 家店

---

## U-F 收藏店铺

### U-F-01 收藏列表
- 步骤：`/pages/user-me/favorites`
- 预期：显示已收藏店铺卡，点卡跳 shop-home

### U-F-02 取消收藏
- 步骤：列表上滑卡 → 删除
- 预期：列表移除该卡

---

## 自动化执行约定（共享）

```javascript
// playwright.config.js
{
  baseURL: 'https://www.doupaidoudian.com',
  use: {
    headless: false,
    channel: 'chrome',           // 复用系统 Chrome（不用下 Chromium）
    viewport: { width: 414, height: 896 },  // iPhone 11 大小，更接近真实场景
    locale: 'zh-CN',
    geolocation: { latitude: 31.23, longitude: 121.47 },  // 上海
    permissions: ['geolocation', 'clipboard-read', 'clipboard-write'],
  },
  projects: [
    { name: 'merchant', testDir: './tests/merchant', use: { storageState: '.auth/merchant.json' } },
    { name: 'user',     testDir: './tests/user',     use: { storageState: '.auth/user.json' } },
  ],
}

// 共享 setup（global-setup.js）
// 启动时跑一次商户登录 + 用户登录，把 storageState 存盘
// 各 spec 直接用 storageState 跳过登录步骤
```

---

## 测试数据约定

| 类型 | 值 | 用途 |
|---|---|---|
| 商户手机 | `13800138001` | 已 active 商户（需 DB 提前 seed） |
| 用户手机 | `13700137000` | C 端用户 |
| 短信验证码 | `888888` | YUDAO_SMS_DEMO_MODE=true 固定 |
| 商户租户 ID | `2` | 测试店铺 |
| 测试商品图 | `tests/fixtures/product1.jpg` | 上架/AI 视频通用 |
| 推广人 ID | `10001` | inviter 链路 |

---

## Playwright spec 文件命名

```
tests/
├── merchant/
│   ├── A-auth.spec.js          // M-A-* 全部用例
│   ├── P-product.spec.js       // M-P-*
│   ├── V-video.spec.js         // M-V-*
│   ├── O-order.spec.js
│   └── ...
└── user/
    ├── A-auth.spec.js
    ├── S-shop.spec.js
    ├── C-cart.spec.js
    └── ...
```

每个 .spec.js 一个模块，测试 ID 与文件内 `test('M-A-01 短信登录', ...)` 一一对应。失败截图 `test-results/<id>.png`。
