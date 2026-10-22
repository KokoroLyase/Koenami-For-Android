# Koenami for Android

非官方 Android 客户端，使用 Android WebView 打开 [Koenami 普通话官网](https://koe.transnavi.jp/zh-CN/)，逐步改善手机操作体验。与 Koenami 官方无隶属或背书关系。

当前处于 M0 验证与正式版本开发阶段；首个正式版本计划为 1.0。

## 原则

- 官方 HTTPS 页面作为真实主文档，声学分析、Worker/WASM、参考音频与 API 沿用官网。
- 不建立分析服务器，不伪造来源或 UA，不修改官方算法及采样链路。
- 客户端负责 Android 权限、文件、生命周期与可关闭的视觉增强。
- 无账户、广告、统计 SDK 或自动日志上传；官网本身的网络行为仍存在。

## 开发

Android 10+；compile/target SDK 37；JDK 17；Gradle 9.4.1；AGP 9.2.1（内置 Kotlin）。

配置 Android SDK 后运行 `gradlew.bat lintDebug testDebugUnitTest assembleDebug`。
本机工具链可通过 `tools/build.ps1` 使用；工具链和签名密钥不进入 Git。

PoC 安装身份为 `local.koenami.android.poc`，与正式包隔离。录音内容属于用户，卸载或清除数据前请自行导出。

## 开源与署名

新增客户端代码采用 [MIT](LICENSE)；上游 Koenami 声明见 [LICENSES/MIT-Koenami.txt](LICENSES/MIT-Koenami.txt)。第三方依赖保留各自许可，官网音频及其他资源不因客户端开源而变成 MIT，也不随 APK 打包。

进度见 [docs/PROGRESS.md](docs/PROGRESS.md)。正式发布前会补齐使用、隐私、签名、构建与测试说明。
