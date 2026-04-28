<template>
  <ContentWrap>
    <div class="mb-3 text-sm text-gray-500">
      管理本租户的营销引擎参数：星级、团队极差、推广积分换算 / 提现门槛、星级积分池。
      JSON 字段请按提示格式填写，长度需与「平台星级数量」一致。
    </div>
  </ContentWrap>

  <ContentWrap>
    <el-form
      ref="formRef"
      v-loading="loading"
      :model="form"
      :rules="rules"
      label-width="180px"
      class="max-w-3xl"
    >
      <el-divider content-position="left">基础</el-divider>

      <el-form-item label="平台星级数量" prop="starLevelCount">
        <el-input-number v-model="form.starLevelCount" :min="1" :max="10" />
      </el-form-item>

      <el-form-item label="每星级团队极差比例(%)" prop="commissionRates">
        <el-input
          v-model="form.commissionRates"
          type="textarea"
          :rows="2"
          placeholder='JSON 数组，长度=星级数。例：[1,2,3,4,5]'
        />
      </el-form-item>

      <el-form-item label="升星门槛" prop="starUpgradeRules">
        <el-input
          v-model="form.starUpgradeRules"
          type="textarea"
          :rows="3"
          placeholder='JSON 数组，长度=星级数。例：[{"directCount":2,"teamSales":3},{"directCount":3,"teamSales":9}]'
        />
      </el-form-item>

      <el-form-item label="推广积分→消费积分比例" prop="pointConversionRatio">
        <el-input-number v-model="form.pointConversionRatio" :min="0.01" :step="0.1" :precision="2" />
        <span class="ml-2 text-gray-400 text-sm">1 推广积分 → ratio 消费积分</span>
      </el-form-item>

      <el-form-item label="推广积分提现门槛(分)" prop="withdrawThreshold">
        <el-input-number v-model="form.withdrawThreshold" :min="0" :step="100" />
        <span class="ml-2 text-gray-400 text-sm">100 分 = 1 元</span>
      </el-form-item>

      <el-divider content-position="left">星级积分池</el-divider>

      <el-form-item label="启用积分池" prop="poolEnabled">
        <el-switch v-model="form.poolEnabled" />
      </el-form-item>

      <el-form-item label="入池比例(%)" prop="poolRatio">
        <el-input-number v-model="form.poolRatio" :min="0" :max="100" :step="0.5" :precision="2" />
      </el-form-item>

      <el-form-item label="可参与瓜分的星级" prop="poolEligibleStars">
        <el-input
          v-model="form.poolEligibleStars"
          type="textarea"
          :rows="2"
          placeholder='JSON 数组。例：[3,4,5] 表示仅 3-5 星可瓜分'
        />
      </el-form-item>

      <el-form-item label="池子分配方式" prop="poolDistributeMode">
        <el-radio-group v-model="form.poolDistributeMode">
          <el-radio label="ALL">全员均分</el-radio>
          <el-radio label="STAR">按星级桶均分</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="结算 cron 表达式" prop="poolSettleCron">
        <el-input v-model="form.poolSettleCron" placeholder="如：0 0 0 1 * ?（每月 1 号 0 点）" />
      </el-form-item>

      <el-form-item label="cron 自动结算模式" prop="poolSettleMode">
        <el-radio-group v-model="form.poolSettleMode">
          <el-radio label="FULL">均分</el-radio>
          <el-radio label="LOTTERY">抽奖</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="抽奖中奖占比(%)" prop="poolLotteryRatio">
        <el-input-number v-model="form.poolLotteryRatio" :min="0" :max="100" :step="1" :precision="2" />
        <span class="ml-2 text-gray-400 text-sm">仅 LOTTERY 模式生效</span>
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

defineOptions({ name: 'MerchantPromoConfig' })

const message = useMessage()

const loading = ref(false)
const submitting = ref(false)
const formRef = ref()

const form = reactive<PromoApi.PromoConfigVO>({
  starLevelCount: 5,
  commissionRates: '[1,2,3,4,5]',
  starUpgradeRules: '[]',
  pointConversionRatio: 1,
  withdrawThreshold: 10000,
  poolEnabled: false,
  poolRatio: 5,
  poolEligibleStars: '[1,2,3,4,5]',
  poolDistributeMode: 'ALL',
  poolSettleCron: '0 0 0 1 * ?',
  poolLotteryRatio: 5,
  poolSettleMode: 'FULL'
})

const rules = {
  starLevelCount: [{ required: true, message: '请输入星级数量', trigger: 'blur' }],
  commissionRates: [{ required: true, validator: jsonArrayValidator, trigger: 'blur' }],
  starUpgradeRules: [{ required: true, validator: jsonArrayValidator, trigger: 'blur' }],
  pointConversionRatio: [{ required: true, message: '请输入比例', trigger: 'blur' }],
  withdrawThreshold: [{ required: true, message: '请输入门槛', trigger: 'blur' }],
  poolEnabled: [{ required: true }],
  poolRatio: [{ required: true }],
  poolEligibleStars: [{ required: true, validator: jsonArrayValidator, trigger: 'blur' }],
  poolDistributeMode: [{ required: true }],
  poolSettleCron: [{ required: true, message: '请输入 cron', trigger: 'blur' }],
  poolLotteryRatio: [{ required: true }],
  poolSettleMode: [{ required: true }]
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
  loading.value = true
  try {
    const data = await PromoApi.getPromoConfig()
    if (data) Object.assign(form, data)
  } finally {
    loading.value = false
  }
}

const onSave = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await PromoApi.savePromoConfig({ ...form })
    message.success('保存成功')
  } finally {
    submitting.value = false
  }
}

onMounted(loadConfig)
</script>
