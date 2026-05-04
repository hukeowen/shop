<template>
  <view class="page">
    <!-- 配额头 -->
    <view class="head">
      <view class="label">剩余视频配额</view>
      <view class="value">
        <text class="big">{{ remaining }}</text>
        <text class="small"> 条</text>
      </view>
      <view class="sub">配额永久有效，不过期</view>
    </view>

    <!-- 套餐列表 -->
    <view class="card">
      <view class="section-title">购买加量包</view>
      <view v-if="loadingPackages" class="loading-row">
        <text class="tip">加载中…</text>
      </view>
      <view v-else-if="packages.length === 0" class="loading-row">
        <text class="tip">暂无在售套餐</text>
      </view>
      <view v-else class="packages">
        <view
          v-for="pkg in packages"
          :key="pkg.id"
          class="package"
          :class="{ selected: selectedId === pkg.id }"
          @click="selectedId = pkg.id"
        >
          <view class="pkg-count">{{ pkg.videoCount }} 条</view>
          <view class="pkg-price">¥{{ (pkg.price / 100).toFixed(0) }}</view>
          <view v-if="pkg.originalPrice" class="pkg-origin">
            <text class="line-through">¥{{ (pkg.originalPrice / 100).toFixed(0) }}</text>
          </view>
          <view class="pkg-unit">¥{{ (pkg.price / pkg.videoCount / 100).toFixed(1) }}/条</view>
        </view>
      </view>
    </view>

    <!-- 说明 -->
    <view class="card tips">
      <view class="tip-title">购买说明</view>
      <view class="tip-item">· 加量包永久有效，不过期</view>
      <view class="tip-item">· 生成失败自动回补配额，不损耗</view>
      <view class="tip-item">· 支付后立即到账，可查看流水记录</view>
    </view>

    <!-- 支付按钮 -->
    <view class="actions safe-bottom">
      <button
        class="btn primary"
        :disabled="!selectedPkg || paying"
        @click="onBuyAllinpay"
      >
        <text v-if="paying">支付处理中…</text>
        <text v-else-if="selectedPkg">立即支付 ¥{{ (selectedPkg.price / 100).toFixed(0) }}（通联）</text>
        <text v-else>请选择套餐</text>
      </button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getMyQuota, listPackages, purchasePackage, purchasePackageAllinpay, submitPayOrder } from '../../api/quotaApi.js';

const remaining = ref(0);
const packages = ref([]);
const selectedId = ref(null);
const loadingPackages = ref(false);
const paying = ref(false);

const selectedPkg = computed(() => packages.value.find((p) => p.id === selectedId.value) || null);

async function loadQuota() {
  try {
    const data = await getMyQuota();
    remaining.value = data?.remaining ?? 0;
  } catch {
    // toast 已在 request.js 弹出
  }
}

async function loadPackages() {
  loadingPackages.value = true;
  try {
    const list = await listPackages();
    packages.value = list || [];
    // 默认选中第一个
    if (packages.value.length > 0 && !selectedId.value) {
      selectedId.value = packages.value[0].id;
    }
  } catch {
    // ignore
  } finally {
    loadingPackages.value = false;
  }
}

// 通联 H5 收银台购买。
//
// M2 修复：mp-weixin（微信小程序）没有 window/document，直接用 uni.navigateTo 兜底；
//        H5 浏览器才能 location.href 跳通联。
// M6 修复：用 finally 兜底重置 paying；redirect 走前用 setTimeout 30s 看门狗
//        防止 location.href 失败（弹窗拦截 / iframe 限制）卡死。
async function onBuyAllinpay() {
  if (!selectedPkg.value || paying.value) return;
  paying.value = true;
  // 30s 看门狗：redirect 失败 / 异常未走 catch 时强制解锁 UI
  const watchdog = setTimeout(() => { paying.value = false; }, 30_000);
  try {
    // #ifdef MP-WEIXIN
    uni.showModal({
      title: '请用浏览器打开',
      content: '通联收银台支付暂不支持小程序内购买，请在浏览器中打开本页面后重试',
      showCancel: false,
    });
    return;
    // #endif

    const resp = await purchasePackageAllinpay(selectedPkg.value.id);
    if (!resp) {
      uni.showToast({ title: '收银台参数获取失败', icon: 'none' });
      return;
    }
    // 优先 redirect 模式：后端已经打通联拿到 302 Location，前端直接跳
    if (resp.redirect && resp.redirectUrl) {
      // #ifdef H5
      window.location.href = resp.redirectUrl;
      // #endif
      // #ifndef H5
      uni.showToast({ title: '请在浏览器中打开本页面', icon: 'none' });
      // #endif
      return;
    }
    // 后端无 redirectUrl 视为下单失败（M1：不再前端 form POST 兜底，已知 sign 错）
    uni.showToast({ title: '通联收银台获取失败，请稍后重试', icon: 'none' });
  } catch (err) {
    uni.showToast({ title: err?.message || '通联购买失败', icon: 'none' });
  } finally {
    clearTimeout(watchdog);
    // redirect 成功的情况下浏览器已经跳走，paying 状态丢失无所谓；
    // 只有未跳走才会真正执行到这里，重置状态让用户能重试
    paying.value = false;
  }
}

