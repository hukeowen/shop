<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      title="独立直清模式：每个商户在通联自己开户，拥有独立的 cusId + RSA 密钥对。私钥不会在列表 / 详情接口返回明文，只显示「已配置 / 未配置」。"
      class="mb-15px"
    />
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="72px">
      <el-form-item label="店铺名" prop="shopName">
        <el-input v-model="queryParams.shopName" placeholder="模糊搜索" clearable class="!w-200px" />
      </el-form-item>
      <el-form-item label="启用状态" prop="enabled">
        <el-select v-model="queryParams.enabled" placeholder="全部" clearable class="!w-140px">
          <el-option label="已启用" :value="true" />
          <el-option label="未启用" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="编号" align="center" prop="id" width="80" />
      <el-table-column label="店铺名" align="center" prop="shopName" min-width="140" />
      <el-table-column label="租户 ID" align="center" prop="tenantId" width="100" />
      <el-table-column label="启用" align="center" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.tlEnabled" type="success">启用</el-tag>
          <el-tag v-else type="info">未启用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="cusId" align="center" prop="tlMchId" width="180">
        <template #default="{ row }">
          <span v-if="row.tlMchId">{{ row.tlMchId }}</span>
          <span v-else class="text-gray-400">—</span>
        </template>
      </el-table-column>
      <el-table-column label="appId" align="center" prop="tlAppId" width="180">
        <template #default="{ row }">
          <span v-if="row.tlAppId">{{ row.tlAppId }}</span>
          <span v-else class="text-gray-400">—</span>
        </template>
      </el-table-column>
      <el-table-column label="签名" align="center" prop="tlSignType" width="80" />
      <el-table-column label="商户私钥" align="center" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.privateKeyConfigured" type="success" size="small">已配置</el-tag>
          <el-tag v-else type="danger" size="small">未配置</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="通联公钥" align="center" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.publicKeyConfigured" type="success" size="small">已配置</el-tag>
          <el-tag v-else type="danger" size="small">未配置</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" fixed="right" width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑配置</el-button>
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

  <!-- 编辑抽屉 -->
  <el-drawer v-model="editVisible" :title="`通联配置 - ${editForm.shopName || ''}`" size="640px">
    <el-form :model="editForm" label-width="120px">
      <el-form-item label="启用通联">
        <el-switch v-model="editForm.tlEnabled" />
        <span class="ml-10px text-gray-500 text-12px">关闭后该商户不支持线上支付</span>
      </el-form-item>
      <el-form-item label="cusId（商户号）">
        <el-input v-model="editForm.tlMchId" placeholder="通联分配的商户号" maxlength="64" show-word-limit />
      </el-form-item>
      <el-form-item label="appId">
        <el-input v-model="editForm.tlAppId" placeholder="通联 appId（部分接口必填）" maxlength="64" />
      </el-form-item>
      <el-form-item label="签名算法">
        <el-radio-group v-model="editForm.tlSignType">
          <el-radio label="RSA">RSA</el-radio>
          <el-radio label="RSA2">RSA2</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="异步回调地址">
        <el-input
          v-model="editForm.tlNotifyUrl"
          placeholder="可留空，留空走全局默认"
          maxlength="512"
        />
      </el-form-item>
      <el-form-item label="商户 RSA 私钥">
        <template #label>
          <span>
            商户 RSA 私钥
            <el-tag v-if="form_meta.privateKeyConfigured" type="success" size="small" class="ml-5px">已配置</el-tag>
            <el-tag v-else type="danger" size="small" class="ml-5px">未配置</el-tag>
          </span>
        </template>
        <el-input
          v-model="editForm.tlRsaPrivateKey"
          type="textarea"
          :rows="6"
          placeholder="留空 = 不变；粘贴新私钥 PEM = 覆盖；填 __CLEAR__ = 清空"
          show-word-limit
          :maxlength="4096"
        />
      </el-form-item>
      <el-form-item label="通联 RSA 公钥">
        <template #label>
          <span>
            通联 RSA 公钥
            <el-tag v-if="form_meta.publicKeyConfigured" type="success" size="small" class="ml-5px">已配置</el-tag>
            <el-tag v-else type="danger" size="small" class="ml-5px">未配置</el-tag>
          </span>
        </template>
        <el-input
          v-model="editForm.tlRsaPublicKey"
          type="textarea"
          :rows="5"
          placeholder="留空 = 不变；粘贴新公钥 PEM = 覆盖；填 __CLEAR__ = 清空"
          show-word-limit
          :maxlength="4096"
        />
      </el-form-item>

      <el-form-item label="商户 SM2 私钥">
        <template #label>
          <span>
            商户 SM2 私钥
            <el-tag v-if="form_meta.sm2PrivateKeyConfigured" type="success" size="small" class="ml-5px">已配置</el-tag>
            <el-tag v-else type="info" size="small" class="ml-5px">未配置</el-tag>
          </span>
        </template>
        <el-input
          v-model="editForm.tlSm2PrivateKey"
          type="textarea"
          :rows="5"
          placeholder="国密签名场景填；留空 = 不变；__CLEAR__ = 清空"
          show-word-limit
          :maxlength="4096"
        />
      </el-form-item>

      <el-form-item label="通联 SM2 公钥">
        <template #label>
          <span>
            通联 SM2 公钥
            <el-tag v-if="form_meta.sm2PublicKeyConfigured" type="success" size="small" class="ml-5px">已配置</el-tag>
            <el-tag v-else type="info" size="small" class="ml-5px">未配置</el-tag>
          </span>
        </template>
        <el-input
          v-model="editForm.tlSm2PublicKey"
          type="textarea"
          :rows="4"
          placeholder="国密验回调签名；留空 = 不变；__CLEAR__ = 清空"
          show-word-limit
          :maxlength="4096"
        />
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
        <el-button @click="editVisible = false">取消</el-button>
      </el-form-item>
    </el-form>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getTlpayConfigPage,
  saveTlpayConfig,
  type TlpayConfigVO,
  type TlpayConfigSaveReqVO,
} from '@/api/merchant/tlpay'

