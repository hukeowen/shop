<template>
  <view class="page">
    <view :style="sbhSpacer"></view>
    <view class="topbar">
      <text class="back" @click="goBack">‹</text>
      <text class="title">评价</text>
      <text class="right"></text>
    </view>

    <view v-if="loading" class="loading">加载中…</view>
    <view v-else-if="!items.length" class="empty">
      <text class="em">✅</text>
      <text class="t">该订单已全部评价</text>
      <view class="empty-btn" @click="goBack">返回</view>
    </view>

    <view v-else>
      <view v-for="(it, idx) in items" :key="it.id" class="cmt-card">
        <view class="cmt-prod">
          <image v-if="it.picUrl" :src="it.picUrl" class="cmt-pic" mode="aspectFill" />
          <view v-else class="cmt-pic ph">🛍</view>
          <view class="cmt-prod-info">
            <view class="cmt-prod-name">{{ it.spuName }}</view>
            <view class="cmt-prod-spec">×{{ it.count }}</view>
          </view>
        </view>

        <view class="rate-row">
          <text class="rate-label">商品描述</text>
          <view class="stars">
            <text v-for="n in 5" :key="n" class="star" :class="{ on: n <= it.descriptionScores }"
                  @click="it.descriptionScores = n">★</text>
          </view>
          <text class="rate-tip">{{ scoreText(it.descriptionScores) }}</text>
        </view>
        <view class="rate-row">
          <text class="rate-label">商家服务</text>
          <view class="stars">
            <text v-for="n in 5" :key="n" class="star" :class="{ on: n <= it.benefitScores }"
                  @click="it.benefitScores = n">★</text>
          </view>
          <text class="rate-tip">{{ scoreText(it.benefitScores) }}</text>
        </view>

        <textarea class="cmt-input" v-model="it.content" placeholder="说说这次的体验，帮助更多邻居参考~" maxlength="500" />

        <view class="cmt-pics">
          <view v-for="(p, pi) in it.picUrls" :key="pi" class="cmt-thumb">
            <image :src="p" mode="aspectFill" class="thumb-img" @click="preview(it.picUrls, p)" />
            <text class="thumb-del" @click="it.picUrls.splice(pi, 1)">×</text>
          </view>
          <view v-if="it.picUrls.length < 3" class="cmt-add-pic" @click="addPic(it)">＋</view>
        </view>

        <view class="cmt-anon" @click="it.anonymous = !it.anonymous">
          <text class="anon-box" :class="{ on: it.anonymous }">{{ it.anonymous ? '✓' : '' }}</text>
          <text>匿名评价</text>
        </view>
      </view>

      <view class="submit-bar">
        <view class="submit-btn" :class="{ disabled: submitting }" @click="submitAll">
          {{ submitting ? '提交中…' : '提交评价' }}
        </view>
      </view>
    </view>
    <view class="bottom-pad"></view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { getOrderDetail, createOrderItemComment } from '@/api/order.js';
import { chooseAndUploadImage } from '@/api/upload.js';
import { sbhSpacerStyle } from '@/utils/safeTop.js';
const sbhSpacer = sbhSpacerStyle();

const loading = ref(true);
const submitting = ref(false);
const items = ref([]);
let orderId = null;

function scoreText(n) { return ['', '很差', '一般', '还行', '满意', '超赞'][n] || ''; }

onLoad((opts) => { orderId = opts?.id; load(); });

async function load() {
  loading.value = true;
  try {
    const d = await getOrderDetail(orderId);
    const raw = (d?.items || []).filter((it) => !it.commentStatus); // 只评未评价项
    items.value = raw.map((it) => reactive({
      id: it.id, spuName: it.spuName, picUrl: it.picUrl, count: it.count,
      descriptionScores: 5, benefitScores: 5, content: '', picUrls: [], anonymous: false,
    }));
  } catch {
    items.value = [];
  } finally {
    loading.value = false;
  }
}

async function addPic(it) {
  try {
    const url = await chooseAndUploadImage();
    if (url) it.picUrls.push(url);
  } catch (e) {
    if (e && e.message && !/cancel/i.test(e.message)) uni.showToast({ title: '上传失败', icon: 'none' });
  }
}
function preview(urls, cur) { uni.previewImage({ urls, current: cur }); }

