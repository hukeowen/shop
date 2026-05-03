<template>
  <div :class="prefixCls" class="tan-login">
    <!-- 装饰圈（橙色品牌） -->
    <div class="decor decor-1"></div>
    <div class="decor decor-2"></div>
    <div class="decor decor-3"></div>

    <div class="container">
      <!-- 左：品牌叙事面板（lt-md 隐藏） -->
      <div class="brand-panel">
        <div class="brand-head">
          <div class="logo">摊</div>
          <div class="brand">
            <div class="brand-name">摊小二</div>
            <div class="brand-en">Tanxiaer Admin</div>
          </div>
        </div>

        <div class="brand-body">
          <div class="hero-title">让每一个普通生意<br />都能开口说话</div>
          <div class="hero-sub">AI 上架 · 朋友推荐分钱 · 一键短视频</div>

          <div class="features">
            <div class="feature">
              <div class="ic">🤖</div>
              <div class="ft-title">AI 一键成片</div>
              <div class="ft-sub">拍图自动生成短视频 + 文案</div>
            </div>
            <div class="feature">
              <div class="ic">🔗</div>
              <div class="ft-title">推 N 反 1</div>
              <div class="ft-sub">朋友扫码消费即返推广积分</div>
            </div>
            <div class="feature">
              <div class="ic">📊</div>
              <div class="ft-title">店铺看板</div>
              <div class="ft-sub">订单 / 营销 / 提现一屏掌握</div>
            </div>
          </div>
        </div>

        <div class="brand-foot">
          © 2026 摊小二 · 让每一个小生意都被看见
        </div>
      </div>

      <!-- 右：登录卡片 -->
      <div class="form-panel">
        <div class="form-top">
          <div class="brand-mini">
            <div class="logo-mini">摊</div>
            <span>{{ appStore.getTitle || '摊小二管理后台' }}</span>
          </div>
          <div class="actions">
            <ThemeSwitch />
            <LocaleDropdown />
          </div>
        </div>

        <Transition appear enter-active-class="animate__animated animate__fadeInUp">
          <div class="form-wrap">
            <LoginForm class="form-card" />
            <MobileForm class="form-card" />
            <QrCodeForm class="form-card" />
            <RegisterForm class="form-card" />
            <SSOLoginVue class="form-card" />
            <ForgetPasswordForm class="form-card" />
          </div>
        </Transition>
      </div>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { useDesign } from '@/hooks/web/useDesign'
import { useAppStore } from '@/store/modules/app'
import { ThemeSwitch } from '@/layout/components/ThemeSwitch'
import { LocaleDropdown } from '@/layout/components/LocaleDropdown'

import { LoginForm, MobileForm, QrCodeForm, RegisterForm, SSOLoginVue, ForgetPasswordForm } from './components'

defineOptions({ name: 'Login' })

const appStore = useAppStore()
const { getPrefixCls } = useDesign()
const prefixCls = getPrefixCls('login')
</script>

<style lang="scss" scoped>
$brand: #FF6B35;
$brand-2: #FF9A4A;
$ink-900: #1a1a1a;
$ink-600: #5A6577;
$ink-400: #909399;

