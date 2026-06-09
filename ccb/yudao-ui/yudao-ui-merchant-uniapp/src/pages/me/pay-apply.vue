<template>
  <view class="page">
    <!-- 状态卡片 -->
    <view class="card status-card" v-if="shop">
      <view class="status-row">
        <text class="status-label">当前状态</text>
        <text class="status-val" :class="statusClass">{{ statusText }}</text>
      </view>
      <view class="status-row" v-if="shop.payApplyRejectReason">
        <text class="status-label">驳回原因</text>
        <text class="status-val danger">{{ shop.payApplyRejectReason }}</text>
      </view>
      <view class="status-row" v-if="shop.tlMchId">
        <text class="status-label">通联商户号</text>
        <text class="status-val">{{ shop.tlMchId }}</text>
      </view>
      <view class="status-row" v-if="shop.tlMchKey">
        <text class="status-label">密钥（脱敏）</text>
        <text class="status-val">{{ shop.tlMchKey }}</text>
      </view>
    </view>

    <!-- 说明 -->
    <view class="card desc-card">
      <view class="desc-title">关于在线支付</view>
      <view class="desc-body">
        <view class="desc-item">· 开通后用户可通过微信支付在线下单，资金 T+1 直达您的账户</view>
        <view class="desc-item">· 未开通时订单走「到店付款」，您手动确认收款</view>
        <view class="desc-item">· 填写法人 / 营业执照 / 结算账户信息 + 上传资质照，提交后审核（通常 1-2 个工作日）</view>
        <view class="desc-item">· 审核通过后，系统自动为您开通通联收付通商户号，收款直达结算账户</view>
        <view class="desc-item">· 证件号、账户号等敏感信息加密存储，仅平台审核员可见</view>
      </view>
    </view>

    <!-- 申请表单（未申请或已驳回时显示） -->
    <view class="card form-card" v-if="canApply">
      <view class="form-title">{{ shop && shop.payApplyStatus === 3 ? '重新提交进件资料' : '提交开通申请' }}</view>

      <!-- 1. 商户主体 -->
      <view class="form-sec">商户主体</view>
      <view class="frow">
        <text class="flabel">商户性质</text>
        <picker mode="selector" :range="COMPROP" range-key="l" :value="compropIdx" @change="onCompropChange">
          <view class="fpick">{{ compropLabel || '请选择' }}</view>
        </picker>
      </view>
      <view class="frow">
        <text class="flabel">商户全称</text>
        <input class="finput" v-model="form.merchantFullName" :placeholder="shop?.shopName || '与营业执照一致'" />
      </view>
      <view class="frow">
        <text class="flabel">联系邮箱</text>
        <input class="finput" v-model="form.contactEmail" placeholder="用于接收开通通知（选填）" />
      </view>

      <!-- 2. 法人 / 经营者 -->
      <view class="form-sec">法人 / 经营者</view>
      <view class="frow">
        <text class="flabel">姓名</text>
        <input class="finput" v-model="form.legalName" placeholder="法人/经营者姓名" />
      </view>
      <view class="frow">
        <text class="flabel">身份证号</text>
        <input class="finput" v-model="form.legalIdNo" placeholder="18 位身份证号" />
      </view>
      <view class="frow">
        <text class="flabel">证件有效期</text>
        <input class="finput" v-model="form.legalIdExpire" placeholder="如 2035-08-01 或 长期" />
      </view>

      <!-- 3. 营业执照（非个人显示） -->
      <template v-if="!isPersonal">
        <view class="form-sec">营业执照</view>
        <view class="frow">
          <text class="flabel">统一社会信用代码</text>
          <input class="finput" v-model="form.creditCode" placeholder="营业执照上 18 位代码" />
        </view>
        <view class="frow">
          <text class="flabel">执照有效期</text>
          <input class="finput" v-model="form.creditCodeExpire" placeholder="如 2040-01-01 或 长期" />
        </view>
      </template>

      <!-- 4. 结算账户 -->
      <view class="form-sec">结算银行账户（用于收款到账）</view>
      <view class="frow">
        <text class="flabel">账户类型</text>
        <picker mode="selector" :range="ACCTTYPE" range-key="l" :value="acctTypeIdx" @change="onAcctTypeChange">
          <view class="fpick">{{ acctTypeLabel || '请选择' }}</view>
        </picker>
      </view>
      <view class="frow">
        <text class="flabel">账户名</text>
        <input class="finput" v-model="form.settleAcctName" :placeholder="form.settleAcctType === '1' ? '企业账户全称' : '持卡人姓名'" />
      </view>
      <view class="frow">
        <text class="flabel">账户号</text>
        <input class="finput" type="number" v-model="form.settleAcctNo" placeholder="银行卡号 / 对公账号" />
      </view>
      <view class="frow">
        <text class="flabel">开户银行</text>
        <input class="finput" v-model="form.settleBankName" placeholder="如 招商银行 / 工商银行" />
      </view>
      <view class="frow" v-if="form.settleAcctType === '1'">
        <text class="flabel">支付行号</text>
        <input class="finput" type="number" v-model="form.settleCnapsNo" placeholder="对公账户必填（12 位联行号）" />
      </view>

      <!-- 5. 资质照 -->
      <view class="form-sec">资质照片</view>
      <view class="upload-row">
        <text class="upload-label">法人身份证 · 正面（人像面）</text>
        <view class="upload-box" @click="pickImage('idCardFront')">
          <image v-if="formViewUrl.idCardFront" :src="formViewUrl.idCardFront" class="upload-img" mode="aspectFill" />
          <view v-else class="upload-placeholder">
            <text class="plus">+</text>
            <text class="hint">点击上传</text>
          </view>
        </view>
      </view>

      <view class="upload-row">
        <text class="upload-label">法人身份证 · 背面（国徽面）</text>
        <view class="upload-box" @click="pickImage('idCardBack')">
          <image v-if="formViewUrl.idCardBack" :src="formViewUrl.idCardBack" class="upload-img" mode="aspectFill" />
          <view v-else class="upload-placeholder">
            <text class="plus">+</text>
            <text class="hint">点击上传</text>
          </view>
        </view>
      </view>

      <view class="upload-row" v-if="!isPersonal">
        <text class="upload-label">营业执照（彩色清晰原件）</text>
        <view class="upload-box" @click="pickImage('businessLicense')">
          <image v-if="formViewUrl.businessLicense" :src="formViewUrl.businessLicense" class="upload-img" mode="aspectFill" />
          <view v-else class="upload-placeholder">
            <text class="plus">+</text>
            <text class="hint">点击上传</text>
          </view>
        </view>
      </view>

      <button class="submit-btn" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中...' : '提交开通申请' }}
      </button>
    </view>

    <!-- 已提交等待审核：展示已上传资质 -->
    <view class="card tip-card" v-if="shop && shop.payApplyStatus === 1">
      <text class="tip">申请已提交，请等待平台审核（通常 1-2 个工作日）</text>
      <view class="readonly-grid">
        <image v-if="shopViewUrl.idCardFront" :src="shopViewUrl.idCardFront" class="readonly-img" mode="aspectFill" />
        <image v-if="shopViewUrl.idCardBack" :src="shopViewUrl.idCardBack" class="readonly-img" mode="aspectFill" />
        <image v-if="shopViewUrl.businessLicense" :src="shopViewUrl.businessLicense" class="readonly-img" mode="aspectFill" />
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, reactive } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';
import { blobUrlToBase64, uploadImage, signOss } from '../../api/oss.js';

