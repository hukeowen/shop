<template>
  <view class="page">
    <nav-bar title="编辑资料" />

    <view class="card">
      <!-- 头像 -->
      <view class="row">
        <text class="label">头像</text>
        <!-- #ifdef MP-WEIXIN -->
        <!-- 微信头像昵称填写组件：用户点一下从微信头像里选（不能静默获取） -->
        <button class="avatar-btn" open-type="chooseAvatar" @chooseavatar="onChooseAvatar">
          <image v-if="form.avatar" :src="form.avatar" class="avatar" mode="aspectFill" />
          <view v-else class="avatar ph">{{ avatarText }}</view>
        </button>
        <!-- #endif -->
        <!-- #ifndef MP-WEIXIN -->
        <view class="avatar-btn" @click="onPickAvatar">
          <image v-if="form.avatar" :src="form.avatar" class="avatar" mode="aspectFill" />
          <view v-else class="avatar ph">{{ avatarText }}</view>
        </view>
        <!-- #endif -->
      </view>

      <!-- 昵称 -->
      <view class="row">
        <text class="label">昵称</text>
        <!-- #ifdef MP-WEIXIN -->
        <input class="nick-input" type="nickname" v-model="form.nickname" placeholder="点此填写昵称" placeholder-class="ph-cls" />
        <!-- #endif -->
        <!-- #ifndef MP-WEIXIN -->
        <input class="nick-input" v-model="form.nickname" placeholder="填写昵称" maxlength="20" placeholder-class="ph-cls" />
        <!-- #endif -->
      </view>

      <!-- 手机号（只读，已通过微信授权绑定）-->
      <view class="row">
        <text class="label">手机号</text>
        <text class="readonly">{{ user.phone || '—' }}</text>
      </view>
    </view>

    <view class="tip">头像、昵称用于在优惠公告、邀请海报等场景展示</view>
    <view class="save-btn" :class="{ disabled: saving }" @click="onSave">{{ saving ? '保存中…' : '保存' }}</view>
  </view>
</template>

<script setup>
import { reactive, ref, computed } from 'vue';
import { useUserStore } from '@/store/user.js';
import { updateProfile } from '@/api/auth.js';
import { uploadImage, chooseAndUploadImage } from '@/api/upload.js';

const user = useUserStore();
const form = reactive({ nickname: user.nickname || '', avatar: user.avatar || '' });
const saving = ref(false);
const avatarText = computed(() => (user.nickname?.[0] || user.phone?.[0] || '客'));

// #ifdef MP-WEIXIN
// 微信头像填写组件回调：e.detail.avatarUrl 是临时路径，要上传换公网 URL
async function onChooseAvatar(e) {
  const path = e.detail && e.detail.avatarUrl;
  if (!path) return;
  uni.showLoading({ title: '上传中…' });
  try { form.avatar = await uploadImage(path); }
  catch { uni.showToast({ title: '头像上传失败', icon: 'none' }); }
  finally { uni.hideLoading(); }
}
// #endif

// #ifndef MP-WEIXIN
// H5 / APP：相册选图 → 上传
async function onPickAvatar() {
  uni.showLoading({ title: '上传中…' });
  try { form.avatar = await chooseAndUploadImage(); }
  catch (err) { if (err && err.message !== '未选择图片') uni.showToast({ title: '头像上传失败', icon: 'none' }); }
  finally { uni.hideLoading(); }
}
// #endif

async function onSave() {
  if (saving.value) return;
  if (!form.nickname || !form.nickname.trim()) {
    uni.showToast({ title: '请填写昵称', icon: 'none' });
    return;
  }
  saving.value = true;
  try {
    await updateProfile({ nickname: form.nickname.trim(), avatar: form.avatar || undefined });
    user.setProfile({ nickname: form.nickname.trim(), avatar: form.avatar });
    uni.showToast({ title: '已保存', icon: 'success' });
    setTimeout(() => uni.navigateBack(), 600);
  } catch (err) {
    uni.showToast({ title: err?.message || '保存失败', icon: 'none' });
  } finally {
    saving.value = false;
  }
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; }
.card { margin: 12px 14px 0; background: #fff; border-radius: $r-lg; box-shadow: $sh-1; overflow: hidden; }
.row { display: flex; align-items: center; padding: 14px 16px; border-bottom: 1px solid $line; min-height: 30px; }
.row .label { width: 70px; font-size: 14px; color: $t1; font-weight: 600; }
.avatar-btn { margin-left: auto; padding: 0; background: transparent; line-height: 1; border: none; }
.avatar-btn::after { border: none; }
.avatar { width: 56px; height: 56px; border-radius: 50%; background: $bg-2; }
.avatar.ph { display: flex; align-items: center; justify-content: center; font-size: 22px; font-weight: 700; color: $o-d; background: $o-50; }
.nick-input { flex: 1; text-align: right; font-size: 14px; color: $t1; }
.ph-cls { color: $t4; }
.readonly { margin-left: auto; font-size: 14px; color: $t3; }
.tip { padding: 12px 18px; font-size: 11px; color: $t4; }
.save-btn { margin: 10px 14px; height: 46px; border-radius: $r-pill; background: linear-gradient(135deg, $o, $o-d); color: #fff; font-size: 15px; font-weight: 800; display: flex; align-items: center; justify-content: center; box-shadow: $sh-warm; }
.save-btn.disabled { opacity: .6; }
</style>
