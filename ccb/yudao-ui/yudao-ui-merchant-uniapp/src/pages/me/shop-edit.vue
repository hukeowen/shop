<template>
  <view class="page">
    <view v-if="loading" class="empty-tip">加载中...</view>
    <template v-else>
      <view class="card section">
        <view class="field">
          <text class="label">店铺名称</text>
          <input class="input" v-model="form.shopName" placeholder="请输入店铺名称" />
        </view>
        <view class="field biz-field">
          <text class="label">行业类型</text>
          <view class="biz-wrap">
            <view class="biz-grid">
              <text
                v-for="b in BIZ_TYPES"
                :key="b.key"
                :class="['biz-pill', form.businessType === b.key ? 'on' : '']"
                @click="form.businessType = b.key"
              ><text class="biz-emoji">{{ b.emoji }}</text>{{ b.label }}</text>
            </view>
            <text class="hint">选准行业 → AI 视频会按你这一行的镜头风格拍（油花/拉丝/陈列/治愈…），少打 60% 废稿</text>
          </view>
        </view>
        <view class="field">
          <text class="label">客服电话</text>
          <input class="input" type="number" v-model="form.mobile" placeholder="请输入客服电话" />
        </view>
        <view class="field">
          <text class="label">营业时间</text>
          <view class="hours-row">
            <picker mode="time" :value="form.startTime" @change="(e) => (form.startTime = e.detail.value)">
              <view class="time-pill">{{ form.startTime || '开始时间' }}</view>
            </picker>
            <text class="dash">–</text>
            <picker mode="time" :value="form.endTime" @change="(e) => (form.endTime = e.detail.value)">
              <view class="time-pill">{{ form.endTime || '结束时间' }}</view>
            </picker>
          </view>
        </view>
        <view class="field">
          <text class="label">营业天数</text>
          <view class="days-col">
            <view class="days-row">
              <text
                v-for="d in [{v:1,l:'一'},{v:2,l:'二'},{v:3,l:'三'},{v:4,l:'四'},{v:5,l:'五'},{v:6,l:'六'},{v:7,l:'日'}]"
                :key="d.v"
                :class="['day-pill', form.days.includes(d.v) ? 'on' : '']"
                @click="toggleDay(d.v)"
              >{{ d.l }}</text>
            </view>
            <text class="hint">不在营业天数 / 不在时段内：用户侧仍展示，但排在列表末尾</text>
          </view>
        </view>
        <view class="field">
          <text class="label">主动打烊</text>
          <view class="toggle-wrap">
            <switch :checked="form.manualClosed" color="#FF6B35" @change="(e) => (form.manualClosed = e.detail.value)" />
            <text class="toggle-hint">{{ form.manualClosed ? '打烊中 · 用户侧不显示且无法下单' : '正常营业 / 关闭打烊' }}</text>
          </view>
        </view>
        <view class="field">
          <text class="label">详细地址</text>
          <input class="input" v-model="form.address" placeholder="请输入店铺地址" />
        </view>
        <view class="field">
          <text class="label">店铺简介</text>
          <textarea class="textarea" v-model="form.description" placeholder="请输入店铺简介" />
        </view>
        <view class="field">
          <text class="label">店铺公告</text>
          <textarea class="textarea" v-model="form.notice" placeholder="请输入店铺公告" />
        </view>
        <view class="field">
          <text class="label">特色标签</text>
          <input
            class="input"
            v-model="form.featureTags"
            placeholder="逗号分隔，最多 6 个，如：炭火现烤,现做现卖,不赶时间"
            maxlength="64"
          />
          <text class="hint">用户进店时显示在店铺信息卡上（第 1 个会高亮带 🔥）</text>
        </view>
      </view>

      <view class="bottom-bar safe-bottom">
        <view class="save-btn" @click="save">保存</view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';

// V040: 13 个行业类型（用户进店时 emoji + 中文标签）；key 跟后端 BUSINESS_CONTEXT_MAP 一一对应
const BIZ_TYPES = [
  { key: 'bbq', label: '烧烤夜市', emoji: '🍢' },
  { key: 'snack', label: '小吃快餐', emoji: '🥟' },
  { key: 'drink', label: '奶茶咖啡', emoji: '🧋' },
  { key: 'restaurant', label: '正餐餐厅', emoji: '🍽' },
  { key: 'fruit', label: '水果生鲜', emoji: '🍓' },
  { key: 'super', label: '超市便利店', emoji: '🛒' },
  { key: 'tea', label: '茶叶酒水', emoji: '🍵' },
  { key: 'tea_house', label: '茶楼茶馆', emoji: '🏯' },
  { key: 'bakery', label: '烘焙甜品', emoji: '🥐' },
  { key: 'clothing', label: '服装鞋帽', emoji: '👕' },
  { key: 'massage', label: '按摩 SPA', emoji: '💆' },
  { key: 'beauty', label: '美容美发', emoji: '💄' },
  { key: 'other', label: '其他', emoji: '🏪' },
];

