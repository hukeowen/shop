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

      <el-divider content-position="left">v8 星级递减（按商品独立）</el-divider>

      <el-form-item label="星级数量（0=不启用）">
        <el-input-number v-model="form.starCount" :min="0" :max="10" :step="1" @change="onStarCountChange" />
      </el-form-item>

      <el-form-item label="v8 直推/间推奖比例(%)">
        <el-input-number v-model="form.directRate" :min="0" :max="100" :step="0.1" :precision="2" />
      </el-form-item>

      <el-divider content-position="left">星级积分池</el-divider>

      <el-form-item label="入池比例(%)">
        <el-input-number v-model="form.poolRatio" :min="0" :max="100" :step="0.1" :precision="2" />
        <span class="ml-2 text-gray-400 text-sm">每订单实付 × 此比例累入该 SPU 池</span>
      </el-form-item>

      <el-form-item label="参与星级积分池（v6 兼容）">
        <el-switch v-model="form.poolEnabled" />
      </el-form-item>

      <!-- v8 奖池分配规则 -->
      <el-form-item v-if="form.starCount && form.starCount > 0" label="奖池分配规则">
        <div class="dist-table">
          <div v-for="(d, i) in poolDistList" :key="i" class="dist-row">
            <span class="star-tag">{{ d.star }} 星</span>
            <el-input-number
              v-model="d.ratio" :min="0" :max="100" :step="0.1" :precision="2"
              size="default" controls-position="right" />
            <span class="unit">% 占池</span>
            <el-radio-group v-model="d.mode" size="default" class="mode-radio">
              <el-radio-button value="EQUAL">均分</el-radio-button>
              <el-radio-button value="LOTTERY">抽奖</el-radio-button>
            </el-radio-group>
            <el-input-number
              v-if="d.mode === 'LOTTERY'"
              v-model="d.winners" :min="1" :step="1" size="default"
              placeholder="名额" class="!w-100px" />
            <span v-if="d.mode === 'LOTTERY'" class="unit">名额</span>
          </div>
          <div class="dist-sum" :class="{ warn: distSum !== 100 }">
            合计 {{ distSum.toFixed(1) }}% / 100%
            <span v-if="distSum !== 100" class="warn-text">必须严格 = 100%</span>
          </div>
        </div>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="onSave">保存</el-button>
        <el-button @click="loadConfig">重置为已保存值</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- v8 奖池运营 -->
  <ContentWrap v-if="loaded && (form.poolRatio || 0) > 0">
    <el-divider content-position="left">奖池运营</el-divider>
    <el-descriptions :column="3" border>
      <el-descriptions-item label="池余额（分）">
        <span class="text-orange-500 font-bold">¥{{ (poolBalance / 100).toFixed(2) }}</span>
        <span class="ml-2 text-gray-400 text-sm">{{ poolBalance }} 分</span>
      </el-descriptions-item>
      <el-descriptions-item label="累计入池">¥{{ (poolTotalIn / 100).toFixed(2) }}</el-descriptions-item>
      <el-descriptions-item label="累计已发">¥{{ (poolTotalOut / 100).toFixed(2) }}</el-descriptions-item>
    </el-descriptions>
    <div class="mt-3">
      <el-button type="primary" :disabled="poolBalance <= 0" :loading="settling" @click="onSettle">
        立即结算
      </el-button>
      <el-button @click="loadRecords">刷新结算记录</el-button>
    </div>

    <el-divider content-position="left">历次结算</el-divider>
    <el-table :data="settleRecords" v-loading="recordsLoading" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="结算前余额" align="right" width="120">
        <template #default="{ row }">¥{{ (row.poolBalanceBefore / 100).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="分配总额" align="right" width="120">
        <template #default="{ row }">
          <span class="text-orange-500 font-bold">¥{{ (row.totalDistributed / 100).toFixed(2) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="残值留池" align="right" width="120">
        <template #default="{ row }">¥{{ (row.poolBalanceAfter / 100).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="operatorName" label="操作人" width="120" />
      <el-table-column prop="createTime" label="时间" width="170" />
      <el-table-column label="明细" align="center" width="100">
        <template #default="{ row }">
          <el-button type="primary" link @click="openPayouts(row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="payoutDialogVisible" :title="`结算 #${currentSettleId} 中奖明细`" width="640px">
      <el-table :data="currentPayouts" v-loading="payoutsLoading" stripe>
        <el-table-column prop="userId" label="用户 ID" width="120" />
        <el-table-column prop="star" label="星级" width="80" align="center" />
        <el-table-column label="方式" width="100" align="center">
          <template #default="{ row }">{{ row.mode === 'LOTTERY' ? '抽中' : '均分' }}</template>
        </el-table-column>
        <el-table-column label="金额" align="right">
          <template #default="{ row }">
            <span class="text-orange-500 font-bold">+¥{{ (row.amount / 100).toFixed(2) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
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
  poolEnabled: false,
  directRate: 0,
  starCount: 0,
  starRatios: '[]',
  starUpgradeRules: '[]',
  poolRatio: 0,
  poolDistRules: ''
})

// N 个比例独立维护成数字数组，提交时再 stringify 回 form.tuijianRatios
const ratios = ref<number[]>([25, 25, 25, 25])

// v8 奖池分配规则
interface PoolDistRow {
  star: number
  ratio: number
  mode: 'EQUAL' | 'LOTTERY'
  winners: number
}
const poolDistList = ref<PoolDistRow[]>([])

const distSum = computed(() => {
  let s = 0
  for (const d of poolDistList.value) s += Number(d.ratio) || 0
  return s
})

function syncPoolDistRows() {
  const target = Math.max(0, Math.min(10, Number(form.starCount) || 0))
  const existing = new Map(poolDistList.value.map((d) => [d.star, d]))
  const next: PoolDistRow[] = []
  for (let s = target; s >= 1; s--) {
    const e = existing.get(s)
    next.push(e || { star: s, ratio: 0, mode: 'EQUAL', winners: 1 })
  }
  poolDistList.value = next
}

function onStarCountChange() {
  syncPoolDistRows()
}

// v8 奖池运营
const poolBalance = ref(0)
const poolTotalIn = ref(0)
const poolTotalOut = ref(0)
const settling = ref(false)
const settleRecords = ref<PromoApi.SpuPoolSettleRecordVO[]>([])
const recordsLoading = ref(false)
const payoutDialogVisible = ref(false)
const currentSettleId = ref(0)
const currentPayouts = ref<PromoApi.SpuPoolPayoutItemVO[]>([])
const payoutsLoading = ref(false)

async function loadPoolBalance() {
  if (!spuId.value) return
  try {
    const r = (await PromoApi.getSpuPoolBalance(spuId.value)) as PromoApi.SpuPoolBalanceVO
    if (r) {
      poolBalance.value = r.poolBalance || 0
      poolTotalIn.value = r.totalIn || 0
      poolTotalOut.value = r.totalOut || 0
    }
  } catch {}
}

async function loadRecords() {
  if (!spuId.value) return
  recordsLoading.value = true
  try {
    const r = await PromoApi.listSpuPoolSettleRecords(spuId.value, 1, 20)
    settleRecords.value = (r as any)?.list || []
  } finally {
    recordsLoading.value = false
  }
}

async function onSettle() {
  await message.confirm(
    `将按"奖池分配规则"把当前 ¥${(poolBalance.value / 100).toFixed(2)} 全部分发到对应星级用户的推广积分。结算后池清零，无法撤销。`,
    '确认结算'
  )
  settling.value = true
  try {
    const rec = (await PromoApi.settleSpuPool(spuId.value!)) as PromoApi.SpuPoolSettleRecordVO
    message.success(`已分发 ¥${((rec?.totalDistributed || 0) / 100).toFixed(2)}`)
    await loadPoolBalance()
    await loadRecords()
  } finally {
    settling.value = false
  }
}

async function openPayouts(row: PromoApi.SpuPoolSettleRecordVO) {
  currentSettleId.value = row.id
  payoutDialogVisible.value = true
  payoutsLoading.value = true
  try {
    currentPayouts.value = ((await PromoApi.listSpuPoolPayouts(row.id)) as any) || []
  } finally {
    payoutsLoading.value = false
  }
}

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

    // v8 解析 poolDistRules
    syncPoolDistRows()
    if (form.poolDistRules) {
      try {
        const arr = JSON.parse(form.poolDistRules)
        if (Array.isArray(arr) && arr.length > 0) {
          const map = new Map<number, any>(arr.map((r: any) => [Number(r.star), r]))
          const next: PoolDistRow[] = []
          const target = Number(form.starCount) || 0
          for (let s = target; s >= 1; s--) {
            const r = map.get(s)
            next.push(
              r
                ? {
                    star: s,
                    ratio: Number(r.ratio) || 0,
                    mode: (r.mode === 'LOTTERY' ? 'LOTTERY' : 'EQUAL') as 'EQUAL' | 'LOTTERY',
                    winners: Number(r.winners) || 1
                  }
                : { star: s, ratio: 0, mode: 'EQUAL', winners: 1 }
            )
          }
          poolDistList.value = next
        }
      } catch {}
    }

    loaded.value = true
    await loadPoolBalance()
    await loadRecords()
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
    if (ratiosSum.value > 100) {
      message.warning(`N 个比例加总 ${ratiosSum.value.toFixed(1)}% > 100%，请调整`)
      return
    }
  }

  // v8 奖池分配规则校验：入池比例 > 0 且 starCount > 0 时才校验
  let poolDistJson = ''
  if ((Number(form.poolRatio) || 0) > 0 && (Number(form.starCount) || 0) > 0) {
    const filled = poolDistList.value
      .filter((d) => (Number(d.ratio) || 0) > 0)
      .map((d) => {
        const o: any = { star: d.star, ratio: Number(d.ratio) || 0, mode: d.mode }
        if (d.mode === 'LOTTERY') o.winners = Number(d.winners) || 1
        return o
      })
    if (filled.length > 0) {
      const sum = filled.reduce((s, x) => s + x.ratio, 0)
      if (Math.abs(sum - 100) > 0.001) {
        message.warning(`奖池分配加总 ${sum.toFixed(1)}% ≠ 100%，请调整`)
        return
      }
      for (const x of filled) {
        if (x.mode === 'LOTTERY' && (!x.winners || x.winners < 1)) {
          message.warning(`${x.star} 星抽奖名额必须 ≥ 1`)
          return
        }
      }
      poolDistJson = JSON.stringify(filled)
    }
  }

  submitting.value = true
  try {
    await PromoApi.saveProductPromoConfig({
      ...form,
      spuId: spuId.value!,
      tuijianRatios: JSON.stringify(ratios.value.map((v) => Number(v) || 0)),
      poolDistRules: poolDistJson
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

.dist-table {
  display: flex;
  flex-direction: column;
  gap: 10px;

  .dist-row {
    display: flex;
    align-items: center;
    gap: 12px;

    .star-tag {
      flex: 0 0 56px;
      text-align: center;
      font-size: 13px;
      color: #fff;
      background: linear-gradient(135deg, #ffd6b8, #ff6b35);
      border-radius: 4px;
      padding: 6px 8px;
      font-weight: 600;
    }

    .unit {
      font-size: 12px;
      color: #909399;
    }

    .mode-radio {
      margin-left: 8px;
    }
  }

  .dist-sum {
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
  }
}
</style>
