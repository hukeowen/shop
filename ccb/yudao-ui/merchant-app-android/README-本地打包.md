# 拓小二 商户端 Android（原生 WebView 工程）本地打包

纯原生 Android 工程，一个 WebView 全屏加载线上商户端 H5
`https://tuo.doupaidoudian.com/m/`，支持拍照/相册上传、定位、返回键、外部跳转。
**不依赖 DCloud**，标准 Android Studio 一键出 APK。

---

## 一、装环境（你机器现在只有 JDK8，缺 Android SDK）

最省事：装 **Android Studio**（一个安装包自带 **JDK17 + Android SDK + Gradle**）。
- 下载：https://developer.android.com/studio （或国内镜像 https://www.androiddevtools.cn/ ）
- 安装时默认勾选 Android SDK 即可；首次启动它会自动装 SDK Platform / Build-Tools。
- 不用动你现有的 JDK8，Android Studio 用自带的 JDK17。

> 不想装 IDE 也可以：单独装 commandline-tools + JDK17，设 `ANDROID_HOME`，命令行 `gradlew assembleDebug`。但首次建议用 Android Studio，省心。

## 二、打开工程

1. Android Studio → **Open** → 选本目录 `merchant-app-android`。
2. 等 **Gradle Sync**：会按 `gradle-wrapper.properties` 下载 Gradle 8.2（已配腾讯云镜像），
   并按 `app/build.gradle` 下载 AGP/依赖（已配阿里云镜像，国内快）。
   - 若提示缺 SDK（compileSdk 34 / Build-Tools），点提示里的 **Install** 自动装。
   - 若提示 “Gradle wrapper jar missing / 未找到”，让它按推荐**重新生成 wrapper** 即可
     （或本机有 gradle 时在本目录跑一次 `gradle wrapper --gradle-version 8.2`）。

## 三、出 APK（自测）

菜单 **Build → Build Bundle(s) / APK(s) → Build APK(s)** →
完成后右下角弹「locate」→ 拿到 `app/build/outputs/apk/debug/app-debug.apk`。
- debug 包用调试签名，**能直接装手机自测**（手机开「允许安装未知来源」）。
- 也可命令行：本目录 `gradlew assembleDebug`（Windows）。

## 四、正式发布（可选，要自己的签名证书）

1. 生成 keystore（Android Studio：Build → Generate Signed Bundle/APK → Create new…）。
2. 在 `app/build.gradle` 加 `signingConfigs` 并在 `release` 引用，或直接用
   Generate Signed APK 向导选 release，出 `app-release.apk`，可上架/分发。

---

## 改配置
- **换加载地址**：`app/src/main/java/com/doupaidoudian/merchant/MainActivity.java` 里 `HOME_URL`。
- **App 名称**：`app/src/main/res/values/strings.xml` 的 `app_name`。
- **包名**：`app/build.gradle` 的 `applicationId` + `namespace`（改了记得同步 java 目录包路径）。
- **图标**：现在是占位矢量图 `res/drawable/ic_launcher.xml`；要正式图标用 Android Studio
  右键 res → New → Image Asset 导一张图自动生成各尺寸。

## 关键能力已内置
- JS / localStorage（登录态）/ DOM storage
- `<input type=file>` 拍照 + 相册上传（证件照/收款码）+ 运行时权限申请
- H5 定位（geolocation 自动授权）
- 返回键网页回退、外部 scheme（tel/微信/支付宝）交系统
