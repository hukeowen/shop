<template>
  <ContentWrap>
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="72px">
      <el-form-item label="申请状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-160px">
          <el-option label="未申请" :value="0" />
          <el-option label="审核中" :value="1" />
          <el-option label="已开通" :value="2" />
          <el-option label="已驳回" :value="3" />
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
      <el-table-column label="商户名称" align="center" prop="shopName" min-width="140" />
      <el-table-column label="通联商户号" align="center" prop="tlMchId" width="160">
        <template #default="{ row }">
          <span v-if="row.tlMchId">{{ row.tlMchId }}</span>
          <span v-else class="text-gray-400">—</span>
        </template>
      </el-table-column>
      <el-table-column label="申请状态" align="center" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.payApplyStatus === 0" type="info">未申请</el-tag>
          <el-tag v-else-if="row.payApplyStatus === 1" type="warning">审核中</el-tag>
          <el-tag v-else-if="row.payApplyStatus === 2" type="success">已开通</el-tag>
          <el-tag v-else type="danger">已驳回</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="驳回原因" align="center" prop="payApplyRejectReason" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.payApplyRejectReason">{{ row.payApplyRejectReason }}</span>
          <span v-else class="text-gray-400">—</span>
        </template>
      </el-table-column>
      <el-table-column label="申请时间" align="center" prop="createTime" width="180" :formatter="dateFormatter" />
      <el-table-column label="操作" align="center" width="160" fixed="right">
        <template #default="{ row }">
          <template v-if="row.payApplyStatus === 1">
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
  <el-dialog v-model="rejectVisible" title="驳回在线支付开通申请" width="480px" @close="resetRejectForm">
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
import * as PayApplyApi from '@/api/merchant/payApply'

defineOptions({ name: 'MerchantPayApply' })

const message = useMessage()

const loading = ref(true)
const total = ref(0)
const list = ref<PayApplyApi.ShopPayApplyVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  status: undefined as number | undefined
})
const queryFormRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const data = await PayApplyApi.getPayApplyPage(queryParams)
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

const handleApprove = async (row: PayApplyApi.ShopPayApplyVO) => {
  submitLoading.value = true
  try {
    await PayApplyApi.auditPayApply({ shopId: row.id, approved: true })
    message.success('审核通过成功')
    getList()
  } finally {
    submitLoading.value = false
  }
}

// ===== 驳回弹窗 =====
const rejectVisible = ref(false)
const rejectFormRef = ref()
const rejectForm = reactive({ shopId: 0, rejectReason: '' })

const openRejectDialog = (row: PayApplyApi.ShopPayApplyVO) => {
  rejectForm.shopId = row.id
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
    await PayApplyApi.auditPayApply({
      shopId: rejectForm.shopId,
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