const BASE = '/app-api/merchant/mini/shop';

const shop = ref(null);
// formKey：用户当前选好准备提交的 3 个 TOS key
// formViewUrl：对应的 1h 预签名 URL，用于 <image :src> 渲染
const formKey = reactive({ idCardFront: '', idCardBack: '', businessLicense: '' });
const formViewUrl = reactive({ idCardFront: '', idCardBack: '', businessLicense: '' });
// shopViewUrl：已提交进件的资质对应的预签名 URL（GET /pay-apply 返 *_key 后 sign 出来）
const shopViewUrl = reactive({ idCardFront: '', idCardBack: '', businessLicense: '' });
const submitting = ref(false);

// 进件结构化资料
const COMPROP = [
  { v: '1', l: '企业' },
  { v: '3', l: '个体工商户' },
  { v: '4', l: '个人' },
  { v: '5', l: '其他组织' },
  { v: '6', l: '事业单位' },
];
const ACCTTYPE = [
  { v: '0', l: '对私（个人银行卡）' },
  { v: '1', l: '对公（企业账户）' },
];
const form = reactive({
  comproperty: '', merchantFullName: '', contactEmail: '',
  legalName: '', legalIdNo: '', legalIdExpire: '长期',
  creditCode: '', creditCodeExpire: '长期',
  settleAcctName: '', settleAcctNo: '', settleAcctType: '', settleBankName: '', settleCnapsNo: '',
});
const isPersonal = computed(() => form.comproperty === '4');
const compropIdx = computed(() => Math.max(0, COMPROP.findIndex((x) => x.v === form.comproperty)));
const compropLabel = computed(() => COMPROP.find((x) => x.v === form.comproperty)?.l || '');
const acctTypeIdx = computed(() => Math.max(0, ACCTTYPE.findIndex((x) => x.v === form.settleAcctType)));
const acctTypeLabel = computed(() => ACCTTYPE.find((x) => x.v === form.settleAcctType)?.l || '');
function onCompropChange(e) { form.comproperty = COMPROP[e.detail.value].v; }
function onAcctTypeChange(e) { form.settleAcctType = ACCTTYPE[e.detail.value].v; }

