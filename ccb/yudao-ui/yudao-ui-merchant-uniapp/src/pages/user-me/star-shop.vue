<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="back" @click="goBack">‹</text>
      <text class="title">{{ shopName || '店铺星级' }}</text>
      <view style="width:60rpx"></view>
    </view>

    <view v-if="loading && !items.length" class="empty-tip">加载中…</view>
    <view v-else-if="!items.length" class="empty-state">
      <view class="empty-emoji">🛍</view>
      <view class="empty-title">本店暂无星级商品</view>
      <view class="empty-sub">购买带「推 N 反 1」标识的商品后会在这里展示</view>
    </view>

    <view v-else class="list">
      <view v-for="it in items" :key="it.spuId" class="spu-card">
        <view class="spu-head">
          <image v-if="it.picUrl" class="spu-pic" :src="it.picUrl" mode="aspectFill" />
          <view v-else class="spu-pic placeholder">🛍</view>
          <view class="spu-info">
            <view class="spu-name">{{ it.spuName || '商品 #' + it.spuId }}</view>
            <view class="spu-star">
              <text v-if="it.currentStar > 0" class="star-badge">⭐ {{ it.currentStar }}★</text>
              <text v-else class="star-badge zero">未升星</text>
            </view>
          </view>
        </view>

        <!-- 升星规则 + 当前进度 -->
        <view v-if="parseRules(it).length" class="rules">
          <view class="rules-title">升星条件</view>
          <view
            v-for="(r, i) in parseRules(it)"
            :key="i"
            :class="['rule-row', (it.currentStar || 0) >= (i + 1) ? 'achieved' : '']"
          >
            <view class="rule-star">
              <text>{{ i + 1 }}★</text>
              <text v-if="(it.currentStar || 0) >= (i + 1)" class="check">✓</text>
            </view>
            <view class="rule-cond">
              直推 ≥ <text class="b">{{ r.requiredCount || 0 }}</text> 个
              <text v-if="r.requiredStar > 0">{{ r.requiredStar }}★ </text>
              <text v-else>付费用户 </text>
              + 团队累计 ≥ <text class="b">¥{{ ((r.teamSales || 0) / 100).toFixed(2) }}</text>
            </view>
          </view>

          <!-- 当前进度（仅显示下一星距离） -->
          <view v-if="nextRule(it)" class="progress">
            <view class="prog-row">
              <text class="prog-lbl">直推 {{ it.directCount || 0 }} / {{ nextRule(it).requiredCount }}</text>
              <view class="bar"><view class="fill" :style="`width:${pct(it.directCount, nextRule(it).requiredCount)}%`"></view></view>
            </view>
            <view class="prog-row">
              <text class="prog-lbl">团队累计 ¥{{ ((it.teamSalesAmount || 0) / 100).toFixed(2) }} / ¥{{ ((nextRule(it).teamSales || 0) / 100).toFixed(2) }}</text>
              <view class="bar"><view class="fill" :style="`width:${pct(it.teamSalesAmount, nextRule(it).teamSales)}%`"></view></view>
            </view>
          </view>
          <view v-else class="max-tip">🏆 已达本商品最高星级</view>
        </view>
        <view v-else class="no-rules">商家未配置升星规则</view>
      </view>
    </view>

    <view class="bottom-space"></view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';

const tenantId = ref(null);
const shopName = ref('');
const items = ref([]);
const loading = ref(false);

