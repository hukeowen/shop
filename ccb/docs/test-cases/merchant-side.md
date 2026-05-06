# 商户端 H5 测试用例（自动化友好）

> 路径：`https://www.doupaidoudian.com/m/#/...`
> 商户登录页：`/pages/merchant-login/index`
> 商户工作台：`/pages/index/index`
>
> 执行环境：Playwright + Chromium，桌面 1280x800（先桌面再 mobile-emu）。
>
> 用例 ID 格式：`M-<模块>-<序号>`，模块缩写：A=Auth、H=Home、P=Product、V=Video、O=Order、W=Withdraw、Q=Quota、S=Settings。

## M-A 商户登录 / 入驻

### M-A-01 商户登录（短信验证码）
- **前置**：手机号 `13800138001` 已是 active 商户
- **步骤**：
  1. 打开 `/m/#/pages/merchant-login/index`
  2. 输入手机号 `13800138001`
  3. 点「获取验证码」→ 等 1s
  4. 输入 `888888`（YUDAO_SMS_DEMO_MODE=true 时固定）
  5. 点「登录」
- **预期**：
  - 跳转 `/pages/index/index`
  - localStorage 含 `token`
  - 顶部显示商户名称
- **断言**：URL 含 `pages/index/index`；page 含 `text=工作台`

### M-A-02 商户入驻（新手机号自动建商户）
- **前置**：手机号 `13900139999`（数据库无）
- **步骤**：
  1. 打开 `/m/#/pages/merchant-apply/index`
  2. 填店铺名 `测试小店-${ts}`、经营品类「小吃」、地址、营业时间
  3. 输入手机号 + `888888`
  4. 提交
- **预期**：跳商户工作台 + 显示新店名

### M-A-03 商户登出后访问受限页 → 跳商户登录
- **步骤**：
  1. 已登录 → 个人页点「退出登录」
  2. 直接访问 `/m/#/pages/me/index`
- **预期**：toast「登录已失效」+ 跳 `/pages/merchant-login/index`
- **断言**：URL 含 `merchant-login`

---

## M-P 商品管理

### M-P-01 AI 拍照上架（极简）
- **前置**：登录后
- **步骤**：
  1. 进 `/pages/product/list` → 点「AI 一键上架」
  2. 拍照（自动化用本地 fixture 图 `tests/fixtures/product1.jpg`）
  3. 等 AI 识别返回（≤ 30s）
  4. 修改商品名为 `测试商品-${ts}`、价格 `5.00`
  5. 点提交
- **预期**：
  - HTTP 200，无「商品分类不正确」「商品品牌不能为空」
  - 跳商品列表，新商品在首位
- **断言**：列表第一行含 `text=测试商品-${ts}`

### M-P-02 商品分类自动建（无 categoryId / 无 brand）
- **场景**：极端兜底——前端只传名+价+图
- **接口**：`POST /app-api/merchant/mini/product/simple-create` body `{name,price,picUrl}`
- **预期**：HTTP 200 返 spuId；后端自动建「AI 上架→通用」二级分类 + 「通用」品牌
- **断言**：响应 code=0；spu 详情 categoryId 对应 level=2 分类

### M-P-03 修改商品价格
- 步骤：列表点商品 → 改价格 6.00 → 保存
- 预期：列表显示新价格

### M-P-04 商品下架
- 步骤：商品列表「下架」
- 预期：状态变「已下架」，C 端 shop-home 不再显示

---

## M-V AI 视频生成

### M-V-01 上传 1 张图生视频（最快路径）
- **步骤**：
  1. `/pages/ai-video/index` 点「创建」
  2. 选 1 张本地图（自动化用 fixture）
  3. 输入"招牌烤串，3 元一串"
  4. 点「生成」→ 等到分镜确认页
  5. 选语音、BGM 默认，点「确认生成」
- **预期**：等 ~60s 后视频生成完成（含端卡）
- **断言**：详情页显示 mp4 player、点「播放」可播

### M-V-02 上传 6 张图生视频（最大幕数）
- **步骤**：同上但选 6 张图
- **预期**：6 个动态分镜 + 1 个端卡，总时长 ≤ 32s
- **断言**：详情页 7 个分镜 status='ready'

### M-V-03 视频生成中刷新页 → 不丢任务
- **步骤**：
  1. 启动视频生成
  2. 立即 reload 页面
  3. 进 `/pages/ai-video/history` 看任务卡片
- **预期**：任务还在「生成中」状态，不会消失

### M-V-04 配额耗尽
- **前置**：商户配额 = 0
- **预期**：点「生成」按钮禁用 + toast「配额不足，请购买套餐」
- **断言**：弹窗指向 `/pages/ai-video/quota`

### M-V-05 购买配额套餐
- **步骤**：`/pages/ai-video/quota` 点 ¥29.9 套餐 → 通联 H5 收银台 → 完成支付
- **预期**：返回首页 + 配额 +3
- **断言**：自动轮询 5/15/25s 内 quota.remain 变 ≥ 3

---

## M-O 订单管理

### M-O-01 收到新订单显示
- **前置**：用户端下单
- **步骤**：商户端 `/pages/order/list`
- **预期**：新订单在顶部 status='待发货'

### M-O-02 商户发货
- **步骤**：订单详情点「发货」→ 填快递单号
- **预期**：状态变「已发货」，用户端 user-order 同步

---

## M-W 提现

### M-W-01 用户端提现审批
- **前置**：用户提交 ¥10 提现
- **步骤**：商户 `/pages/withdraw/user-list` 点「通过」
- **预期**：状态「审批通过」+ 用户钱包扣减

### M-W-02 商户自己提现
- **步骤**：`/pages/withdraw/merchant-apply` 填金额 + 审批人
- **预期**：跳「审批中」状态

---

## M-Q 二维码 + 邀请

### M-Q-01 店铺二维码中心叠店铺名
- **步骤**：`/pages/me/qrcode`
- **预期**：图片可见、二维码中心叠店铺名（黑字白底圆）
- **断言**：`<image>` 已加载（natural width > 0）+ src 含 `/qr?text=...&center=...`

### M-Q-02 商户分享自家进店码
- **步骤**：进店首页点「分享码」
- **预期**：弹窗显示带 logo 二维码

---

## M-S 设置 / 个人页

### M-S-01 编辑店铺名
- 步骤：me → 编辑店铺 → 改名 → 保存
- 预期：列表+二维码上的店铺名同步更新

### M-S-02 营销配置（v6）
- 步骤：me → 营销配置 → 切「推 N 反 1」N=3 → 输入 3 个比例（30/30/40，和=100）
- 预期：保存成功；切到 N=4 自动加输入框

### M-S-03 关于摊小二
- 步骤：点「关于摊小二」
- 预期：显示版本号、ICP、客服电话

---

## 自动化执行约定

```javascript
// Playwright config
{
  baseURL: 'https://www.doupaidoudian.com',
  use: { headless: false, viewport: { width: 1280, height: 800 } }
}

// 共享 setup：登录态写到 storageState
// global-setup.js: 用 M-A-01 流程登录，把 cookie/localStorage 存到 .auth/merchant.json
// 各 spec 用 storageState: '.auth/merchant.json' 跳过登录
```

每个用例**最多 60 秒**（AI 视频用例放宽到 180 秒）。失败截图保存到 `test-results/<id>.png`。
