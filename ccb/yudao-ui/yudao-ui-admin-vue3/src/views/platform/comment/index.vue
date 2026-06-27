<!-- 平台运营 - 商品评价总览（跨租户查看各店铺评价） -->
<template>
  <ContentWrap>
    <el-form :inline="true" :model="queryParams" class="-mb-15px" label-width="68px">
      <el-form-item label="所属店铺" prop="tenantId">
        <el-select v-model="queryParams.tenantId" class="!w-220px" clearable filterable placeholder="全部店铺" @change="handleQuery">
          <el-option v-for="s in shopOptions" :key="s.tenantId" :label="s.shopName" :value="s.tenantId" />
        </el-select>
      </el-form-item>
      <el-form-item label="评分" prop="scores">
        <el-select v-model="queryParams.scores" class="!w-120px" clearable placeholder="全部" @change="handleQuery">
          <el-option v-for="n in [5, 4, 3, 2, 1]" :key="n" :label="n + ' 星'" :value="n" />
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
      <el-table-column label="所属店铺" min-width="130">
        <template #default="{ row }"><el-tag type="success" size="small">{{ row.shopName }}</el-tag></template>
      </el-table-column>
      <el-table-column label="商品" min-width="160">
        <template #default="{ row }">
          <div class="flex items-center">
            <el-image v-if="row.skuPicUrl" :src="row.skuPicUrl" :preview-src-list="[row.skuPicUrl]" preview-teleported
                      class="w-36px h-36px rounded mr-8px flex-shrink-0" fit="cover" />
            <span>{{ row.spuName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="会员" prop="userNickname" min-width="110" />
      <el-table-column align="center" label="评分" width="120">
        <template #default="{ row }">
          <el-rate :model-value="row.scores || 0" disabled size="small" />
        </template>
      </el-table-column>
      <el-table-column label="评价内容" min-width="220">
        <template #default="{ row }">
          <div>{{ row.content }}</div>
          <div v-if="row.picUrls && row.picUrls.length" class="flex mt-4px">
            <el-image v-for="(p, i) in row.picUrls" :key="i" :src="p" :preview-src-list="row.picUrls" :initial-index="i"
                      preview-teleported class="w-44px h-44px rounded mr-6px" fit="cover" />
          </div>
          <div v-if="row.replyStatus && row.replyContent" class="text-12px text-gray-500 mt-4px">
            商家回复：{{ row.replyContent }}
          </div>
        </template>
      </el-table-column>
      <el-table-column align="center" label="是否可见" width="90">
        <template #default="{ row }">
          <el-tag :type="row.visible ? 'success' : 'info'" size="small">{{ row.visible ? '显示' : '隐藏' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :formatter="dateFormatter" align="center" label="评价时间" prop="createTime" width="160" />
    </el-table>
    <Pagination v-model:limit="queryParams.pageSize" v-model:page="queryParams.pageNo" :total="total" @pagination="getList" />
  </ContentWrap>
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import * as PlatformApi from '@/api/merchant/platform'

defineOptions({ name: 'PlatformComment' })

const loading = ref(false)
const total = ref(0)
const list = ref<any[]>([])
const shopOptions = ref<{ tenantId: number; shopName: string }[]>([])

const queryParams = ref({
  pageNo: 1,
  pageSize: 10,
  tenantId: undefined as number | undefined,
  scores: undefined as number | undefined
})

const getList = async () => {
  loading.value = true
  try {
    const data = await PlatformApi.getPlatformCommentPage(queryParams.value)
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
  queryParams.value = { pageNo: 1, pageSize: 10, tenantId: undefined, scores: undefined }
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