const statusText = computed(() => {
  const s = shop.value?.payApplyStatus;
  if (s == null) return '未申请';
  return { 0: '未申请', 1: '审核中', 2: '已开通', 3: '已驳回' }[s] ?? '-';
});

const statusClass = computed(() => {
  const s = shop.value?.payApplyStatus;
  if (s == null || s === 0) return 'gray';
  return { 1: 'pending', 2: 'success', 3: 'danger' }[s] ?? '';
});

const canApply = computed(() => {
  const s = shop.value?.payApplyStatus;
  return s == null || s === 0 || s === 3;
});

async function load() {
  try {
    shop.value = await request({ url: `${BASE}/pay-apply` });
    // 已提交进件状态：把已存 key 现签预签名 URL 用于回显
    const fields = [
      ['idCardFrontKey', 'idCardFront'],
      ['idCardBackKey', 'idCardBack'],
      ['businessLicenseKey', 'businessLicense'],
    ];
    await Promise.all(
      fields.map(async ([keyField, viewField]) => {
        const k = shop.value?.[keyField];
        shopViewUrl[viewField] = k ? await signOss(k).catch(() => '') : '';
      })
    );
    // 回填进件结构化资料（未申请也回填，驳回重提更省事；敏感字段后端返脱敏值，不动即保持原值）
    const s = shop.value || {};
    form.comproperty = s.comproperty || '';
    form.merchantFullName = s.merchantFullName || '';
    form.contactEmail = s.contactEmail || '';
    form.legalName = s.legalName || '';
    form.legalIdNo = s.legalIdNo || '';
    form.legalIdExpire = s.legalIdExpire || '长期';
    form.creditCode = s.creditCode || '';
    form.creditCodeExpire = s.creditCodeExpire || '长期';
    form.settleAcctName = s.settleAcctName || '';
    form.settleAcctNo = s.settleAcctNo || '';
    form.settleAcctType = s.settleAcctType || '';
    form.settleBankName = s.settleBankName || '';
    form.settleCnapsNo = s.settleCnapsNo || '';
    // 驳回后重新申请：把上次的 key + 预签名 URL 回填到表单，方便只换被驳回的那张
    if (shop.value?.payApplyStatus === 3) {
      formKey.idCardFront = shop.value.idCardFrontKey || '';
      formKey.idCardBack = shop.value.idCardBackKey || '';
      formKey.businessLicense = shop.value.businessLicenseKey || '';
      formViewUrl.idCardFront = shopViewUrl.idCardFront;
      formViewUrl.idCardBack = shopViewUrl.idCardBack;
      formViewUrl.businessLicense = shopViewUrl.businessLicense;
    }
  } catch {}
}

function pickImage(field) {
  uni.chooseImage({
    count: 1,
    success: async (r) => {
      const tempPath = r.tempFilePaths?.[0];
      if (!tempPath) return;
      uni.showLoading({ title: '上传中…' });
      try {
        const base64 = await blobUrlToBase64(tempPath);
        // ⚠️ KYC 证件必须 acl=private — 永不向公网开放永久 URL
        const { url, key } = await uploadImage(base64, { ext: 'jpg', acl: 'private', prefix: 'tanxiaoer/kyc' });
        formKey[field] = key;
        formViewUrl[field] = url; // 上传响应里返的预签名 URL 已可立即展示
        uni.hideLoading();
        uni.showToast({ title: '上传成功', icon: 'success' });
      } catch (e) {
        uni.hideLoading();
        uni.showToast({ title: '上传失败：' + (e?.message || e), icon: 'none' });
      }
    },
  });
}

function firstMissing() {
  if (!form.comproperty) return '请选择商户性质';
  if (!form.legalName?.trim()) return '请填写法人/经营者姓名';
  if (!form.legalIdNo?.trim()) return '请填写法人身份证号';
  if (!isPersonal.value && !form.creditCode?.trim()) return '请填写统一社会信用代码';
  if (!form.settleAcctType) return '请选择结算账户类型';
  if (!form.settleAcctName?.trim()) return '请填写结算账户名';
  if (!form.settleAcctNo?.trim()) return '请填写结算账户号';
  if (!form.settleBankName?.trim()) return '请填写开户银行';
  if (!formKey.idCardFront || !formKey.idCardBack) return '请上传法人身份证正反面';
  if (!isPersonal.value && !formKey.businessLicense) return '请上传营业执照';
  return '';
}

