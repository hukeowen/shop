<template>
  <view class="page">
    <view v-if="loading" class="empty-tip">加载中...</view>
    <template v-else>
      <view class="card section">
        <view class="field cover-field">
          <text class="label">店铺照片</text>
          <view class="cover-wrap">
            <view class="cover-box" @click="pickCover">
              <image v-if="form.coverUrl" :src="form.coverUrl" mode="aspectFill" class="cover-img" />
              <view v-else class="cover-empty">
                <text class="cover-plus">+</text>
                <text class="cover-tip">点击上传</text>
              </view>
              <view v-if="form.coverUrl" class="cover-replace">📷 更换</view>
            </view>
            <text class="hint">展示在用户端店铺主页顶部封面；建议横向 3:2 比例 · 单张 ≤ 5MB</text>
          </view>
        </view>
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
        <view class="field geo-field">
          <text class="label">店铺定位</text>
          <view class="geo-wrap">
            <view class="geo-btns">
              <view class="geo-btn primary" :class="{ busy: locating }" @click="locateNow">
                {{ locating ? '定位中…' : (hasGeo() ? '📍 重新定位' : '📍 定位到当前位置') }}
              </view>
              <view class="geo-btn ghost" @click="chooseOnMap">地图选点</view>
            </view>
            <view v-if="hasGeo()" class="geo-state ok">
              <text class="geo-dot">●</text>
              <text class="geo-text">已定位{{ geoAddress ? '：' + geoAddress : ('（' + form.latitude + ', ' + form.longitude + '）') }}</text>
            </view>
            <view v-else class="geo-state none">未定位 · 用户将无法在「附近店铺」按距离找到你</view>
            <text class="hint">请站在店内点击定位，用户端会按这个位置帮你被附近的人找到</text>
          </view>
        </view>
        <view class="field">
          <text class="label">店铺简介</text>
          <textarea class="textarea" v-model="form.description" placeholder="请输入店铺简介" />
        </view>
        <view class="field">
          <text class="label">店铺公告</text>
          <textarea class="textarea" v-model="form.notice" placeholder="请输入店铺公告" />
        </view>
        <view class="field feat-field">
          <text class="label">特色标签</text>
          <view class="feat-body">
            <view class="feat-presets">
              <text
                v-for="t in featurePresets"
                :key="t"
                class="feat-chip"
                :class="{ on: featureTagList.includes(t) }"
                @click="toggleFeature(t)"
              >{{ featureTagList.includes(t) ? '✓ ' : '＋ ' }}{{ t }}</text>
            </view>
            <input
              class="input feat-input"
              v-model="form.featureTags"
              placeholder="点上面快速添加，或手动输入（逗号分隔，最多 6 个）"
              maxlength="64"
            />
            <text class="hint">用户进店时显示在店铺信息卡上（第 1 个会高亮带 🔥）</text>
          </view>
        </view>
      </view>

      <view class="bottom-bar safe-bottom">
        <view class="save-btn" @click="save">保存</view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
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

// 特色标签：常用预设供快速选择（也可手动输入），逗号分隔存 form.featureTags
const featurePresets = ['现做现卖', '新鲜直达', '炭火现烤', '手工现做', '24小时营业', '招牌推荐', '人气爆款', '支持外送', '环境干净', '服务热情'];
const loading = ref(true);
const form = ref({
  shopName: '',
  coverUrl: '',
  mobile: '',
  businessHours: '',
  businessType: '',
  // V039 结构化营业时间（startTime/endTime/days 拼成 businessHoursJson 发后端）
  startTime: '09:00',
  endTime: '22:00',
  days: [1, 2, 3, 4, 5, 6, 7],
  manualClosed: false,
  address: '',
  longitude: '',
  latitude: '',
  description: '',
  notice: '',
  featureTags: '',
});

// 特色标签 CSV ↔ 数组
const featureTagList = computed(() =>
  String(form.value.featureTags || '').split(/[,，、]/).map((s) => s.trim()).filter(Boolean)
);
function toggleFeature(t) {
  const arr = featureTagList.value.slice();
  const i = arr.indexOf(t);
  if (i >= 0) {
    arr.splice(i, 1);
  } else {
    if (arr.length >= 6) { uni.showToast({ title: '最多 6 个标签', icon: 'none' }); return; }
    arr.push(t);
  }
  form.value.featureTags = arr.join(',');
}

// 店铺定位（gcj02，与用户端 uni.getLocation/附近店铺 Haversine 对齐）
const locating = ref(false);
const geoAddress = ref(''); // 逆地理回显，便于商户确认定位是否准确
const hasGeo = () => !!(form.value.longitude && form.value.latitude);

async function pickCover() {
  const tempPath = await new Promise((resolve) => {
    uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      success: (r) => resolve(r.tempFilePaths[0]),
      fail: () => resolve(null),
    });
  });
  if (!tempPath) return;
  uni.showLoading({ title: '上传中…' });
  try {
    const { blobUrlToBase64, uploadImage } = await import('../../api/oss.js');
    const base64 = await blobUrlToBase64(tempPath);
    const { url: publicUrl } = await uploadImage(base64, { ext: 'jpg' });
    form.value.coverUrl = publicUrl;
    uni.hideLoading();
    uni.showToast({ title: '上传成功', icon: 'success' });
  } catch (e) {
    uni.hideLoading();
    uni.showToast({ title: '上传失败：' + (e?.message || e), icon: 'none' });
  }
}

