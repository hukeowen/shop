<template>
  <ContentWrap>
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="72px">
      <el-form-item label="商户名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="请输入商户名称" clearable class="!w-200px" />
      </el-form-item>
      <el-form-item label="联系电话" prop="contactPhone">
        <el-input v-model="queryParams.contactPhone" placeholder="请输入联系电话" clearable class="!w-160px" />
      </el-form-item>
      <el-form-item label="审核状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-140px">
          <el-option label="待审核" :value="0" />
          <el-option label="已通过" :value="1" />
          <el-option label="已驳回" :value="2" />
          <el-option label="已禁用" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="编号" align="center" prop="id" width="80" />
      <el-table-column label="商户名称" align="center" prop="name" min-width="140" />
      <el-table-column label="联系人" align="center" prop="contactName" width="100" />
      <el-table-column label="联系电话" align="center" prop="contactPhone" width="140" />
      <el-table-column label="经营类目" align="center" prop="businessCategory" min-width="120" show-overflow-tooltip />
      <el-table-column label="审核状态" align="center" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status === 0" type="warning">待审核</el-tag>
          <el-tag v-else-if="row.status === 1" type="success">已通过</el-tag>
          <el-tag v-else-if="row.status === 2" type="danger">已驳回</el-tag>
          <el-tag v-else type="info">已禁用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="驳回原因" align="center" prop="rejectReason" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.rejectReason">{{ row.rejectReason }}</span>
          <span v-else class="text-gray-400">—</span>
        </template>
      </el-table-column>
      <el-table-column label="申请时间" align="center" prop="createTime" width="180" :formatter="dateFormatter" />
      <el-table-column label="操作" align="center" width="160" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button link type="success" @click="handleApprove(row)">通过</el-button>
            <el-button link type="danger" @click="openRejectDialog(row)">驳回</el-button>
          </template>
          <span v-else class="text-gray-400 text-sm">—</span>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <!-- 驳回弹窗 -->
  <el-dialog v-model="rejectVisible" title="驳回商户入驻申请" width="480px" @close="resetRejectForm">
    <el-form :model="rejectForm" ref="rejectFormRef" label-width="100px">
      <el-form-item
        label="驳回原因"
        prop="rejectReason"
        :rules="[{ required: true, message: '请填写驳回原因', trigger: 'blur' }]"
      >
        <el-input
          v-model="rejectForm.rejectReason"
          type="textarea"
          :rows="3"
          placeholder="请输入驳回原因"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="rejectVisible = false">取消</el-button>
      <el-button type="danger" :loading="submitLoading" @click="handleReject">确认驳回</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import * as MerchantInfoApi from '@/api/merchant/info'

defineOptions({ name: 'MerchantInfo' })

const message = useMessage()

const loading = ref(true)
const total = ref(0)
const list = ref<MerchantInfoApi.MerchantInfoVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: undefined as string | undefined,
  contactPhone: undefined as string | undefined,
  status: undefined as number | undefined
})
const queryFormRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const data = await MerchantInfoApi.getMerchantPage(queryParams)
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

// ===== 通过 =====
const submitLoading = ref(false)

const handleApprove = async (row: MerchantInfoApi.MerchantInfoVO) => {
  submitLoading.value = true
  try {
    await MerchantInfoApi.auditMerchant({ id: row.id, approved: true })
    message.success('审核通过成功')
    getList()
  } finally {
    submitLoading.value = false
  }
}

// ===== 驳回弹窗 =====
const rejectVisible = ref(false)
const rejectFormRef = ref()
const rejectForm = reactive({ id: 0, rejectReason: '' })

const openRejectDialog = (row: MerchantInfoApi.MerchantInfoVO) => {
  rejectForm.id = row.id
  rejectForm.rejectReason = ''
  rejectVisible.value = true
}

const resetRejectForm = () => {
  rejectFormRef.value?.resetFields()
}

const handleReject = async () => {
  const valid = await rejectFormRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    await MerchantInfoApi.auditMerchant({
      id: rejectForm.id,
      approved: false,
      rejectReason: rejectForm.rejectReason
    })
    message.success('驳回成功')
    rejectVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

onMounted(() => {
  getList()
})
</script>
