<template>
  <s-layout :title="shopName" navbar="normal">
    <view class="shop-page">
      <!-- 店铺头部 -->
      <view class="shop-header">
        <image :src="shopInfo.logo || '/static/img/shop-default.png'" class="shop-logo" mode="aspectFill" />
        <text class="shop-name">{{ shopInfo.name || '加载中...' }}</text>
      </view>

      <!-- 商品列表 -->
      <view class="goods-section">
        <text class="section-title">全部商品</text>
        <view class="goods-grid">
          <view v-for="item in goodsList" :key="item.id" class="goods-item" @tap="goGoods(item.id)">
            <image :src="item.picUrl" class="goods-img" mode="aspectFill" />
            <text class="goods-name">{{ item.name }}</text>
            <text class="goods-price">¥{{ (item.price / 100).toFixed(2) }}</text>
          </view>
        </view>
        <view v-if="goodsList.length === 0 && !loading" class="empty">
          <text>店铺暂无商品</text>
        </view>
      </view>
    </view>
  </s-layout>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import sheep from '@/sheep';
import MerchantApi from '@/sheep/api/merchant';

const shopInfo = ref({});
const goodsList = ref([]);
const merchantId = ref(null);
const loading = ref(true);

const shopName = computed(() => shopInfo.value.name || '店铺');

onLoad((options) => {
  // 从小程序码 scene 参数解析商户ID
  if (options.scene) {
    const scene = decodeURIComponent(options.scene);
    const match = scene.match(/mid=(\d+)/);
    if (match) {
      merchantId.value = match[1];
    }
  }
  // 也支持直接传 merchantId
  if (options.merchantId) {
    merchantId.value = options.merchantId;
  }
  if (merchantId.value) {
    loadShopInfo();
  }
});

const loadShopInfo = async () => {
  loading.value = true;
  try {
    // 根据商户ID获取商户信息（公开接口，非当前登录用户的商户）
    const { data } = await MerchantApi.getById(merchantId.value);
    shopInfo.value = data || {};
  } catch (e) {
    console.error('加载店铺信息失败', e);
  }
  // 获取该商户的商品列表
  try {
    const { data } = await sheep.$api.product.page({ merchantId: merchantId.value, pageNo: 1, pageSize: 20 });
    if (data && data.list) {
      goodsList.value = data.list;
    }
  } catch (e) {
    console.error('加载商品列表失败', e);
  }
  loading.value = false;
};

const goGoods = (id) => {
  uni.navigateTo({ url: '/pages/goods/index?id=' + id });
};
</script>

<style lang="scss" scoped>
.shop-page { padding: 0; }
.shop-header {
  background: linear-gradient(135deg, #ff6600, #ff9900);
  padding: 60rpx 30rpx 40rpx;
  text-align: center;
  .shop-logo { width: 120rpx; height: 120rpx; border-radius: 50%; border: 4rpx solid #fff; }
  .shop-name { font-size: 36rpx; color: #fff; font-weight: bold; margin-top: 16rpx; display: block; }
}
.goods-section {
  padding: 20rpx 30rpx;
  .section-title { font-size: 30rpx; font-weight: bold; margin-bottom: 16rpx; display: block; }
}
.goods-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  .goods-item {
    width: calc(50% - 8rpx);
    background: #fff;
    border-radius: 12rpx;
    overflow: hidden;
    .goods-img { width: 100%; height: 340rpx; }
    .goods-name { font-size: 26rpx; padding: 12rpx; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .goods-price { font-size: 30rpx; color: #ff4d4f; font-weight: bold; padding: 0 12rpx 16rpx; display: block; }
  }
}
.empty { text-align: center; padding: 100rpx 0; color: #999; }
</style>
