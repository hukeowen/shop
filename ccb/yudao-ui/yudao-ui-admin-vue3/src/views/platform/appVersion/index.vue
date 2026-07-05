<!-- 平台运营 - App 版本管理（商户端 App 自动升级） -->
<template>
  <ContentWrap>
    <el-alert
      class="mb-15px"
      type="info"
      :closable="false"
      title="商户端 App 启动时会拉取此处「已发布」的最高版本号（versionCode）比对，若比用户机上版本更高则弹窗提示升级。版本号必须比线上更大，且勾选强制更新后用户不可跳过。"
    />
    <!-- 搜索 -->
    <el-form :inline="true" :model="queryParams" class="-mb-15px" label-width="68px">
      <el-form-item label="平台" prop="platform">
        <el-select v-model="queryParams.platform" class="!w-160px" clearable placeholder="全部" @change="handleQuery">
          <el-option label="Android" value="android" />
          <el-option label="iOS" value="ios" />
        </el-select>
      </el-form-item>
      <el-form-item label="版本名" prop="versionName">
        <el-input v-model="queryParams.versionName" class="!w-160px" clearable placeholder="如 1.0.2" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" class="!w-120px" clearable placeholder="全部" @change="handleQuery">
          <el-option label="发布" :value="0" />
          <el-option label="停用" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon class="mr-5px" icon="ep:search" />搜索</el-button>
        <el-button @click="resetQuery"><Icon class="mr-5px" icon="ep:refresh" />重置</el-button>
        <el-button type="primary" @click="openForm()"><Icon class="mr-5px" icon="ep:plus" />发布新版本</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="平台" prop="platform" width="90" align="center" />
      <el-table-column label="版本名" prop="versionName" width="120" align="center" />
      <el-table-column label="版本号" prop="versionCode" width="90" align="center" />
      <el-table-column label="强制更新" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.forceUpdate" type="danger">强制</el-tag>
          <el-tag v-else type="info">可选</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.status === 0" type="success">发布</el-tag>
          <el-tag v-else type="info">停用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="大小" width="100" align="center">
        <template #default="{ row }">{{ fmtSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="更新说明" prop="updateLog" min-width="180" show-overflow-tooltip />
      <el-table-column label="APK" width="80" align="center">
        <template #default="{ row }">
          <el-link v-if="row.downloadUrl" :href="row.downloadUrl" target="_blank" type="primary">下载</el-link>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="发布时间" prop="createTime" width="170" align="center" :formatter="dateFormatter" />
      <el-table-column label="操作" width="140" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row.id)">修改</el-button>
          <el-button link type="danger" @click="handleDelete(row.id)">删除</el-button>
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

  <!-- 新增/修改弹窗 -->
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="640px">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
      <el-form-item label="平台" prop="platform">
        <el-select v-model="formData.platform" class="!w-200px">
          <el-option label="Android" value="android" />
          <el-option label="iOS" value="ios" />
        </el-select>
      </el-form-item>
      <el-form-item label="版本名" prop="versionName">
        <el-input v-model="formData.versionName" class="!w-200px" placeholder="如 1.0.2" />
      </el-form-item>
      <el-form-item label="版本号" prop="versionCode">
        <el-input-number v-model="formData.versionCode" :min="1" :step="1" class="!w-200px" />
        <span class="ml-10px text-gray-400 text-12px">整数，必须比线上版本大</span>
      </el-form-item>
      <el-form-item label="APK 文件" prop="downloadUrl">
        <div class="w-full">
          <UploadFile
            v-model="formData.downloadUrl"
            :file-type="['apk']"
            :file-size="200"
            :limit="1"
            :is-show-tip="true"
          />
          <el-input
            v-model="formData.downloadUrl"
            class="mt-5px"
            placeholder="上传后自动回填；也可直接粘贴外部 APK 直链"
          />
        </div>
      </el-form-item>
      <el-form-item label="更新说明" prop="updateLog">
        <el-input v-model="formData.updateLog" :rows="4" type="textarea" placeholder="本次更新内容，每行一条" />
      </el-form-item>
      <el-form-item label="强制更新" prop="forceUpdate">
        <el-switch v-model="formData.forceUpdate" />
        <span class="ml-10px text-gray-400 text-12px">开启后用户必须升级才能继续使用</span>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio :label="0">发布</el-radio>
          <el-radio :label="1">停用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" placeholder="选填" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :disabled="formLoading" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import {
  getAppVersionPage,
  getAppVersion,
  createAppVersion,
  updateAppVersion,
  deleteAppVersion,
  type AppVersionVO
} from '@/api/merchant/appVersion'

defineOptions({ name: 'PlatformAppVersion' })

const message = useMessage()

const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  platform: undefined as string | undefined,
  versionName: undefined as string | undefined,
  status: undefined as number | undefined
})

const getList = async () => {
  loading.value = true
  try {
    const data = await getAppVersionPage(queryParams)
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
  queryParams.platform = undefined
  queryParams.versionName = undefined
  queryParams.status = undefined
  handleQuery()
}

const fmtSize = (bytes?: number) => {
  if (!bytes || bytes <= 0) return '-'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

// ===== 弹窗 =====
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formRef = ref()
const formData = ref<AppVersionVO>({
  platform: 'android',
  versionName: '',
  versionCode: 1,
  downloadUrl: '',
  updateLog: '',
  forceUpdate: false,
  fileSize: undefined,
  status: 0,
  remark: ''
})
const formRules = {
  platform: [{ required: true, message: '请选择平台', trigger: 'change' }],
  versionName: [{ required: true, message: '请填写版本名', trigger: 'blur' }],
  versionCode: [{ required: true, message: '请填写版本号', trigger: 'blur' }],
  downloadUrl: [{ required: true, message: '请上传 APK 或填写下载地址', trigger: 'blur' }]
}

const resetForm = () => {
  formData.value = {
    platform: 'android',
    versionName: '',
    versionCode: 1,
    downloadUrl: '',
    updateLog: '',
    forceUpdate: false,
    fileSize: undefined,
    status: 0,
    remark: ''
  }
  formRef.value?.resetFields()
}

const openForm = async (id?: number) => {
  dialogVisible.value = true
  resetForm()
  if (id) {
    dialogTitle.value = '修改 App 版本'
    formLoading.value = true
    try {
      formData.value = await getAppVersion(id)
    } finally {
      formLoading.value = false
    }
  } else {
    dialogTitle.value = '发布新版本'
  }
}

const submitForm = async () => {
  await formRef.value?.validate()
  formLoading.value = true
  try {
    const data = { ...formData.value } as AppVersionVO
    if (data.id) {
      await updateAppVersion(data)
      message.success('修改成功')
    } else {
      await createAppVersion(data)
      message.success('发布成功')
    }
    dialogVisible.value = false
    await getList()
  } finally {
    formLoading.value = false
  }
}

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm()
    await deleteAppVersion(id)
    message.success('删除成功')
    await getList()
  } catch {}
}

onMounted(getList)
</script>
