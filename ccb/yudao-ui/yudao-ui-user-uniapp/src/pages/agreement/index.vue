<template>
  <view class="page">
    <nav-bar :title="title" />
    <scroll-view scroll-y class="content">
      <view class="md-body">
        <view class="md-h1">{{ title }}</view>
        <view class="md-meta">版本 V1.0 · 生效日期 ____</view>

        <!-- 用户服务协议 -->
        <template v-if="type === 'user'">
          <view class="md-h2">第一条 平台性质说明</view>
          <view class="md-p">1.1 本平台为「商户营销服务工具平台」，性质为<text class="b">纯技术信息中介</text>。</view>
          <view class="md-p">1.2 平台本身<text class="b">不销售任何商品 / 不提供任何服务</text>，您在平台上购买的商品、享受的服务，均由独立经营的商户提供。</view>
          <view class="md-p">1.3 <text class="b">平台不对商品质量、商户经营行为、营销规则承担担保责任</text>。您与商户之间形成独立的买卖合同关系，与平台无关。</view>

          <view class="md-h2">第二条 营销激励性质说明</view>
          <view class="md-p">2.1 您在平台参与的「老顾客分享激励」「邀请有礼」「促销让利抽奖」等营销活动，性质为：</view>
          <view class="md-p md-quote">- <text class="b">商户对老顾客的复购感谢奖励</text></view>
          <view class="md-p md-quote">- 奖励来自<text class="b">商户自费</text>投入的营销让利预算</view>
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

          <view class="md-h2">第六条 争议解决</view>
          <view class="md-p">本协议适用中华人民共和国法律。本协议项下争议，由 [平台注册地] 人民法院专属管辖。</view>
          <view class="md-p">您与商户之间的商品交易争议，应<text class="b">直接向商户主张</text>，与平台无关。</view>
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
  return '用户服务协议';
});

onLoad((q) => {
  if (q?.type === 'privacy') type.value = 'privacy';
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
