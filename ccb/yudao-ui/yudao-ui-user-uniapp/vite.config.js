import { defineConfig } from 'vite';
import uni from '@dcloudio/vite-plugin-uni';

export default defineConfig(() => {
  return {
    base: process.env.UNI_PLATFORM === 'h5' ? '/u/' : '/',
    plugins: [uni()],
    server: {
      host: true,
      port: 5181,
      proxy: {
        '/admin-api': { target: 'http://localhost:48080', changeOrigin: true },
        '/app-api':   { target: 'http://localhost:48080', changeOrigin: true },
      },
    },
  };
});
