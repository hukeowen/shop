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

      <el-form-item label="启用推 N 反 1">
        <el-switch v-model="form.tuijianEnabled" />
      </el-form-item>

      <template v-if="form.tuijianEnabled">
        <el-form-item label="N 值（推几个）">
          <el-input-number
            v-model="form.tuijianN"
            :min="1"
            :max="20"
            :step="1"
            @change="onNChange"
          />
          <span class="ml-2 text-gray-400 text-sm">改 N 会同步增减下方比例输入框</span>
        </el-form-item>

        <el-form-item label="N 个返佣比例(%)">
          <div class="ratios-grid">
            <div v-for="(r, i) in ratios" :key="i" class="ratio-cell">
              <span class="ratio-tag">第 {{ i + 1 }} 次</span>
              <el-input-number
                v-model="ratios[i]"
                :min="0"
                :max="100"
                :step="1"
                :precision="1"
                size="default"
                controls-position="right"
              />
              <span class="ratio-unit">%</span>
            </div>
          </div>
          <div class="ratios-sum" :class="{ warn: ratiosSum > 100 }">
            合计 {{ ratiosSum.toFixed(1) }}% / 100%
            <span v-if="ratiosSum > 100" class="warn-text">超过 100% 不能保存</span>
            <span v-else-if="ratiosSum < 100" class="hint-text">建议加总 = 100%（推满 N 次累计返足商品价）</span>
          </div>
        </el-form-item>
      </template>

      <el-divider content-position="left">星级积分池</el-divider>

      <el-form-item label="参与星级积分池">
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

// N 个比例独立维护成数字数组，提交时再 stringify 回 form.tuijianRatios
const ratios = ref<number[]>([25, 25, 25, 25])

const ratiosSum = computed(() => {
  let s = 0
  for (let i = 0; i < ratios.value.length; i++) {
    s += Number(ratios.value[i]) || 0
  }
  return s
})

const rules = {
  consumePointRatio: [{ required: true, message: '请输入倍率', trigger: 'blur' }]
}

/**
 * N 切换 → ratios 数组按 N 截断或补 0；保持已有值不丢
 */
function onNChange(n: number | undefined) {
  const target = Math.max(1, Math.min(20, Number(n) || 1))
  form.tuijianN = target
  while (ratios.value.length < target) ratios.value.push(0)
  if (ratios.value.length > target) ratios.value.length = target
}

/**
 * 把后端的 JSON 字符串安全解析成数字数组，长度对齐到 N
 */
function parseRatios(json: string, n: number): number[] {
  let arr: any[] = []
  try {
    const v = JSON.parse(json)
    if (Array.isArray(v)) arr = v
  } catch {
    arr = []
  }
  const out: number[] = []
  for (let i = 0; i < n; i++) {
    const v = Number(arr[i])
    out.push(Number.isFinite(v) ? v : 0)
  }
  return out
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
    const n = Math.max(1, Math.min(20, Number(form.tuijianN) || 4))
    form.tuijianN = n
    ratios.value = parseRatios(form.tuijianRatios || '[]', n)
    loaded.value = true
  } finally {
    loading.value = false
  }
}

const onSave = async () => {
  if (form.tuijianEnabled) {
    if (!form.tuijianN || form.tuijianN <= 0) {
      message.warning('启用推 N 反 1 时 N 必须 > 0')
      return
    }
    // v6 文档：N 个比例加总不能超过 100%（避免商家把商品价超额返出去）
    if (ratiosSum.value > 100) {
      message.warning(`N 个比例加总 ${ratiosSum.value.toFixed(1)}% > 100%，请调整`)
      return
    }
  }
  submitting.value = true
  try {
    await PromoApi.saveProductPromoConfig({
      ...form,
      spuId: spuId.value!,
      tuijianRatios: JSON.stringify(ratios.value.map((v) => Number(v) || 0))
    })
    message.success('保存成功')
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.ratios-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
  margin-bottom: 8px;
}

.ratio-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ratio-tag {
  flex: 0 0 64px;
  text-align: center;
  font-size: 12px;
  color: #909399;
  background: #f4f4f5;
  border-radius: 4px;
  padding: 4px 6px;
}

.ratio-unit {
  font-size: 12px;
  color: #909399;
}

.ratios-sum {
  margin-top: 4px;
  font-size: 13px;
  color: #606266;

  &.warn {
    color: #f56c6c;
    font-weight: 600;
  }

  .warn-text {
    margin-left: 8px;
    color: #f56c6c;
  }

  .hint-text {
    margin-left: 8px;
    color: #909399;
    font-weight: 400;
  }
}
</style>
