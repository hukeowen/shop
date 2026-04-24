<template>
  <ContentWrap>
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="商户ID" prop="merchantId">
        <el-input
          v-model.number="queryParams.merchantId"
          placeholder="请输入商户ID"
          clearable
          class="!w-160px"
        />
      </el-form-item>
      <el-form-item label="业务类型" prop="bizType">
        <el-select v-model="queryParams.bizType" placeholder="全部" clearable class="!w-160px">
          <el-option label="购买套餐" :value="1" />
          <el-option label="视频生成扣减" :value="2" />
          <el-option label="生成失败回补" :value="3" />
          <el-option label="平台手动调整" :value="4" />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间" prop="createTime">
        <el-date-picker
          v-model="queryParams.createTime"
          value-format="YYYY-MM-DD HH:mm:ss"
          type="daterange"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :default-time="[new Date('1 00:00:00'), new Date('1 23:59:59')]"
          class="!w-280px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="ID" align="center" prop="id" width="80" />
      <el-table-column label="商户ID" align="center" prop="merchantId" width="100" />
      <el-table-column label="变动" align="center" prop="quotaChange" width="90">
        <template #default="scope">
          <span :class="scope.row.quotaChange > 0 ? 'text-green-600' : 'text-red-500'">
            {{ scope.row.quotaChange > 0 ? '+' : '' }}{{ scope.row.quotaChange }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="变动后余量" align="center" prop="quotaAfter" width="110" />
      <el-table-column label="业务类型" align="center" width="130">
        <template #default="scope">
          <el-tag :type="BIZ_TYPE_TAG[scope.row.bizType] || ''">
            {{ BIZ_TYPE_LABEL[scope.row.bizType] || scope.row.bizType }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="业务外键" align="center" prop="bizId" :show-overflow-tooltip="true" />
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column
        label="创建时间"
        align="center"
        prop="createTime"
        width="180"
        :formatter="dateFormatter"
      />
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import * as PackageApi from '@/api/merchant/aiVideoPackage'

defineOptions({ name: 'MerchantAiVideoQuotaLog' })

const loading = ref(true)
const total = ref(0)
const list = ref<PackageApi.AiVideoQuotaLogVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 20,
  merchantId: undefined as number | undefined,
  bizType: undefined as number | undefined,
  createTime: [] as string[]
})
const queryFormRef = ref()

const BIZ_TYPE_LABEL: Record<number, string> = {
  1: '购买套餐',
  2: '视频扣减',
  3: '失败回补',
  4: '平台调整'
}
const BIZ_TYPE_TAG: Record<number, string> = {
  1: 'success',
  2: 'danger',
  3: 'warning',
  4: 'info'
}

const getList = async () => {
  loading.value = true
  try {
    const data = await PackageApi.getQuotaLogPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value.resetFields()
  handleQuery()
}

onMounted(() => {
  getList()
})
</script>
