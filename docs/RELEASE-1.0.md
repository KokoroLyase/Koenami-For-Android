# v1.0 发布验证

日期：2026-10-22（Asia/Singapore）。用户已在手机审阅界面并明确要求定版和公开发布。

- 正式名称 Koenami；版本 1.0，versionCode 10000；安装身份 local.koenami.android；min API 29、target API 37。
- 独立 RSA 3072 发布密钥（CN=Isomeria, OU=Koenami for Android），未沿用 PoC / Android Debug 证书；公开证书见 release-signing-cert.pem。
- 发布证书 SHA-256：`0f23777a70ce4784728fc1930c950f2f92ace8d32ecb3dca628dd3e34d8ce627`。
- APK SHA-256：`c29d6c5d5a9ba10ab101e70e2e6ba42380fd8d9c0c60b11241070e15ee0b33d1`。
- Gradle Wrapper 9.4.1 实际运行，分发包通过固定 SHA-256；Release lint 0 errors / 8 warnings；Release 单元测试 4 项通过。未将非阻断警告写成零警告。
- apksigner verify 通过，APK Signature Scheme v2 有效；不宣称具备未生成的 v3、v4 或 SourceStamp。
- API 37：发布签名包的三项容器 / 模式测试通过；另通过官网标准文件输入导入五秒 160Hz 合成 WAV，列入历史且音频可加载。
- API 37：同一发布密钥的 9999 测试版本覆盖升级为 1.0 / 10000 后，原五秒音频仍列于官网历史并加载为五秒，升级测试通过。未直接写入官网数据库。合成夹具未随 APK 或 Release 分发。
- 实际手机：旧 PoC / Debug 站点数据及原包已备份到本机私有忽略目录，旧测试应用已清理，安装的正式包版本与包名正确，未含 DEBUGGABLE 标记；进程未提供 WebView DevTools socket，未发布测试 APK。
- 实际手机：正式版官网及最终界面显示正常，首次麦克风系统授权由用户完成，录音达到官网当前 60 秒上限并自动停止，完成官方分析并显示已保存历史。音频留在手机，不进入公开材料。

完整测试边界见 TESTING.md。30 分钟耐久及其他未覆盖项未被此记录替代。

## 公开仓库清理

移除旧进度 / 审批 / 权限提案、原始大规划和重复 PoC 调试脚本，将有效内容整理到使用、构建、隐私、署名及测试说明。清理重复 SDK 解压目录和下载用安装 ZIP，保留活跃工具链与必要私有备份。

Git 忽略规则覆盖私钥、加密密码、本机配置、SDK / JDK、构建输出、APK / 压缩包、音频、原始设备证据和日志。源码、必要 Wrapper、公共证书和整理后的验证报告保留。全历史令牌 / 私钥 / 设备标识 / 本机路径扫描通过；这是一组明确规则的扫描，不宣称能证明不存在任何未知形式的敏感内容。

上游固定提交 MIT 原文已逐字比对；65 项 Release 运行时构件的 POM 许可及随附 NOTICE 已整理，仓库与 APK 内声明一致；不把官网音频、用户录音、商标或远程依赖概括授权为客户端 MIT。
