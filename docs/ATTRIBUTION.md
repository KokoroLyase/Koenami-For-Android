# 许可、署名与资源来源

客户端 Koenami for Android（显示名称 Koenami）由 Isomeria / [KokoroLyase](https://github.com/KokoroLyase) 维护。新增代码、文档和可授权素材采用根目录 MIT License，保留原版权和许可文字。

## 上游 Koenami

- 官网：https://koe.transnavi.jp/zh-CN/
- 源码：https://github.com/transnavi/koenami
- 审阅源码基线：`870b7282122e8c290955f17a32898ccb74ee6fcb`；线上部署版本未确认为该提交。
- 原署名：Copyright (c) 2026 Koenami contributors；MIT 全文保留于 `LICENSES/MIT-Koenami.txt` 和 APK 声明中。

客户端使用官网页面及其公共控件，训练、分析和内容由官网提供。不包含官网完整前端、WASM 二进制、参考录音或语料数据。网页依赖、语料与音频可能有各自条款，应以官网和原始来源为准，不能一概标为客户端 MIT。用户录音仍属于用户及相关权利人。

项目名称用于指明兼容网站，不主张上游商标权，不表示合作、隶属或背书。图标未复制上游标志。

## 第三方与图标

`docs/runtime-dependencies.json` 来自实际 Gradle 运行时依赖；`THIRD_PARTY_NOTICES.txt` 包含依赖坐标、POM 许可、Apache-2.0 全文及构件随附的 LICENSE / NOTICE，APK 内包含相同声明并保留原署名。测试库和构建工具不被列为 APK 运行时依赖。

Gradle Wrapper 按 Apache-2.0 分发，见脚本头及 `LICENSES/Apache-2.0.txt`。Android SDK、JDK、下载工具不随仓库分发，适用各提供方条款。

图标使用 OpenAI 内置 imagegen 生成，无第三方图片输入，单色主题版提供 VectorDrawable。仅在项目方有权授权的范围内随 MIT 提供，不主张 AI 图像的排他权利。设计与提示词见 `design/ICON.md`。

## 许可证与证书的区别

1. 开源许可证规定代码或素材使用条件，见 LICENSE 与第三方声明。
2. APK 签名证书标识发布密钥和更新身份。公开证书可以分发，私钥及密码不能公开，不表示上游认证。
3. 官网 HTTPS 证书用于连接认证，交由系统校验；错误时取消连接，不允许绕过。

依据：[上游许可](https://github.com/transnavi/koenami/blob/870b7282122e8c290955f17a32898ccb74ee6fcb/LICENSE)、[Android 签名说明](https://developer.android.com/studio/publish/app-signing)。
