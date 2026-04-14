# 铺星（PuXing）SaaS 商户平台 — 开发设计文档

> 版本：v1.0 | 日期：2026-04-14

---

## 目录

1. [产品概述](#1-产品概述)
2. [整体架构](#2-整体架构)
3. [商户入驻流程](#3-商户入驻流程)
4. [租户开通与试用](#4-租户开通与试用)
5. [AI 一键成片](#5-ai-一键成片)
6. [用户小程序](#6-用户小程序)
7. [商户小程序](#7-商户小程序)
8. [核销与配送](#8-核销与配送)
9. [扫码入店流程](#9-扫码入店流程)
10. [数据库设计](#10-数据库设计)
11. [接口设计](#11-接口设计)
12. [部署与运维](#12-部署与运维)
13. [推广大使与返佣体系](#13-推广大使与返佣体系)
14. [推N返1 活动](#14-推n返1-活动)
15. [提现流程](#15-提现流程)

---

## 1. 产品概述

### 1.1 定位

面向中国地摊、小摊贩、小商铺主（统称"商户"）的轻量级 SaaS 平台。让没有技术能力的摊主能在 10 分钟内开出自己的线上店铺，并借助 AI 生成推广视频发到抖音/微信。

### 1.2 核心价值

| 角色 | 价值 |
|------|------|
| 商户 | 开店零门槛、AI 视频推广、扫码收款核销 |
| 用户/消费者 | 附近好店一键找、扫码进店下单 |
| 平台 | 年费 + AI 成片按次收费 + 推3返1裂变 |

### 1.3 收费模式

- **年费**：300 元/年/商户
- **AI 成片**：10 元/次（首次免费 1 次）
- **裂变**：推荐 3 个商户成功付费 → 返 1 年免费续费
- **试用期**：审核通过后 30 天免费试用

---

## 2. 整体架构

```
┌─────────────────────────────────────────────────────────┐
│                      前端层                              │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │  用户小程序   │  │  商户小程序   │  │  管理后台(Vue) │  │
│  │  (UniApp)    │  │  (UniApp)    │  │               │  │
│  └──────────────┘  └──────────────┘  └───────────────┘  │
└─────────────────────────────────────────────────────────┘
                           │
┌─────────────────────────────────────────────────────────┐
│                    API 网关层                            │
│          Spring Cloud Gateway + 租户路由                  │
└─────────────────────────────────────────────────────────┘
                           │
┌─────────────────────────────────────────────────────────┐
│                    业务模块层                            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐  │
│  │  商户模块 │ │  商城模块 │ │  营销模块 │ │  支付模块  │  │
│  │(merchant)│ │  (mall)  │ │(promotion)│ │  (pay)   │  │
│  └──────────┘ └──────────┘ └──────────┘ └───────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────────────┐ │
│  │  会员模块 │ │  AI模块  │ │       文件/OSS 模块       │ │
│  │ (member) │ │  (ai)    │ │                          │ │
│  └──────────┘ └──────────┘ └──────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
                           │
┌─────────────────────────────────────────────────────────┐
│                    数据层                               │
│  MySQL (多租户行隔离)  Redis (缓存)  OSS (图片/视频)      │
└─────────────────────────────────────────────────────────┘
```

### 2.1 多租户隔离策略

- 每个商户 = 一个租户（`tenant_id`）
- 所有业务表均含 `tenant_id` 列，由 `TenantLineInnerInterceptor` 自动过滤
- 商户小程序登录后，JWT 中携带 `tenantId`，每次请求自动隔离数据

---

## 3. 商户入驻流程

### 3.1 入口

- **用户小程序**首页底部固定入口："我要开店"
- **独立 H5 落地页**（可被分享/扫码进入）

### 3.2 申请步骤（共 5 步）

```
第1步：填写基本信息
  - 店铺名称（必填，最多 20 字）
  - 经营类目（单选，从平台预设分类选）
  - 联系手机号（必填，接收验证码验证）
  - 推荐人手机号（选填，推3返1裂变来源）

第2步：上传资质材料
  - 营业执照照片（必填）
    → 支持拍照/从相册选择
    → 仅上传图片，由管理员人工审核，无需OCR
  - 法人身份证正面（必填）
  - 法人身份证背面（必填）
    → 仅上传图片，无需OCR识别

第3步：摊位/店铺位置授权
  - 弹出位置授权提示（"授权位置，消费者可通过地图找到您的店"）
  - 用户点击允许 → 获取当前 GPS 坐标
  - 地图展示当前位置，支持手动拖拽纠正
  - 填写详细地址（如：XX 夜市 B 区 23 号摊位）

第4步：微信收款配置（可跳过，后续补充）
  - 提示：开通微信支付，消费者可扫码付款
  - 选项一：已有微信商户号（填写 mchid）
  - 选项二：申请成为平台子商户（跳转微信特约商户申请流程）
  - 选项三：暂不配置（现金收款）

第5步：确认提交
  - 汇总展示填写内容
  - 勾选《服务协议》《隐私政策》
  - 提交申请
```

### 3.3 审核流程

```
商户提交申请
    │
    ▼
系统自动预审（黑名单匹配 + 资料完整性校验）
    │
    ├── 不通过 → 短信通知商户，说明原因，可重新提交
    │
    ▼
人工审核（管理后台，审核员操作）
    │
    ├── 通过  → 自动开通租户 → 发送开通短信 → 进入30天试用
    │
    └── 驳回 → 短信通知原因，可修改后重新提交
```

### 3.4 数据模型：商户申请表

```sql
CREATE TABLE `merchant_apply` (
  `id`              bigint      NOT NULL AUTO_INCREMENT,
  `shop_name`       varchar(50) NOT NULL        COMMENT '店铺名称',
  `category_id`     bigint      NOT NULL        COMMENT '经营类目',
  `mobile`          varchar(20) NOT NULL        COMMENT '联系手机号',
  `referrer_mobile` varchar(20)                 COMMENT '推荐人手机号',
  -- 资质材料
  `license_url`     varchar(512) NOT NULL       COMMENT '营业执照图片URL',
  `license_no`      varchar(50)                 COMMENT '统一社会信用代码',
  `legal_name`      varchar(30)                 COMMENT '法人姓名',
  `id_card_front`   varchar(512) NOT NULL       COMMENT '身份证正面URL',
  `id_card_back`    varchar(512) NOT NULL       COMMENT '身份证背面URL',
  `id_card_no`      varchar(30)                 COMMENT '身份证号（脱敏存储）',
  -- 位置信息
  `longitude`       decimal(10,7)               COMMENT '经度',
  `latitude`        decimal(10,7)               COMMENT '纬度',
  `address`         varchar(200)                COMMENT '详细地址',
  -- 审核
  `status`          tinyint     NOT NULL DEFAULT 0 COMMENT '0待审核 1通过 2驳回',
  `reject_reason`   varchar(200)                COMMENT '驳回原因',
  `auditor_id`      bigint                      COMMENT '审核员用户ID',
  `audit_time`      datetime                    COMMENT '审核时间',
  -- 租户
  `tenant_id`       bigint                      COMMENT '开通后关联的租户ID',
  `creator`         varchar(64) NOT NULL DEFAULT '',
  `create_time`     datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater`         varchar(64)          DEFAULT '',
  `update_time`     datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`         tinyint(1)  NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB COMMENT='商户入驻申请';
```

---

## 4. 租户开通与试用

### 4.1 审核通过后自动执行

```
1. 创建系统租户（tenant 表插入记录）
2. 初始化租户数据：
   - 创建商户管理员账号（手机号 + 随机密码）
   - 创建默认店铺信息（shop_name / address / location）
   - 生成商家专属小程序二维码（scene=shopId）
   - 生成商家专属 H5 链接
3. 设置试用期：
   - 到期时间 = 当前时间 + 30 天
   - 赠送 AI 成片次数 = 1 次
   - 写入 tenant_subscription 表
4. 发送短信：
   - 内容："您的店铺【{shop_name}】已开通，登录商户小程序开始经营。
     初始密码：{password}，请登录后修改。"
```

### 4.2 订阅状态表

```sql
CREATE TABLE `tenant_subscription` (
  `id`                bigint   NOT NULL AUTO_INCREMENT,
  `tenant_id`         bigint   NOT NULL COMMENT '租户ID',
  `status`            tinyint  NOT NULL DEFAULT 1 COMMENT '1试用 2正式 3过期 4禁用',
  `expire_time`       datetime NOT NULL COMMENT '到期时间',
  `ai_video_quota`    int      NOT NULL DEFAULT 0 COMMENT '剩余AI成片次数',
  `ai_video_used`     int      NOT NULL DEFAULT 0 COMMENT '已用AI成片次数',
  -- ...BaseDO 公共字段
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`)
) ENGINE=InnoDB COMMENT='租户订阅状态';
```

### 4.3 推3返1裂变

```
推荐人A 推荐了 商户B、商户C、商户D（3人均完成付费）
    → 系统自动延长 A 的到期时间 +1年
    → 短信通知 A："恭喜！您已成功推荐3位商户，账号已免费续期1年"

记录表：merchant_referral
  - referrer_tenant_id （推荐人）
  - referee_tenant_id  （被推荐人）
  - paid_at            （被推荐人付费时间）
  - rewarded           （是否已触发返利）
```

---

## 5. AI 一键成片

### 5.1 功能概述

商户上传商品/摊位照片 + 简短描述 → AI 生成推广视频（带商户二维码结尾）→ 可直接下载/分享到抖音微信。

### 5.2 素材要求设计

| 项目 | 规格 | 说明 |
|------|------|------|
| 图片数量 | 3～9 张 | 建议 5 张（商品封面1张 + 细节3张 + 环境1张） |
| 图片格式 | JPG/PNG | 单张 ≤ 10MB，自动压缩 |
| 图片比例 | 推荐 9:16 或 1:1 | 竖版视频效果更好 |
| 文案描述 | 20～200 字 | 语音输入 或 文字输入 |
| 视频时长 | 15～30 秒 | 系统自动控制 |
| 视频分辨率 | 1080×1920 (竖屏) | 适配抖音/微信 |

**图片最佳实践引导（在上传界面显示）：**
```
- 第1张：主打商品/摊位全景（封面用）
- 第2-4张：商品细节特写
- 第5张：顾客消费/摊位环境氛围
```

### 5.3 文案生成流程

```
商户输入简短描述（例："我是卖烤地瓜的，烤箱现烤，香甜软糯，5块钱一个"）
    │
    ▼
后台调用大语言模型（如通义千问/文心一言）
Prompt 模板：
  """
  你是一位专业的短视频文案策划。
  商品简介：{user_input}
  店铺名称：{shop_name}
  请生成一段适合抖音/微信短视频的推广文案，要求：
  1. 开头3秒要有吸引力（钩子）
  2. 突出商品特色和性价比
  3. 结尾引导用户扫码/进店
  4. 总字数 150-250 字，口语化，有节奏感
  输出：逐句台词（每句不超过 15 字，换行分隔）
  """
    │
    ▼
返回 AI 生成文案（商户可预览并手动修改）
    │
    ▼
商户确认文案 → 触发视频合成
```

### 5.4 视频合成流程

```
输入：
  - 图片列表（OSS URLs）
  - 文案台词（逐句）
  - 商户二维码图片（含 shopId 参数）
  - 背景音乐（平台提供 10 种风格选择）

合成步骤：
  1. 调用 AI 成片 API（推荐：即梦AI / 可灵 / 剪映开放平台）
  2. 图片序列 → 视频片段（Ken Burns 效果：缓慢推拉）
  3. AI 配音（TTS 朗读文案台词）或 选择纯背景音乐
  4. 字幕自动生成（基于台词）
  5. 结尾 3 秒：
     - 黑底白字："扫码进店"
     - 显示商户二维码（居中，大号）
     - 叠加店铺名称
  6. 输出 MP4，上传至 OSS

输出：
  - 视频 OSS URL
  - 封面图 URL（截取第一帧或商户上传的封面图）
```

### 5.5 二维码规则

```
小程序码参数：scene=shop_{shopId}
扫码后跳转逻辑：
  1. 进入用户小程序
  2. 自动识别 shopId
  3. 跳转到该商户店铺首页
  4. 调用 wxSubscribe 自动收藏该店铺（弹出授权提示）
  5. 如用户未登录 → 先手机号一键登录 → 再执行上述逻辑
```

### 5.6 计费

```
每次生成（文案 + 视频）扣除 1 次配额
配额来源：
  - 开店赠送 1 次
  - 购买套餐：10次/88元、30次/198元、100次/498元
扣费时机：视频合成成功后
合成失败：不扣费，自动退回配额
```

### 5.7 数据库：AI 成片任务表

```sql
CREATE TABLE `ai_video_task` (
  `id`               bigint      NOT NULL AUTO_INCREMENT,
  `tenant_id`        bigint      NOT NULL,
  `user_id`          bigint      NOT NULL COMMENT '操作的商户管理员',
  -- 输入
  `image_urls`       text        NOT NULL COMMENT '图片URL列表（JSON数组）',
  `user_description` varchar(500) NOT NULL COMMENT '用户原始描述',
  `ai_copywriting`   text        COMMENT 'AI生成文案（逐句JSON）',
  `final_copywriting` text       COMMENT '用户确认后的最终文案',
  `bgm_id`           int         COMMENT '背景音乐ID',
  -- 输出
  `status`           tinyint     NOT NULL DEFAULT 0
                     COMMENT '0待处理 1文案生成中 2等待用户确认 3视频合成中 4完成 5失败',
  `video_url`        varchar(512) COMMENT '生成视频OSS URL',
  `cover_url`        varchar(512) COMMENT '封面图OSS URL',
  `fail_reason`      varchar(200) COMMENT '失败原因',
  `quota_deducted`   tinyint(1)  NOT NULL DEFAULT 0 COMMENT '是否已扣配额',
  -- ...BaseDO
  PRIMARY KEY (`id`)
) ENGINE=InnoDB COMMENT='AI成片任务';
```

---

## 6. 用户小程序

### 6.1 页面结构（Tab Bar）

```
┌──────────────────────────────────────┐
│           底部导航（4个Tab）           │
│  首页  │  附近  │  分类  │  我的      │
└──────────────────────────────────────┘
```

### 6.2 首页设计

```
┌─────────────────────────────────┐
│  🔍 搜索框 "搜索商品/店铺"         │
├─────────────────────────────────┤
│  📍 当前位置：XX市XX区  [切换]     │
├─────────────────────────────────┤
│  【最近去过的店铺】               │
│  横向滚动卡片（头像+店名+上次时间） │
│  最多显示 10 家                   │
├─────────────────────────────────┤
│  【附近热门店铺】（按销量排序）     │
│  瀑布流卡片，每张显示：            │
│    - 店铺封面图                   │
│    - 店名 + 经营类目              │
│    - ⭐评分 + 月销量              │
│    - 距离（基于用户位置）          │
│    - [进店] 按钮                  │
└─────────────────────────────────┘
```

### 6.3 附近页（地图模式）

```
┌─────────────────────────────────┐
│       高德/腾讯地图               │
│  用户位置：🧍                    │
│  商户位置：🏪（点击显示店铺卡片）   │
│                                 │
│  底部抽屉：附近商户列表（可上拉）   │
│  - 支持筛选：分类 / 距离范围       │
│  - 每项显示：头像、名称、距离、      │
│    [导航] [进店] 两个按钮          │
│  点击[导航] → 调起高德/微信地图    │
└─────────────────────────────────┘
```

### 6.4 分类页

```
左侧固定分类列表（一级分类）：
  蔬果生鲜 / 熟食小吃 / 服装饰品 / 日用百货
  / 手工艺品 / 农副产品 / 其他

右侧：该分类下商户列表
  - 按销量排序（默认）/ 按距离排序 / 按评分排序
  - 每行两列瀑布流
```

### 6.5 我的页

```
[头像 + 昵称]（微信一键登录）

订单中心：
  - 待付款 / 待发货 / 待收货 / 待评价 / 退款

我的收藏（收藏的店铺 + 收藏的商品）

足迹（最近浏览）

地址管理

帮助与客服

---

[我要开店] 入口（固定展示）
```

### 6.6 店铺详情页

```
店铺 Banner（首图）
店铺名称 + 类目 + 评分 + 月销
[收藏店铺] [导航] [分享]

商品列表（瀑布流）：
  - 商品图、名称、价格、销量
  - 点击进入商品详情

店铺公告（如有）
```

### 6.7 排名规则

```java
// 综合排名分数（每日凌晨重新计算）
score = sales_30d * 0.5        // 近30天销量权重 50%
      + avg_rating * 10 * 0.3  // 评分权重 30%
      + is_new_shop * 50 * 0.2  // 新店加成（开店<30天）20%
```

---

## 7. 商户小程序

### 7.1 功能模块

```
商户小程序（独立 AppID）
│
├── 登录：手机号 + 密码 / 短信验证码
│   └── 登录后绑定微信 OpenID（后续免密登录）
│
├── 首页（数据看板）
│   ├── 今日订单数 / 营业额
│   ├── 本月订单数 / 营业额
│   ├── 待处理事项：[待发货 N] [待核销 N] [退款申请 N]
│   └── 快捷入口：[扫码核销] [发布商品] [AI成片]
│
├── 订单管理
│   ├── 全部 / 待付款 / 待发货 / 待核销 / 已完成 / 退款
│   ├── 自提订单：显示核销码（6位数字）
│   └── 快递订单：填写快递单号 / 确认发货
│
├── 扫码核销（独立页面，首页快捷入口）
│   └── 详见第8章
│
├── 商品管理
│   ├── 商品列表（上架/下架/编辑/删除）
│   ├── 发布商品（极简模式，详见 7.3）
│   └── 商品积分设置（每个商品可设置购买赠送积分数）
│
├── 会员管理
│   ├── 会员列表（按消费金额/次数排序）
│   ├── 会员详情（消费记录、积分余额、推广关系）
│   └── 积分手动发放
│
├── 推广与返佣
│   ├── 返佣开关（是否开启推广大使功能）
│   ├── 推广大使列表（谁在帮我推广）
│   ├── 佣金记录（哪笔订单产生了佣金）
│   ├── 推N返1设置（推荐几单返佣1单，详见第13章）
│   └── 参与推N返1的商品设置
│
├── 数据统计
│   ├── 销售趋势图（近7/30天）
│   ├── 商品销量排行
│   └── 会员增长趋势
│
├── 提现管理
│   ├── 用户提现申请列表（待我处理）
│   └── 我向平台申请提现
│
├── 店铺设置
│   ├── 基本信息（名称/简介/封面/营业时间）
│   ├── 配送设置（自提 / 快递 / 同城配送）
│   ├── 积分规则（消费1元=N积分，由商户自定义）
│   ├── 支付设置（微信支付配置）
│   └── 我的二维码（下载店铺码）
│
└── AI 成片
    ├── 创建新视频
    ├── 历史成片记录
    └── 购买配额
```

### 7.2 店铺首页看板（UI 设计要点）

```
┌─────────────────────────────────┐
│  👋 早上好，[店主姓名]            │
│  【铺星商户】到期：2027-04-14    │
├─────────────────────────────────┤
│  今日营业    本月累计             │
│  ¥1,280     ¥28,600            │
│  23单        560单              │
├─────────────────────────────────┤
│  待处理：待核销 5 | 待发货 2      │
├─────────────────────────────────┤
│  [📷扫码核销] [📦发货] [🎬AI成片] │
│  [🛒发布商品] [📊数据] [⚙️设置]   │
└─────────────────────────────────┘
```

### 7.3 极简商品发布

面向不懂操作的摊主，发布一件商品只需 3 个输入项。

**发布流程（商户小程序）：**

```
点击 [发布商品]
    │
    ▼
① 拍照 / 从相册选择商品主图（1张，必填）
    │
    ▼
② 填写商品名称（必填，最多 30 字）
    │
    ▼
③ 填写商品价格（必填，单位：元）
    │
    ▼
④ 分类（默认选中"小吃"，可切换其他分类）
    │
    ▼
⑤ 配送方式（二选一或都选）：
    - [x] 到店自提（默认勾选）
    - [ ] 快递配送 → 弹出运费设置（固定运费，单位元）
    │
    ▼
⑥ 点击 [立即上架] → 商品自动上架，无需审核
```

**其他可选设置（在商品详情页后续编辑）：**
- 库存数量（不填则不限库存）
- 商品详情描述（文字或多图）
- 购买赠送积分数
- 是否参与推N返1活动

**数据库字段说明（product_spu / product_sku）：**

```
product_spu:
  name          商品名
  category_id   分类（默认为"小吃"分类ID）
  pic_url       主图
  price         价格（冗余自sku）
  status        1=上架（默认）
  delivery_types  1=自提，2=快递（逗号分隔存VARCHAR）

product_sku:
  price         单价
  stock         库存（NULL=不限）
  brokerage_first_percent   一级佣金比例（%）
  brokerage_second_percent  二级佣金比例（%）
```

---

## 13. 推广大使与返佣体系

### 13.1 概念说明

| 角色 | 说明 |
|------|------|
| 商户 | 开启返佣功能后，自动激活推广大使体系 |
| 推广大使 | 在该商户成功消费过1次的用户，自动成为推广大使 |
| 下级用户 | 通过推广大使的分享码/链接进入并下单的用户 |

> 推广关系是**商户维度**的，不是全平台共享。用户A在商户甲是推广大使，在商户乙可能没有这个角色。

### 13.2 推广大使激活流程

```
用户在某商户下单并支付成功
    │
    ▼
检查该商户是否开启返佣（shop_brokerage_config.enabled = true）
    │
    ├── 未开启 → 不处理
    │
    ▼（开启）
检查该用户是否已是该商户的推广大使
    │
    ├── 已是 → 不处理
    │
    ▼（首次）
自动成为推广大使：
  - 生成该用户在该商户的专属分享码（6位字母数字，如 A3X9P2）
  - 写入 brokerage_user 表（user_id + tenant_id）
  - 可在用户小程序"我的"→"推广中心"查看分享码和佣金
```

### 13.3 分享码绑定下级

```
推广大使A 分享链接/二维码给朋友B
链接携带参数：?referrer=A3X9P2&shopId=xxx
    │
    ▼
朋友B 打开链接（或扫码）进入商户店铺
    │
    ▼
系统检查B是否已绑定该商户的上级推广关系：
  - 未绑定 → 绑定 A 为 B 在该商户的上级（写入 brokerage_user.bind_user_id）
  - 已绑定 → 忽略（先绑定原则，不覆盖）
    │
    ▼
B 在该商户下单后，系统自动给A结算佣金
```

### 13.4 佣金结算规则

```
商户可在"推广与返佣"设置中配置：
  - 一级佣金比例（推广大使直接下级消费）：如 5%
  - 二级佣金比例（推广大使的下级的下级消费）：如 2%
  - 也可在单个商品上单独设置佣金比例（覆盖全局配置）

结算时机：订单状态变为"已完成"时（自提核销成功 或 确认收货）
结算金额：实付金额 × 对应佣金比例
佣金状态：冻结 → 7天后解冻为可提现余额（防退款纠纷）
```

### 13.5 积分体系

```
商户在"店铺设置→积分规则"中设置：
  - 消费积分：消费1元 = N积分（N由商户设定，如1元=10积分）
  - 商品积分：可在单个商品上单独设置购买赠送积分数

积分来源：
  - 消费赠积分（按金额）
  - 单品特殊积分（商户手动设置）
  - 商户手动发放（会员管理页）

积分用途（v1.0 先做记录，用途可后期扩展）：
  - 暂作为会员忠诚度标识，展示在会员列表
  - 后期可扩展为：积分兑换商品、积分抵扣
```

### 13.6 数据库设计

```sql
-- 商户返佣配置（每个租户一条）
CREATE TABLE `shop_brokerage_config` (
  `id`                        bigint  NOT NULL AUTO_INCREMENT,
  `tenant_id`                 bigint  NOT NULL UNIQUE,
  `enabled`                   tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否开启返佣',
  `first_brokerage_percent`   decimal(5,2) NOT NULL DEFAULT 0 COMMENT '一级佣金比例%',
  `second_brokerage_percent`  decimal(5,2) NOT NULL DEFAULT 0 COMMENT '二级佣金比例%',
  `freeze_days`               int NOT NULL DEFAULT 7 COMMENT '佣金冻结天数',
  -- 推N返1设置
  `push_return_enabled`       tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否开启推N返1',
  `push_n`                    int NOT NULL DEFAULT 3 COMMENT '推N单',
  `return_amount`             int NOT NULL DEFAULT 1 COMMENT '返1单（积分/金额）',
  -- 积分规则
  `point_per_yuan`            int NOT NULL DEFAULT 10 COMMENT '消费1元赠多少积分',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB COMMENT='商户返佣与积分配置';

-- 商品返佣参与设置（在 product_sku 表新增字段）
-- ALTER TABLE product_sku
--   ADD COLUMN brokerage_first_percent  decimal(5,2) COMMENT '一级佣金（NULL=用全局配置）',
--   ADD COLUMN brokerage_second_percent decimal(5,2) COMMENT '二级佣金（NULL=用全局配置）',
--   ADD COLUMN push_return_join         tinyint(1) DEFAULT 0 COMMENT '是否参与推N返1';
```

---

## 14. 推N返1 活动

### 14.1 平台级推N返1（商户裂变）

针对**平台**推广商户：推荐 N 个商户成功付费 → 推荐人获得 1 年免费续期。
- 配置入口：管理后台 → 平台设置 → 推N返1
- 当前默认：推 3 个 → 返 1 年
- 逻辑见第 4.3 节

### 14.2 商户级推N返1（用户裂变）

针对**商户店铺内**的用户推广：推广大使帮助商户推荐 N 单 → 商户给推广大使返 1 单金额的佣金（或其他奖励）。

**设置（商户端）：**

```
商户小程序 → 推广与返佣 → 推N返1设置
  - 开关：是否开启
  - 推N：推荐带来几单有效订单触发奖励（如 5 单）
  - 返1：奖励内容（如：返现10元 / 赠送20积分）
  - 参与商品：全部商品 或 指定商品参与（在商品编辑页勾选）
```

**触发逻辑：**

```
推广大使A 通过分享码带来了 B、C、D、E、F 共5人下单（均已完成）
    │
    ▼
系统统计A在该商户的有效推广完成订单数 >= N（5单）
    │
    ▼
触发奖励：
  - 返现：写入A的佣金余额（brokerage_record，来源=推N返1奖励）
  - 积分：给A加积分
  - 通知A："恭喜！您的推广已达5单，获得10元奖励，可申请提现"
    │
    ▼
重置计数 or 累计计数（商户可配置：每N单奖励1次 or 只奖励1次）
```

---

## 15. 提现流程

### 15.1 资金流向总览

```
消费者付款
    │ 100元
    ▼
平台收款账户（微信服务商收款 or 各商户直收）
    │
    ├── 商户佣金余额（存在平台账本）
    │     └── 商户申请提现 → 平台人工转账到商户收款码
    │
    └── 用户佣金余额（存在平台账本，归属该商户下）
          └── 用户申请提现 → 商户审核并线下/线上支付给用户
```

### 15.2 用户提现流程

```
用户在商户店铺积累了佣金余额（如：推广佣金 ¥38.5）

用户小程序 → 我的 → 推广中心 → 申请提现
  - 填写提现金额（≥ 设定最低提现额，如1元）
  - 选择到账方式：微信零钱（需商户支持）/ 到店领取现金
  - 提交申请
    │
    ▼
商户小程序 → 提现管理 → 用户提现申请列表
  [申请人头像+昵称] [申请金额] [申请时间] [到账方式]
  [拒绝] [确认已支付]
    │
    ├── 拒绝 → 填写原因 → 通知用户，佣金余额退回
    │
    └── 确认已支付 → 用户佣金余额扣减，状态=已完成
                  → 通知用户："您的提现申请已处理，¥38.5已到账"
```

### 15.3 商户提现流程

```
商户在平台积累了佣金/返利余额（来源：推荐商户奖励、平台返利等）

商户小程序 → 提现管理 → 我向平台申请提现
  - 填写提现金额
  - 上传收款二维码（微信/支付宝收款码截图）
  - 提交申请
    │
    ▼
管理后台 → 提现管理 → 商户提现申请列表
  平台运营人员审核
    │
    ├── 驳回 → 说明原因 → 余额退回
    │
    └── 通过 → 运营人员扫描商户收款码人工转账
           → 在后台点击"确认已转账"并上传转账截图
           → 通知商户："您的提现申请已处理"
```

### 15.4 数据库设计

```sql
-- 佣金账户余额（用户维度，归属某商户）
CREATE TABLE `brokerage_wallet` (
  `id`              bigint NOT NULL AUTO_INCREMENT,
  `tenant_id`       bigint NOT NULL COMMENT '归属商户',
  `user_id`         bigint NOT NULL COMMENT '用户ID',
  `balance`         int    NOT NULL DEFAULT 0 COMMENT '可提现余额（分）',
  `frozen_balance`  int    NOT NULL DEFAULT 0 COMMENT '冻结中余额（分）',
  `total_earned`    int    NOT NULL DEFAULT 0 COMMENT '累计获得佣金（分）',
  `total_withdrawn` int    NOT NULL DEFAULT 0 COMMENT '累计已提现（分）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_user` (`tenant_id`, `user_id`)
) ENGINE=InnoDB COMMENT='用户佣金钱包（商户维度）';

-- 提现申请
CREATE TABLE `brokerage_withdraw_apply` (
  `id`            bigint      NOT NULL AUTO_INCREMENT,
  `tenant_id`     bigint      NOT NULL COMMENT '归属商户',
  `apply_type`    tinyint     NOT NULL COMMENT '1=用户提现 2=商户提现',
  `user_id`       bigint      COMMENT '用户ID（apply_type=1时有值）',
  `amount`        int         NOT NULL COMMENT '申请金额（分）',
  `withdraw_type` tinyint     NOT NULL COMMENT '1=微信零钱 2=到店现金 3=收款码（商户用）',
  `account_info`  varchar(512) COMMENT '收款信息（收款码图片URL或备注）',
  `status`        tinyint     NOT NULL DEFAULT 0 COMMENT '0待审核 1已支付 2已驳回',
  `remark`        varchar(200) COMMENT '驳回原因或备注',
  `pay_proof_url` varchar(512) COMMENT '转账凭证截图（商户提现用）',
  `pay_time`      datetime    COMMENT '确认支付时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB COMMENT='提现申请';
```

---

## 8. 核销与配送

### 8.1 自提核销流程（核心功能）

```
用户下单（选择"到店自提"）
    │
    ▼
生成核销码（6位纯数字，有效期与订单相同）
存储在 trade_order.pickup_verify_code 字段
    │
    ▼
用户到店，出示核销码（在用户小程序"我的订单"中显示）
    │
    ▼
商户操作（两种方式）：
  方式A：扫描用户出示的二维码/条形码
    → 商户小程序 → [扫码核销] → 调起相机扫描
    → 识别核销码 → 展示订单信息（商品、金额、用户昵称）
    → 商户点击[确认核销] → 订单状态更新为"已完成"

  方式B：输入核销码
    → 商户小程序 → [扫码核销] → 切换"手动输入"
    → 输入6位核销码 → 同上
    │
    ▼
系统：
  - 更新 trade_order.status = COMPLETED
  - 更新 trade_order.pickup_verify_time = now()
  - 给用户发送"已完成"通知
  - 触发积分/佣金结算
```

### 8.2 订单核销码设计

```sql
-- 在 trade_order 表中添加字段（已有表，新增列）
ALTER TABLE `trade_order`
  ADD COLUMN `delivery_type`        tinyint COMMENT '1快递 2自提',
  ADD COLUMN `pickup_verify_code`   varchar(10) COMMENT '自提核销码（6位数字）',
  ADD COLUMN `pickup_verify_time`   datetime COMMENT '核销时间',
  ADD COLUMN `pickup_store_remark`  varchar(200) COMMENT '自提备注（摊位地址）';
```

### 8.3 快递订单流程

```
用户下单（选择"快递配送"）
    │
    ▼
系统生成订单，状态"待发货"
    │
    ▼
商户在商户小程序 → 订单管理 → 待发货列表
    │
    ▼
商户操作：
  点击订单 → [发货] → 选择快递公司 → 输入快递单号 → 确认发货
    │
    ▼
系统：更新状态为"已发货"，推送快递信息给用户
    │
    ▼
（可选）商户手动点击[确认送达]
 或 用户确认收货
    → 订单完成
```

### 8.4 商户主动确认送达

对于同城配送或有自己配送能力的商户，提供"确认送达"按钮：

```
商户小程序 → 订单详情 → [确认已送达]
  → 二次确认弹窗："确认商品已送达客户手中？"
  → 确认 → 订单状态 → COMPLETED
  → 通知用户（微信服务通知）
```

---

## 9. 扫码入店流程

### 9.1 商户二维码类型

| 类型 | 场景 | 参数 |
|------|------|------|
| 店铺码 | 贴在摊位、印在宣传物料 | `scene=shop_{shopId}` |
| 商品码 | 单品推广 | `scene=sku_{skuId}` |
| AI 视频结尾码 | 抖音/微信视频结尾 | `scene=shop_{shopId}&from=video` |

### 9.2 完整扫码入店流程

```
用户扫描商户二维码
    │
    ▼
进入用户小程序（pages/shop/index?shopId=xxx&from=xxx）
    │
    ├── 用户未登录？
    │     └── 弹出手机号授权页
    │           - 微信手机号一键获取（getPhoneNumber）
    │           - 系统自动注册/登录
    │           - 绑定微信 UnionID
    │
    ▼（已登录）
加载店铺首页
    │
    ▼
自动收藏该店铺（静默操作，不打扰用户）
  写入 product_favorite 表（type=SHOP）
  若已收藏则忽略
    │
    ▼
记录浏览足迹
  写入 product_browse_history 表
    │
    ▼
若 from=video（来自AI推广视频）：
  - 埋点：ai_video_scan_count +1
  - 可展示"扫码专属优惠"弹窗（商户可配置）
```

### 9.3 手机号绑定规则

- 一个微信 OpenID 只能绑定一个手机号
- 同一手机号可在多个商户店铺使用（同一用户账号）
- 申请商户时填写的手机号 = 商户管理员账号，与用户账号体系分离

---

## 10. 数据库设计补充

### 10.1 新增表清单

```
merchant_apply          - 商户入驻申请
merchant_referral       - 推荐裂变记录
tenant_subscription     - 租户订阅状态
ai_video_task           - AI成片任务
ai_video_bgm            - 背景音乐素材
shop_info               - 店铺详细信息（扩展tenant基础信息）
shop_qrcode             - 店铺码管理
```

### 10.2 shop_info 表

```sql
CREATE TABLE `shop_info` (
  `id`            bigint      NOT NULL AUTO_INCREMENT,
  `tenant_id`     bigint      NOT NULL UNIQUE COMMENT '一个租户一个店铺',
  `shop_name`     varchar(50) NOT NULL,
  `category_id`   bigint      NOT NULL,
  `cover_url`     varchar(512) COMMENT '店铺封面图',
  `description`   varchar(500) COMMENT '店铺简介',
  `notice`        varchar(300) COMMENT '店铺公告',
  `longitude`     decimal(10,7) COMMENT '经度',
  `latitude`      decimal(10,7) COMMENT '纬度',
  `address`       varchar(200) COMMENT '详细地址',
  `business_hours` varchar(100) COMMENT '营业时间（如 09:00-22:00）',
  `mobile`        varchar(20) COMMENT '客服电话',
  `status`        tinyint NOT NULL DEFAULT 1 COMMENT '1正常 2暂停营业 3违规关闭',
  `sales_30d`     int NOT NULL DEFAULT 0 COMMENT '近30天销量（缓存，每日更新）',
  `avg_rating`    decimal(3,1) NOT NULL DEFAULT 5.0 COMMENT '平均评分',
  -- ...BaseDO
  PRIMARY KEY (`id`),
  KEY `idx_category_sales` (`category_id`, `sales_30d`),
  KEY `idx_location` (`longitude`, `latitude`)
) ENGINE=InnoDB COMMENT='店铺信息';
```

---

## 11. 接口设计

### 11.1 用户端核心接口

```
# 商户入驻
POST   /app-api/merchant/apply/submit       提交入驻申请
POST   /app-api/merchant/apply/ocr-license  营业执照OCR识别
POST   /app-api/merchant/apply/ocr-idcard   身份证OCR识别
GET    /app-api/merchant/apply/status       查询申请状态

# 首页
GET    /app-api/shop/list/nearby            附近店铺列表（lat/lng/radius）
GET    /app-api/shop/list/recent            最近访问店铺
GET    /app-api/shop/list/category          按分类店铺列表
GET    /app-api/shop/{shopId}               店铺详情

# 扫码入店
POST   /app-api/shop/scan-enter             扫码进店（记录足迹+自动收藏）
```

### 11.2 商户端核心接口

```
# 认证
POST   /merchant-api/auth/login-by-mobile   手机号登录
POST   /merchant-api/auth/login-by-pwd      密码登录

# 核销
POST   /merchant-api/order/verify           核销订单（传入核销码）
GET    /merchant-api/order/verify/preview   核销预览（传入核销码，返回订单信息）

# AI成片
POST   /merchant-api/ai-video/create        创建成片任务
GET    /merchant-api/ai-video/{taskId}      查询任务状态
POST   /merchant-api/ai-video/{taskId}/confirm-copy  确认文案，触发视频合成
GET    /merchant-api/ai-video/list          历史成片列表
POST   /merchant-api/ai-video/quota/buy     购买配额

# 订单
GET    /merchant-api/order/list             订单列表（按状态筛选）
POST   /merchant-api/order/{id}/ship        发货（填写快递信息）
POST   /merchant-api/order/{id}/delivered   确认送达
```

### 11.3 管理端核心接口

```
# 商户审核
GET    /admin-api/merchant/apply/list       申请列表
POST   /admin-api/merchant/apply/{id}/approve  审核通过
POST   /admin-api/merchant/apply/{id}/reject   驳回
```

---

## 12. 部署与运维

### 12.1 最小可行部署（MVP）

```
服务器：2核4G × 1台（阿里云 ECS）
数据库：MySQL 8.0（RDS 或同机部署）
缓存：Redis 7.0
文件存储：阿里云 OSS（图片/视频）
短信：阿里云 SMS
地图：腾讯位置服务（小程序内置支持好）
AI文案：通义千问 API（阿里云）
AI成片：即梦AI / 剪映开放平台 API
```

### 12.2 开发优先级（MVP 路线）

```
Phase 1（4周）：
  ✅ 商户入驻申请 + 审核
  ✅ 租户开通 + 试用期
  ✅ 用户小程序：首页、附近、店铺详情
  ✅ 扫码入店 + 手机号绑定
  ✅ 基础商城：商品发布、下单、支付

Phase 2（3周）：
  ✅ 商户小程序：订单管理、核销
  ✅ AI 成片：文案生成
  ✅ 快递配送
  ✅ 推3返1裂变

Phase 3（3周）：
  ✅ AI 成片：视频合成 + 二维码植入
  ✅ 数据统计看板
  ✅ 评价系统
  ✅ 积分体系
```

### 12.3 注意事项

1. **营业执照/身份证图片**：涉及敏感信息，OSS 存储需设置私有权限，访问需签名 URL，有效期不超过 1 小时
2. **身份证号存储**：AES 加密存储，展示时脱敏（如：320***1234）
3. **AI 成片费用控制**：对接 AI API 前先校验配额，防止超额调用
4. **核销码防重**：核销操作加分布式锁（Redis），防止并发重复核销
5. **租户隔离验证**：所有商户端接口必须验证 JWT 中 tenantId 与操作数据的 tenant_id 一致性
