<template>
  <view class="page">
    <!-- 加载失败兜底：不再静默白屏 -->
    <view class="card err-card" v-if="loadError">
      <text class="err-txt">{{ loadError }}</text>
      <view class="err-retry" @click="load">点击重试</view>
    </view>
    <view class="loading-tip" v-else-if="loading && !shop">加载中…</view>

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
    </view>

    <view class="card desc-card">
      <view class="desc-title">关于在线支付</view>
      <view class="desc-item">· 开通后顾客可微信/支付宝在线支付，资金按结算账户到账</view>
      <view class="desc-item">· 只需填以下必要信息 + 拍证件照，平台审核后自动开通</view>
      <view class="desc-item">· 证件号、卡号等敏感信息加密存储，仅审核员可见</view>
    </view>

    <!-- 申请表单 -->
    <view class="card form-card" v-if="canApply">
      <view class="form-title">{{ shop && shop.payApplyStatus === 3 ? '重新提交开通申请' : '填写开通申请' }}</view>

      <view class="frow">
        <text class="flabel">商户类型</text>
        <picker mode="selector" :range="COMPROP" range-key="l" :value="compropIdx" @change="e => form.comproperty = COMPROP[e.detail.value].v">
          <view class="fpick">{{ compropLabel || '请选择' }}</view>
        </picker>
      </view>
      <view class="frow">
        <text class="flabel">商户名称</text>
        <input class="finput" v-model="form.merchantFullName" :placeholder="shop?.shopName || '店铺/营业执照名称'" />
      </view>
      <view class="frow">
        <text class="flabel">所属行业</text>
        <picker mode="selector" :range="MCC" range-key="l" :value="mccIdx" @change="e => form.mccId = MCC[e.detail.value].v">
          <view class="fpick">{{ mccLabel || '请选择' }}</view>
        </picker>
      </view>

      <view class="form-sec">法人 / 经营者</view>
      <view class="frow">
        <text class="flabel">姓名</text>
        <input class="finput" v-model="form.legalName" placeholder="法人/经营者姓名" />
      </view>
      <view class="frow">
        <text class="flabel">身份证号</text>
        <input class="finput" v-model="form.legalIdNo" placeholder="18 位身份证号" />
      </view>
      <view class="frow" v-if="!isPersonal">
        <text class="flabel">营业执照号</text>
        <input class="finput" v-model="form.creditCode" placeholder="统一社会信用代码（18 位）" />
      </view>

      <view class="form-sec">联系方式 / 地址</view>
      <view class="frow">
        <text class="flabel">联系电话</text>
        <input class="finput" type="number" v-model="form.contactPhone" placeholder="客服/负责人电话" />
      </view>
      <view class="frow">
        <text class="flabel">所在地区</text>
        <picker mode="multiSelector" :range="regionRange" :value="regionIdx" @columnchange="onRegionColumn" @change="onRegionChange">
          <view class="fpick">{{ regionLabel || (regionLoading ? '加载中…' : '请选择省/区县') }}</view>
        </picker>
      </view>
      <view class="frow">
        <text class="flabel">详细地址</text>
        <input class="finput" v-model="form.address" :placeholder="shop?.address || '街道门牌等'" />
      </view>

      <view class="form-sec">收款结算账户</view>
      <view class="frow">
        <text class="flabel">开户银行</text>
        <picker mode="selector" :range="BANKS" :value="bankIdx" @change="e => form.settleBankName = BANKS[e.detail.value]">
          <view class="fpick">{{ form.settleBankName || '请选择' }}</view>
        </picker>
      </view>
      <view class="frow">
        <text class="flabel">收款卡号</text>
        <input class="finput" type="number" v-model="form.settleAcctNo" placeholder="银行卡号 / 对公账号" />
      </view>
      <view class="frow">
        <text class="flabel">持卡人/户名</text>
        <input class="finput" v-model="form.settleAcctName" :placeholder="form.legalName || '与法人一致'" />
      </view>

      <view class="form-sec">证件照片</view>
      <view class="upload-grid">
        <view class="upload-item">
          <text class="upload-label">身份证人像面</text>
          <view class="upload-box" @click="pickImage('idCardFront')">
            <image v-if="formViewUrl.idCardFront" :src="formViewUrl.idCardFront" class="upload-img" mode="aspectFill" />
            <text v-else class="plus">+</text>
          </view>
        </view>
        <view class="upload-item">
          <text class="upload-label">身份证国徽面</text>
          <view class="upload-box" @click="pickImage('idCardBack')">
            <image v-if="formViewUrl.idCardBack" :src="formViewUrl.idCardBack" class="upload-img" mode="aspectFill" />
            <text v-else class="plus">+</text>
          </view>
        </view>
        <view class="upload-item" v-if="!isPersonal">
          <text class="upload-label">营业执照</text>
          <view class="upload-box" @click="pickImage('businessLicense')">
            <image v-if="formViewUrl.businessLicense" :src="formViewUrl.businessLicense" class="upload-img" mode="aspectFill" />
            <text v-else class="plus">+</text>
          </view>
        </view>
        <view class="upload-item" v-if="isPersonal">
          <text class="upload-label">手持身份证</text>
          <view class="upload-box" @click="pickImage('legalHold')">
            <image v-if="formViewUrl.legalHold" :src="formViewUrl.legalHold" class="upload-img" mode="aspectFill" />
            <text v-else class="plus">+</text>
          </view>
        </view>
      </view>

      <view class="form-sec">门店实景照片</view>
      <view class="sec-hint">用于审核核实门店真实存在，请拍清晰实景</view>
      <view class="upload-grid">
        <view class="upload-item">
          <text class="upload-label">门店照片（门头）</text>
          <view class="upload-box wide" @click="pickImage('storePic')">
            <image v-if="formViewUrl.storePic" :src="formViewUrl.storePic" class="upload-img" mode="aspectFill" />
            <text v-else class="plus">+</text>
          </view>
        </view>
        <view class="upload-item">
          <text class="upload-label">店内照片</text>
          <view class="upload-box wide" @click="pickImage('indoorPic')">
            <image v-if="formViewUrl.indoorPic" :src="formViewUrl.indoorPic" class="upload-img" mode="aspectFill" />
            <text v-else class="plus">+</text>
          </view>
        </view>
      </view>

      <button class="submit-btn" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中...' : '提交开通申请' }}
      </button>
    </view>

    <view class="card tip-card" v-if="shop && shop.payApplyStatus === 1">
      <text class="tip">申请已提交，请等待平台审核（通常 1-2 个工作日）</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, reactive } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';
