<!-- 平台运营 - 店铺套餐（每店当前套餐/到期/累计付费） -->
<template>
  <ContentWrap>
    <el-form :inline="true" :model="queryParams" class="-mb-15px" label-width="68px">
      <el-form-item label="所属店铺" prop="tenantId">
        <el-select
          v-model="queryParams.tenantId"
          class="!w-200px"
          clearable
          filterable
          placeholder="全部店铺"
          @change="handleQuery"
        >
          <el-option
            v-for="s in shopOptions"
            :key="s.tenantId"
            :label="s.shopName"
            :value="s.tenantId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="套餐" prop="level">
        <el-select
          v-model="queryParams.level"
          class="!w-160px"
          clearable
          placeholder="全部"
          @change="handleQuery"
        >
          <el-option label="试用版" value="TRIAL" />
          <el-option label="旺铺版(BASIC)" value="BASIC" />
          <el-option label="旗舰版(PRO)" value="PRO" />
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
      <el-table-column label="店铺" min-width="160" prop="shopName">
        <template #default="{ row }">
          <el-tag type="success" size="small">{{ row.shopName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" label="当前套餐" min-width="130">
        <template #default="{ row }">
          <el-tag v-if="row.paid" type="warning">{{ row.levelName }}</el-tag>
          <el-tag v-else type="info">{{ row.levelName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" label="是否付费" min-width="100">
        <template #default="{ row }">
          <el-tag v-if="row.paid" type="danger">付费</el-tag>
          <span v-else class="text-gray-400">试用/未付</span>
        </template>
      </el-table-column>
      <el-table-column
        align="center"
        label="到期时间"
        min-width="170"
        prop="serviceExpireAt"
        :formatter="dateFormatter"
      />
      <el-table-column align="center" label="累计付费金额" min-width="130">
        <template #default="{ row }">
          <span :class="row.totalPaidFen > 0 ? 'text-red-500 font-bold' : 'text-gray-400'">
            ¥ {{ fenToYuan(row.totalPaidFen || 0) }}
          </span>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import { fenToYuan } from '@/utils'
import * as PlatformApi from '@/api/merchant/platform'

defineOptions({ name: 'PlatformSubscription' })

const loading = ref(false)
const total = ref(0)
const list = ref<any[]>([])
const shopOptions = ref<{ tenantId: number; shopName: string }[]>([])

const queryParams = ref({
  pageNo: 1,
  pageSize: 10,
  tenantId: undefined as number | undefined,
  level: undefined as string | undefined
})

const getList = async () => {
  loading.value = true
  try {
    const data = await PlatformApi.getPlatformSubscriptionPage(queryParams.value)
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
  queryParams.value = { pageNo: 1, pageSize: 10, tenantId: undefined, level: undefined }
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
