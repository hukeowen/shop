<template>
  <s-layout title="商户入驻">
    <view class="apply-container">
      <!-- 已提交状态 -->
      <view v-if="merchantInfo" class="status-card">
        <view class="status-icon">
          <text v-if="merchantInfo.status === 0" class="iconfont icon-time" style="color: #ff9900;font-size:48px;">⏳</text>
          <text v-else-if="merchantInfo.status === 1" style="color: #07c160;font-size:48px;">✅</text>
          <text v-else-if="merchantInfo.status === 2" style="color: #ff4d4f;font-size:48px;">❌</text>
        </view>
        <view class="status-text">
          <text v-if="merchantInfo.status === 0" class="title">审核中</text>
          <text v-else-if="merchantInfo.status === 1" class="title">审核通过</text>
          <text v-else-if="merchantInfo.status === 2" class="title">审核未通过</text>
        </view>
        <text v-if="merchantInfo.status === 2" class="reject-reason">原因：{{ merchantInfo.rejectReason }}</text>
        <button v-if="merchantInfo.status === 1" class="ss-btn" @tap="goShop">进入店铺管理</button>
      </view>

      <!-- 入驻表单 -->
      <view v-else class="form-section">
        <view class="section-title">基本信息</view>
        <view class="form-item">
          <text class="label">店铺名称 *</text>
          <input v-model="form.name" placeholder="请输入店铺名称" />
        </view>
        <view class="form-item">
          <text class="label">联系人 *</text>
          <input v-model="form.contactName" placeholder="请输入联系人姓名" />
        </view>
        <view class="form-item">
          <text class="label">联系电话 *</text>
          <input v-model="form.contactPhone" placeholder="请输入联系电话" type="number" />
        </view>

        <view class="section-title">营业资质</view>
        <view class="form-item">
          <text class="label">营业执照号 *</text>
          <input v-model="form.licenseNo" placeholder="请输入营业执照号" />
        </view>
        <view class="form-item">
          <text class="label">营业执照照片 *</text>
          <view class="upload-area" @tap="chooseImage('licenseUrl')">
            <image v-if="form.licenseUrl" :src="form.licenseUrl" mode="aspectFit" class="upload-img" />
            <view v-else class="upload-placeholder">
              <text class="plus">+</text>
              <text class="tip">上传营业执照</text>
            </view>
          </view>
        </view>

        <view class="section-title">法人信息</view>
        <view class="form-item">
          <text class="label">法人姓名 *</text>
          <input v-model="form.legalPersonName" placeholder="请输入法人姓名" />
        </view>
        <view class="form-item">
          <text class="label">身份证号 *</text>
          <input v-model="form.legalPersonIdCard" placeholder="请输入身份证号" />
        </view>
        <view class="form-item">
          <text class="label">身份证正面 *</text>
          <view class="upload-area" @tap="chooseImage('legalPersonIdCardFrontUrl')">
            <image v-if="form.legalPersonIdCardFrontUrl" :src="form.legalPersonIdCardFrontUrl" mode="aspectFit" class="upload-img" />
            <view v-else class="upload-placeholder">
              <text class="plus">+</text>
              <text class="tip">上传身份证正面</text>
            </view>
          </view>
        </view>
        <view class="form-item">
          <text class="label">身份证反面 *</text>
          <view class="upload-area" @tap="chooseImage('legalPersonIdCardBackUrl')">
            <image v-if="form.legalPersonIdCardBackUrl" :src="form.legalPersonIdCardBackUrl" mode="aspectFit" class="upload-img" />
            <view v-else class="upload-placeholder">
              <text class="plus">+</text>
              <text class="tip">上传身份证反面</text>
            </view>
          </view>
        </view>

        <view class="section-title">结算信息</view>
        <view class="form-item">
          <text class="label">开户名 *</text>
          <input v-model="form.bankAccountName" placeholder="请输入开户名" />
        </view>
        <view class="form-item">
          <text class="label">银行账号 *</text>
          <input v-model="form.bankAccountNo" placeholder="请输入银行账号" />
        </view>
        <view class="form-item">
          <text class="label">开户行 *</text>
          <input v-model="form.bankName" placeholder="请输入开户行" />
        </view>
        <view class="form-item">
          <text class="label">经营类目 *</text>
          <input v-model="form.businessCategory" placeholder="请输入经营类目" />
        </view>

        <button class="ss-btn submit-btn" :loading="submitting" @tap="submitApply">提交入驻申请</button>
      </view>
    </view>
  </s-layout>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import sheep from '@/sheep';
