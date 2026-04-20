<template>
  <view class="page">
    <view class="header safe-top">
      <view class="back-btn" @click="goBack">
        <text class="back-arrow">‹</text>
      </view>
      <text class="title">商品管理</text>
      <view class="add-btn" @click="goAdd">
        <text class="plus">＋</text>
        新增
      </view>
    </view>

    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.value"
        class="tab"
        :class="{ active: current === t.value }"
        @click="switchTab(t.value)"
      >
        {{ t.label }}
        <text v-if="t.count != null" class="tab-count">({{ t.count }})</text>
      </view>
    </view>

    <view class="list">
      <view v-for="s in list" :key="s.id" class="card">
        <view class="row" @click="goEdit(s.id)">
          <image class="thumb" :src="s.picUrl" mode="aspectFill" />
          <view class="body">
            <view class="name">{{ s.name }}</view>
            <view class="cat">分类：{{ s.categoryName }}</view>
            <view class="tags">
              <text v-if="s.brokerageEnabled" class="tag brokerage">返利</text>
              <text v-if="s.pushBackEnabled" class="tag pushback">推N返一</text>
              <text v-if="s.stock === 0" class="tag oos">已售罄</text>
            </view>
            <view class="bottom">
              <text class="price">¥{{ fen2yuan(s.price) }}</text>
              <text class="sales">已售 {{ s.salesCount }}</text>
            </view>
          </view>
        </view>
        <view class="actions">
          <button
            class="act"
            :class="s.status === 0 ? 'down' : 'up'"
            @click="onToggleStatus(s)"
          >
            {{ s.status === 0 ? '下架' : '上架' }}
          </button>
          <button class="act edit" @click="goEdit(s.id)">编辑</button>
          <button class="act del" @click="onDelete(s)">删除</button>
        </view>
      </view>
      <view v-if="!list.length" class="empty">
        <view class="empty-icon">📦</view>
        <text class="empty-text">还没有商品</text>
        <button class="empty-btn" @click="goAdd">立即上架第一个商品</button>
      </view>
    </view>

    <view class="bottom-space" />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getSpuPage, updateStatus, deleteSpu } from '../../api/product.js';
import { fen2yuan } from '../../utils/format.js';

const all = ref([]);
const current = ref(-1);

const tabs = computed(() => [
  { label: '全部', value: -1, count: all.value.length },
  { label: '在售', value: 0, count: all.value.filter((s) => s.status === 0).length },
  { label: '已下架', value: 1, count: all.value.filter((s) => s.status === 1).length },
]);

const list = computed(() => {
  if (current.value === -1) return all.value;
  return all.value.filter((s) => s.status === current.value);
});

async function load() {
  const page = await getSpuPage();
  all.value = page.list;
}

function switchTab(v) {
  current.value = v;
}

function goAdd() {
  uni.navigateTo({ url: '/pages/product/edit' });
}
function goEdit(id) {
  uni.navigateTo({ url: `/pages/product/edit?id=${id}` });
}
function goBack() {
  const pages = getCurrentPages();
  if (pages.length > 1) {
    uni.navigateBack();
  } else {
    uni.switchTab({ url: '/pages/me/index' });
  }
}

async function onToggleStatus(s) {
  const next = s.status === 0 ? 1 : 0;
  const r = await uni.showModal({
    title: '提示',
    content: next === 1 ? `确认下架"${s.name}"？` : `确认上架"${s.name}"？`,
  });
  if (!r.confirm) return;
  await updateStatus({ id: s.id, status: next });
  s.status = next;
  uni.showToast({ title: next === 1 ? '已下架' : '已上架', icon: 'success' });
}

async function onDelete(s) {
  const r = await uni.showModal({
    title: '删除商品',
    content: `删除后无法恢复，确认删除"${s.name}"？`,
    confirmColor: '#EF4444',
  });
  if (!r.confirm) return;
  await deleteSpu(s.id);
  await load();
  uni.showToast({ title: '已删除', icon: 'success' });
}