async function submit() {
  const miss = firstMissing();
  if (miss) {
    uni.showToast({ title: miss, icon: 'none' });
    return;
  }
  submitting.value = true;
  try {
    await request({
      url: `${BASE}/pay-apply`,
      method: 'POST',
      data: {
        idCardFrontKey: formKey.idCardFront,
        idCardBackKey: formKey.idCardBack,
        businessLicenseKey: formKey.businessLicense,
        comproperty: form.comproperty,
        merchantFullName: form.merchantFullName,
        contactEmail: form.contactEmail,
        legalName: form.legalName,
        legalIdNo: form.legalIdNo,
        legalIdExpire: form.legalIdExpire,
        creditCode: form.creditCode,
        creditCodeExpire: form.creditCodeExpire,
        settleAcctName: form.settleAcctName,
        settleAcctNo: form.settleAcctNo,
        settleAcctType: form.settleAcctType,
        settleBankName: form.settleBankName,
        settleCnapsNo: form.settleCnapsNo,
      },
    });
    uni.showToast({ title: '申请已提交', icon: 'success' });
    formKey.idCardFront = formKey.idCardBack = formKey.businessLicense = '';
    formViewUrl.idCardFront = formViewUrl.idCardBack = formViewUrl.businessLicense = '';
    load();
  } catch {
    // toast from request.js
  } finally {
    submitting.value = false;
  }
}

onShow(() => load());
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 24rpx;
  min-height: 100vh;
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 28rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.status-card .status-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12rpx 0;
  border-bottom: 1rpx solid $border-color;

  &:last-child { border-bottom: none; }

  .status-label { font-size: 26rpx; color: $text-secondary; }

  .status-val {
    font-size: 26rpx; color: $text-primary;

    &.gray { color: $text-placeholder; }
    &.pending { color: #F59E0B; font-weight: 600; }
    &.success { color: #10B981; font-weight: 600; }
    &.danger { color: $danger; }
  }
}

.desc-card {
  .desc-title {
    font-size: 28rpx;
    font-weight: 700;
    color: $text-primary;
    margin-bottom: 16rpx;
  }

  .desc-item {
    font-size: 24rpx;
    color: $text-secondary;
    line-height: 48rpx;
  }
}

.form-card {
  .form-title {
    font-size: 28rpx;
    font-weight: 700;
    color: $text-primary;
    margin-bottom: 20rpx;
  }
}

.form-sec {
  font-size: 26rpx;
  font-weight: 700;
  color: $brand-primary;
  margin: 24rpx 0 8rpx;
}
.frow {
  display: flex;
  align-items: center;
  padding: 18rpx 0;
  border-bottom: 1rpx solid $border-color;
}
.flabel {
  width: 200rpx;
  flex-shrink: 0;
  font-size: 26rpx;
  color: $text-secondary;
}
.finput {
  flex: 1;
  font-size: 28rpx;
  color: $text-primary;
  text-align: right;
}
.fpick {
  flex: 1;
  font-size: 28rpx;
  color: $text-primary;
  text-align: right;
}

.upload-row {
  margin-bottom: 28rpx;

  .upload-label {
    display: block;
    font-size: 26rpx;
    color: $text-secondary;
    margin-bottom: 12rpx;
  }

  .upload-box {
    width: 320rpx;
    height: 200rpx;
    border-radius: 12rpx;
    background: #f7f8fa;
    border: 1rpx dashed #d8dde6;
    overflow: hidden;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .upload-img {
    width: 100%;
    height: 100%;
  }

  .upload-placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;

    .plus {
      font-size: 56rpx;
      color: #b6bcc6;
      line-height: 1;
    }
    .hint {
      margin-top: 8rpx;
      font-size: 24rpx;
      color: $text-placeholder;
    }
  }
}

.submit-btn {
  margin-top: 16rpx;
  height: 88rpx;
  line-height: 88rpx;
  background: $brand-primary;
  color: #fff;
  border-radius: $radius-pill;
  font-size: 30rpx;
  font-weight: 600;

  &[disabled] { opacity: 0.5; }
  &::after { border: none; }
}

.tip-card {
  .tip {
    font-size: 26rpx;
    color: #F59E0B;
    line-height: 1.6;
  }

  .readonly-grid {
    margin-top: 20rpx;
    display: flex;
    gap: 12rpx;
    flex-wrap: wrap;

    .readonly-img {
      width: 200rpx;
      height: 140rpx;
      border-radius: 8rpx;
    }
  }
}
</style>