// 逆地理：坐标 → 具体地址（后端代理腾讯位置服务，key 不下发前端）
async function reverseGeo(lng, lat) {
  try {
    const r = await request({
      url: `/app-api/merchant/shop/public/geo-reverse?lng=${lng}&lat=${lat}`,
    });
    geoAddress.value = (r && (r.recommend || r.address)) || '';
  } catch {
    geoAddress.value = '';
  }
}

// 获取当前位置作为店铺定位（请商户在店内点击）
function locateNow() {
  if (locating.value) return;
  locating.value = true;
  uni.getLocation({
    type: 'gcj02',
    success: async (r) => {
      form.value.longitude = String(r.longitude);
      form.value.latitude = String(r.latitude);
      locating.value = false;
      uni.showToast({ title: '定位成功', icon: 'success' });
      await reverseGeo(r.longitude, r.latitude);
      // 地址为空时用逆地理结果兜底填上详细地址，方便商户
      if (!form.value.address && geoAddress.value) form.value.address = geoAddress.value;
    },
    fail: (e) => {
      locating.value = false;
      uni.showToast({
        title: '定位失败：' + (e?.errMsg || '请允许定位权限'),
        icon: 'none',
        duration: 2500,
      });
    },
  });
}

// 地图选点（更精确）：App / 小程序原生支持；H5 无地图 SDK 时优雅回退到「获取当前位置」
function chooseOnMap() {
  if (typeof uni.chooseLocation !== 'function') {
    locateNow();
    return;
  }
  uni.chooseLocation({
    success: (r) => {
      if (r && r.longitude && r.latitude) {
        form.value.longitude = String(r.longitude);
        form.value.latitude = String(r.latitude);
        geoAddress.value = r.address || r.name || '';
        if (r.address) form.value.address = r.address;
        uni.showToast({ title: '已选点', icon: 'success' });
      }
    },
    fail: () => {
      // H5 无地图 key / 用户取消：回退到当前位置定位
      locateNow();
    },
  });
}

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
      form.value.coverUrl = res.coverUrl || '';
      form.value.mobile = res.mobile || '';
      form.value.businessHours = res.businessHours || '';
      form.value.businessType = res.businessType || ''; // V040
      form.value.manualClosed = !!res.manualClosed;
      applyBusinessHoursJson(res.businessHoursJson);
      form.value.address = res.address || '';
      form.value.longitude = res.longitude != null ? String(res.longitude) : '';
      form.value.latitude = res.latitude != null ? String(res.latitude) : '';
      form.value.description = res.description || '';
      form.value.notice = res.notice || '';
      form.value.featureTags = res.featureTags || '';
      // 已有坐标：逆地理回显当前定位地址，便于商户确认
      if (hasGeo()) reverseGeo(form.value.longitude, form.value.latitude);
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
    const payload = { ...form.value, businessHours, businessHoursJson };
    // 经纬度后端是 BigDecimal：未定位时为空串会 400，未设置就别传
    if (!form.value.longitude || !form.value.latitude) {
      delete payload.longitude;
      delete payload.latitude;
    }
    await request({
      url: '/app-api/merchant/mini/shop/info',
      method: 'PUT',
      data: payload,
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

/* 特色标签：右侧整列（预设 chips + 输入框 + 提示） */
.feat-body { flex: 1; min-width: 0; }
.feat-presets { display: flex; flex-wrap: wrap; gap: 14rpx; }
.feat-chip {
  font-size: 24rpx; line-height: 1;
  padding: 12rpx 20rpx; border-radius: 999rpx;
  background: #f4f5f7; color: $text-secondary;
  border: 1rpx solid $border-color;
}
.feat-chip.on {
  background: rgba(255, 107, 53, 0.12);
  color: $brand-primary;
  border-color: $brand-primary;
  font-weight: 600;
}
.feat-input {
  height: 72rpx;
  margin-top: 16rpx;
  padding: 0 20rpx;
  background: #f6f7f9;
  border-radius: $radius-md;
  width: 100%;
  box-sizing: border-box;
}

/* 店铺照片 */
.cover-field { align-items: flex-start; }
.cover-wrap { flex: 1; min-width: 0; }
.cover-box {
  position: relative;
  width: 100%;
  aspect-ratio: 3 / 2;
  border-radius: 16rpx;
  background: $bg-page;
  border: 2rpx dashed $border-color;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.cover-img { width: 100%; height: 100%; }
.cover-empty {
  display: flex; flex-direction: column;
  align-items: center; gap: 8rpx;
  color: $text-placeholder;
}
.cover-plus { font-size: 56rpx; font-weight: 300; line-height: 1; }
.cover-tip { font-size: 24rpx; }
.cover-replace {
  position: absolute; bottom: 12rpx; right: 12rpx;
  padding: 8rpx 16rpx;
  background: rgba(0,0,0,.55);
  color: #fff;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 600;
}

/* 店铺定位 */
.geo-field { align-items: flex-start; }
.geo-wrap { flex: 1; min-width: 0; }
.geo-btns { display: flex; gap: 16rpx; }
.geo-btn {
  padding: 14rpx 28rpx; border-radius: 999rpx; font-size: 26rpx; font-weight: 600;
  &.primary { background: $brand-primary; color: #fff; &.busy { opacity: .6; } }
  &.ghost { background: $bg-page; color: $text-regular; border: 2rpx solid $border-color; }
}
.geo-state { margin-top: 14rpx; font-size: 24rpx; line-height: 1.4; }
.geo-state.ok { color: #059669; display: flex; align-items: flex-start; gap: 8rpx;
  .geo-dot { color: #10B981; } }
.geo-state.none { color: #b45309; }

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
