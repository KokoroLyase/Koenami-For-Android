# Koenami for Android

<img src="app/src/main/res/drawable-nodpi/koenami_icon.png" width="96" alt="Koenami 图标">

**Koenami** 是由 Isomeria 维护的非官方 Android 客户端。使用 WebView 打开 [Koenami 普通话官网](https://koe.transnavi.jp/zh-CN/)，改善手机布局、文件操作及会话管理。与 Koenami 官方无隶属或背书关系。

## 安装与使用

从 [最新正式 Release](https://github.com/KokoroLyase/Koenami-For-Android/releases/latest) 下载 `Koenami-v1.1.apk`；同页提供 SHA-256 和公开签名证书。安装后名称为 **Koenami**。v1.0 用户首次需要手动覆盖安装 v1.1，此后可在应用内检查更新。

- Android 10 / API 29 起，需要联网和近期版本的 Android System WebView / Chrome。已验证 WebView 155；Android 10 镜像内置 74 版无法运行当前官网，须更新。
- 麦克风按钮进行普通录音，结束后在历史中播放和导出；“实时”仅实时测量，不新增录音历史。
- 右上角菜单提供原始界面、优化界面、纯网页安全模式、官网工具、隐私和开源许可。
- 三点菜单新增“检查更新”和默认每日自动检查，来自本仓库的最新正式 GitHub Release；下载和安装均需用户确认。在“应用更新设置”可关闭检查、开启后台通知，详见 [更新机制](docs/UPDATES.md)。
- 五项声音参数默认完整展示；地图支持全屏；分析入口固定在录音栏上方。
- 导出使用系统保存或分享面板，导入使用系统文件选择器，不申请存储权限。

进入后台或锁屏会请求官网结束采集；已提交历史在本机保留，异常终止前尚未提交的采集可能丢失。**卸载或清除应用数据会删除历史；官网 ZIP 不是完整数据库恢复备份。** 请定期导出重要音频。

正式包名为 `local.koenami.android`。以后升级应使用同一发布签名并覆盖安装。旧 PoC / Debug 包与正式包隔离，不自动迁移私有数据库。

## 实现与边界

Kotlin 原生容器加载真实官方 HTTPS 页面，本地 CSS / JavaScript 增强层可以关闭。声学算法、AudioWorklet、Worker/WASM、参考音频和 API 沿用官网；不打包官网副本，不设置分析代理，不伪造 UA，不修改 CSP 或官方采样链路。

客户端不新增广告、统计 SDK、账户或自动日志上传。官网自身的联网行为仍存在，见 [隐私与权限](docs/PRIVACY.md)。验证范围及未覆盖项目见 [测试说明](docs/TESTING.md)，不宣称所有设备和场景均已验证。

## 构建与维护

JDK 17、Android SDK Platform 37.0、Build Tools 36.0.0；Gradle Wrapper 9.4.1、AGP 9.2.1（内置 Kotlin）。

```powershell
./gradlew.bat lintDebug testDebugUnitTest assembleDebug
```

Linux/macOS 使用 `./gradlew`。配置 `ANDROID_HOME` 或本机 `local.properties`，首次构建需要下载依赖。开发包名为 `local.koenami.android.debug`，使用开发者本机 Debug 证书。

签名、升级和密钥保管见 [构建与发布](docs/BUILDING.md)，版本变更见 [CHANGELOG](CHANGELOG.md)。

## 许可、署名与版权

新增客户端采用 [MIT License](LICENSE)，维护者 **Isomeria**。上游 [transnavi/koenami](https://github.com/transnavi/koenami) 的 MIT 许可和署名保留于 [LICENSES/MIT-Koenami.txt](LICENSES/MIT-Koenami.txt)。

第三方依赖保留各自许可：[完整声明](THIRD_PARTY_NOTICES.txt)、[实际依赖](docs/runtime-dependencies.json)、[版权与来源](docs/ATTRIBUTION.md)。应用内也可查阅。官网音频、语料、商标和用户录音不因本仓库采用 MIT 而改变权利归属，不随 APK 打包。

图标使用 imagegen 生成，设计与提示词见 [design/ICON.md](design/ICON.md)。APK 公开签名证书用于识别更新身份，不是网站 HTTPS 证书，不代表官方认证。
