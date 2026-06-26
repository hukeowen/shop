<!-- 平台运营 - 店铺管理（通联费率可改 + 每店自动上架开关） -->
<template>
  <ContentWrap>
    <el-form :inline="true" :model="queryParams" class="-mb-15px" label-width="68px">
      <el-form-item label="店铺名称" prop="shopName">
        <el-input v-model="queryParams.shopName" class="!w-200px" clearable placeholder="店铺名称" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon class="mr-5px" icon="ep:search" />搜索</el-button>
        <el-button @click="resetQuery"><Icon class="mr-5px" icon="ep:refresh" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="店铺" min-width="150" prop="shopName" />
      <el-table-column align="center" label="客服电话" prop="mobile" min-width="120" />
      <el-table-column label="地址" prop="address" min-width="180" show-overflow-tooltip />
      <el-table-column align="center" label="在线支付" min-width="120">
        <template #default="{ row }">
          <el-tag v-if="row.onlinePayEnabled" type="success" size="small">已开通</el-tag>
          <el-tag v-else type="info" size="small">未开通</el-tag>
          <div v-if="row.tlMchId" class="text-12px text-gray-400">{{ row.tlMchId }}</div>
        </template>
      </el-table-column>
      <el-table-column align="center" label="通联费率" min-width="150">
        <template #default="{ row }">
          <el-input
            v-model="row.tlFeeRate"
            size="small"
            class="!w-90px"
            placeholder="如 0.6"
            @blur="saveRate(row)"
          >
            <template #append>%</template>
          </el-input>
        </template>
      </el-table-column>
      <el-table-column align="center" label="自动上架(免审核)" min-width="130">
        <template #default="{ row }">
          <el-switch
            v-model="row.autoApprove"
            :active-value="1"
            :inactive-value="0"
            active-text="自动"
            inactive-text="需审核"
            inline-prompt
            @change="(v) => saveAutoApprove(row, v as number)"
          />
        </template>
      </el-table-column>
      <el-table-column :formatter="dateFormatter" align="center" label="入驻时间" prop="createTime" width="170" />
    </el-table>
    <Pagination v-model:limit="queryParams.pageSize" v-model:page="queryParams.pageNo" :total="total" @pagination="getList" />
  </ContentWrap>
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import * as PlatformApi from '@/api/merchant/platform'

defineOptions({ name: 'PlatformShop' })

const message = useMessage()
const loading = ref(false)
const total = ref(0)
const list = ref<any[]>([])
const queryParams = ref({ pageNo: 1, pageSize: 10, shopName: '' })

const getList = async () => {
  loading.value = true
  try {
    const data = await PlatformApi.getPlatformShopPage(queryParams.value)
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
  queryParams.value = { pageNo: 1, pageSize: 10, shopName: '' }
  getList()
}
const saveRate = async (row: any) => {
  try {
    await PlatformApi.updateShopRate(row.id, row.tlFeeRate)
    message.success('费率已保存')
  } catch {}
}
const saveAutoApprove = async (row: any, v: number) => {
  try {
    await PlatformApi.updateShopAutoApprove(row.id, v)
    message.success(v === 1 ? '已设为自动上架' : '已设为需审核')
  } catch {
    row.autoApprove = v === 1 ? 0 : 1
  }
}
onMounted(getList)
</script>
