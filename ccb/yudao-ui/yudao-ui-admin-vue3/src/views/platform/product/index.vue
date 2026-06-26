<!-- 平台运营 - 商品总览（跨所有商户店铺，免切租户） -->
<template>
  <ContentWrap>
    <el-form :inline="true" :model="queryParams" class="-mb-15px" label-width="68px">
      <el-form-item label="商品名称" prop="name">
        <el-input
          v-model="queryParams.name"
          class="!w-200px"
          clearable
          placeholder="请输入商品名称"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
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
      <el-form-item label="状态" prop="status">
        <el-select
          v-model="queryParams.status"
          class="!w-140px"
          clearable
          placeholder="全部"
          @change="handleQuery"
        >
          <el-option label="上架" :value="1" />
          <el-option label="下架" :value="0" />
          <el-option label="回收站" :value="4" />
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
      <el-table-column label="商品编号" prop="id" min-width="90" />
      <el-table-column label="所属店铺" min-width="150">
        <template #default="{ row }">
          <el-tag type="success" size="small">{{ row.shopName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="商品信息" min-width="280">
        <template #default="{ row }">
          <div class="flex items-center">
            <el-image
              v-if="row.picUrl"
              fit="cover"
              :src="row.picUrl"
              :preview-src-list="[row.picUrl]"
              preview-teleported
              class="flex-none w-44px h-44px"
            />
            <span class="ml-2">{{ row.name }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column align="center" label="价格" min-width="100">
        <template #default="{ row }">¥ {{ fenToYuan(row.price) }}</template>
      </el-table-column>
      <el-table-column align="center" label="销量" prop="salesCount" min-width="80" />
      <el-table-column align="center" label="库存" prop="stock" min-width="80" />
      <el-table-column align="center" label="状态" min-width="120">
        <template #default="{ row }">
          <el-switch
            v-if="row.status === 0 || row.status === 1"
            :model-value="row.status"
            :active-value="1"
            :inactive-value="0"
            active-text="上架"
            inactive-text="下架"
            inline-prompt
            @change="(v) => handleStatus(row, v as number)"
          />
          <el-tag v-else-if="row.status === 2" type="warning">审核中</el-tag>
          <el-tag v-else-if="row.status === 3" type="danger">审核拒绝</el-tag>
          <el-tag v-else type="info">回收站</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        :formatter="dateFormatter"
        align="center"
        label="创建时间"
        prop="createTime"
        width="170"
      />
      <el-table-column align="center" label="操作" min-width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 1"
            link
            type="danger"
            @click="handleStatus(row, 0)"
          >下架</el-button>
          <el-button
            v-else-if="row.status === 0"
            link
            type="success"
            @click="handleStatus(row, 1)"
          >上架</el-button>
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

defineOptions({ name: 'PlatformProductOverview' })

const message = useMessage()

const loading = ref(false)
const total = ref(0)
const list = ref<any[]>([])
const shopOptions = ref<{ tenantId: number; shopName: string }[]>([])

const queryParams = ref({
  pageNo: 1,
  pageSize: 10,
  name: '',
  tenantId: undefined as number | undefined,
  status: undefined as number | undefined
})

const getList = async () => {
  loading.value = true
  try {
    const data = await PlatformApi.getPlatformProductPage(queryParams.value)
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
  queryParams.value = { pageNo: 1, pageSize: 10, name: '', tenantId: undefined, status: undefined }
  getList()
}

const handleStatus = async (row: any, newStatus: number) => {
  try {
    const text = newStatus === 1 ? '上架' : '下架'
    await message.confirm(`确认要${text}「${row.shopName}」的「${row.name}」吗？`)
    await PlatformApi.updatePlatformProductStatus(row.id, newStatus)
    message.success(text + '成功')
    await getList()
  } catch {}
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