async function submitAll() {
  if (submitting.value) return;
  for (const it of items.value) {
    if (!it.content || !it.content.trim()) {
      uni.showToast({ title: `请填写「${it.spuName}」的评价内容`, icon: 'none' });
      return;
    }
  }
  submitting.value = true;
  let ok = 0;
  try {
    for (const it of items.value) {
      try {
        await createOrderItemComment({
          orderItemId: it.id,
          anonymous: !!it.anonymous,
          descriptionScores: it.descriptionScores,
          benefitScores: it.benefitScores,
          content: it.content.trim(),
          picUrls: it.picUrls.slice(0, 9),
        });
        ok += 1;
      } catch (e) { /* 单项失败继续其余 */ }
    }
    if (ok === items.value.length) {
      uni.showToast({ title: '评价成功，感谢反馈', icon: 'success' });
      setTimeout(() => goBack(), 800);
    } else if (ok > 0) {
      uni.showToast({ title: `成功 ${ok}/${items.value.length}，部分失败`, icon: 'none' });
      setTimeout(() => load(), 800);
    } else {
      uni.showToast({ title: '提交失败，请重试', icon: 'none' });
    }
  } finally {
    submitting.value = false;
  }
}

function goBack() {
  const ps = getCurrentPages();
  if (ps.length > 1) uni.navigateBack();
  else uni.reLaunch({ url: '/pages/order/list' });
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page { min-height: 100vh; background: $bg-2; padding-bottom: 100px; }
.topbar { display: flex; align-items: center; padding: 12px 14px; background: $card; border-bottom: 1px solid $line; }
.topbar .back { font-size: 22px; color: $t1; padding: 4px 10px; line-height: 1; }
.topbar .title { flex: 1; text-align: center; font-size: 16px; font-weight: 700; color: $t1; }
.topbar .right { width: 40px; }

.loading { padding: 40px; text-align: center; color: $t4; }
.empty { padding: 80px 20px; text-align: center; }
.empty .em { font-size: 48px; display: block; }
.empty .t { display: block; margin-top: 12px; color: $t2; font-size: 15px; }
.empty-btn { margin: 24px auto 0; width: 160px; padding: 12px; border-radius: 99px; background: linear-gradient(135deg, $o, $o-d); color: #fff; font-weight: 800; }

.cmt-card { margin: 12px 14px 0; background: $card; border-radius: $r-lg; border: 1px solid $line; padding: 14px; }
.cmt-prod { display: flex; gap: 10px; align-items: center; }
.cmt-pic { width: 48px; height: 48px; border-radius: 8px; background: $o-50; flex-shrink: 0; }
.cmt-pic.ph { display: flex; align-items: center; justify-content: center; font-size: 24px; }
.cmt-prod-info { flex: 1; min-width: 0; }
.cmt-prod-name { font-size: 14px; font-weight: 700; color: $t1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmt-prod-spec { font-size: 12px; color: $t3; margin-top: 2px; }

.rate-row { display: flex; align-items: center; gap: 10px; margin-top: 14px; }
.rate-label { font-size: 13px; color: $t2; width: 64px; }
.stars { display: flex; gap: 4px; }
.star { font-size: 24px; color: #DDD3C8; }
.star.on { color: #FF9F2E; }
.rate-tip { font-size: 12px; color: $o-d; font-weight: 700; }

.cmt-input { margin-top: 14px; width: 100%; box-sizing: border-box; min-height: 80px; padding: 12px; background: $bg-2; border-radius: 10px; font-size: 14px; color: $t1; }

.cmt-pics { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 12px; }
.cmt-thumb { position: relative; width: 72px; height: 72px; }
.thumb-img { width: 72px; height: 72px; border-radius: 8px; }
.thumb-del { position: absolute; top: -6px; right: -6px; width: 20px; height: 20px; line-height: 18px; text-align: center; background: rgba(0,0,0,.6); color: #fff; border-radius: 50%; font-size: 14px; }
.cmt-add-pic { width: 72px; height: 72px; border: 1px dashed $o-100; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-size: 28px; color: $o; }

.cmt-anon { display: flex; align-items: center; gap: 8px; margin-top: 14px; font-size: 13px; color: $t2; }
.anon-box { width: 18px; height: 18px; border: 1px solid $line; border-radius: 4px; text-align: center; line-height: 18px; font-size: 12px; color: #fff; }
.anon-box.on { background: $o; border-color: $o; }

.submit-bar { position: fixed; left: 0; right: 0; bottom: 0; padding: 10px 14px calc(10px + env(safe-area-inset-bottom)); background: $card; border-top: 1px solid $line; }
.submit-btn { padding: 13px; text-align: center; border-radius: 99px; background: linear-gradient(135deg, $o, $o-d); color: #fff; font-weight: 800; font-size: 15px; box-shadow: $sh-warm; }
.submit-btn.disabled { opacity: .6; }
.bottom-pad { height: 12px; }
</style>
