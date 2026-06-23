<template>
  <view class="page">
    <nav-bar :title="title" />
    <scroll-view scroll-y class="content">
      <view class="md-body">
        <view class="md-h1">{{ title }}</view>
        <view class="md-meta">版本 V1.0 · 生效日期 2026-05-28</view>

        <!-- 商户服务协议 -->
        <template v-if="type === 'merchant'">
          <view class="md-p md-quote">本协议由商户（以下简称"您"或"商户"）与拓小二 SaaS 平台（以下简称"平台"）共同签订。商户在平台完成入驻、付费订阅即视为同意全部条款。</view>

          <view class="md-h2">第一条 平台性质与服务边界</view>
          <view class="md-p">1.1 平台为<text class="b">独立的 SaaS 技术服务提供方</text>，向您出租以下能力：商城建站、订单系统、推广积分引擎、AI 视频生成、抖音对接、通联收款通道接入。</view>
          <view class="md-p">1.2 平台<text class="b">不参与您与消费者之间的任何买卖、不持有任何用户资金、不就您的商品质量与营销兑付承担担保责任</text>。</view>
          <view class="md-p">1.3 您与消费者之间形成<text class="b">独立的买卖合同关系</text>，平台仅作为技术信息撮合工具。</view>

          <view class="md-h2">第二条 资金 & 收款</view>
          <view class="md-p">2.1 您应在通联支付独立开通商户账户，由本人/法人/公司实名认证。所有顾客订单货款<text class="b">订单完成后直接进入您的通联账户</text>，平台不经手、不暂存、不分账。</view>
          <view class="md-p">2.2 您从通联账户提现到本人银行卡的全部流程由通联支付提供，与平台无关。</view>
          <view class="md-p">2.3 平台<text class="b">不收取商城 GMV 服务费 / 抽成</text>。您仅须支付订阅费（¥598/年 基础包 或 ¥1688/年 全功能包）。</view>

          <view class="md-h2">第三条 商户主体责任（重点）</view>
          <view class="md-p">3.1 <text class="b">您是商品销售方</text>：商品资质、税务发票、退换货、售后服务，全部由您独立承担。</view>
          <view class="md-p">3.2 <text class="b">您是营销活动主办方</text>：包括但不限于「推 N 反 1」「邀请有礼」「优惠池抽奖」「老客感谢奖」「消费积分」等所有营销规则，由您独立设定、独立审批、独立兑付。</view>
          <view class="md-p">3.3 <text class="b">您是积分兑付义务人</text>：用户获得的"推广积分"由您独立承诺兑现，兑付形式（购买本店所有商品 / 线下兑换现金 / 优惠券 / 实物）与额度由您决定。如因您经营状况无法兑付，<text class="b">用户应直接向您主张权利</text>，与平台无关。</view>
          <view class="md-p">3.4 <text class="b">营销争议最终解释权归您</text>：但您对营销规则与兑付承诺<text class="b">不得擅自违约</text>。</view>

          <view class="md-h2">第四条 商户禁止行为（高压线）</view>
          <view class="md-p">您不得：</view>
          <view class="md-p">（一）<text class="b">销售非自营商品 / 道具型空壳商品</text>用以套取推广奖励；</view>
          <view class="md-p">（二）<text class="b">以发展会员 / 拉人头</text>为目的开展营销，设置任何多层级返佣；</view>
          <view class="md-p">（三）<text class="b">收取入门费 / 加盟费</text>以取得购物或推广资格；</view>
          <view class="md-p">（四）发布<text class="b">投资 / 躺赚 / 保本保息 / 暴富</text>等任何承诺收益的宣传话术；</view>
          <view class="md-p">（五）虚假交易刷单 / 与员工合谋自购套取积分 / 套现；</view>
          <view class="md-p">（六）任何违反《中华人民共和国电子商务法》《反不正当竞争法》《禁止传销条例》《刑法》第二百二十四条之一 等法律法规的行为。</view>
          <view class="md-p"><text class="b">违规处理</text>：暂停服务 / 关闭店铺 / 冻结订阅费 / 已结订单货款由通联代管 / 平台向监管机关举报 / 移交司法机关。</view>

          <view class="md-h2">第五条 法定代表人 / 实控人个人连带责任</view>
          <view class="md-p">5.1 您（含法定代表人 / 实际控制人 / 经营者本人）<text class="b">自愿承诺对商户行为承担个人连带责任</text>。</view>
          <view class="md-p">5.2 若商户因传销 / 资金盘 / 非法集资 / 虚假宣传 等行为被监管或司法机关认定违法，导致用户损失、监管罚款或平台损失，<text class="b">由您个人承担连带赔偿</text>。</view>
          <view class="md-p">5.3 您不得以"公司有限责任"为由逃避兑付义务。</view>

          <view class="md-h2">第六条 订阅费与退款</view>
          <view class="md-p">6.1 订阅费按年预付，自付款日起一年内服务有效。</view>
          <view class="md-p">6.2 <text class="b">订阅费一经支付不予退款</text>。但您可在一年内一次性升级到更高套餐，差额抵扣。</view>
          <view class="md-p">6.3 因您违规被平台暂停服务的，订阅费不予退还。</view>

          <view class="md-h2">第七条 平台权利</view>
          <view class="md-p">平台有权对您发布的商品 / 营销规则 / 文案进行<text class="b">合规审核</text>，对违规内容直接下架、整改或停服，无须事先通知。</view>

          <view class="md-h2">第八条 争议解决</view>
          <view class="md-p">本协议适用中华人民共和国法律。本协议项下争议由 [平台注册地] 人民法院专属管辖。</view>
          <view class="md-p">您与消费者之间的商品交易或营销兑付争议，应<text class="b">由您直接处理</text>，平台仅在收到监管 / 司法通知时配合协查。</view>
        </template>

        <!-- 用户服务协议 -->
        <template v-else-if="type === 'user'">
          <view class="md-h2">第一条 平台性质说明</view>
          <view class="md-p">1.1 本平台为「商户营销服务工具平台」，性质为<text class="b">纯技术信息中介</text>。</view>
          <view class="md-p">1.2 平台本身<text class="b">不销售任何商品 / 不提供任何服务</text>，您在平台上购买的商品、享受的服务，均由独立经营的商户提供。</view>
          <view class="md-p">1.3 <text class="b">平台不对商品质量、商户经营行为、营销规则承担担保责任</text>。您与商户之间形成独立的买卖合同关系，与平台无关。</view>

          <view class="md-h2">第二条 营销激励性质说明</view>
          <view class="md-p">2.1 您在平台参与的「老顾客分享激励」「邀请有礼」「促销优惠抽奖」等营销活动，性质为：</view>
          <view class="md-p md-quote">- <text class="b">商户对老顾客的复购优惠</text></view>
          <view class="md-p md-quote">- 奖励来自<text class="b">商户自费</text>投入的营销优惠预算</view>
          <view class="md-p md-quote">- 与平台无关，与"投资"无关，与"传销"无关</view>
          <view class="md-p">2.2 上述激励严格符合以下规则：</view>
          <view class="md-p">（一）<text class="b">严格单层奖励</text>：仅奖励您的直接邀请人，不存在多层级返佣；</view>
          <view class="md-p">（二）<text class="b">不构成投资</text>：您参与任何营销活动均不构成任何形式的投资行为，不存在保证收益；</view>
          <view class="md-p">（三）<text class="b">不构成发展资格</text>：您购买商品的目的是消费商品本身，而非取得"推广资格"或"会员资格"；</view>
          <view class="md-p">（四）<text class="b">资金性质</text>：奖励资金为商户营销预算，非平台资金，非用户互助资金，非投资回报。</view>
          <view class="md-p">2.3 商户对营销活动的具体规则、奖励金额、提现条件等保留<text class="b">「最终解释权」</text>。您参与活动即视为接受商户全部规则，任何争议<text class="b">请直接联系对应商户处理</text>。</view>

          <view class="md-h2">第三条 用户禁止行为</view>
          <view class="md-p">您不得：</view>
          <view class="md-p">（一）注册多个账号刷取营销奖励；</view>
          <view class="md-p">（二）虚假交易 / 自买自卖 / 套现；</view>
          <view class="md-p">（三）在第三方平台传播误导性话术（包括但不限于"投资""躺赚""零成本暴富""加盟赚钱"）；</view>
          <view class="md-p">（四）利用本平台从事任何变相传销 / 资金盘 / 非法集资活动；</view>
          <view class="md-p">（五）冒用他人身份注册或交易；</view>
          <view class="md-p">（六）违反《中华人民共和国电子商务法》《禁止传销条例》《刑法》等法律法规的任何行为。</view>
          <view class="md-p">违规处理：封号 / 冻结账户余额 / 拒绝提现 / 已发放奖励追回 / 向工商监管举报 / 移交司法机关。</view>

          <view class="md-h2">第四条 资金兑付</view>
          <view class="md-p">4.1 您的「分享奖励」「推广积分」等的提现申请，由对应<text class="b">商户处理</text>。</view>
          <view class="md-p">4.2 若商户因经营状况无法履行兑付义务，您应<text class="b">直接向商户主张权利</text>，平台仅作为技术通道，<text class="b">不承担兑付保证责任</text>。</view>

          <view class="md-h2">第五条 免责条款</view>
          <view class="md-p">因不可抗力、商户经营行为、第三方原因导致您的损失，平台在法律允许的最大范围内不承担责任。</view>

          <view class="md-h2">第六条 账户与协议管理</view>
          <view class="md-p">6.1 您应妥善保管登录账号与短信验证码，不得借用他人账号或将账号转让。</view>
          <view class="md-p">6.2 您可随时<text class="b">注销账号</text>。注销后的账户余额（如有）按现行规则处理；已发放但未兑付的推广积分由对应商户独立处理。</view>
          <view class="md-p">6.3 平台有权根据法律法规与业务调整修订本协议，修订内容将通过 App / H5 公告通知。您继续使用即视为接受修订。</view>

          <view class="md-h2">第七条 争议解决</view>
          <view class="md-p">7.1 本协议适用中华人民共和国法律。本协议项下争议，由 [平台注册地] 人民法院专属管辖。</view>
          <view class="md-p">7.2 您与商户之间的商品交易争议、营销兑付争议，应<text class="b">直接向商户主张</text>，平台仅在收到监管 / 司法机关通知时配合协查。</view>

          <view class="md-h2">第八条 投诉与举报</view>
          <view class="md-p">如您发现商户或其他用户存在违法违规行为，请通过平台内"客服 / 投诉"入口提交，平台将在 5 个工作日内回应。涉及违法可直接向 12315 / 12321 举报。</view>
        </template>

        <!-- 隐私协议 -->
        <template v-else-if="type === 'privacy'">
          <view class="md-h2">一、信息收集范围</view>
          <view class="md-p">1.1 您提供的信息：手机号、昵称、头像、交易订单、收货地址、推荐关系。</view>
          <view class="md-p">1.2 自动收集：设备型号、操作系统、IP 地址、浏览行为、位置（仅在您授权时）。</view>

          <view class="md-h2">二、信息使用目的</view>
          <view class="md-p">（一）提供基础服务：注册登录 / 订单管理 / 支付结算 / 客服支持；</view>
          <view class="md-p">（二）营销活动：商户营销展示 / 推荐奖励计算 / 抽奖派发；</view>
          <view class="md-p">（三）安全合规：反欺诈 / 反传销 / 反洗钱监测；</view>
          <view class="md-p">（四）配合监管：依法配合监管机关、司法机关的合法调查。</view>

          <view class="md-h2">三、信息共享</view>
          <view class="md-p">3.1 平台<text class="b">不向第三方出售</text>您的个人信息。</view>
          <view class="md-p">3.2 在以下情况共享：商户（订单履约）、支付通道（完成支付）、物流公司（配送）、监管机关（依法）。</view>

          <view class="md-h2">四、信息存储</view>
          <view class="md-p">您的信息存储在境内服务器，按法律法规要求期限存储。</view>

          <view class="md-h2">五、您的权利</view>
          <view class="md-p">您有权访问、更正、删除您的个人信息，撤回授权，注销账户，投诉举报。</view>

          <view class="md-h2">六、未成年人保护</view>
          <view class="md-p">本平台不向 14 周岁以下未成年人提供服务。</view>

          <view class="md-h2">七、第三方 SDK 与 Cookie</view>
          <view class="md-p">7.1 为提供服务，平台 H5 端集成以下第三方组件：通联支付（收款 / 提现）、抖音开放平台（视频发布授权）、火山引擎（AI 视频生成）、地图服务（按需，仅显示附近店铺）。各 SDK 独立处理其必要信息，详见对应隐私政策。</view>
          <view class="md-p">7.2 平台使用 localStorage 与少量 cookie 维持登录态、保存推荐关系绑定、防止重复点击。您可随时清除浏览器缓存以删除。</view>

          <view class="md-h2">八、数据安全措施</view>
          <view class="md-p">8.1 传输层使用 HTTPS / TLS 1.2+ 加密。</view>
          <view class="md-p">8.2 存储层敏感字段（手机号、收款账户）按需脱敏 / 加密。</view>
          <view class="md-p">8.3 服务器部署在境内合规云厂商，按《网络安全法》《个人信息保护法》要求开展安全等级保护。</view>

          <view class="md-h2">九、协议修订与联系</view>
          <view class="md-p">9.1 本隐私政策可能根据法律法规与业务变化修订，修订后将通过 App / H5 公告通知。</view>
          <view class="md-p">9.2 个人信息保护投诉与咨询：通过平台内"客服 / 投诉"入口或邮件 privacy@doupaidoudian.com。涉及违法可向 12377 / 12321 举报。</view>
        </template>

        <view class="md-footer">
          【本协议受中华人民共和国法律保护】
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { ref } from 'vue';