onShow(() => {
  load();
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 0 24rpx 48rpx;
  min-height: 100vh;
}

.safe-top {
  padding-top: calc(env(safe-area-inset-top) + 24rpx);
}

.header {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx 12rpx;

  .back-btn {
    width: 64rpx;
    height: 64rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-left: -8rpx;
  }

  .back-arrow {
    font-size: 56rpx;
    line-height: 1;
    color: $text-primary;
    font-weight: 300;
  }

  .title {
    flex: 1;
    font-size: 40rpx;
    font-weight: 700;
    color: $text-primary;
  }

  .add-btn {
    display: flex;
    align-items: center;
    height: 64rpx;
    line-height: 64rpx;
    padding: 0 28rpx;
    background: $brand-primary;
    color: #fff;
    border-radius: $radius-pill;
    font-size: 26rpx;

    .plus {
      font-size: 32rpx;
      font-weight: 200;
      margin-right: 6rpx;
    }
  }
}

.tabs {
  display: flex;
  gap: 32rpx;
  padding: 0 12rpx 24rpx;

  .tab {
    position: relative;
    padding: 12rpx 0;
    font-size: 28rpx;
    color: $text-secondary;

    .tab-count {
      font-size: 22rpx;
      color: $text-placeholder;
      margin-left: 4rpx;
    }

    &.active {
      color: $text-primary;
      font-weight: 700;

      &::after {
        content: '';
        position: absolute;
        left: 25%;
        right: 25%;
        bottom: 0;
        height: 6rpx;
        border-radius: 3rpx;
        background: $brand-primary;
      }
    }
  }
}

.list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.row {
  display: flex;
  gap: 24rpx;
}

.thumb {
  width: 180rpx;
  height: 180rpx;
  flex-shrink: 0;
  border-radius: $radius-md;
  background: #f0f0f0;
}

.body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;

  .name {
    font-size: 30rpx;
    font-weight: 600;
    color: $text-primary;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .cat {
    font-size: 22rpx;
    color: $text-secondary;
  }
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-top: 4rpx;

  .tag {
    padding: 2rpx 12rpx;
    border-radius: $radius-pill;
    font-size: 20rpx;

    &.brokerage {
      background: #fff3ec;
      color: $brand-primary;
    }
    &.pushback {
      background: #ede9fe;
      color: #7c3aed;
    }
    &.oos {
      background: #fee2e2;
      color: $danger;
    }
  }
}

.bottom {
  margin-top: auto;
  display: flex;
  justify-content: space-between;
  align-items: baseline;

  .price {
    font-size: 36rpx;
    font-weight: 700;
    color: $brand-primary;
  }

  .sales {
    font-size: 22rpx;
    color: $text-secondary;
  }
}

.actions {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid $border-color;

  .act {
    flex: 1;
    height: 64rpx;
    line-height: 64rpx;
    padding: 0;
    border-radius: $radius-pill;
    font-size: 26rpx;
    background: #fff;

    &::after {
      border: 1rpx solid $border-color;
      border-radius: $radius-pill;
    }

    &.up {
      color: $success;
    }
    &.down {
      color: $warning;
    }
    &.edit {
      color: $info;
    }
    &.del {
      color: $danger;
    }
  }
}

.empty {
  padding: 160rpx 0 80rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24rpx;

  .empty-icon {
    font-size: 96rpx;
  }

  .empty-text {
    font-size: 28rpx;
    color: $text-secondary;
  }

  .empty-btn {
    margin-top: 16rpx;
    height: 80rpx;
    line-height: 80rpx;
    padding: 0 48rpx;
    background: $brand-primary;
    color: #fff;
    font-size: 28rpx;
    border-radius: $radius-pill;

    &::after {
      border: none;
    }
  }
}

.bottom-space {
  height: 80rpx;
}
</style>
