<script setup>
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app';
import { savePendingReferrer } from '@/utils/referral.js';

// V044：落地 H5 URL 含 ?inviter=X&tenantId=Y 时先存 localStorage，
// 等用户登录/进店后由 flushPendingReferrer 真正绑定。
function captureLandingInviter() {
  try {
    if (typeof location === 'undefined') return;
    // 顶层 query 形如：?tenantId=1010&inviter=99043
    if (location.search) {
      const sp = new URLSearchParams(location.search);
      const inviter = sp.get('inviter') || sp.get('referrerUserId');
      const tenantId = sp.get('tenantId');
      if (inviter) savePendingReferrer(inviter, tenantId);
    }
    // hash 路由：#/pages/shop/home?tenantId=1010&inviter=99043
    if (location.hash && location.hash.includes('?')) {
      const q = location.hash.slice(location.hash.indexOf('?') + 1);
      const sp = new URLSearchParams(q);
      const inviter = sp.get('inviter') || sp.get('referrerUserId');
      const tenantId = sp.get('tenantId');
      if (inviter) savePendingReferrer(inviter, tenantId);
    }
  } catch {}
}

onLaunch(() => {
  // V044：首先捕获 URL 上的 inviter（无论是否登录都先存起来）
  captureLandingInviter();

  // v9 风格主题色（H5 状态栏 / safari 顶部）
  // eslint-disable-next-line no-undef
  // #ifdef H5
  try {
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', '#FF6B35');
  } catch {}
  // #endif
});

onShow(() => {});
onHide(() => {});
</script>

<style lang="scss">
@import './uni.scss';

/* 全局复刻 v9-wow 基础样式 */
page {
  background: $bg;
  color: $t1;
  font: 14px/1.5 -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  -webkit-tap-highlight-color: transparent;
}

/* #ifdef H5 */
* { box-sizing: border-box; }
/* #endif */
view, scroll-view, swiper, swiper-item { box-sizing: border-box; }

/* 全局工具 class */
.t1 { color: $t1; } .t2 { color: $t2; } .t3 { color: $t3; } .t4 { color: $t4; }
.f-o { color: $o; } .f-gold { color: $gold; } .f-mint { color: $mint; }

.card {
  background: $card;
  border-radius: $r-lg;
  box-shadow: $sh-2;
  padding: 16px;
}

.btn-warm {
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff;
  border-radius: $r-pill;
  padding: 12px 28px;
  font-weight: 700;
  box-shadow: $sh-warm;
}

.safe-bottom { padding-bottom: env(safe-area-inset-bottom); }
</style>
