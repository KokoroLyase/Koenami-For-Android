# 应用更新

1.1 开始提供原生更新功能。v1.0 本身没有更新器，升级到 1.1 需要首次手动覆盖安装同签名 APK。不要卸载，否则会删除训练历史。

## 使用

- 右上角三点菜单 → **检查更新**：立即查询本仓库最新正式 Release；有新版时展示版本、更新说明、大小和来源，可下载、稍后或打开 Release 网页。
- **应用更新设置**：默认开启每日自动检查，可随时关闭。首次进入和以后进入应用时补查到期任务，后台由 WorkManager 每 24 小时调度。手动检查不受每日间隔限制，但仍遵守 GitHub 限流。
- 自动发现更新时，前台会等待录音 / 实时采集结束再提示；后台使用“应用版本更新”通知。Android 13 起可从设置页的“后台通知设置”允许通知。拒绝通知不影响检查，下次进入前台提示。
- 点击“稍后”后同一版本暂停前台提醒 24 小时；手动检查仍可查看。后台对同一版本最多发送一次通知。
- 用户选择下载后才使用网络下载 APK，可能消耗移动流量；支持取消。下载中留在后台可继续至当前进程被系统回收，回到前台才邀请安装。进程被终止后不保证续传，可以重新检查下载。
- 校验通过后，由用户选择继续安装；系统可能要求开启“允许 Koenami 安装应用”，随后由 Android 安装界面再次确认。不会静默安装或自动卸载。

系统省电、休眠、厂商后台限制、断网和应用强行停止会使检查延迟，无法保证每天固定时刻弹窗。再次进入应用会补查已到期任务；不使用精确闹钟，也不要求关闭系统电池保护。后台请求失败不弹错误窗口，可手动重试。

## 来源及校验

固定公共 API：`https://api.github.com/repos/KokoroLyase/Koenami-For-Android/releases/latest`。不接收网页、Intent 或用户输入指定的更新源，不嵌入 GitHub Token。跳过草稿和预发布版，只接受 `v1.1` / `1.1.0` 等数字版本，按数值比较，不降级。

Release 必须具备唯一的通用 `.apk` 资产、正整数大小（最多 150 MiB）和 GitHub API 提供的 `sha256:` 摘要。若摘要缺失或资源格式异常，拒绝自动安装；可从可信 Release 页面自行查看原因。初始网页和 APK URL 必须属于指定仓库，HTTPS 下载跳转只允许 GitHub 及指定资产 CDN 主机。

下载后比较实际大小及 SHA-256，读取 APK 包名、versionCode、versionName 和签名。必须满足：与当前包名相同、versionCode 严格增加、versionName 与标签一致、当前签名集合相同。安装前重新检查包信息，再由系统验证 APK。现阶段不接受签名轮换；更换发布签名前须另外设计兼容迁移。Debug 包安装身份和证书不同，不能从正式源直接更新。

摘要保证传输完整性，签名保证安装身份，两者不是同一概念。GitHub 信息和文件均来自 HTTPS；摘要不是独立第三方的认证。更新不修改官方算法、音频链路、WebView 数据库或许可归属。

## 发布维护

每次保持原发布密钥，递增 versionCode，使用数字版本标签（如 `v1.1`），上传唯一通用 APK。发布顺序应先上传资产，再将草稿公开为正式 Release；确保 API 中 APK 的 `state=uploaded` 且 `digest=sha256:…`。继续附带 `SHA256SUMS.txt` 和公开签名证书供人工核验。预发布版不会被该更新器选中。

默认保留手机审核后才公开发布的流程；1.1 依据用户明确授权，在开发测试通过后直接发布，不宣称已完成该版本真机验证。1.2 已在手机验证并由用户确认定版和公开发布。新增运行时 AndroidX WorkManager 依赖的 Apache 2.0 声明已纳入应用内和仓库的完整第三方声明。

参考：[GitHub latest Release API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)、[Android 持久后台任务](https://developer.android.com/develop/background-work/background-tasks/persistent-work)、[通知权限](https://developer.android.com/develop/ui/views/notifications/notification-permission)、[应用安装来源授权](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls())。
