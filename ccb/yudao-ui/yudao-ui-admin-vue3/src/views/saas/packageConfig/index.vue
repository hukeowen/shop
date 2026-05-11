<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      title="SaaS 套餐配置 — 商户续费页看到的就是这里的内容。改价或赠送 AI 视频次数后，新购订单按新规则；已购订单的服务期不受影响。"
      class="mb-15px"
    />
    <div class="mb-15px">
      <el-button type="primary" @click="openNew">
        <Icon icon="ep:plus" class="mr-5px" /> 新增套餐
      </el-button>
    </div>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="ID" prop="id" width="60" />
      <el-table-column label="档位 level" prop="level" width="100" />
      <el-table-column label="套餐名" prop="name" min-width="120" />
      <el-table-column label="价格（元）" width="110" align="right">
        <template #default="{ row }">
          ¥{{ (row.priceFen / 100).toFixed(2) }}
        </template>
      </el-table-column>
      <el-table-column label="天数" prop="durationDays" width="80" align="center" />
      <el-table-column label="赠 AI 视频" prop="aiVideoGrant" width="110" align="center" />
      <el-table-column label="包含功能" min-width="200">
        <template #default="{ row }">
          <el-tag v-for="f in parseFeatures(row.features)" :key="f" size="small" class="mr-5px">{{ f }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="排序" prop="sort" width="80" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.status === 0" type="success">上架</el-tag>
          <el-tag v-else type="info">下架</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" fixed="right" width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button
            link
            :type="row.status === 0 ? 'warning' : 'success'"
            @click="toggleStatus(row)"
          >
            {{ row.status === 0 ? '下架' : '上架' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <el-dialog v-model="dialogVisible" :title="editForm.id ? '编辑套餐' : '新增套餐'" width="600px">
    <el-form :model="editForm" label-width="120px">
      <el-form-item label="档位 level" required>
        <el-input v-model="editForm.level" :disabled="!!editForm.id" placeholder="如 BASIC / PRO（唯一）" />
      </el-form-item>
      <el-form-item label="套餐名" required>
        <el-input v-model="editForm.name" placeholder="如 基础包 / 全功能包" />
      </el-form-item>
      <el-form-item label="价格（分）" required>
        <el-input-number v-model="editForm.priceFen" :min="1" :step="100" />
        <span class="ml-10px text-gray-500">= ¥{{ (editForm.priceFen / 100).toFixed(2) }}</span>
      </el-form-item>
      <el-form-item label="天数" required>
        <el-input-number v-model="editForm.durationDays" :min="1" :max="3650" />
      </el-form-item>
      <el-form-item label="赠送 AI 视频">
        <el-input-number v-model="editForm.aiVideoGrant" :min="0" :max="999" />
      </el-form-item>
      <el-form-item label="包含功能">
        <el-checkbox-group v-model="selectedFeatures">
          <el-checkbox label="order">订单系统</el-checkbox>
          <el-checkbox label="tuijian">推 N 反 1</el-checkbox>
          <el-checkbox label="team">团队 / 极差</el-checkbox>
          <el-checkbox label="star">星级</el-checkbox>
          <el-checkbox label="pool">积分池</el-checkbox>
          <el-checkbox label="brokerage">分销返佣</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="editForm.sort" :min="0" :max="999" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="editForm.status">
          <el-radio :label="0">上架</el-radio>
          <el-radio :label="1">下架</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listPackages,
  savePackage,
  updatePackageStatus,
  type SaasPackageConfigVO,
} from '@/api/saas/packageConfig'

defineOptions({ name: 'SaasPackageConfig' })

const loading = ref(false)
const saving = ref(false)
const list = ref<SaasPackageConfigVO[]>([])
const dialogVisible = ref(false)
const selectedFeatures = ref<string[]>([])
const editForm = reactive<SaasPackageConfigVO>({
  id: undefined,
  level: '',
  name: '',
  priceFen: 0,
  durationDays: 365,
  aiVideoGrant: 0,
  features: '',
  sort: 0,
  status: 0,
})

const FEATURE_LABELS: Record<string, string> = {
  order: '订单',
  tuijian: '推N反1',
  team: '团队',
  star: '星级',
  pool: '积分池',
  brokerage: '分销',
}

function parseFeatures(s?: string): string[] {
  if (!s) return []
  try {
    const arr = JSON.parse(s) as string[]
    return arr.map(k => FEATURE_LABELS[k] || k)
  } catch {
    return []
  }
}

async function load() {
  loading.value = true
  try {
    const data: any = await listPackages()
    list.value = data || []
  } finally {
    loading.value = false
  }
}

function openNew() {
  editForm.id = undefined
  editForm.level = ''
  editForm.name = ''
  editForm.priceFen = 29800
  editForm.durationDays = 365
  editForm.aiVideoGrant = 10
  editForm.features = '[]'
  editForm.sort = 0
  editForm.status = 0
  selectedFeatures.value = ['order', 'tuijian']
  dialogVisible.value = true
}
function openEdit(row: SaasPackageConfigVO) {
  Object.assign(editForm, row)
  try {
    selectedFeatures.value = row.features ? JSON.parse(row.features) : []
  } catch {
    selectedFeatures.value = []
  }
  dialogVisible.value = true
}

watch(selectedFeatures, (v) => {
  editForm.features = JSON.stringify(v)
})

async function onSave() {
  if (!editForm.level || !editForm.name || editForm.priceFen <= 0) {
    ElMessage.warning('请填齐 level / 名称 / 价格')
    return
  }
  saving.value = true
  try {
    editForm.features = JSON.stringify(selectedFeatures.value)
    await savePackage(editForm)
    ElMessage.success('已保存')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: SaasPackageConfigVO) {
  const next = row.status === 0 ? 1 : 0
  await updatePackageStatus(row.id!, next)
  ElMessage.success(next === 0 ? '已上架' : '已下架')
  load()
}

onMounted(() => load())
</script>