import { blobUrlToBase64, uploadImage, signOss } from '../../api/oss.js';

const BASE = '/app-api/merchant/mini/shop';

// 商户类型
const COMPROP = [
  { v: '4', l: '个人' },
  { v: '3', l: '个体工商户' },
  { v: '1', l: '企业' },
  { v: '5', l: '其他组织' },
  { v: '6', l: '事业单位' },
];
// 常用行业（通联 MCC 附录8.4 真实码）
const MCC = [
  { v: '5814', l: '小吃快餐' }, { v: '5812', l: '正餐餐馆' }, { v: '5813', l: '酒吧/咖啡/茶馆' },
  { v: '5462', l: '烘焙糕点' }, { v: '5411', l: '超市' }, { v: '5331', l: '便利店' },
  { v: '5422', l: '生鲜肉类水产' }, { v: '5499', l: '食品店' }, { v: '5651', l: '服装' },
  { v: '5661', l: '鞋帽' }, { v: '7230', l: '美发' }, { v: '7298', l: '美容/SPA' },
  { v: '7297', l: '按摩足疗' }, { v: '5912', l: '药店' }, { v: '5945', l: '玩具' },
  { v: '5943', l: '文具' }, { v: '5992', l: '花店' }, { v: '7210', l: '洗衣' },
  { v: '7295', l: '家政' }, { v: '7699', l: '维修' }, { v: '8299', l: '教育培训' },
  { v: '7911', l: 'KTV/歌舞厅' }, { v: '7997', l: '健身' }, { v: '7221', l: '摄影' },
  { v: '5995', l: '宠物' }, { v: '5942', l: '书店' }, { v: '5251', l: '五金' },
  { v: '5211', l: '建材' }, { v: '5999', l: '其他零售' }, { v: '7399', l: '其他服务' },
];
// 常用银行
const BANKS = [
  '工商银行', '农业银行', '中国银行', '建设银行', '交通银行', '招商银行', '邮政储蓄银行',
  '浦发银行', '中信银行', '光大银行', '华夏银行', '民生银行', '广发银行', '平安银行',
  '兴业银行', '北京银行', '上海银行', '农村商业银行', '农村信用社', '其他',
];