defineOptions({ name: 'MerchantTlpayConfig' })

const loading = ref(false)
const saving = ref(false)
const list = ref<TlpayConfigVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  shopName: '',
  enabled: undefined as boolean | undefined,
})

async function getList() {
  loading.value = true
  try {
    const params: any = { pageNo: queryParams.pageNo, pageSize: queryParams.pageSize }
    if (queryParams.shopName) params.shopName = queryParams.shopName
    if (queryParams.enabled !== undefined && queryParams.enabled !== null) {
      params.enabled = queryParams.enabled
    }
    const data: any = await getTlpayConfigPage(params)
    list.value = data?.list || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}
function handleQuery() {
  queryParams.pageNo = 1
  getList()
}
function resetQuery() {
  queryParams.shopName = ''
  queryParams.enabled = undefined
  handleQuery()
}

// 编辑
const editVisible = ref(false)
const editForm = reactive<TlpayConfigSaveReqVO & { shopName?: string }>({
  id: 0,
  shopName: '',
  tlEnabled: false,
  tlMchId: '',
  tlAppId: '',
  tlSignType: 'RSA',
  tlNotifyUrl: '',
  tlRsaPrivateKey: '',
  tlRsaPublicKey: '',
  tlSm2PrivateKey: '',
  tlSm2PublicKey: '',
})
const form_meta = reactive({
  privateKeyConfigured: false,
  publicKeyConfigured: false,
  sm2PrivateKeyConfigured: false,
  sm2PublicKeyConfigured: false,
})

function openEdit(row: TlpayConfigVO) {
  editForm.id = row.id
  editForm.shopName = row.shopName
  editForm.tlEnabled = !!row.tlEnabled
  editForm.tlMchId = row.tlMchId || ''
  editForm.tlAppId = row.tlAppId || ''
  editForm.tlSignType = row.tlSignType || 'RSA'
  editForm.tlNotifyUrl = row.tlNotifyUrl || ''
  // 私钥 / 公钥不回填明文 — 留空表示"不变"
  editForm.tlRsaPrivateKey = ''
  editForm.tlRsaPublicKey = ''
  editForm.tlSm2PrivateKey = ''
  editForm.tlSm2PublicKey = ''
  form_meta.privateKeyConfigured = !!row.privateKeyConfigured
  form_meta.publicKeyConfigured = !!row.publicKeyConfigured
  form_meta.sm2PrivateKeyConfigured = !!row.sm2PrivateKeyConfigured
  form_meta.sm2PublicKeyConfigured = !!row.sm2PublicKeyConfigured
  editVisible.value = true
}

async function onSave() {
  if (editForm.tlEnabled && !editForm.tlMchId) {
    ElMessage.warning('启用通联时 cusId 必填')
    return
  }
  saving.value = true
  try {
    const payload: TlpayConfigSaveReqVO = {
      id: editForm.id,
      tlEnabled: editForm.tlEnabled,
      tlMchId: editForm.tlMchId,
      tlAppId: editForm.tlAppId,
      tlSignType: editForm.tlSignType,
      tlNotifyUrl: editForm.tlNotifyUrl,
      tlRsaPrivateKey: editForm.tlRsaPrivateKey,
      tlRsaPublicKey: editForm.tlRsaPublicKey,
      tlSm2PrivateKey: editForm.tlSm2PrivateKey,
      tlSm2PublicKey: editForm.tlSm2PublicKey,
    }
    await saveTlpayConfig(payload)
    ElMessage.success('已保存')
    editVisible.value = false
    getList()
  } finally {
    saving.value = false
  }
}

onMounted(() => getList())
</script>
