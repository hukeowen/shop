<!-- 平台运营 - 数据概览 -->
<template>
  <ContentWrap>
    <el-row :gutter="16" v-loading="loading">
      <el-col :span="6" v-for="c in cards" :key="c.label" class="mb-16px">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">{{ c.label }}</div>
          <div class="stat-value" :style="{ color: c.color }">{{ c.value }}</div>
          <div class="stat-sub" v-if="c.sub">{{ c.sub }}</div>
        </el-card>
      </el-col>
    </el-row>
  </ContentWrap>
</template>

<script lang="ts" setup>
import { fenToYuan } from '@/utils'
import * as PlatformApi from '@/api/merchant/platform'

defineOptions({ name: 'PlatformStats' })

const loading = ref(false)
const s = ref<any>({})

const cards = computed(() => [
  { label: '订单总额(GMV)', value: '¥ ' + fenToYuan(s.value.orderTotalFen || 0), color: '#409EFF', sub: `共 ${s.value.orderCount || 0} 单` },
  { label: '净销售额(用户实付)', value: '¥ ' + fenToYuan(s.value.netSalesFen || 0), color: '#F56C6C', sub: `已支付 ${s.value.paidOrderCount || 0} 单` },
  { label: '套餐付费收入(平台)', value: '¥ ' + fenToYuan(s.value.subscriptionRevenueFen || 0), color: '#E6A23C', sub: 'SaaS 套餐累计' },
  { label: '付费商户', value: (s.value.paidMerchantCount || 0) + ' 家', color: '#67C23A', sub: `共 ${s.value.merchantCount || 0} 家商户` },
  { label: '店铺数', value: (s.value.shopCount || 0) + ' 家', color: '#909399', sub: '' },
  { label: '商品数', value: (s.value.productCount || 0) + ' 个', color: '#909399', sub: '全平台' },
  { label: '即将到期商户', value: (s.value.expiringSoonCount || 0) + ' 家', color: '#F56C6C', sub: '30 天内到期·需续费' }
])

const load = async () => {
  loading.value = true
  try {
    s.value = (await PlatformApi.getPlatformStats()) || {}
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style lang="scss" scoped>
.stat-card { text-align: center; }
.stat-label { font-size: 14px; color: #909399; }
.stat-value { font-size: 26px; font-weight: 700; margin: 8px 0 4px; }
.stat-sub { font-size: 12px; color: #C0C4CC; }
</style>