const loading = ref(true);
const form = ref({
  shopName: '',
  mobile: '',
  businessHours: '',
  businessType: '',
  // V039 结构化营业时间（startTime/endTime/days 拼成 businessHoursJson 发后端）
  startTime: '09:00',
  endTime: '22:00',
  days: [1, 2, 3, 4, 5, 6, 7],
  manualClosed: false,
  address: '',
  description: '',
  notice: '',
  featureTags: '',
});

function toggleDay(d) {
  if (form.value.days.includes(d)) {
    form.value.days = form.value.days.filter((x) => x !== d);
  } else {
    form.value.days = [...form.value.days, d].sort((a, b) => a - b);
  }
}

// 解析 businessHoursJson 回填 form；老数据（纯文本 businessHours）忽略不解析
function applyBusinessHoursJson(json) {
  if (!json) return;
  try {
    const obj = JSON.parse(json);
    if (obj.start) form.value.startTime = obj.start;
    if (obj.end) form.value.endTime = obj.end;
    if (Array.isArray(obj.days) && obj.days.length) form.value.days = obj.days;
  } catch {}
}

onLoad(async () => {
  try {
    const res = await request({ url: '/app-api/merchant/mini/shop/info' });
    if (res) {
      form.value.shopName = res.shopName || '';
      form.value.mobile = res.mobile || '';
      form.value.businessHours = res.businessHours || '';
      form.value.businessType = res.businessType || ''; // V040
      form.value.manualClosed = !!res.manualClosed;
      applyBusinessHoursJson(res.businessHoursJson);
      form.value.address = res.address || '';
      form.value.description = res.description || '';
      form.value.notice = res.notice || '';
      form.value.featureTags = res.featureTags || '';
    }
  } catch {}
  loading.value = false;
});

async function save() {
  if (!form.value.shopName?.trim()) {
    uni.showToast({ title: '店铺名称不能为空', icon: 'none' });
    return;
  }
  if (!form.value.days.length) {
    uni.showToast({ title: '请至少选 1 个营业天', icon: 'none' });
    return;
  }
  try {
    // 把 startTime/endTime/days 打包成 businessHoursJson
    const businessHoursJson = JSON.stringify({
      start: form.value.startTime,
      end: form.value.endTime,
      days: form.value.days,
    });
    // 老 businessHours 文本同步刷新一下，便于不识别 JSON 的旧接口显示
    const businessHours = `${form.value.startTime}-${form.value.endTime}`;
    await request({
      url: '/app-api/merchant/mini/shop/info',
      method: 'PUT',
      data: { ...form.value, businessHours, businessHoursJson },
    });
    uni.showToast({ title: '保存成功', icon: 'success' });
    setTimeout(() => uni.navigateBack(), 1000);
  } catch {}
}
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh;
  padding: 24rpx;
  padding-bottom: 140rpx;
}

.empty-tip {
  text-align: center;
  color: $text-placeholder;
  padding: 120rpx 0;
  font-size: 28rpx;
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 16rpx 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.field {
  display: flex;
  align-items: flex-start;
  padding: 24rpx 0;
  border-bottom: 1rpx solid $border-color;

  &:last-child {
    border-bottom: none;
  }
}

.label {
  width: 160rpx;
  font-size: 28rpx;
  color: $text-secondary;
  flex-shrink: 0;
  padding-top: 4rpx;
}

.input {
  flex: 1;
  font-size: 28rpx;
  color: $text-primary;
  height: 56rpx;
}

.textarea {
  flex: 1;
  font-size: 28rpx;
  color: $text-primary;
  min-height: 120rpx;
  line-height: 1.6;
}

.hint {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: $text-secondary;
  line-height: 1.4;
}

/* V040 行业类型 13 选 1 */
.biz-field { align-items: flex-start; }
.biz-wrap { flex: 1; min-width: 0; }
.biz-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12rpx;
}
.biz-pill {
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
.biz-pill .biz-emoji {
  font-size: 30rpx;
  line-height: 1;
}
.biz-pill.on {
  background: $brand-primary-light;
  color: $brand-primary;
  border-color: $brand-primary;
  font-weight: 700;
}

/* V039 营业时间结构化输入 */
.hours-row {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.time-pill {
  padding: 12rpx 28rpx;
  background: $bg-page;
  color: $text-primary;
  border-radius: 999rpx;
  font-size: 28rpx;
  border: 2rpx solid transparent;
  font-variant-numeric: tabular-nums;
}
.dash { color: $text-placeholder; font-size: 32rpx; }

.days-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  min-width: 0;
}
.days-row {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 12rpx;
  align-items: center;
  justify-items: center;
}
.day-pill {
  width: 64rpx; height: 64rpx; line-height: 64rpx;
  text-align: center;
  border-radius: 50%;
  background: $bg-page;
  font-size: 26rpx;
  color: $text-secondary;
  border: 2rpx solid transparent;
  display: block;
  &.on {
    background: $brand-primary;
    color: #fff;
    font-weight: 700;
  }
}

.toggle-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 16rpx;
  .toggle-hint {
    font-size: 22rpx;
    color: $text-secondary;
    line-height: 1.4;
  }
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16rpx 32rpx;
  background: #fff;
  border-top: 1rpx solid $border-color;
}

.save-btn {
  background: $brand-primary;
  color: #fff;
  border-radius: $radius-pill;
  padding: 24rpx 0;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
}
</style>