import MerchantApi from '@/sheep/api/merchant';

const merchantInfo = ref(null);
const submitting = ref(false);
const form = ref({
  name: '',
  contactName: '',
  contactPhone: '',
  licenseNo: '',
  licenseUrl: '',
  legalPersonName: '',
  legalPersonIdCard: '',
  legalPersonIdCardFrontUrl: '',
  legalPersonIdCardBackUrl: '',
  bankAccountName: '',
  bankAccountNo: '',
  bankName: '',
  businessCategory: '',
});

onMounted(async () => {
  const { data } = await MerchantApi.getMy();
  if (data) {
    merchantInfo.value = data;
  }
});

const chooseImage = (field) => {
  uni.chooseImage({
    count: 1,
    success: async (res) => {
      const tempPath = res.tempFilePaths[0];
      // 上传图片到服务器
      uni.uploadFile({
        url: sheep.$url.base_url + '/infra/file/upload',
        filePath: tempPath,
        name: 'file',
        header: { Authorization: 'Bearer ' + sheep.$store('user').token },
        success: (uploadRes) => {
          const result = JSON.parse(uploadRes.data);
          if (result.code === 0) {
            form.value[field] = result.data;
          }
        },
      });
    },
  });
};

const submitApply = async () => {
  // 基本校验
  if (!form.value.name || !form.value.contactName || !form.value.contactPhone) {
    uni.showToast({ title: '请填写完整的基本信息', icon: 'none' });
    return;
  }
  if (!form.value.licenseNo || !form.value.licenseUrl) {
    uni.showToast({ title: '请上传营业执照信息', icon: 'none' });
    return;
  }

  submitting.value = true;
  try {
    await MerchantApi.apply(form.value);
    uni.showToast({ title: '提交成功，等待审核', icon: 'success' });
    const { data } = await MerchantApi.getMy();
    merchantInfo.value = data;
  } catch (e) {
    uni.showToast({ title: e.msg || '提交失败', icon: 'none' });
  } finally {
    submitting.value = false;
  }
};

const goShop = () => {
  uni.navigateTo({ url: '/pages/merchant/shop' });
};
</script>

<style lang="scss" scoped>
.apply-container {
  padding: 20rpx 30rpx;
}
.status-card {
  text-align: center;
  padding: 80rpx 40rpx;
  background: #fff;
  border-radius: 16rpx;
  margin-top: 20rpx;
  .title { font-size: 36rpx; font-weight: bold; display: block; margin-top: 20rpx; }
  .reject-reason { color: #999; font-size: 26rpx; margin-top: 16rpx; display: block; }
}
.section-title {
  font-size: 30rpx;
  font-weight: bold;
  padding: 30rpx 0 16rpx;
  color: #333;
}
.form-item {
  background: #fff;
  padding: 24rpx;
  margin-bottom: 2rpx;
  .label { font-size: 28rpx; color: #333; margin-bottom: 12rpx; display: block; }
  input { font-size: 28rpx; height: 60rpx; }
}
.upload-area {
  width: 200rpx;
  height: 200rpx;
  border: 2rpx dashed #ddd;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.upload-img { width: 100%; height: 100%; }
.upload-placeholder {
  text-align: center;
  .plus { font-size: 48rpx; color: #ccc; display: block; }
  .tip { font-size: 24rpx; color: #999; }
}
.ss-btn {
  background: #ff6600;
  color: #fff;
  border-radius: 48rpx;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  font-size: 32rpx;
  margin-top: 40rpx;
}
</style>
