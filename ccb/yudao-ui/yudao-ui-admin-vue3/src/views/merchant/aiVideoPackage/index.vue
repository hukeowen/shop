<template>
  <ContentWrap>
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="72px"
    >
      <el-form-item label="套餐名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入套餐名称"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-160px">
          <el-option label="上架" :value="0" />
          <el-option label="下架" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button
          type="primary"
          plain
          @click="openForm('create')"
          v-hasPermi="['merchant:ai-video-package:create']"
        >
          <Icon icon="ep:plus" class="mr-5px" /> 新增套餐
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="ID" align="center" prop="id" width="80" />
      <el-table-column label="套餐名称" align="center" prop="name" min-width="160" />
      <el-table-column label="视频条数" align="center" prop="videoCount" width="100" />
      <el-table-column label="售价（元）" align="center" width="110">
        <template #default="scope">
          ￥{{ (scope.row.price / 100).toFixed(2) }}
        </template>
      </el-table-column>
      <el-table-column label="划线价（元）" align="center" width="120">
        <template #default="scope">
          <span v-if="scope.row.originalPrice">￥{{ (scope.row.originalPrice / 100).toFixed(2) }}</span>
          <span v-else class="text-gray-400">—</span>
        </template>
      </el-table-column>
      <el-table-column label="排序" align="center" prop="sort" width="80" />
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <el-tag v-if="scope.row.status === 0" type="success">上架</el-tag>
          <el-tag v-else type="info">下架</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="描述" align="center" prop="description" :show-overflow-tooltip="true" />
      <el-table-column
        label="创建时间"
        align="center"
        prop="createTime"
        width="180"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" align="center" width="180" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openForm('update', scope.row.id)"
            v-hasPermi="['merchant:ai-video-package:update']"
          >
            编辑
          </el-button>
          <el-button
            link
            :type="scope.row.status === 0 ? 'warning' : 'success'"
            @click="handleToggleStatus(scope.row)"
            v-hasPermi="['merchant:ai-video-package:update']"
          >
            {{ scope.row.status === 0 ? '下架' : '上架' }}
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
            v-hasPermi="['merchant:ai-video-package:delete']"
          >
            删除
          </el-button>
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

  <AiVideoPackageForm ref="formRef" @success="getList" />
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import * as PackageApi from '@/api/merchant/aiVideoPackage'
import AiVideoPackageForm from './AiVideoPackageForm.vue'

defineOptions({ name: 'MerchantAiVideoPackage' })

const message = useMessage()
const { t } = useI18n()

const loading = ref(true)
const total = ref(0)
const list = ref<PackageApi.AiVideoPackageVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: undefined as string | undefined,
  status: undefined as number | undefined
})
const queryFormRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const data = await PackageApi.getPackagePage(queryParams)
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

const formRef = ref()
const openForm = (type: string, id?: number) => {
  formRef.value.open(type, id)
}

const handleToggleStatus = async (row: PackageApi.AiVideoPackageVO) => {
  const nextStatus = row.status === 0 ? 1 : 0
  const label = nextStatus === 0 ? '上架' : '下架'
  try {
    await message.confirm(`确定${label}套餐【${row.name}】吗？`)
    await PackageApi.updatePackage({ ...row, status: nextStatus } as PackageApi.AiVideoPackageVO)
    message.success(`${label}成功`)
    await getList()
  } catch {}
}

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm()
    await PackageApi.deletePackage(id)
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

onMounted(() => {
  getList()
})
</script>
