# 构建、签名与版本维护

## 开发构建

使用 JDK 17、Android SDK Platform 37.0、Build Tools 36.0.0，配置 ANDROID_HOME 或不提交的 local.properties。运行 `./gradlew.bat lintDebug testDebugUnitTest assembleDebug`；Linux/macOS 使用 `./gradlew`。Wrapper 校验 Gradle 9.4.1 下载包的 SHA-256。

本机可用 `tools/build.ps1` 自动选择 `.toolchain` 下的 JDK 和缓存；其他机器直接使用 Wrapper。Debug 使用系统标准开发证书，安装身份与 Release 不同。

## Release 签名

当前开发候选版本 1.2 / versionCode 10200；公开稳定版本为 1.1 / 10100，首个公开基线为 1.0 / 10000，包名 local.koenami.android。只有显式传入 `-PenableRelease=true` 且配置发布密钥后才启用 Release。私钥和密码禁止进入源码、CI 日志或 Release 附件。

通用构建：私下设置 `KOENAMI_KEYSTORE` 为本机 PKCS12 文件路径，`KOENAMI_STORE_PASSWORD` 为密码，别名 koenami；运行：

```text
./gradlew -PenableRelease=true lintRelease testReleaseUnitTest assembleRelease
```

本维护环境（Windows，PowerShell 7）首次创建独立身份使用 `tools/new-signing-key.ps1`，只允许在签名材料不存在时运行。密钥位于 `.local/signing/koenami-release.p12`，随机密码通过当前 Windows 用户的 DPAPI 保存为 `store-password.dpapi`。之后运行 `tools/release.ps1`，不需要重新生成密钥。

**DPAPI 文件绑定当前 Windows 用户和机器，不能充当可跨机恢复的密码备份。** 维护者需要把 PKCS12 和解密后的密码分别保存到安全位置 / 密码管理器。不要在聊天、Issue 或 Git 中发送它们。Windows 重装或账户变更前必须完成独立备份。

丢失原发布私钥通常会失去对现有安装直接更新的能力；不要通过生成新密钥掩盖问题。公开证书位于 `docs/release-signing-cert.pem`，可安全分发；证书指纹与 APK hash 是不同的值。

官方参考：[Android 应用签名](https://developer.android.com/studio/publish/app-signing)。

## 依赖与许可更新

依赖版本集中于 `gradle/libs.versions.toml`。修改依赖后运行：

```text
./gradlew -PenableRelease=true writeRuntimeInventory
python tools/build-notices.py
```

在设置相同签名环境的同一终端运行；Windows 可用 `tools/release.ps1 -Tasks writeRuntimeInventory`。脚本读取 GRADLE_USER_HOME 的 POM 和构件，生成完整声明及运行时清单；生成文件应一同提交，不手工删除第三方 NOTICE。

## 发布顺序

1. 更新版本名和递增 versionCode，运行相关检查，复核实际测试范围。
2. 清理过期文件，扫描当前源码及全部 Git 历史；密钥、密码、录音、设备日志和本机工具链留在忽略目录。
3. 同签名构建并验证 APK；覆盖升级，确认 Release 无调试入口；计算 SHA-256。
4. 默认在用户手机确认通过后，才创建版本标签并公开发布。1.0 已于 2026-10-22 获明确确认；1.1 按用户 2026-10-23 的明确要求免手机验证，在开发测试通过后直接发布。后续版本如无另行约定，仍保留手机审核流程。
5. Release 上传签名 APK、SHA256SUMS.txt 和公开证书。CI 只做开发构建与检查，不持有发布密钥，不自动发行。

版本覆盖测试可使用 `-PoverrideVersionCode=9999 -PoverrideVersionName=0.9.9` 构建仅本地模拟器使用的旧版本，当前候选产物使用默认 1.2 / 10200。这些覆盖参数不能用来把旧代码伪装成新版本，也不能发布测试夹具。

GitHub 更新器的标签、唯一 APK 资产、摘要及签名要求见 [更新机制](UPDATES.md)。先创建草稿并上传完整资产，确认 API 摘要后再公开；不能只发源码标签而缺少安装包。

## 官网变化与恢复

官网 DOM、算法和服务可独立变化。增强层探测失败时应退回原始界面；纯网页安全模式禁用本地适配。不要为兼容官网修改 CSP、UA、音频链路或私有数据库。先更新 WebView、检查官方页面与适配器探测，再修复受影响模块。

`tools/cdp-client.mjs`、布局回归和 Debug 注入脚本仅用于开发 WebView，不随 APK 打包；合成音频由 `tools/make-fixture.py` 在忽略的 evidence 目录生成。
