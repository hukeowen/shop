<script setup>
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app';

onLaunch(() => {
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

* { box-sizing: border-box; }
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