const shop = ref(null);
const submitting = ref(false);
const loadError = ref('');   // 加载失败提示（不再静默白屏）
const loading = ref(true);
const formKey = reactive({ idCardFront: '', idCardBack: '', businessLicense: '', legalHold: '', storePic: '', indoorPic: '' });
const formViewUrl = reactive({ idCardFront: '', idCardBack: '', businessLicense: '', legalHold: '', storePic: '', indoorPic: '' });
const form = reactive({
  comproperty: '3', merchantFullName: '', mccId: '', legalName: '', legalIdNo: '',
  creditCode: '', contactPhone: '', address: '', districtCode: '',
  settleBankName: '', settleAcctNo: '', settleAcctName: '',
});

const isPersonal = computed(() => form.comproperty === '4');
const compropIdx = computed(() => Math.max(0, COMPROP.findIndex(x => x.v === form.comproperty)));
const compropLabel = computed(() => COMPROP.find(x => x.v === form.comproperty)?.l || '');
const mccIdx = computed(() => Math.max(0, MCC.findIndex(x => x.v === form.mccId)));
const mccLabel = computed(() => MCC.find(x => x.v === form.mccId)?.l || '');
const bankIdx = computed(() => Math.max(0, BANKS.indexOf(form.settleBankName)));

const statusText = computed(() => ({ 0: '未申请', 1: '审核中', 2: '已开通', 3: '已驳回' })[shop.value?.payApplyStatus] ?? '未申请');
const statusClass = computed(() => ({ 1: 'pending', 2: 'success', 3: 'danger' })[shop.value?.payApplyStatus] ?? 'gray');
const canApply = computed(() => {
  const s = shop.value?.payApplyStatus;
  return s == null || s === 0 || s === 3;
});

// ===== 省/区县 级联（运行时拉静态资源，避免打进主包）=====
const regionLoading = ref(false);
const regionData = ref(null); // { prov:[[code,name]], dist:{ "11":[[code,name]] } }
const provI = ref(0);
const distI = ref(0);
const regionRange = computed(() => {
  if (!regionData.value) return [[], []];
  const provs = regionData.value.prov.map(p => p[1]);
  const pcode = regionData.value.prov[provI.value]?.[0];
  const dists = (regionData.value.dist[pcode] || []).map(d => d[1]);
  return [provs, dists];
});
const regionIdx = computed(() => [provI.value, distI.value]);
const regionLabel = computed(() => {
  if (!regionData.value || !form.districtCode) return '';
  for (const p of regionData.value.prov) {
    const list = regionData.value.dist[p[0]] || [];
    const hit = list.find(d => d[0] === form.districtCode);
    if (hit) return hit[1];
  }
  return '';
});
function onRegionColumn(e) {
  const { column, value } = e.detail;
  if (column === 0) { provI.value = value; distI.value = 0; }
  else { distI.value = value; }
}
function onRegionChange(e) {
  const [pi, di] = e.detail.value;
  provI.value = pi; distI.value = di;
  const pcode = regionData.value?.prov[pi]?.[0];
  const d = (regionData.value?.dist[pcode] || [])[di];
  if (d) form.districtCode = d[0];
}
async function loadRegion() {
  if (regionData.value) return;
  regionLoading.value = true;
  try {
    const res = await new Promise((resolve, reject) => {
      uni.request({ url: '/m/static/region.json', method: 'GET', success: r => resolve(r.data), fail: reject });
    });
    regionData.value = typeof res === 'string' ? JSON.parse(res) : res;
  } catch (e) {
    uni.showToast({ title: '地区数据加载失败', icon: 'none' });
  } finally {
    regionLoading.value = false;
  }
}

