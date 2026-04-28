<template>
  <ContentWrap>
    <div class="mb-3 text-sm text-gray-500">
      为单个商品配置营销规则：消费积分倍率、推 N 反 1、是否参与积分池。
      请先填入商品 SPU ID（来自「商品管理」列表）后点「拉取」。
    </div>

    <el-form :inline="true" label-width="100px">
      <el-form-item label="商品 SPU ID">
        <el-input-number v-model="spuId" :min="1" :step="1" placeholder="商品 SPU ID" class="!w-200px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="loadConfig">拉取</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap v-if="loaded">
    <el-form
      ref="formRef"
      v-loading="loading"
      :model="form"
      :rules="rules"
      label-width="180px"
      class="max-w-3xl"
    >
      <el-divider content-position="left">消费积分</el-divider>

      <el-form-item label="每元返多少消费积分" prop="consumePointRatio">
        <el-input-number v-model="form.consumePointRatio" :min="0" :step="0.1" :precision="2" />
        <span class="ml-2 text-gray-400 text-sm">设为 0 = 关闭消费积分</span>
      </el-form-item>

      <el-divider content-position="left">推 N 反 1（直推 / 队列 / 自然推）</el-divider>

      <el-form-item label="启用推 N 反 1" prop="tuijianEnabled">
        <el-switch v-model="form.tuijianEnabled" />
      </el-form-item>

      <el-form-item label="N 值（推几个）" prop="tuijianN">
        <el-input-number v-model="form.tuijianN" :min="0" :max="20" :step="1" />
      </el-form-item>

      <el-form-item label="N 个返佣比例(%)" prop="tuijianRatios">
        <el-input
          v-model="form.tuijianRatios"
          type="textarea"
          :rows="2"
          placeholder='JSON 数组，长度 = N。例：[25,25,25,25]，加总建议 = 100'
        />
      </el-form-item>

      <el-divider content-position="left">星级积分池</el-divider>

      <el-form-item label="参与星级积分池" prop="poolEnabled">
        <el-switch v-model="form.poolEnabled" />
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="onSave">保存</el-button>
        <el-button @click="loadConfig">重置为已保存值</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
</template>

<script lang="ts" setup>
import * as PromoApi from '@/api/merchant/promo'

defineOptions({ name: 'MerchantProductPromoConfig' })

const message = useMessage()

const spuId = ref<number | undefined>(undefined)
const loading = ref(false)
const loaded = ref(false)
const submitting = ref(false)
const formRef = ref()

const form = reactive<PromoApi.ProductPromoConfigVO>({
  spuId: 0,
  consumePointRatio: 1,
  tuijianEnabled: false,
  tuijianN: 4,
  tuijianRatios: '[25,25,25,25]',
  poolEnabled: false
})

const rules = {
  consumePointRatio: [{ required: true, message: '请输入倍率', trigger: 'blur' }],
  tuijianEnabled: [{ required: true }],
  tuijianN: [{ required: true, message: '请输入 N', trigger: 'blur' }],
  tuijianRatios: [{ required: true, validator: jsonArrayValidator, trigger: 'blur' }],
  poolEnabled: [{ required: true }]
}

function jsonArrayValidator(_rule: any, value: string, cb: any) {
  try {
    const v = JSON.parse(value)
    if (!Array.isArray(v)) {
      cb(new Error('需为 JSON 数组'))
      return
    }
    cb()
  } catch {
    cb(new Error('JSON 解析失败'))
  }
}

const loadConfig = async () => {
  if (!spuId.value || spuId.value <= 0) {
    message.warning('请输入有效的 SPU ID')
    return
  }
  loading.value = true
  try {
    const data = await PromoApi.getProductPromoConfig(spuId.value)
    if (data) Object.assign(form, data)
    form.spuId = spuId.value
    loaded.value = true
  } finally {
    loading.value = false
  }
}

const onSave = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await PromoApi.saveProductPromoConfig({ ...form, spuId: spuId.value! })
    message.success('保存成功')
  } finally {
    submitting.value = false
  }
}
</script>
