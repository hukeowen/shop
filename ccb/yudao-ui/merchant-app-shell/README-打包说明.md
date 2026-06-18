# 拓小二 商户端 Android App（WebView 壳）打包说明

这是一个极简 uni-app 壳工程：一个全屏 `web-view` 加载线上商户端 H5
`https://tuo.doupaidoudian.com/m/`。H5 在壳里就是移动浏览器环境，
现有上传 / AI 视频 / 支付等浏览器 API 原样可用，无需重构。

## 用 HBuilderX 云打包出 APK（5 步）

1. **打开工程**：HBuilderX → 文件 → 打开目录 → 选本文件夹 `merchant-app-shell`。
2. **获取 appid**：双击 `manifest.json` → 进可视化界面 → 顶部「基础配置」→ 点
   **「重新获取」** DCloud appid（需登录 DCloud 账号，免费注册）。这一步必须做，
   否则不能云打包。
3. **（可选）配图标/启动图**：manifest 可视化 →「App 图标配置」上传图标自动生成各尺寸；
   「App 启动界面配置」传启动图。不配则用默认。
4. **改 App 名称/包名**：manifest「基础配置」名称已是「拓小二」；「App 常用其它设置」里
   可设 Android 包名（如 `com.doupaidoudian.merchant`）。
5. **云打包**：菜单「发行」→「原生 App-云打包」→ 勾选 **Android**，
   - 证书：首测可选「**使用 DCloud 公共测试证书**」（仅自测，不能上架）；
     正式发布用你自己的签名证书（keystore）。
   - 点「打包」，等几分钟，HBuilderX 控制台给出 APK 下载链接。

## 换 H5 地址
只改 `pages/index/index.vue` 里的 `url` 一处。

## 说明
- 壳 App 需联网（和 H5 一样）；登录态存在 webview 本地，重开 App 仍在。
- 要做离线/推送/扫码等原生能力时，再单独加原生模块；当前壳已满足「把商户端装成 App 用」。