async function load() {
  loading.value = true;
  loadError.value = '';
  try {
    shop.value = await request({ url: `${BASE}/pay-apply` });
    const s = shop.value || {};
    // 预填：优先用上次进件资料，没有就用店铺信息兜底
    form.comproperty = s.comproperty || '3';
    form.merchantFullName = s.merchantFullName || s.shopName || '';
    form.mccId = s.mccId || '';
    form.legalName = s.legalName || '';
    form.legalIdNo = s.legalIdNo && !String(s.legalIdNo).includes('****') ? s.legalIdNo : '';
    form.creditCode = s.creditCode || '';
    form.contactPhone = s.contactPhone || s.servicePhone || s.mobile || '';
    form.address = s.address || '';
    form.districtCode = s.districtCode || '';
    form.settleBankName = s.settleBankName || '';
    form.settleAcctNo = s.settleAcctNo && !String(s.settleAcctNo).includes('****') ? s.settleAcctNo : '';
    form.settleAcctName = s.settleAcctName || s.legalName || '';
    // 驳回重提：回填已传证件照 + 门店/店内照
    if (s.payApplyStatus === 3) {
      const map = [['idCardFrontKey', 'idCardFront'], ['idCardBackKey', 'idCardBack'], ['businessLicenseKey', 'businessLicense'], ['legalHoldPicKey', 'legalHold'], ['storePicKey', 'storePic'], ['indoorPicKey', 'indoorPic']];
      for (const [kf, vf] of map) {
        if (s[kf]) { formKey[vf] = s[kf]; signOss(s[kf]).then(u => formViewUrl[vf] = u).catch(() => {}); }
      }
    }
  } catch (e) {
    // 不再静默白屏：把失败原因显式展示，便于「登录失效/网络异常」一眼看出并重试
    // （request 失效时已弹「登录已失效」并自动跳登录；这里兜住其它失败）
    const msg = e?.msg || e?.message || '';
    if (!/unauthorized/i.test(msg)) {
      loadError.value = msg || '加载失败，请检查网络后重试';
    }
  } finally {
    loading.value = false;
  }
  loadRegion();
}

function pickImage(field) {
  uni.chooseImage({
    count: 1, sizeType: ['compressed'],
    success: async (r) => {
      const tempPath = r.tempFilePaths?.[0];
      if (!tempPath) return;
      uni.showLoading({ title: '上传中…' });
      try {
        const base64 = await blobUrlToBase64(tempPath);
        const { url, key } = await uploadImage(base64, { ext: 'jpg', acl: 'private', prefix: 'tanxiaoer/kyc' });
        formKey[field] = key;
        formViewUrl[field] = url;
        uni.hideLoading();
        uni.showToast({ title: '上传成功', icon: 'success' });
      } catch (e) {
        uni.hideLoading();
        uni.showToast({ title: '上传失败', icon: 'none' });
      }
    },
  });
}

function firstMissing() {
  if (!form.comproperty) return '请选择商户类型';
  if (!form.merchantFullName?.trim()) return '请填写商户名称';
  if (!form.mccId) return '请选择所属行业';
  if (!form.legalName?.trim()) return '请填写法人/经营者姓名';
  if (!form.legalIdNo?.trim()) return '请填写身份证号';
  if (!isPersonal.value && !form.creditCode?.trim()) return '请填写营业执照号';
  if (!form.contactPhone?.trim()) return '请填写联系电话';
  if (!form.districtCode) return '请选择所在地区';
  if (!form.address?.trim()) return '请填写详细地址';
  if (!form.settleBankName) return '请选择开户银行';
  if (!form.settleAcctNo?.trim()) return '请填写收款卡号';
  if (!formKey.idCardFront || !formKey.idCardBack) return '请上传身份证正反面';
  if (!isPersonal.value && !formKey.businessLicense) return '请上传营业执照';
  if (isPersonal.value && !formKey.legalHold) return '请上传手持身份证照';
  if (!formKey.storePic) return '请上传门店照片（门头/招牌）';
  if (!formKey.indoorPic) return '请上传店内照片';
  return '';
}

async function submit() {
  const miss = firstMissing();
  if (miss) { uni.showToast({ title: miss, icon: 'none' }); return; }
  submitting.value = true;
  try {
    await request({
      url: `${BASE}/pay-apply`, method: 'POST',
      data: {
        // 用户填写
        comproperty: form.comproperty,
        merchantFullName: form.merchantFullName,
        mccId: form.mccId,
        legalName: form.legalName,
        legalIdNo: form.legalIdNo,
        creditCode: form.creditCode,
        districtCode: form.districtCode,
        address: form.address,
        settleBankName: form.settleBankName,
        settleAcctNo: form.settleAcctNo,
        settleAcctName: form.settleAcctName || form.legalName,
        // 由联系电话派生（客服/负责人电话统一）
        servicePhone: form.contactPhone,
        contactPhone: form.contactPhone,
        contactPerson: form.legalName,
        busAddress: form.address,
        busContactPerson: form.legalName,
        busContactTel: form.contactPhone,
        // 默认值（后端拼进件时也会兜底）
        legalIdType: '01',
        clearMode: '1',
        acctTp: '00',
        settleAcctType: '0',
        thrCertFlag: '1',
        // 证件照 TOS key
        idCardFrontKey: formKey.idCardFront,
        idCardBackKey: formKey.idCardBack,
        businessLicenseKey: formKey.businessLicense || undefined,
        legalHoldPicKey: formKey.legalHold || undefined,
        // 门店照片 + 店内照片（审核必看）
        storePicKey: formKey.storePic,
        indoorPicKey: formKey.indoorPic,
      },
    });
    uni.showToast({ title: '申请已提交', icon: 'success' });
    load();
  } catch {
  } finally {
    submitting.value = false;
  }
}

