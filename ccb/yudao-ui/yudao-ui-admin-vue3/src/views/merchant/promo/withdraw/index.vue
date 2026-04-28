<template>
  <ContentWrap>
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="72px">
      <el-form-item label="状态" prop="status">
        <el-select
          v-model="queryParams.status"
          placeholder="请选择状态"
          clearable
          class="!w-160px"
        >
          <el-option label="待审批" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
          <el-option label="已打款" value="PAID" />
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
      <el-table-column label="用户 ID" align="center" prop="userId" width="100" />
      <el-table-column label="金额（元）" align="center" width="120">
        <template #default="{ row }">
          ￥{{ (row.amount / 100).toFixed(2) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'PENDING'" type="warning">待审批</el-tag>
          <el-tag v-else-if="row.status === 'APPROVED'" type="primary">已通过</el-tag>
          <el-tag v-else-if="row.status === 'REJECTED'" type="danger">已驳回</el-tag>
          <el-tag v-else-if="row.status === 'PAID'" type="success">已打款</el-tag>
          <el-tag v-else>{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="申请时间" align="center" prop="applyAt" width="180" :formatter="dateFormatter" />
      <el-table-column label="处理时间" align="center" prop="processedAt" width="180" :formatter="dateFormatter">
        <template #default="{ row }">
          <span v-if="row.processedAt">{{ formatDate(row.processedAt) }}</span>
          <span v-else class="text-gray-400">—</span>
        </template>
      </el-table-column>
      <el-table-column label="审批人" align="center" prop="processorId" width="100">
        <template #default="{ row }">
          <span v-if="row.processorId">{{ row.processorId }}</span>
          <span v-else class="text-gray-400">—</span>
        </template>
      </el-table-column>
      <el-table-column label="审批备注" align="center" prop="processorRemark" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.processorRemark">{{ row.processorRemark }}</span>
          <span v-else class="text-gray-400">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="220" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button link type="success" @click="openDialog(row, 'approve')">通过</el-button>
            <el-button link type="danger" @click="openDialog(row, 'reject')">驳回</el-button>
          </template>
          <el-button v-else-if="row.status === 'APPROVED'" link type="primary" @click="openDialog(row, 'mark-paid')">
            标记打款
          </el-button>
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

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px">
    <el-form :model="dialogForm" label-width="100px">
      <el-form-item :label="actionType === 'reject' ? '驳回原因' : '备注'">
        <el-input
          v-model="dialogForm.remark"
          type="textarea"
          :rows="3"
          :placeholder="actionType === 'reject' ? '请填写驳回原因（必填）' : '可选'"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">确认</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { dateFormatter, formatDate } from '@/utils/formatTime'
import * as PromoApi from '@/api/merchant/promo'

defineOptions({ name: 'MerchantPromoWithdraw' })

const message = useMessage()

const loading = ref(true)
const total = ref(0)
const list = ref<PromoApi.PromoWithdrawVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  status: undefined as string | undefined
})
const queryFormRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const data = await PromoApi.pagePromoWithdraw(queryParams)
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

// ===== 操作弹窗（共用） =====
type ActionType = 'approve' | 'reject' | 'mark-paid'
const dialogVisible = ref(false)
const submitting = ref(false)
const actionType = ref<ActionType>('approve')
const dialogForm = reactive({ id: 0, remark: '' })

const dialogTitle = computed(() => {
  if (actionType.value === 'approve') return '审批通过'
  if (actionType.value === 'reject') return '驳回提现'
  return '标记已打款'
})

const openDialog = (row: PromoApi.PromoWithdrawVO, type: ActionType) => {
  actionType.value = type
  dialogForm.id = row.id
  dialogForm.remark = ''
  dialogVisible.value = true
}

const onSubmit = async () => {
  if (actionType.value === 'reject' && !dialogForm.remark.trim()) {
    message.warning('请填写驳回原因')
    return
  }
  submitting.value = true
  try {
    if (actionType.value === 'approve') {
      await PromoApi.approvePromoWithdraw(dialogForm.id, dialogForm.remark || undefined)
      message.success('审批通过')
    } else if (actionType.value === 'reject') {
      await PromoApi.rejectPromoWithdraw(dialogForm.id, dialogForm.remark)
      message.success('已驳回（推广积分已自动退还）')
    } else {
      await PromoApi.markPaidPromoWithdraw(dialogForm.id, dialogForm.remark || undefined)
      message.success('已标记打款')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitting.value = false
  }
}

onMounted(getList)
</script>
