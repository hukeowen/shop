<template>
  <ContentWrap>
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="72px">
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable class="!w-140px">
          <el-option label="待处理" :value="0" />
          <el-option label="处理中" :value="1" />
          <el-option label="已解决" :value="2" />
          <el-option label="已关闭" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="分类" prop="category">
        <el-select v-model="queryParams.category" placeholder="全部" clearable class="!w-160px">
          <el-option label="问题反馈" value="BUG" />
          <el-option label="功能建议" value="FEATURE" />
          <el-option label="支付" value="PAYMENT" />
          <el-option label="账户" value="ACCOUNT" />
          <el-option label="店铺" value="SHOP" />
          <el-option label="其他" value="OTHER" />
        </el-select>
      </el-form-item>
      <el-form-item label="用户 ID" prop="userId">
        <el-input v-model="queryParams.userId" placeholder="精确匹配" clearable class="!w-140px" />
      </el-form-item>
      <el-form-item v-if="isPlatformAdmin" label="跨租户" prop="allTenants">
        <el-switch v-model="queryParams.allTenants" />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="编号" prop="id" width="80" align="center" />
      <el-table-column label="店铺" prop="tenantId" width="100" align="center">
        <template #default="{ row }">
          <span v-if="row.tenantId === 0">平台</span>
          <span v-else>#{{ row.tenantId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="用户" prop="userId" width="100" align="center" />
      <el-table-column label="分类" prop="category" width="110" align="center">
        <template #default="{ row }">
          <el-tag size="small">{{ catLabel(row.category) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="内容" prop="content" min-width="280" show-overflow-tooltip />
      <el-table-column label="联系方式" prop="contact" width="140" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.status === 0" type="warning">待处理</el-tag>
          <el-tag v-else-if="row.status === 1" type="primary">处理中</el-tag>
          <el-tag v-else-if="row.status === 2" type="success">已解决</el-tag>
          <el-tag v-else type="info">已关闭</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" prop="createTime" width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="180" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openReplyDialog(row)">回复</el-button>
          <el-button link type="success" v-if="row.status !== 2" @click="markStatus(row, 2)">标记已解决</el-button>
          <el-button link type="info" v-if="row.status !== 3" @click="markStatus(row, 3)">关闭</el-button>
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

  <!-- 回复对话框 -->
  <el-dialog v-model="replyVisible" title="回复反馈" width="600px">
    <div v-if="current" class="origin">
      <div><b>{{ catLabel(current.category) }}</b> · 用户 #{{ current.userId }} · 店铺 {{ current.tenantId === 0 ? '平台' : '#' + current.tenantId }}</div>
      <div class="content">{{ current.content }}</div>
      <div v-if="current.contact" class="contact">联系方式：{{ current.contact }}</div>
    </div>
    <el-form ref="replyFormRef" :model="replyForm" :rules="replyRules" label-width="80px">
      <el-form-item label="回复内容" prop="reply">
        <el-input
          v-model="replyForm.reply"
          type="textarea"
          :rows="5"
          placeholder="给用户的回复，<= 2000 字"
          maxlength="2000"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="replyVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitReply">提交回复</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { dateFormatter } from '@/utils/formatTime'
import { UserFeedbackApi, UserFeedbackVO } from '@/api/merchant/userFeedback'
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'MerchantUserFeedback' })

const message = useMessage()
const userStore = useUserStore()
const isPlatformAdmin = computed(() => Number(userStore.getUser?.tenantId) === 1)

const loading = ref(false)
const list = ref<UserFeedbackVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  status: undefined as number | undefined,
  category: undefined as string | undefined,
  userId: undefined as number | undefined,
  allTenants: false,
})
const queryFormRef = ref()

const replyVisible = ref(false)
const submitting = ref(false)
const current = ref<UserFeedbackVO | null>(null)
const replyForm = reactive({ reply: '' })
const replyFormRef = ref()
const replyRules = {
  reply: [
    { required: true, message: '回复内容不能为空', trigger: 'blur' },
    { min: 1, max: 2000, message: '1-2000 字', trigger: 'blur' },
  ],
}

const CATEGORY_LABEL: Record<string, string> = {
  BUG: '问题反馈',
  FEATURE: '功能建议',
  PAYMENT: '支付',
  ACCOUNT: '账户',
  SHOP: '店铺',
  OTHER: '其他',
}
function catLabel(k: string) { return CATEGORY_LABEL[k] || '其他' }

async function getList() {
  loading.value = true
  try {
    const data = await UserFeedbackApi.page(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function handleQuery() {
  queryParams.pageNo = 1
  getList()
}
function resetQuery() {
  queryFormRef.value?.resetFields()
  Object.assign(queryParams, { status: undefined, category: undefined, userId: undefined, allTenants: false, pageNo: 1 })
  getList()
}

function openReplyDialog(row: UserFeedbackVO) {
  current.value = row
  replyForm.reply = row.reply || ''
  replyVisible.value = true
}
async function submitReply() {
  await replyFormRef.value?.validate()
  if (!current.value) return
  submitting.value = true
  try {
    await UserFeedbackApi.reply(current.value.id, replyForm.reply.trim())
    message.success('已回复')
    replyVisible.value = false
    getList()
  } finally {
    submitting.value = false
  }
}
async function markStatus(row: UserFeedbackVO, status: number) {
  const label = status === 2 ? '标记已解决' : status === 3 ? '关闭' : '更新状态'
  await message.confirm(`确认${label}？`)
  await UserFeedbackApi.updateStatus(row.id, status)
  message.success('已更新')
  getList()
}

onMounted(getList)
</script>

<style scoped>
.origin { background: #f5f7fa; padding: 12px 16px; border-radius: 6px; margin-bottom: 16px; }
.origin .content { margin-top: 8px; line-height: 1.6; color: #333; word-break: break-all; }
.origin .contact { margin-top: 6px; font-size: 12px; color: #888; }
</style>