function parseRules(it) {
  try {
    const arr = JSON.parse(it.starUpgradeRules || '[]');
    return Array.isArray(arr) ? arr : [];
  } catch { return []; }
}
function nextRule(it) {
  const rules = parseRules(it);
  const s = it.currentStar || 0;
  if (s >= rules.length) return null;
  return rules[s];
}
function pct(cur, max) {
  if (!max || max <= 0) return 0;
  const v = Math.min(100, Math.round(((cur || 0) / max) * 100));
  return v;
}
function goBack() { uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/user-me/star' }) }); }

onLoad(async (q) => {
  tenantId.value = q?.tenantId ? Number(q.tenantId) : null;
  shopName.value = q?.shopName ? decodeURIComponent(q.shopName) : '';
  if (!tenantId.value) return;
  loading.value = true;
  try {
    const res = await request({
      url: `/app-api/merchant/mini/promo/my-spu-stars?tenantId=${tenantId.value}`,
    });
    items.value = Array.isArray(res) ? res : [];
  } catch { items.value = []; }
  finally { loading.value = false; }
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';
.page { min-height: 100vh; background: $bg-page; }
.safe-top { padding-top: calc(env(safe-area-inset-top) + 16rpx); }
.topbar { display: flex; align-items: center; padding: 16rpx 32rpx; background: $bg-card; border-bottom: 1rpx solid $border-color; }
.topbar .back { font-size: 44rpx; color: $text-primary; padding-right: 16rpx; }
.topbar .title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: $text-primary; }

.empty-tip { text-align: center; padding: 80rpx 0; color: $text-placeholder; font-size: 26rpx; }
.empty-state { text-align: center; padding: 120rpx 60rpx; }
.empty-state .empty-emoji { font-size: 96rpx; margin-bottom: 24rpx; opacity: .5; }
.empty-state .empty-title { font-size: 32rpx; font-weight: 700; color: $text-primary; }
.empty-state .empty-sub { margin-top: 12rpx; font-size: 24rpx; color: $text-placeholder; }

.list { padding: 24rpx 32rpx; }
.spu-card {
  background: $bg-card; border-radius: $radius-lg;
  padding: 28rpx; margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(15,23,42,.04);
}
.spu-head { display: flex; align-items: center; gap: 20rpx; }
.spu-pic {
  width: 96rpx; height: 96rpx; border-radius: $radius-md;
  background: #f6f7f9;
  flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 44rpx; color: $text-placeholder;
}
.spu-info { flex: 1; min-width: 0; }
.spu-name { font-size: 28rpx; font-weight: 600; color: $text-primary; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.spu-star { margin-top: 8rpx; }
.star-badge {
  display: inline-block;
  padding: 4rpx 16rpx;
  background: linear-gradient(135deg, #fff5ef, #ffd1ba);
  color: $brand-primary;
  border-radius: 999rpx;
  font-size: 24rpx; font-weight: 700;
}
.star-badge.zero { background: #f6f7f9; color: $text-placeholder; font-weight: 400; }

.rules { margin-top: 24rpx; padding-top: 20rpx; border-top: 1rpx dashed $border-color; }
.rules-title { font-size: 24rpx; font-weight: 700; color: $text-primary; margin-bottom: 12rpx; }
.rule-row {
  display: flex; align-items: center;
  padding: 12rpx 0;
  font-size: 24rpx;
}
.rule-row .rule-star {
  width: 80rpx; flex-shrink: 0;
  font-size: 24rpx; font-weight: 700; color: $text-placeholder;
}
.rule-row.achieved .rule-star { color: $brand-primary; }
.rule-row .rule-star .check { color: $success; margin-left: 4rpx; }
.rule-row .rule-cond { flex: 1; color: $text-secondary; }
.rule-row.achieved .rule-cond { color: $text-primary; }
.rule-row .b { color: $brand-primary; font-weight: 700; }

.progress { margin-top: 16rpx; padding: 16rpx; background: #f6f7f9; border-radius: $radius-sm; }
.prog-row { margin-bottom: 12rpx; }
.prog-row:last-child { margin-bottom: 0; }
.prog-lbl { display: block; font-size: 22rpx; color: $text-secondary; margin-bottom: 6rpx; font-variant-numeric: tabular-nums; }
.bar { height: 12rpx; border-radius: 6rpx; background: #fff; overflow: hidden; }
.bar .fill { height: 100%; background: $brand-primary; border-radius: 6rpx; transition: width .35s ease-out; }

.max-tip { margin-top: 16rpx; padding: 16rpx; text-align: center; background: linear-gradient(135deg, #fff8e1, #ffe7a3); border-radius: $radius-sm; font-size: 24rpx; color: #b07b15; }
.no-rules { margin-top: 24rpx; padding-top: 20rpx; border-top: 1rpx dashed $border-color; text-align: center; color: $text-placeholder; font-size: 24rpx; }

.bottom-space { height: 80rpx; }
</style>
