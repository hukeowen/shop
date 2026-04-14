# 铺星开发进度

> 最后更新：2026-04-14（全部40项任务完成 ✅ + 代码审查修复完成）
> 规则：每完成一个任务立即更新此文档，状态用 ✅ 完成 / 🔄 进行中 / ⬜ 待开始
>
> ## 代码审查修复记录
> - SQL注入修复：AppUserShopController 改为参数化 @Select 查询
> - PII泄露修复：/status 接口只返回 {status, rejectReason, shopName}
> - BrokerageUserDO 添加 shareCode 字段
> - brandId=0L 改为 null 防止运行时崩溃
> - 全部公开接口添加 @PermitAll + @TenantIgnore
> - TenantContextHolder 手动操作移除，依赖框架 filter
> - AI成片对接 VideoTaskService + DouyinService（抖音授权+发布）
> - 商户余额体系：ShopInfoDO.balance + 提现余额校验 + 驳回退款
> - 推广功能付费门槛：requirePaidSubscription() 检查
> - onRefereePaid 接入 renewSubscription 触发推N返1
> - buy-quota 关闭直接增加，待支付系统对接

---

## 一、数据库变更（8项）

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 1 | `member_user` uk_mobile → `(tenant_id, mobile)` 复合唯一索引 | ✅ | 已确认 SQL 中索引已含 tenant_id，无需修改 |
| 2 | 新增 `merchant_apply` 商户入驻申请表 | ✅ | v2_business_tables.sql |
| 3 | 新增 `tenant_subscription` 租户订阅状态表 | ✅ | v2_business_tables.sql |
| 4 | 新增 `shop_info` 店铺详情表 | ✅ | v2_business_tables.sql |
| 5 | 新增 `shop_brokerage_config` 商户返佣配置表 | ✅ | v2_business_tables.sql |
| 6 | `trade_brokerage_user` 新增 `share_code` 字段 | ✅ | v2_business_tables.sql（ALTER TABLE） |
| 7 | 新增 `merchant_referral` 平台推荐裂变记录表 | ✅ | v2_business_tables.sql |
| 8 | 新增 `ai_video_task` AI成片任务表 | ✅ | v2_business_tables.sql（替代旧 video_task） |

---

## 二、平台管理后台（5项）

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 9 | 商户入驻申请列表 + 审核（通过/驳回） | ✅ | MerchantApplyController |
| 10 | 审核通过自动开通租户初始化逻辑 | ✅ | MerchantApplyServiceImpl：创建租户+shop_info+订阅+短信 |
| 11 | 平台推N返1配置（推N商户付费返1年） | ✅ | MerchantReferralService：recordReferral/onRefereePaid/返利续期 |
| 12 | 商户订阅管理（续费/到期/禁用）+ 到期定时任务 | ✅ | 分页查询/禁用/TenantSubscriptionExpireJob 每日凌晨1点 |
| 13 | 商户提现审核（确认转账 + 上传凭证） | ✅ | merchant_withdraw_apply 表 + MerchantWithdrawController |

---

## 三、商户小程序（15项）

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 14 | 手机号登录 + 微信OpenID免密登录 | ✅ | AppMerchantAuthController：resolve-tenant/sms-send/sms-login/wx-login |
| 15 | 首页数据看板 | ✅ | MerchantDashboardService：今日订单/销售额/新会员/待处理/在售商品 |
| 16 | 极简商品发布（拍照+名称+价格，默认小吃分类） | ✅ | AppMerchantProductController.simpleCreate |
| 17 | 商品列表管理（上下架/编辑/删除/积分设置） | ✅ | AppMerchantProductController：page/update/updateStatus/delete |
| 18 | 扫码核销（扫描 + 手动输入） | ✅ | AppMerchantOrderController：get-by-verify-code/pick-up-verify |
| 19 | 订单列表 + 快递发货 + 商户确认送达 | ✅ | AppMerchantOrderController：page/delivery/pick-up-by-id |
| 20 | 会员列表 + 会员详情（含推广上下级关系） | ✅ | AppMerchantMemberController：page/get/brokerage-user |
| 21 | 返佣配置（开关+比例+冻结天数+推N返1） | ✅ | AppMerchantConfigController：GET/PUT /brokerage |
| 22 | 积分规则配置（消费1元=N积分） | ✅ | 合并到 ShopBrokerageConfigDO.pointPerYuan |
| 23 | 推广大使列表（业绩统计） | ✅ | AppMerchantMemberController：/brokerage-user/page |
| 24 | 用户提现审核（确认已支付/拒绝） | ✅ | AppMerchantWithdrawController：user/approve、user/reject |
| 25 | 商户向平台申请提现 | ✅ | AppMerchantWithdrawController：merchant/create |
| 26 | 店铺设置（基本信息/配送/二维码下载） | ✅ | AppMerchantShopController：GET/PUT /info、PUT /status |
| 27 | AI成片——上传图片+输入描述+确认文案 | ✅ | AppMerchantAiVideoController：create/confirm |
| 28 | AI成片——历史记录+视频下载+配额购买 | ✅ | AppMerchantAiVideoController：page/quota/buy-quota |

