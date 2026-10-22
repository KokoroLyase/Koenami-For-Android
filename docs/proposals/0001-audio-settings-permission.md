# M0 权限边界变更提案（已批准）

日期：2026-10-22，Asia/Singapore。

## 观察

- 真机：2210132C，Android 16 / API 36，WebView 155.0.8059.43。
- 当前 PoC 只声明规划指定的三项权限。
- 系统 RECORD_AUDIO 授权成功；AppOps 为 foreground，测试时 PoC 为 topResumedActivity。
- 官网原始界面普通录音失败：NotReadableError / Could not start audio source。
- Debug 中用 audio:true 做一次不保留数据的能力探测，同样失败。
- 本应用进程 Chromium 日志：Unable to select communication device!

## 官方源码证据

取得于：https://chromium.googlesource.com/chromium/src/+/refs/heads/main/media/base/android/java/src/org/chromium/media/AudioManagerAndroid.java

本地只读证据：evidence/AudioManagerAndroid.java，setCommunicationDevice（约 346–367 行）。
该实现检查 MODIFY_AUDIO_SETTINGS 与 RECORD_AUDIO，缺少任一项直接返回 false。
这是 Chromium main 的源码证据；未将其错误宣称为手机二进制的逐字同一版本。

Android 权限说明：https://developer.android.com/reference/android/Manifest.permission#MODIFY_AUDIO_SETTINGS

## 具体提议

仅向 AndroidManifest.xml 增加：

```xml
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

允许 WebView 自己选择系统音频通信设备。应用不调用原生录音器，不修改官方采样、重采样或声学算法，不添加服务器，也不原生强制切换到特定扬声器/麦克风。

这是一项系统音频设置权限，属于 normal 权限，不能将其描述成完全没有系统影响。尚需 A/B 真机验证才能确认在此设备上是否充分解决问题。

用户于本聊天明确答复：“批准增加并继续真机对照验证”。实际清单现已增加此项权限，准备 A/B 验证。
