<!-- 平台运营 - 会员管理（按店铺查会员） -->
<template>
  <ContentWrap>
    <el-form :inline="true" :model="queryParams" class="-mb-15px" label-width="68px">
      <el-form-item label="所属店铺" prop="tenantId">
        <el-select v-model="queryParams.tenantId" class="!w-220px" clearable filterable placeholder="全部店铺" @change="handleQuery">
          <el-option v-for="s in shopOptions" :key="s.tenantId" :label="s.shopName" :value="s.tenantId" />
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
      <el-table-column label="所属店铺" min-width="150">
        <template #default="{ row }"><el-tag type="success" size="small">{{ row.shopName }}</el-tag></template>
      </el-table-column>
      <el-table-column label="会员" min-width="160">
        <template #default="{ row }">
          <div>{{ row.nickname || ('用户' + row.userId) }}</div>
          <div class="text-12px text-gray-400">{{ row.mobile || '—' }}</div>
        </template>
      </el-table-column>
      <el-table-column align="center" label="店内余额" min-width="100">
        <template #default="{ row }">¥ {{ fenToYuan(row.balance || 0) }}</template>
      </el-table-column>
      <el-table-column align="center" label="店内积分" prop="points" min-width="90" />
      <el-table-column align="center" label="推荐人" min-width="100">
        <template #default="{ row }">{{ row.referrerUserId ? ('用户' + row.referrerUserId) : '—' }}</template>
      </el-table-column>
      <el-table-column :formatter="dateFormatter" align="center" label="首次进店" prop="firstVisitAt" width="160" />
      <el-table-column :formatter="dateFormatter" align="center" label="最近进店" prop="lastVisitAt" width="160" />
    </el-table>
    <Pagination v-model:limit="queryParams.pageSize" v-model:page="queryParams.pageNo" :total="total" @pagination="getList" />
  </ContentWrap>
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import { fenToYuan } from '@/utils'
import * as PlatformApi from '@/api/merchant/platform'

defineOptions({ name: 'PlatformMember' })

const loading = ref(false)
const total = ref(0)
const list = ref<any[]>([])
const shopOptions = ref<{ tenantId: number; shopName: string }[]>([])

const queryParams = ref({ pageNo: 1, pageSize: 10, tenantId: undefined as number | undefined })

const getList = async () => {
  loading.value = true
  try {
    const data = await PlatformApi.getPlatformMemberPage(queryParams.value)
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
  queryParams.value = { pageNo: 1, pageSize: 10, tenantId: undefined }
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