onShow(() => load());
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page { padding: 24rpx; min-height: 100vh; padding-bottom: 60rpx; }
.card { background: $bg-card; border-radius: $radius-lg; padding: 28rpx; margin-bottom: 24rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.03); }

.status-card .status-row { display: flex; justify-content: space-between; align-items: center; padding: 12rpx 0; border-bottom: 1rpx solid $border-color; }
.status-card .status-row:last-child { border-bottom: none; }
.status-label { font-size: 26rpx; color: $text-secondary; }
.status-val { font-size: 26rpx; color: $text-primary; }
.status-val.gray { color: $text-placeholder; }
.status-val.pending { color: #F59E0B; font-weight: 600; }
.status-val.success { color: #10B981; font-weight: 600; }
.status-val.danger { color: $danger; }

.desc-card .desc-title { font-size: 28rpx; font-weight: 700; color: $text-primary; margin-bottom: 12rpx; }
.desc-card .desc-item { font-size: 24rpx; color: $text-secondary; line-height: 44rpx; }

.form-card .form-title { font-size: 30rpx; font-weight: 700; color: $text-primary; margin-bottom: 12rpx; }
.form-sec { font-size: 26rpx; font-weight: 700; color: $brand-primary; margin: 24rpx 0 4rpx; }
.frow { display: flex; align-items: center; padding: 20rpx 0; border-bottom: 1rpx solid $border-color; }
.flabel { width: 180rpx; flex-shrink: 0; font-size: 28rpx; color: $text-secondary; }
.finput { flex: 1; font-size: 28rpx; color: $text-primary; text-align: right; }
.fpick { flex: 1; font-size: 28rpx; color: $text-primary; text-align: right; }

.upload-grid { display: flex; flex-wrap: wrap; gap: 24rpx; margin-top: 16rpx; }
.upload-item { display: flex; flex-direction: column; align-items: center; gap: 10rpx; }
.upload-label { font-size: 24rpx; color: $text-secondary; }
.upload-box {
  width: 200rpx; height: 140rpx; border-radius: 12rpx; background: #f7f8fa;
  border: 1rpx dashed #d8dde6; overflow: hidden; display: flex; align-items: center; justify-content: center;
}
.upload-img { width: 100%; height: 100%; }
.plus { font-size: 56rpx; color: #b6bcc6; line-height: 1; }

.submit-btn {
  margin-top: 32rpx; height: 92rpx; line-height: 92rpx; background: $brand-primary; color: #fff;
  border-radius: $radius-pill; font-size: 32rpx; font-weight: 600;
}
.submit-btn[disabled] { opacity: 0.5; }
.submit-btn::after { border: none; }

.tip-card .tip { font-size: 26rpx; color: #F59E0B; line-height: 1.6; }

.loading-tip { text-align: center; color: $text-placeholder; font-size: 26rpx; padding: 60rpx 0; }
.err-card { display: flex; flex-direction: column; align-items: center; gap: 20rpx; }
.err-txt { font-size: 26rpx; color: $danger; text-align: center; line-height: 1.5; }
.err-retry { padding: 14rpx 48rpx; background: $brand-primary; color: #fff; border-radius: $radius-pill; font-size: 28rpx; font-weight: 600; }

.form-sec + .sec-hint { margin-top: 0; }
.sec-hint { display: block; font-size: 22rpx; color: $text-secondary; margin: 2rpx 0 8rpx; line-height: 1.4; }
.upload-box.wide { width: 320rpx; height: 200rpx; }
</style>
