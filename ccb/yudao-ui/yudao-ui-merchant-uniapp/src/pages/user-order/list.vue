<template>
  <view class="page">
    <!-- Tab bar -->
    <view class="tabs">
      <view
        v-for="tab in tabs"
        :key="tab.status"
        :class="['tab', activeTab === tab.status ? 'tab-active' : '']"
        @click="switchTab(tab.status)"
      >{{ tab.label }}</view>
    </view>

    <view v-if="loading" class="empty-tip">加载中...</view>
    <view v-else-if="!orders.length" class="empty-tip">暂无订单</view>
    <view v-else class="order-list">
      <view
        v-for="order in orders"
        :key="order.id"
        class="order-card card"
      >
        <view class="order-head">
          <text class="order-no">订单 #{{ order.no || order.id }}</text>
          <text class="order-status" :class="statusClass(order.status)">{{ statusLabel(order.status) }}</text>
        </view>
        <view
          v-for="(item, i) in (order.items || order.orderItems || []).slice(0, 2)"
          :key="i"
          class="order-item"
        >
          <text class="item-name">{{ item.spuName || item.name || '商品' }}</text>
          <text class="item-price">¥{{ fen2yuan(item.price) }} × {{ item.count }}</text>
        </view>
        <view class="order-foot">
          <text class="order-time">{{ formatTime(order.createTime) }}</text>
          <text class="order-total">合计 ¥{{ fen2yuan(order.payPrice || order.totalPrice || 0) }}</text>
        </view>
      </view>
    </view>

    <!-- Pagination -->
    <view v-if="hasMore && !loading" class="load-more" @click="loadMore">加载更多</view>
  </view>
</template>

<script>
import { request } from '../../api/request.js';
import { fen2yuan } from '../../utils/format.js';

const STATUS_MAP = {
  0: '待付款',
  10: '待发货',
  20: '待收货',
  30: '已完成',
  40: '已取消',
};

export default {
  data() {
    return {
      tenantId: null,
      activeTab: -1,
      tabs: [
        { status: -1, label: '全部' },
        { status: 0, label: '待付款' },
        { status: 10, label: '待发货' },
        { status: 30, label: '已完成' },
        { status: 40, label: '已取消' },
      ],
      orders: [],
      loading: false,
      pageNo: 1,
      hasMore: false,
    };
  },
  onLoad(query) {
    this.tenantId = query.tenantId ? Number(query.tenantId) : null;
    this.loadOrders(true);
  },
  methods: {
    fen2yuan,
    statusLabel(status) {
      return STATUS_MAP[status] || '未知';
    },
    statusClass(status) {
      if (status === 0) return 'status-pending';
      if (status === 30) return 'status-done';
      if (status === 40) return 'status-cancel';
      return '';
    },
    formatTime(ts) {
      if (!ts) return '';
      const d = new Date(ts);
      return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`;
    },
    switchTab(status) {
      this.activeTab = status;
      this.loadOrders(true);
    },
    async loadOrders(reset = false) {
      if (reset) {
        this.pageNo = 1;
        this.orders = [];
      }
      this.loading = true;
      try {
        let url = `/app-api/trade/order/page?pageNo=${this.pageNo}&pageSize=10`;
        if (this.activeTab !== -1) url += `&status=${this.activeTab}`;
        const res = await request({ url, tenantId: this.tenantId });
        const list = (res && res.list) ? res.list : (Array.isArray(res) ? res : []);
        this.orders = reset ? list : [...this.orders, ...list];
        const total = (res && res.total) || 0;
        this.hasMore = this.orders.length < total;
      } catch {
        if (reset) this.orders = [];
      } finally {
        this.loading = false;
      }
    },
    loadMore() {
      this.pageNo++;
      this.loadOrders(false);
    },
  },
};
</script>

<style lang="scss">
@import '../../uni.scss';

.page {
  min-height: 100vh;
  background: #f6f7f9;
}

.tabs {
  display: flex;
  background: #fff;
  border-bottom: 1rpx solid $border-color;
  overflow-x: auto;
}

.tab {
  flex-shrink: 0;
  padding: 24rpx 28rpx;
  font-size: 26rpx;
  color: $text-secondary;
}

.tab.tab-active {
  color: $brand-primary;
  border-bottom: 4rpx solid $brand-primary;
  font-weight: 600;
}

.empty-tip {
  text-align: center;
  color: $text-placeholder;
  padding: 120rpx 0;
  font-size: 28rpx;
}

.order-list {
  padding: 24rpx;
}

.order-card {
  padding: 28rpx 32rpx;
  margin-bottom: 20rpx;
  border-radius: $radius-lg;
  background: $bg-card;
}

.order-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}

.order-no {
  font-size: 24rpx;
  color: $text-placeholder;
}

.order-status {
  font-size: 24rpx;
  color: $text-secondary;
}

.order-status.status-pending {
  color: $brand-primary;
}

.order-status.status-done {
  color: #22c55e;
}

.order-status.status-cancel {
  color: $text-placeholder;
}

.order-item {
  display: flex;
  justify-content: space-between;
  padding: 8rpx 0;
}

.item-name {
  font-size: 28rpx;
  color: $text-primary;
  flex: 1;
}

.item-price {
  font-size: 26rpx;
  color: $text-secondary;
}

.order-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid $border-color;
}

.order-time {
  font-size: 24rpx;
  color: $text-placeholder;
}

.order-total {
  font-size: 28rpx;
  font-weight: 600;
  color: $text-primary;
}

.load-more {
  text-align: center;
  color: $text-secondary;
  font-size: 26rpx;
  padding: 32rpx 0;
}
</style>