const type = ref('user');
const title = computed(() => {
  if (type.value === 'privacy') return '隐私协议';
  if (type.value === 'merchant') return '商户服务协议';
  return '用户服务协议';
});

onLoad((q) => {
  const t = q?.type;
  if (t === 'privacy') type.value = 'privacy';
  else if (t === 'merchant') type.value = 'merchant';
  else type.value = 'user';
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page { min-height: 100vh; background: $bg-2; }
.content { height: calc(100vh - 88rpx); padding: 12px 16px 60px; }

.md-body {
  background: #fff;
  border-radius: $r-lg;
  padding: 20px 18px;
  box-shadow: $sh-1;
}

.md-h1 {
  font-size: 18px; font-weight: 900; color: $t1;
  text-align: center; margin-bottom: 8px;
}
.md-meta {
  text-align: center; color: $t4; font-size: 11px;
  padding-bottom: 14px; border-bottom: 1px solid $line;
  margin-bottom: 14px;
}

.md-h2 {
  font-size: 14.5px; font-weight: 800; color: $t1;
  margin: 18px 0 8px;
  padding-left: 8px;
  border-left: 3px solid $o;
}

.md-p {
  font-size: 13px; color: $t2; line-height: 1.7;
  margin-bottom: 6px;
}
.md-p .b {
  color: $o-d; font-weight: 700;
}
.md-p.md-quote {
  padding-left: 12px;
  border-left: 2px dashed $line;
  margin-left: 6px;
  color: $t3;
  font-size: 12px;
}

.md-footer {
  margin-top: 24px;
  padding-top: 14px;
  border-top: 1px dashed $line;
  text-align: center;
  font-size: 11px;
  color: $t4;
}
</style>
