<template>
  <ContentWrap>
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="72px">
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-160px">
          <el-option label="待审核" :value="0" />
          <el-option label="已转账" :value="1" />
          <el-option label="已驳回" :value="2" />
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
      <el-table-column label="金额（元）" align="center" width="120">
        <template #default="{ row }">
          ￥{{ (row.amount / 100).toFixed(2) }}
        </template>
      </el-table-column>
      <el-table-column label="提现方式" align="center" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.withdrawType === 1" type="success">微信</el-tag>
          <el-tag v-else-if="row.withdrawType === 2" type="primary">支付宝</el-tag>
          <el-tag v-else type="warning">银行转账</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="账户姓名" align="center" prop="accountName" width="120" />
      <el-table-column label="账号/收款码" align="center" prop="accountNo" min-width="160" show-overflow-tooltip />
      <el-table-column label="银行名称" align="center" prop="bankName" width="120">
        <template #default="{ row }">
          <span v-if="row.bankName">{{ row.bankName }}</span>
          <span v-else class="text-gray-400">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.status === 0" type="warning">待审核</el-tag>
          <el-tag v-else-if="row.status === 1" type="success">已转账</el-tag>
          <el-tag v-else type="danger">已驳回</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="申请时间" align="center" prop="createTime" width="180" :formatter="dateFormatter" />
      <el-table-column label="操作" align="center" width="160" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button link type="success" @click="openApproveDialog(row)">通过</el-button>
            <el-button link type="danger" @click="openRejectDialog(row)">驳回</el-button>
          </template>
          <span v-else class="text-gray-400 text-sm">已处理</span>
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

  <!-- 通过弹窗：填写转账凭证 URL -->
  <el-dialog v-model="approveVisible" title="审核通过 - 上传转账凭证" width="480px" @close="resetApproveForm">
    <el-form :model="approveForm" ref="approveFormRef" label-width="100px">
      <el-form-item
        label="凭证图片URL"
        prop="voucherUrl"
        :rules="[{ required: true, message: '请填写转账凭证URL', trigger: 'blur' }]"
      >
        <el-input v-model="approveForm.voucherUrl" placeholder="请输入转账凭证图片地址" clearable />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="approveVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitLoading" @click="handleApprove">确认通过</el-button>
    </template>
  </el-dialog>

  <!-- 驳回弹窗：填写驳回原因 -->
  <el-dialog v-model="rejectVisible" title="驳回提现申请" width="480px" @close="resetRejectForm">
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
import * as WithdrawApi from '@/api/merchant/withdraw'

defineOptions({ name: 'MerchantWithdraw' })

const message = useMessage()

const loading = ref(true)
const total = ref(0)
const list = ref<WithdrawApi.WithdrawVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  status: undefined as number | undefined
})
const queryFormRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const data = await WithdrawApi.getWithdrawPage(queryParams)
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

// ===== 通过弹窗 =====
const approveVisible = ref(false)
const approveFormRef = ref()
const approveForm = reactive({ id: 0, voucherUrl: '' })
const submitLoading = ref(false)

const openApproveDialog = (row: WithdrawApi.WithdrawVO) => {
  approveForm.id = row.id
  approveForm.voucherUrl = ''
  approveVisible.value = true
}

const resetApproveForm = () => {
  approveFormRef.value?.resetFields()
}

const handleApprove = async () => {
  const valid = await approveFormRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    await WithdrawApi.auditWithdraw({ id: approveForm.id, approved: true, voucherUrl: approveForm.voucherUrl })
    message.success('审核通过成功')
    approveVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

// ===== 驳回弹窗 =====
const rejectVisible = ref(false)
const rejectFormRef = ref()
const rejectForm = reactive({ id: 0, rejectReason: '' })

const openRejectDialog = (row: WithdrawApi.WithdrawVO) => {
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
    await WithdrawApi.auditWithdraw({ id: rejectForm.id, approved: false, rejectReason: rejectForm.rejectReason })
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
