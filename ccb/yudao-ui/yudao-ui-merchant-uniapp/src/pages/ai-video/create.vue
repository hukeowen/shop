<template>
  <view class="page">
    <!-- ① 行业 banner（未选时显眼提示 + 一键去补） -->
    <view v-if="!loadingShop && !shopBusinessType" class="biz-banner" @click="goShopEdit">
      <view class="bb-ic">💡</view>
      <view class="bb-body">
        <view class="bb-title">先告诉 AI 你是什么行业</view>
        <view class="bb-desc">选准行业 → 视频按你这一行的风格拍（油花/拉丝/陈列…），少打废稿</view>
      </view>
      <view class="bb-arrow">›</view>
    </view>

    <!-- ② 上传商品照片 -->
    <view class="card">
      <view class="section-title">上传商品照片</view>
      <view class="section-sub">1-6 张，角度选最有代表性的（多角度 + 细节更出彩）</view>

      <view class="pics">
        <view
          class="pic"
          v-for="(item, i) in images"
          :key="i"
          :style="{ backgroundImage: `url(${item.preview})` }"
        >
          <view class="pic-del" @click="removePic(i)">×</view>
        </view>
        <view v-if="images.length < 6" class="pic add" @click="pickImage">
          <text class="plus">＋</text>
          <text class="add-text">{{ images.length ? '继续加' : '拍 / 选图' }}</text>
          <text class="add-count">{{ images.length }}/6</text>
        </view>
      </view>
    </view>

    <!-- ③ 结构化卖点 brief —— 替代原"一句话亮点"  -->
    <view class="card brief-card">
      <view class="section-title-row">
        <view class="section-title">商品/服务介绍</view>
        <view v-if="images.length && !autoFilling && !brief.productName" class="ai-refill" @click="triggerAutoFill">
          <text>✦ AI 帮我写</text>
        </view>
        <view v-if="autoFilling" class="ai-filling">
          <text>AI 识别中…</text>
        </view>
      </view>

      <view class="brief-field">
        <text class="bf-label">商品名 <text class="req">*</text></text>
        <input
          class="bf-input"
          v-model="brief.productName"
          :maxlength="32"
          placeholder="如：现烤蜜薯 / 古树普洱 / 头部按摩"
        />
      </view>

      <view class="brief-field">
        <text class="bf-label">核心卖点（1-2 句）</text>
        <textarea
          class="bf-textarea"
          v-model="brief.sellingPoints"
          :maxlength="100"
          placeholder="如：现烤现卖 / 流糖心、十年老茶师手揉"
        />
      </view>

      <view class="brief-field">
        <text class="bf-label">价格 / 优惠</text>
        <input
          class="bf-input"
          v-model="brief.price"
          :maxlength="40"
          placeholder="如：5 元 1 个 / 套餐 88 起"
        />
      </view>

      <view class="brief-field">
        <text class="bf-label">想突出的画面（多选）</text>
        <view class="hl-grid">
          <text
            v-for="h in HIGHLIGHTS"
            :key="h.key"
            :class="['hl-pill', brief.highlights.includes(h.key) ? 'on' : '']"
            @click="toggleHighlight(h.key)"
          ><text class="hl-emoji">{{ h.emoji }}</text>{{ h.label }}</text>
        </view>
      </view>
    </view>

    <!-- ④ 视频风格 5 选 1（默认按 businessType 推荐） -->
    <view class="card">
      <view class="section-title">视频风格</view>
      <view class="section-sub">不同风格出来的镜头节奏完全不一样 · 系统已按你行业推荐了一个</view>
      <view class="style-grid">
        <view
          v-for="s in VIDEO_STYLES"
          :key="s.key"
          :class="['style-card', videoStyle === s.key ? 'on' : '']"
          @click="videoStyle = s.key"
        >
          <view class="sc-emoji">{{ s.emoji }}</view>
          <view class="sc-name">{{ s.label }}</view>
          <view class="sc-desc">{{ s.desc }}</view>
        </view>
      </view>
    </view>

    <!-- ⑤ 配音 -->
    <view class="card">
      <view class="section-title">选择配音</view>
      <view class="section-sub">每幕一句台词，这个声音来读</view>
      <view class="voice-list">
        <view
          v-for="v in voices"
          :key="v.key"
          class="voice"
          :class="{ active: voiceKey === v.key }"
          @click="voiceKey = v.key"
        >
          <view class="voice-name">{{ v.name }}</view>
          <view class="voice-desc">{{ v.desc }}</view>
        </view>
      </view>
    </view>

    <!-- ⑥ 画面比例 -->
    <view class="card">
      <view class="section-title">画面比例</view>
      <view class="ratio-list">
        <view
          v-for="r in ratios"
          :key="r.key"
          class="ratio"
          :class="{ active: ratio === r.key }"
          @click="ratio = r.key"
        >
          <view class="ratio-box" :class="r.key === '9:16' ? 'portrait' : 'landscape'"></view>
          <text>{{ r.name }}</text>
        </view>
      </view>
    </view>

    <!-- ⑦ 美化开关 -->
    <view class="card enhance-card">
      <view class="enhance-row">
        <view class="enhance-info">
          <view class="enhance-title">✨ AI 美化预处理</view>
          <view class="enhance-desc">手机原图 → 即梦 CV 美化 → 浅景深 / 暖色调 / 电影质感，视频质感提升一档</view>
        </view>
        <switch
          :checked="enhance"
          color="#FF6B35"
          @change="(e) => (enhance = e.detail.value)"
        />
      </view>
      <view class="enhance-tip">关闭可省 ~20 秒；开启每张耗 3-5 秒，失败自动回原图</view>
    </view>

    <view class="card tips">
      <view class="tip-title">接下来会发生</view>
      <view class="tip-item">1. 图片上传到 OSS（秒级）</view>
      <view class="tip-item">2. AI 看图 + 你填的卖点 → 每张图拆 1 幕脚本</view>
      <view class="tip-item">3. 你检查脚本，可改可留</view>
      <view class="tip-item">4. 视频 + 配音并行生成（每幕 5-10s，2-3 分钟）</view>
      <view class="tip-item warn">约 ¥3 / 条，失败不扣</view>
    </view>

    <view class="actions safe-bottom">
      <button class="btn primary" :disabled="!canSubmit || submitting" @click="onSubmit">
        {{ submitting ? (submitPhaseLabel || 'AI 处理中...') : '开始生成' }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { createTask } from '../../api/aiVideo.js';
import { blobUrlToBase64, uploadImage } from '../../api/oss.js';
import { VOICES } from '../../api/voice.js';
import { generateHighlight } from '../../api/scriptLlm.js';
import { useUserStore } from '../../store/user.js';
import { request } from '../../api/request.js';

const userStore = useUserStore();

// V040 结构化卖点 brief（替代原 description textarea）
const brief = ref({
  productName: '',
  sellingPoints: '',
  price: '',
  highlights: [], // ['cooking', 'closeup', 'hand', 'vibe', 'customer'] 多选
});

const HIGHLIGHTS = [
  { key: 'cooking', label: '烹饪过程', emoji: '🔥' },
  { key: 'closeup', label: '食材特写', emoji: '🔍' },
  { key: 'hand', label: '手部动作', emoji: '✋' },
  { key: 'vibe', label: '店铺氛围', emoji: '🏮' },
  { key: 'customer', label: '顾客反应', emoji: '😋' },
];

// V040 视频风格 5 选 1
const VIDEO_STYLES = [
  { key: 'visual_burst', emoji: '💥', label: '视觉爆破', desc: '油花/蒸汽/拉丝特写，刺激食欲（烧烤/小吃/奶茶）' },
  { key: 'cozy',         emoji: '☕', label: '治愈探店', desc: '慢节奏 + 暖光，文艺感（咖啡/茶馆/烘焙/按摩）' },
  { key: 'shelf',        emoji: '🛒', label: '货架陈列', desc: '拉远展示 + 产品矩阵（超市/便利店/服装）' },
  { key: 'story',        emoji: '📖', label: '故事感',   desc: '老板手部 + 老物件 + 时间感（匠人/老店）' },
  { key: 'trend',        emoji: '🔥', label: '网红种草', desc: '快节奏 + 反差对比（奶茶/甜品/美容）' },
];

// 按 businessType 推荐默认 videoStyle（用户首次未选时用）
const STYLE_BY_BIZ = {
  bbq: 'visual_burst',
  snack: 'visual_burst',
  drink: 'trend',
  restaurant: 'cozy',
  fruit: 'visual_burst',
  super: 'shelf',
  tea: 'cozy',
  tea_house: 'cozy',
  bakery: 'cozy',
  clothing: 'shelf',
  massage: 'cozy',
  beauty: 'trend',
  other: 'cozy',
};

const images = ref([]);
const voiceKey = ref('cancan');
const ratio = ref('9:16');
const enhance = ref(true);
const submitting = ref(false);
const submitPhaseLabel = ref('');
const autoFilling = ref(false);
const loadingShop = ref(true);
const shopBusinessType = ref('');
const videoStyle = ref('cozy'); // 默认；loadShop 后按 businessType 改

const voices = VOICES;
const ratios = [
  { key: '9:16', name: '竖版 9:16' },
  { key: '16:9', name: '横版 16:9' },
  { key: '1:1', name: '方形 1:1' },
];

// 可提交：商品名 + 至少 1 张图（卖点/价格/highlights 选填）
const canSubmit = computed(
  () => images.value.length >= 1 &&
        brief.value.productName.trim().length >= 2 &&
        !autoFilling.value
);

function toggleHighlight(k) {
  const i = brief.value.highlights.indexOf(k);
  if (i >= 0) brief.value.highlights.splice(i, 1);
  else brief.value.highlights.push(k);
}

function goShopEdit() {
  uni.navigateTo({ url: '/pages/me/shop-edit' });
}

// 把 brief 4 字段拼成"老板原话"风格的 userDescription（向后兼容 polishDescription 那条路径）
const composedDescription = computed(() => {
  const parts = [];
  if (brief.value.productName) parts.push(brief.value.productName);
  if (brief.value.sellingPoints) parts.push(brief.value.sellingPoints);
  if (brief.value.price) parts.push(brief.value.price);
  return parts.join('，');
});

async function loadShop() {
  loadingShop.value = true;
  try {
    const shop = await request({ url: '/app-api/merchant/mini/shop/info' });
    if (shop && shop.businessType) {
      shopBusinessType.value = shop.businessType;
      const recommend = STYLE_BY_BIZ[shop.businessType];
      if (recommend) videoStyle.value = recommend;
    }
  } catch {}
  loadingShop.value = false;
}

async function triggerAutoFill() {
  // 用图 AI 识别"一句话亮点"填到 sellingPoints（保留兼容入口，老板懒得手写）
  const urls = images.value.slice(0, 6).map((x) => x.url).filter(Boolean);
  if (urls.length !== Math.min(6, images.value.length)) return;
  autoFilling.value = true;
  try {
    const result = await generateHighlight(urls);
    // 若 productName 还没填，把识别结果前一段当商品名 fallback
    if (!brief.value.productName) {
      brief.value.productName = result.slice(0, 12);
    }
    brief.value.sellingPoints = result;
  } catch (e) {
    uni.showToast({ title: 'AI 识别失败，请手动填写', icon: 'none' });
  } finally {
    autoFilling.value = false;
  }
}

function pickImage() {
  uni.chooseImage({
    count: 6 - images.value.length,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: async (r) => {
      uni.showLoading({ title: '上传图片…' });
      try {
        for (const p of r.tempFilePaths) {
          const base64 = await blobUrlToBase64(p);
          let url = '';
          try {
            const u = await uploadImage(base64);
            url = u.url;
          } catch (e) {
            console.warn('[pickImage] OSS 上传失败:', e?.message);
          }
          images.value.push({ preview: p, base64, url });
          if (images.value.length >= 6) break;
        }
      } catch (e) {
        uni.showToast({ title: '图片读取失败：' + e.message, icon: 'none' });
        return;
      } finally {
        uni.hideLoading();
      }
    },
  });
}

function removePic(i) {
  images.value.splice(i, 1);
}

async function onSubmit() {
  if (!canSubmit.value) {
    if (!brief.value.productName.trim()) {
      uni.showToast({ title: '请填商品名', icon: 'none' });
    } else if (!images.value.length) {
      uni.showToast({ title: '请上传至少 1 张图', icon: 'none' });
    }
    return;
  }
  submitting.value = true;
  submitPhaseLabel.value = '准备上传…';
  uni.showLoading({ title: '上传图片' });
  try {
    const preUrls = images.value.map((x) => x.url).filter(Boolean);
    const allUploaded = preUrls.length === images.value.length;
    const taskId = await createTask({
      imageUrls: allUploaded ? preUrls : undefined,
      imageBase64s: allUploaded ? undefined : images.value.map((x) => x.base64),
      userDescription: composedDescription.value, // 拼成一段话给老路径兼容
      voiceKey: voiceKey.value,
      ratio: ratio.value,
      shopName: userStore.shop?.name || '',
      enhance: enhance.value,
      // V040: 结构化 brief + 行业 + 风格 透传给后端 LLM prompt
      businessType: shopBusinessType.value || '',
      videoStyle: videoStyle.value || '',
      brief: { ...brief.value },
      onProgress: (s) => {
        if (s.phase === 'uploading') submitPhaseLabel.value = '上传图片到 OSS…';
        else if (s.phase === 'enhancing') submitPhaseLabel.value = `AI 美化 ${s.enhancedCount}/${s.totalCount}`;
        else if (s.phase === 'polishing') submitPhaseLabel.value = 'AI 看图 + 润色文案…';
        else if (s.phase === 'scripting') submitPhaseLabel.value = 'AI 拆镜头…';
        uni.showLoading({ title: submitPhaseLabel.value });
      },
    });
    uni.hideLoading();
    uni.redirectTo({ url: `/pages/ai-video/confirm?id=${taskId}` });
  } catch (e) {
    uni.hideLoading();
    uni.showModal({ title: '提交失败', content: e.message || '未知错误', showCancel: false });
  } finally {
    submitting.value = false;
    submitPhaseLabel.value = '';
  }
}

onLoad(() => {
  loadShop();
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page { padding: 24rpx 24rpx 200rpx; }

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 32rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: $text-primary;
}
.section-sub {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: $text-secondary;
}

/* V040 行业未选 banner（显眼橙色 + ›） */
.biz-banner {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx;
  background: linear-gradient(135deg, #fff5ef, #ffe1c8);
  border: 2rpx solid #ffae74;
  border-radius: $radius-lg;
  margin-bottom: 20rpx;
}
.biz-banner .bb-ic { font-size: 44rpx; flex-shrink: 0; }
.biz-banner .bb-body { flex: 1; min-width: 0; }
.biz-banner .bb-title { font-size: 28rpx; font-weight: 700; color: $text-primary; }
.biz-banner .bb-desc { margin-top: 4rpx; font-size: 22rpx; color: $text-secondary; line-height: 1.5; }
.biz-banner .bb-arrow { font-size: 36rpx; color: $brand-primary; flex-shrink: 0; }

.pics {
  margin-top: 24rpx;
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 12rpx;
}
.pic {
  position: relative;
  padding-top: 100%;
  border-radius: $radius-md;
  background-size: cover;
  background-position: center;
  background-color: #f6f7f9;
}
.pic .pic-del {
  position: absolute;
  top: 8rpx; right: 8rpx;
  width: 40rpx; height: 40rpx;
  line-height: 40rpx;
  text-align: center;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  border-radius: 50%;
  font-size: 28rpx;
}
.pic.add {
  padding-top: 0;
  aspect-ratio: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 4rpx;
  border: 2rpx dashed $text-placeholder;
  color: $text-secondary;
  background: transparent;
}
.pic.add .plus { font-size: 56rpx; font-weight: 200; line-height: 1; color: $brand-primary; }
.pic.add .add-text { font-size: 22rpx; }
.pic.add .add-count { font-size: 20rpx; color: $text-placeholder; }

/* V040 brief 表单 */
.brief-card {
  display: flex;
  flex-direction: column;
}
.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ai-refill {
  font-size: 22rpx;
  color: $brand-primary;
  padding: 6rpx 16rpx;
  background: $brand-primary-light;
  border-radius: $radius-pill;
  flex-shrink: 0;
}
.ai-filling {
  font-size: 22rpx;
  color: $text-secondary;
  flex-shrink: 0;
  animation: pulse 1.2s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: .45; }
}
.brief-field {
  margin-top: 20rpx;
}
.bf-label {
  display: block;
  font-size: 24rpx;
  color: $text-secondary;
  margin-bottom: 8rpx;
}
.bf-label .req {
  color: $danger;
  margin-left: 4rpx;
}
.bf-input,
.bf-textarea {
  display: block;
  width: 100%;
  padding: 18rpx 20rpx;
  background: #f6f7f9;
  border-radius: $radius-md;
  font-size: 28rpx;
  color: $text-primary;
  box-sizing: border-box;
  border: 2rpx solid transparent;
}
.bf-textarea { min-height: 130rpx; line-height: 1.5; }

.hl-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12rpx;
}
.hl-pill {
  padding: 14rpx 8rpx;
  border-radius: 16rpx;
  background: $bg-page;
  font-size: 22rpx;
  color: $text-regular;
  text-align: center;
  border: 2rpx solid transparent;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  line-height: 1.2;
}
.hl-pill .hl-emoji { font-size: 32rpx; line-height: 1; }
.hl-pill.on {
  background: $brand-primary-light;
  color: $brand-primary;
  border-color: $brand-primary;
  font-weight: 700;
}

/* V040 视频风格 */
.style-grid {
  margin-top: 24rpx;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
}
.style-card {
  padding: 24rpx 20rpx;
  border-radius: 20rpx;
  background: #f6f7f9;
  border: 2rpx solid transparent;
  text-align: left;
}
.style-card .sc-emoji {
  font-size: 40rpx;
  line-height: 1;
  margin-bottom: 12rpx;
}
.style-card .sc-name {
  font-size: 28rpx;
  font-weight: 700;
  color: $text-primary;
}
.style-card .sc-desc {
  margin-top: 6rpx;
  font-size: 20rpx;
  color: $text-secondary;
  line-height: 1.5;
}
.style-card.on {
  background: linear-gradient(135deg, #fff5ef, #ffe1c8);
  border-color: $brand-primary;
  box-shadow: 0 4rpx 16rpx rgba(255, 107, 53, .12);
  .sc-name { color: $brand-primary; }
}

/* 配音 */
.voice-list {
  margin-top: 20rpx;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
}
.voice {
  padding: 20rpx 16rpx;
  background: #f6f7f9;
  border-radius: $radius-md;
  border: 2rpx solid transparent;
}
.voice .voice-name { font-size: 28rpx; font-weight: 600; color: $text-primary; }
.voice .voice-desc { margin-top: 6rpx; font-size: 22rpx; color: $text-secondary; }
.voice.active {
  background: $brand-primary-light;
  border-color: $brand-primary;
}

/* 比例 */
.ratio-list {
  margin-top: 20rpx;
  display: flex;
  gap: 24rpx;
  justify-content: space-around;
}
.ratio {
  flex: 1;
  padding: 20rpx 16rpx;
  background: #f6f7f9;
  border-radius: $radius-md;
  border: 2rpx solid transparent;
  text-align: center;
  font-size: 24rpx;
  color: $text-regular;
}
.ratio .ratio-box {
  margin: 0 auto 12rpx;
  background: $text-placeholder;
  border-radius: 6rpx;
}
.ratio .ratio-box.portrait { width: 36rpx; height: 64rpx; }
.ratio .ratio-box.landscape { width: 64rpx; height: 36rpx; }
.ratio .ratio-box:not(.portrait):not(.landscape) { width: 48rpx; height: 48rpx; }
.ratio.active {
  background: $brand-primary-light;
  border-color: $brand-primary;
  .ratio-box { background: $brand-primary; }
}

/* enhance */
.enhance-card .enhance-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
}
.enhance-card .enhance-info { flex: 1; min-width: 0; }
.enhance-card .enhance-title { font-size: 28rpx; font-weight: 600; color: $text-primary; }
.enhance-card .enhance-desc { margin-top: 6rpx; font-size: 22rpx; color: $text-secondary; line-height: 1.5; }
.enhance-card .enhance-tip { margin-top: 16rpx; font-size: 20rpx; color: $text-placeholder; }

.tips {
  background: #fff8ef;
  box-shadow: none;
}
.tips .tip-title { font-size: 26rpx; font-weight: 600; color: $warning; margin-bottom: 12rpx; }
.tips .tip-item { font-size: 24rpx; color: $text-regular; line-height: 1.8; }
.tips .tip-item.warn { margin-top: 8rpx; color: $danger; font-weight: 500; }

.actions {
  position: fixed;
  left: 0; right: 0; bottom: 0;
  padding: 24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);
  background: #fff;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.05);
}
.btn {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  font-size: 30rpx;
  border-radius: $radius-md;
}
.btn.primary {
  background: $brand-primary;
  color: #fff;
}
.btn[disabled] {
  background: $text-placeholder;
  color: #fff;
}
.btn::after { border: none; }
</style>