async function onBuy() {
  if (!selectedPkg.value || paying.value) return;
  paying.value = true;
  try {
    // Step 1: 创建业务订单 + 支付单
    const order = await purchasePackage(selectedPkg.value.id, 'wx_lite');

    // Step 2: 向 pay 模块提交，获取 JSAPI 参数
    const payResult = await submitPayOrder(order.payOrderId, 'wx_lite');
    if (!payResult || !payResult.displayContent) {
      uni.showToast({ title: '获取支付参数失败', icon: 'none' });
      return;
    }

    // Step 3: 解析 JSAPI 参数并调起微信支付
    let jsapiParams;
    try {
      jsapiParams = typeof payResult.displayContent === 'string'
        ? JSON.parse(payResult.displayContent)
        : payResult.displayContent;
    } catch {
      uni.showToast({ title: '支付参数解析失败', icon: 'none' });
      return;
    }

    await new Promise((resolve, reject) => {
      uni.requestPayment({
        provider: 'wxpay',
        ...jsapiParams,
        success: resolve,
        fail: reject,
      });
    });

    // Step 4: 支付成功 - 刷新配额
    uni.showToast({ title: '支付成功，配额已到账', icon: 'success' });
    await loadQuota();
  } catch (err) {
    // 用户主动取消不弹错误
    if (err && (err.errMsg?.includes('cancel') || err.message?.includes('cancel'))) return;
    uni.showToast({ title: err?.message || '支付失败，请稍后重试', icon: 'none' });
  } finally {
    paying.value = false;
  }
}

onShow(() => {
  loadQuota();
  loadPackages();
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 24rpx 24rpx 200rpx;
  min-height: 100vh;
}

.head {
  padding: 48rpx 32rpx;
  margin-bottom: 24rpx;
  background: linear-gradient(135deg, $brand-primary, #ff9b5e);
  color: #fff;
  border-radius: $radius-lg;

  .label {
    font-size: 26rpx;
    opacity: 0.85;
  }

  .value {
    margin-top: 12rpx;
  }

  .big {
    font-size: 80rpx;
    font-weight: 800;
    line-height: 1;
  }

  .small {
    font-size: 28rpx;
    opacity: 0.85;
    margin-left: 8rpx;
  }

  .sub {
    margin-top: 16rpx;
    font-size: 22rpx;
    opacity: 0.75;
  }
}

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
  margin-bottom: 24rpx;
}

.loading-row {
  text-align: center;
  padding: 24rpx 0;
  .tip {
    font-size: 26rpx;
    color: $text-placeholder;
  }
}

.packages {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16rpx;

  .package {
    position: relative;
    padding: 28rpx 8rpx 24rpx;
    background: #f6f7f9;
    border: 3rpx solid transparent;
    border-radius: $radius-md;
    text-align: center;
    transition: border-color 0.15s;

    &.selected {
      background: $brand-primary-light;
      border-color: $brand-primary;
    }

    .pkg-count {
      font-size: 30rpx;
      font-weight: 700;
      color: $text-primary;
    }

    .pkg-price {
      margin-top: 10rpx;
      font-size: 40rpx;
      font-weight: 800;
      color: $brand-primary;
    }

    .pkg-origin {
      margin-top: 4rpx;
      .line-through {
        font-size: 22rpx;
        color: $text-placeholder;
        text-decoration: line-through;
      }
    }

    .pkg-unit {
      margin-top: 8rpx;
      font-size: 20rpx;
      color: $text-secondary;
    }
  }
}

.tips {
  background: #fff8ef;
  box-shadow: none;

  .tip-title {
    font-size: 26rpx;
    font-weight: 600;
    color: $warning;
    margin-bottom: 12rpx;
  }

  .tip-item {
    font-size: 24rpx;
    color: $text-regular;
    line-height: 1.8;
  }
}

.actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);
  background: #fff;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.05);
}

.btn {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  font-size: 32rpx;
  font-weight: 600;
  border-radius: $radius-md;

  &.primary {
    background: $brand-primary;
    color: #fff;
  }

  &[disabled] {
    background: $text-placeholder;
    color: #fff;
    opacity: 0.7;
  }

  &::after {
    border: none;
  }
}
</style>
