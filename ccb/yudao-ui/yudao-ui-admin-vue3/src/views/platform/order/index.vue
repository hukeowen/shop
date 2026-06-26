<!-- 平台运营 - 订单总览（跨所有店铺） -->
<template>
  <ContentWrap>
    <el-form :inline="true" :model="queryParams" class="-mb-15px" label-width="68px">
      <el-form-item label="订单号" prop="no">
        <el-input v-model="queryParams.no" class="!w-200px" clearable placeholder="订单号" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="所属店铺" prop="tenantId">
        <el-select v-model="queryParams.tenantId" class="!w-200px" clearable filterable placeholder="全部店铺" @change="handleQuery">
          <el-option v-for="s in shopOptions" :key="s.tenantId" :label="s.shopName" :value="s.tenantId" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" class="!w-140px" clearable placeholder="全部" @change="handleQuery">
          <el-option v-for="(t, k) in STATUS_MAP" :key="k" :label="t.text" :value="Number(k)" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon class="mr-5px" icon="ep:search" />搜索</el-button>
        <el-button @click="resetQuery"><Icon class="mr-5px" icon="ep:refresh" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="订单号" prop="no" min-width="180" />
      <el-table-column label="所属店铺" min-width="150">
        <template #default="{ row }">
          <el-tag type="success" size="small">{{ row.shopName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" label="下单金额" min-width="110">
        <template #default="{ row }">¥ {{ fenToYuan(row.totalPrice || 0) }}</template>
      </el-table-column>
      <el-table-column align="center" label="实付金额" min-width="110">
        <template #default="{ row }">
          <span :class="row.payStatus ? 'text-red-500 font-bold' : 'text-gray-400'">¥ {{ fenToYuan(row.payPrice || 0) }}</span>
        </template>
      </el-table-column>
      <el-table-column align="center" label="状态" min-width="100">
        <template #default="{ row }">
          <el-tag :type="(STATUS_MAP[row.status] || {}).type || 'info'">{{ (STATUS_MAP[row.status] || {}).text || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" label="用户ID" prop="userId" min-width="90" />
      <el-table-column :formatter="dateFormatter" align="center" label="下单时间" prop="createTime" width="170" />
    </el-table>
    <Pagination v-model:limit="queryParams.pageSize" v-model:page="queryParams.pageNo" :total="total" @pagination="getList" />
  </ContentWrap>
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import { fenToYuan } from '@/utils'
import * as PlatformApi from '@/api/merchant/platform'

defineOptions({ name: 'PlatformOrder' })

const STATUS_MAP: Record<number, { text: string; type: string }> = {
  0: { text: '待支付', type: 'warning' },
  10: { text: '待发货', type: 'primary' },
  20: { text: '待收货', type: 'primary' },
  30: { text: '已完成', type: 'success' },
  40: { text: '已取消', type: 'info' }
}

const loading = ref(false)
const total = ref(0)
const list = ref<any[]>([])
const shopOptions = ref<{ tenantId: number; shopName: string }[]>([])

const queryParams = ref({
  pageNo: 1,
  pageSize: 10,
  no: '',
  tenantId: undefined as number | undefined,
  status: undefined as number | undefined
})

const getList = async () => {
  loading.value = true
  try {
    const data = await PlatformApi.getPlatformOrderPage(queryParams.value)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.value.pageNo = 1
  getList()
}
const resetQuery = () => {
  queryParams.value = { pageNo: 1, pageSize: 10, no: '', tenantId: undefined, status: undefined }
  getList()
}
const loadShops = async () => {
  try {
    shopOptions.value = (await PlatformApi.getPlatformShops()) || []
  } catch {}
}

onMounted(async () => {
  await loadShops()
  await getList()
})
</script>