---

## 四、用户小程序（10项）

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 29 | 手机号一键登录（支持多商户会员） | ✅ | 复用 member AppAuthController，tenant-id Header 隔离 |
| 30 | 首页——最近访问店铺 + 附近商户列表 | ✅ | AppUserShopController：/nearby |
| 31 | 附近页（地图模式）+ 分类页 | ✅ | AppUserShopController：/nearby + /by-category |
| 32 | 扫码入店（scene解析+自动收藏+绑定推广关系） | ✅ | AppUserBrokerageController：/bind |
| 33 | 店铺详情页 + 商品详情 + 购物车 | ✅ | AppUserShopController：/detail + 复用现有 AppProductSpuController |
| 34 | 下单页（选配送方式）+ 微信支付 | ✅ | 复用现有 AppTradeOrderController |
| 35 | 我的订单 + 自提核销码展示 | ✅ | 复用现有 AppTradeOrderController（pickUpVerifyCode 字段） |
| 36 | 推广中心（分享码+我的下级+佣金记录） | ✅ | AppUserBrokerageController：/my + /my-child-count |
| 37 | 申请提现（向商户申请） | ✅ | 复用现有 AppBrokerageUserController.createBrokerageWithdraw |
| 38 | 我的页面（收藏/足迹/积分/地址/开店入口） | ✅ | 复用现有 member 模块 App 接口 |

---

## 五、商户入驻流程（2项）

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 39 | 入驻第1-2步：基本信息 + 上传资质（无OCR） | ✅ | AppMerchantApplyController：submit（含5步合并） |
| 40 | 入驻第3-5步：位置授权 + 收款配置 + 提交 | ✅ | 同 #39，合并为一个提交接口 + status 查询 |

---

## 依赖关系（关键路径）

```
#1 (索引修复)
  └── #29 (用户登录)
        └── #32 (扫码入店)

#2 (merchant_apply表)
  └── #9 (平台审核)
        └── #10 (自动开通租户)
  └── #39/#40 (入驻表单)

#3 (tenant_subscription)
  ├── #10 (开通初始化)
  ├── #12 (订阅管理)
  └── #28 (AI配额购买)

#4 (shop_info)
  ├── #10 (开通初始化)
  ├── #26 (店铺设置)
  ├── #30 (首页商户列表)
  └── #31 (附近/分类页)

#5 (shop_brokerage_config)
  ├── #21 (返佣配置)
  └── #22 (积分规则)

#6 (share_code字段)
  ├── #23 (推广大使列表)
  ├── #32 (扫码绑推广)
  └── #36 (推广中心)

#8 (ai_video_task)
  ├── #27 (AI成片上传)
  └── #28 (AI成片历史)
```

---

## 建议开发顺序

```
第一批（基础，无依赖）：#1 → #2 #3 #4 #5 #6 #7 #8（全部DB变更先做）
第二批（平台）：#9 → #10 → #11 #12 #13
第三批（核心流程）：#14 #29 → #16 #18 #34 #35
第四批（推广体系）：#21 #22 → #23 #24 #25 #36 #37
第五批（完整体验）：#27 #28 #30 #31 #32 #33 #38 #39 #40
```
