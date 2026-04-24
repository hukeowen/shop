<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="640px">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="100px"
    >
      <el-form-item label="套餐名称" prop="name">
        <el-input v-model="formData.name" placeholder="请输入套餐名称" maxlength="64" show-word-limit />
      </el-form-item>
      <el-form-item label="视频条数" prop="videoCount">
        <el-input-number v-model="formData.videoCount" :min="1" :max="9999" />
        <span class="ml-2 text-gray-500">条</span>
      </el-form-item>
      <el-form-item label="售价（元）" prop="priceYuan">
        <el-input-number
          v-model="formData.priceYuan"
          :min="0"
          :precision="2"
          :step="1"
          placeholder="例如 35.00"
        />
      </el-form-item>
      <el-form-item label="划线原价（元）" prop="originalPriceYuan">
        <el-input-number
          v-model="formData.originalPriceYuan"
          :min="0"
          :precision="2"
          :step="1"
          placeholder="选填，用于展示优惠"
        />
      </el-form-item>
      <el-form-item label="排序" prop="sort">
        <el-input-number v-model="formData.sort" :min="0" :max="9999" />
        <span class="ml-2 text-gray-500">数值越大越靠前</span>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio :value="0">上架</el-radio>
          <el-radio :value="1">下架</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="套餐描述" prop="description">
        <el-input
          v-model="formData.description"
          type="textarea"
          placeholder="可选，最多 255 字"
          maxlength="255"
          show-word-limit
          :rows="3"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as PackageApi from '@/api/merchant/aiVideoPackage'

defineOptions({ name: 'MerchantAiVideoPackageForm' })

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')

interface PackageForm {
  id?: number
  name: string
  description?: string
  videoCount: number
  priceYuan: number
  originalPriceYuan?: number
  sort: number
  status: number
}

const emptyForm = (): PackageForm => ({
  id: undefined,
  name: '',
  description: '',
  videoCount: 1,
  priceYuan: 0,
  originalPriceYuan: undefined,
  sort: 0,
  status: 0
})

const formData = ref<PackageForm>(emptyForm())

const formRules = reactive({
  name: [{ required: true, message: '套餐名称不能为空', trigger: 'blur' }],
  videoCount: [{ required: true, message: '视频条数不能为空', trigger: 'blur' }],
  priceYuan: [{ required: true, message: '售价不能为空', trigger: 'blur' }],
  status: [{ required: true, message: '状态不能为空', trigger: 'change' }]
})
const formRef = ref()

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = type === 'create' ? '新增套餐' : '编辑套餐'
  formType.value = type
  resetForm()
  if (id) {
    formLoading.value = true
    try {
      const raw = await PackageApi.getPackage(id)
      formData.value = {
        id: raw.id,
        name: raw.name,
        description: raw.description,
        videoCount: raw.videoCount,
        priceYuan: raw.price != null ? raw.price / 100 : 0,
        originalPriceYuan: raw.originalPrice != null ? raw.originalPrice / 100 : undefined,
        sort: raw.sort ?? 0,
        status: raw.status ?? 0
      }
    } finally {
      formLoading.value = false
    }
  }
}
defineExpose({ open })

const emit = defineEmits(['success'])

const submitForm = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  formLoading.value = true
  try {
    const payload: PackageApi.AiVideoPackageVO = {
      id: formData.value.id,
      name: formData.value.name.trim(),
      description: formData.value.description?.trim() || undefined,
      videoCount: formData.value.videoCount,
      price: Math.round(formData.value.priceYuan * 100),
      originalPrice:
        formData.value.originalPriceYuan != null
          ? Math.round(formData.value.originalPriceYuan * 100)
          : undefined,
      sort: formData.value.sort,
      status: formData.value.status
    }
    if (formType.value === 'create') {
      await PackageApi.createPackage(payload)
      message.success(t('common.createSuccess'))
    } else {
      await PackageApi.updatePackage(payload)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const resetForm = () => {
  formData.value = emptyForm()
  formRef.value?.resetFields()
}
</script>
