import { defineConfig, loadEnv } from 'vite';
import uni from '@dcloudio/vite-plugin-uni';

// 摊小二商户/用户端 H5/小程序构建配置
//
// 历史沿革：早期 sidecar（OSS 上传 / TTS / 视频后处理 / 抖音发布）以 vite plugin
// 形式内嵌在本文件 ~900 行，附带 ffmpeg-static / aws-sdk / undici / msedge-tts
// 等大块依赖。问题：
//   1. ECS 构建时 ffmpeg-static postinstall 拉 GitHub release 30s 超时
//   2. undici 等 transitive dep 在 --ignore-scripts 后偶尔解析失败
//   3. H5 build 产物不打包它们，但 vite.config.js 求值阶段仍 require 全部
//
// 现状：sidecar 抽到 server/sidecar/ 独立 Node 服务（systemd 守护，本机 8081 监听）
//   · 生产：nginx 反代 /oss/* /tts/* /video/* /vproxy /jimeng /douyin/* → sidecar
//   · dev：vite proxy 转发上述前缀到 http://127.0.0.1:8081（开发者本地起 sidecar）
//
// dev 启动顺序（开发者）：
//   一窗：  cd server/sidecar && pnpm install && pnpm start
//   二窗：  cd yudao-ui/yudao-ui-merchant-uniapp && pnpm dev:h5
//
// 不再需要：ffmpeg-static / aws-sdk / msedge-tts / undici 这 4 个依赖（已从 package.json 移除）。

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const SIDECAR_URL = env.VITE_SIDECAR_URL || 'http://127.0.0.1:8081';

  return {
    plugins: [uni()],
    server: {
      host: true,
      port: 5180,
      proxy: {
        // 业务后端：管理域 / 商户用户端
        '/admin-api': { target: 'http://localhost:48080', changeOrigin: true },
        '/app-api':   { target: 'http://localhost:48080', changeOrigin: true },
        // sidecar：OSS 上传 / TTS / 视频后处理 / 抖音发布 / 视频反代 / 二维码出图
        '/oss':    { target: SIDECAR_URL, changeOrigin: true },
        '/tts':    { target: SIDECAR_URL, changeOrigin: true },
        '/video':  { target: SIDECAR_URL, changeOrigin: true },
        '/vproxy': { target: SIDECAR_URL, changeOrigin: true },
        '/jimeng': { target: SIDECAR_URL, changeOrigin: true },
        '/douyin': { target: SIDECAR_URL, changeOrigin: true },
        '/qr':     { target: SIDECAR_URL, changeOrigin: true },
      },
    },
  };
});