.tan-login {
  position: relative;
  min-height: 100vh;
  width: 100%;
  background: linear-gradient(135deg, #FFF5EF 0%, #FFE5D6 50%, #FFF 100%);
  overflow: hidden;
}

/* 装饰背景圈 */
.decor {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  z-index: 0;
  filter: blur(2px);
}
.decor-1 {
  width: 520px; height: 520px;
  top: -180px; right: -160px;
  background: radial-gradient(circle, rgba(255,107,53,.18), rgba(255,107,53,0));
}
.decor-2 {
  width: 380px; height: 380px;
  bottom: -120px; left: -100px;
  background: radial-gradient(circle, rgba(255,154,74,.16), rgba(255,154,74,0));
}
.decor-3 {
  width: 280px; height: 280px;
  top: 36%; left: 38%;
  background: radial-gradient(circle, rgba(255,107,53,.06), rgba(255,107,53,0));
}

.container {
  position: relative;
  z-index: 1;
  display: flex;
  min-height: 100vh;
  margin: 0 auto;
  max-width: 1280px;
}

/* ── 左侧：品牌叙事 ──────────────────── */
.brand-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 56px 60px;
  color: #fff;
  background: linear-gradient(135deg, $brand 0%, $brand-2 60%, #FFB87A 100%);
  border-radius: 0 36px 36px 0;
  box-shadow: 0 24px 60px rgba(255, 107, 53, 0.20);
}
.brand-head {
  display: flex;
  align-items: center;
  gap: 14px;
}
.brand-head .logo {
  width: 56px; height: 56px;
  background: rgba(255,255,255,.95);
  color: $brand;
  border-radius: 14px;
  font-size: 30px;
  font-weight: 800;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 8px 24px rgba(0,0,0,0.12);
}
.brand-head .brand-name {
  font-size: 26px;
  font-weight: 800;
  letter-spacing: 2px;
}
.brand-head .brand-en {
  font-size: 13px;
  opacity: 0.85;
  letter-spacing: 1px;
  margin-top: 2px;
}

.brand-body { flex: 1; display: flex; flex-direction: column; justify-content: center; }
.hero-title {
  font-size: 40px;
  font-weight: 800;
  line-height: 1.3;
  letter-spacing: 1px;
}
.hero-sub {
  margin-top: 16px;
  font-size: 17px;
  opacity: 0.92;
  letter-spacing: 0.5px;
}

.features {
  margin-top: 56px;
  display: flex; flex-direction: column;
  gap: 18px;
  max-width: 360px;
}
.feature {
  position: relative;
  padding: 18px 20px 18px 64px;
  background: rgba(255,255,255,0.14);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255,255,255,0.22);
  border-radius: 16px;
  transition: transform .25s ease;
}
.feature:hover { transform: translateY(-2px); }
.feature .ic {
  position: absolute;
  left: 16px; top: 50%; transform: translateY(-50%);
  width: 36px; height: 36px;
  display: flex; align-items: center; justify-content: center;
  font-size: 22px;
  background: rgba(255,255,255,0.20);
  border-radius: 10px;
}
.feature .ft-title { font-size: 15px; font-weight: 700; }
.feature .ft-sub { font-size: 12px; opacity: 0.85; margin-top: 2px; }

.brand-foot {
  font-size: 12px;
  opacity: 0.75;
  letter-spacing: 0.5px;
}

/* ── 右侧：登录卡片 ──────────────────── */
.form-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 36px 56px;
}
.form-top {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 14px;
}
.brand-mini { display: none; align-items: center; gap: 10px; flex: 1; color: $ink-900; }
.brand-mini .logo-mini {
  width: 36px; height: 36px;
  background: linear-gradient(135deg, $brand, $brand-2);
  color: #fff; font-weight: 800;
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
}
.brand-mini span { font-size: 16px; font-weight: 700; }
.actions { display: flex; align-items: center; gap: 12px; }

.form-wrap {
  flex: 1;
  display: flex; align-items: center; justify-content: center;
  margin-top: 8px;
}
.form-card {
  width: 100%;
  max-width: 420px;
  padding: 36px 32px !important;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 16px 40px rgba(15,23,42,.08), 0 4px 12px rgba(15,23,42,.04);
}

/* lt-md：移动端单栏布局，隐藏左侧叙事 */
@media (max-width: 960px) {
  .container { flex-direction: column; max-width: 100%; }
  .brand-panel { display: none; }
  .form-panel { padding: 24px 16px; min-height: 100vh; }
  .brand-mini { display: flex; }
  .form-card { padding: 28px 22px !important; }
}
</style>

<style lang="scss">
/* 全局：让 LoginForm/MobileForm 的内部按钮统一橙色 */
.tan-login .el-button--primary {
  background: linear-gradient(135deg, #FF6B35, #FF9A4A) !important;
  border-color: #FF6B35 !important;
  box-shadow: 0 6px 18px rgba(255,107,53,.30) !important;
  font-weight: 600;
}
.tan-login .el-button--primary:hover,
.tan-login .el-button--primary:focus {
  background: linear-gradient(135deg, #e85a23, #f08a3d) !important;
  border-color: #e85a23 !important;
}
.tan-login .el-link.el-link--primary,
.tan-login .el-checkbox__input.is-checked .el-checkbox__inner {
  --el-color-primary: #FF6B35;
}

.dark .login-form .el-divider__text { background-color: var(--login-bg-color); }
.dark .login-form .el-card { background-color: var(--login-bg-color); }
</style>
