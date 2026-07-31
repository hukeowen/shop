<!-- 平台运营 - 店铺管理（上架/下架 + 管理员账号 + 入驻资料 + 通联费率 + 自动上架开关） -->
<template>
  <ContentWrap>
    <el-form :inline="true" :model="queryParams" class="-mb-15px" label-width="68px">
      <el-form-item label="店铺名称" prop="shopName">
        <el-input v-model="queryParams.shopName" class="!w-200px" clearable placeholder="店铺名称" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon class="mr-5px" icon="ep:search" />搜索</el-button>
        <el-button @click="resetQuery"><Icon class="mr-5px" icon="ep:refresh" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="店铺" min-width="150" prop="shopName" />
      <el-table-column align="center" label="上架状态" min-width="130">
        <template #default="{ row }">
          <el-switch
            v-model="row.status"
            :active-value="1"
            :inactive-value="0"
            active-text="上架"
            inactive-text="下架"
            inline-prompt
            @change="(v) => saveStatus(row, v as number)"
          />
          <div class="text-12px text-gray-400">{{ row.status === 1 ? '用户端展示' : '用户端隐藏' }}</div>
        </template>
      </el-table-column>
      <el-table-column align="center" label="管理员账号" min-width="140">
        <template #default="{ row }">
          <div v-if="row.adminMobile">
            {{ row.adminMobile }}
            <div v-if="row.adminName" class="text-12px text-gray-400">{{ row.adminName }}</div>
          </div>
          <span v-else class="text-gray-400">—</span>
        </template>
      </el-table-column>
      <el-table-column align="center" label="客服电话" prop="mobile" min-width="120" />
      <el-table-column label="地址" prop="address" min-width="180" show-overflow-tooltip />
      <el-table-column align="center" label="在线支付" min-width="120">
        <template #default="{ row }">
          <el-tag v-if="row.onlinePayEnabled" type="success" size="small">已开通</el-tag>
          <el-tag v-else type="info" size="small">未开通</el-tag>
          <div v-if="row.tlMchId" class="text-12px text-gray-400">{{ row.tlMchId }}</div>
        </template>
      </el-table-column>
      <el-table-column align="center" label="通联费率" min-width="150">
        <template #default="{ row }">
          <el-input
            v-model="row.tlFeeRate"
            size="small"
            class="!w-90px"
            placeholder="如 0.6"
            @blur="saveRate(row)"
          >
            <template #append>%</template>
          </el-input>
        </template>
      </el-table-column>
      <el-table-column align="center" label="自动上架(免审核)" min-width="130">
        <template #default="{ row }">
          <el-switch
            v-model="row.autoApprove"
            :active-value="1"
            :inactive-value="0"
            active-text="自动"
            inactive-text="需审核"
            inline-prompt
            @change="(v) => saveAutoApprove(row, v as number)"
          />
        </template>
      </el-table-column>
      <el-table-column :formatter="dateFormatter" align="center" label="入驻时间" prop="createTime" width="170" />
      <el-table-column align="center" label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openKyc(row)">查看资料</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination v-model:limit="queryParams.pageSize" v-model:page="queryParams.pageNo" :total="total" @pagination="getList" />
  </ContentWrap>

  <!-- 入驻/进件资料 -->
  <el-dialog v-model="kycVisible" :title="`入驻资料 - ${kycShopName}`" width="860px">
    <div v-if="kycLoading" class="py-40px text-center text-gray-400">资料加载中…</div>
    <template v-else-if="kyc">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="商户全称">{{ kyc.merchantFullName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="统一社会信用代码">{{ kyc.creditCode || '—' }}</el-descriptions-item>
        <el-descriptions-item label="法人姓名">{{ kyc.legalName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="法人身份证号">{{ kyc.legalIdNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="经营地址" :span="2">{{ kyc.busAddress || '—' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ kyc.contactPerson || '—' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ kyc.contactPhone || '—' }}</el-descriptions-item>
        <el-descriptions-item label="结算户名">{{ kyc.settleAcctName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="结算账号">{{ kyc.settleAcctNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="结算银行">{{ kyc.settleBankName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="进件状态">
          <el-tag v-if="kyc.payApplyStatus === 2" type="success" size="small">已开通</el-tag>
          <el-tag v-else-if="kyc.payApplyStatus === 1" type="warning" size="small">审核中</el-tag>
          <el-tag v-else-if="kyc.payApplyStatus === 3" type="danger" size="small">已驳回</el-tag>
          <span v-else class="text-gray-400">未申请</span>
          <span v-if="kyc.payApplyRejectReason" class="ml-5px text-12px text-red-500">
            {{ kyc.payApplyRejectReason }}
          </span>
        </el-descriptions-item>
      </el-descriptions>

      <div class="mt-16px mb-8px text-14px font-bold">证件照片</div>
      <div v-if="!picList.length" class="py-20px text-center text-gray-400">该店暂未上传任何证件资料</div>
      <div v-else class="pic-grid">
        <div v-for="p in picList" :key="p.label" class="pic-item">
          <div class="pic-label">{{ p.label }}</div>
          <el-image v-if="p.url" :src="p.url" :preview-src-list="previewList" fit="cover" class="pic-img" />
          <div v-else class="pic-empty">暂不可预览</div>
        </div>
      </div>

      <template v-if="kyc.apply">
        <div class="mt-16px mb-8px text-14px font-bold">入驻申请（merchant_apply）</div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="申请店铺名">{{ kyc.apply.shopName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="联系手机">{{ kyc.apply.mobile || '—' }}</el-descriptions-item>
          <el-descriptions-item label="地址" :span="2">{{ kyc.apply.address || '—' }}</el-descriptions-item>
          <el-descriptions-item label="审核状态">
            <el-tag v-if="kyc.apply.status === 1" type="success" size="small">通过</el-tag>
            <el-tag v-else-if="kyc.apply.status === 2" type="danger" size="small">驳回</el-tag>
            <el-tag v-else type="warning" size="small">待审核</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="驳回原因">{{ kyc.apply.rejectReason || '—' }}</el-descriptions-item>
        </el-descriptions>
      </template>

      <div class="mt-12px text-12px text-gray-400 text-center">证件图片为 1 小时临时链接，过期请重新打开本弹窗</div>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import * as PlatformApi from '@/api/merchant/platform'

defineOptions({ name: 'PlatformShop' })

const message = useMessage()
const loading = ref(false)
const total = ref(0)
const list = ref<any[]>([])
const queryParams = ref({ pageNo: 1, pageSize: 10, shopName: '' })

const getList = async () => {
  loading.value = true
  try {
    const data = await PlatformApi.getPlatformShopPage(queryParams.value)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const handleQuery = () => {
  queryParams.value.pageNo = 1
  getList()
}
const resetQuery = () => {
  queryParams.value = { pageNo: 1, pageSize: 10, shopName: '' }
  getList()
}
const saveRate = async (row: any) => {
  try {
    await PlatformApi.updateShopRate(row.id, row.tlFeeRate)
    message.success('费率已保存')
  } catch {}
}
const saveAutoApprove = async (row: any, v: number) => {
  try {
    await PlatformApi.updateShopAutoApprove(row.id, v)
    message.success(v === 1 ? '已设为自动上架' : '已设为需审核')
  } catch {
    row.autoApprove = v === 1 ? 0 : 1
  }
}

// 上架/下架：下架会让该店在用户端（邀三惠）完全不展示，故二次确认
const saveStatus = async (row: any, v: number) => {
  const revert = () => {
    row.status = v === 1 ? 0 : 1
  }
  if (v === 0) {
    try {
      await message.confirm(`确认下架「${row.shopName}」？下架后用户端首页/搜索/分类都不再展示该店。`)
    } catch {
      revert()
      return
    }
  }
  try {
    await PlatformApi.updateShopStatus(row.id, v)
    message.success(v === 1 ? '已上架，用户端可见' : '已下架，用户端不再展示')
  } catch {
    revert()
  }
}

// ===== 入驻资料 =====
const kycVisible = ref(false)
const kycLoading = ref(false)
const kyc = ref<any>(null)
const kycShopName = ref('')

const PIC_LABELS: Array<[string, string]> = [
  ['businessLicense', '营业执照'],
  ['idCardFront', '法人身份证 · 正面'],
  ['idCardBack', '法人身份证 · 背面'],
  ['storePic', '门店照片（门头）'],
  ['indoorPic', '店内照片']
]
const picList = computed(() => {
  const pics = kyc.value?.pics || {}
  return PIC_LABELS.filter(([k]) => k in pics).map(([k, label]) => ({ label, url: pics[k] }))
})
const previewList = computed(() => picList.value.map((p) => p.url).filter((u): u is string => !!u))

const openKyc = async (row: any) => {
  kycShopName.value = row.shopName
  kyc.value = null
  kycVisible.value = true
  kycLoading.value = true
  try {
    kyc.value = await PlatformApi.getShopKyc(row.id)
  } catch {
    kycVisible.value = false
  } finally {
    kycLoading.value = false
  }
}

onMounted(getList)
</script>

<style lang="scss" scoped>
.pic-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.pic-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.pic-label {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}
.pic-img {
  width: 230px;
  height: 155px;
  border-radius: 6px;
  cursor: zoom-in;
  background: #f5f7fa;
}
.pic-empty {
  width: 230px;
  height: 155px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
}
</style>
