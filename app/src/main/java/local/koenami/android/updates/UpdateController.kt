package local.koenami.android.updates

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.CheckBox
import android.widget.TextView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import local.koenami.android.BuildConfig
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Native-only update UI; downloaded data never enters the WebView or its bridge. */
class UpdateController(
    private val activity: AppCompatActivity,
    private val idle: ((Boolean) -> Unit) -> Unit,
    private val stopBefore: (() -> Unit) -> Unit
) {
    private val store = UpdateStore(activity)
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var alive = true
    private var resumed = false
    private var busy = false
    private var dialog: AlertDialog? = null
    private var downloadCancelled = AtomicBoolean(false)
    private var installFile: File? = null
    private val permission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        Toast.makeText(activity, if (allowed) "已允许后台更新通知" else "仍会每天检查；下次打开应用时提示更新", Toast.LENGTH_LONG).show()
    }
    private val installerPermission = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val file = installFile
        installFile = null
        if (file != null && activity.packageManager.canRequestPackageInstalls()) install(file)
        else if (file != null) message("尚未允许安装应用，可稍后重新尝试")
    }
    private val poll = object : Runnable {
        override fun run() {
            if (!resumed || !alive) return
            val release = store.candidate()
            if (store.automatic && !busy && dialog == null && release != null &&
                UpdatePolicy.newer(release.tag, BuildConfig.VERSION_NAME) && !store.snoozed(release.tag)) {
                idle { ready -> if (ready && resumed && alive && !busy && dialog == null && store.automatic) offer(release) }
            }
            handler.postDelayed(this, 15_000)
        }
    }
    init {
        val now = System.currentTimeMillis()
        File(activity.cacheDir, "updates").listFiles()?.filter { now - it.lastModified() > UpdatePolicy.DAY }?.forEach { it.delete() }
    }
    fun resume() {
        resumed = true; UpdateSchedule.visible = true
        UpdateSchedule.configure(activity)
        handler.removeCallbacks(poll); handler.postDelayed(poll, 2000)
    }
    fun pause() { resumed = false; UpdateSchedule.visible = false; handler.removeCallbacks(poll) }
    fun close() { alive = false; pause(); downloadCancelled.set(true); io.shutdownNow(); handler.removeCallbacksAndMessages(null); dialog?.dismiss(); dialog = null }
    fun manual() {
        if (busy) { message("正在检查或下载，请稍候"); return }
        busy = true
        dialog = AlertDialog.Builder(activity).setTitle("检查更新").setMessage("正在查询 GitHub 最新正式 Release…").setCancelable(false).show()
        io.execute {
            val result = runCatching { UpdateClient(activity.applicationContext).check(true) }
            handler.post {
                if (!alive) return@post
                busy = false; dialog?.dismiss(); dialog = null
                if (!resumed) return@post
                result.fold(onSuccess = {
                    if (it.release != null) offer(it.release)
                    else message("当前版本 ${BuildConfig.VERSION_NAME}，暂无可用更新")
                }, onFailure = { message(failure(it, "检查失败，请检查网络后重试")) })
            }
        }
    }
    fun settings() {
        val checked = if (store.lastAttempt == 0L) "尚未检查" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(store.lastAttempt))
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt(); setPadding(padding, 0, padding, 0)
            addView(CheckBox(activity).apply {
                text = "每天自动检查更新"; isChecked = store.automatic
                setOnCheckedChangeListener { _, enabled -> store.automatic = enabled; UpdateSchedule.configure(activity) }
            })
            addView(TextView(activity).apply { text = "来源：GitHub / ${UpdatePolicy.REPOSITORY}\n最近尝试：$checked\n\n后台检查由系统调度，可能延迟。允许通知后可在后台提醒；未允许时，下次进入应用提示。只在确认后下载，安装由 Android 再次确认。" })
        }
        dialog = AlertDialog.Builder(activity).setTitle("应用更新 · ${BuildConfig.VERSION_NAME}").setView(content)
            .setPositiveButton("完成", null).setNeutralButton("后台通知设置") { _, _ ->
                if (Build.VERSION.SDK_INT >= 33 && !store.notificationAsked && !activity.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
                    store.notificationAsked = true; permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else try { activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)) }
                catch (_: Exception) { message("无法打开通知设置，请在系统应用设置中开启") }
            }.create().apply { setOnDismissListener { if (dialog === this) dialog = null }; show() }
    }
    private fun offer(release: GitHubRelease) {
        activity.getSystemService(NotificationManager::class.java).cancel(UpdateSchedule.NOTIFICATION)
        val notes = release.notes.ifBlank { "此版本未提供更新说明。" }.take(1200) + if (release.notes.length > 1200) "\n…完整说明见 Release。" else ""
        dialog = AlertDialog.Builder(activity).setTitle("发现新版本 ${release.tag}")
            .setMessage("当前版本 ${BuildConfig.VERSION_NAME}\n安装包约 ${(release.size + 1024 * 1024 - 1) / (1024 * 1024)} MB，可使用移动数据。\n来源：GitHub / ${UpdatePolicy.REPOSITORY}\n\n$notes\n\n将校验 SHA-256、包名、版本及签名。覆盖安装保留现有数据，请勿卸载旧版。")
            .setPositiveButton("下载更新") { _, _ -> store.snooze(release.tag); stopBefore { download(release) } }
            .setNegativeButton("稍后") { _, _ -> store.snooze(release.tag) }
            .setNeutralButton("查看 Release") { _, _ ->
                store.snooze(release.tag)
                try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.page))) }
                catch (_: Exception) { message("没有可用的浏览器") }
            }.create().apply {
                setOnCancelListener { store.snooze(release.tag) }
                setOnDismissListener { if (dialog === this) dialog = null }
                show()
            }
    }
    private fun download(release: GitHubRelease) {
        if (!alive || !resumed || busy) return
        busy = true; downloadCancelled = AtomicBoolean(false)
        val cancelled = downloadCancelled
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        dialog = AlertDialog.Builder(activity).setTitle("正在下载 ${release.tag}").setView(bar).setMessage("下载后会校验安装包。切到后台仍可完成下载，回到应用后再安装。")
            .setNegativeButton("取消") { _, _ -> cancelled.set(true) }.setCancelable(false).show()
        io.execute {
            var lastProgress = -1
            val result = runCatching { UpdateClient(activity.applicationContext).download(release, { cancelled.get() || !alive }) { percent ->
                if (percent != lastProgress) { lastProgress = percent; handler.post { if (alive) bar.progress = percent } }
            } }
            handler.post {
                if (!alive) { result.getOrNull()?.delete(); return@post }
                busy = false; dialog?.dismiss(); dialog = null
                result.fold(onSuccess = { file ->
                    if (cancelled.get()) { file.delete(); return@fold }
                    if (resumed) confirmInstall(file) else {
                        installFile = file
                        // No background activity launch. A one-shot foreground callback offers installation.
                        handler.post(waitToInstall)
                    }
                }, onFailure = { if (!cancelled.get() && resumed) message(failure(it, "下载失败，请稍后重试")) })
            }
        }
    }
    private val waitToInstall = object : Runnable {
        override fun run() {
            if (!alive) return
            if (resumed) { val file = installFile; installFile = null; if (file != null) confirmInstall(file) }
            else handler.postDelayed(this, 1000)
        }
    }
    private fun confirmInstall(file: File) {
        dialog = AlertDialog.Builder(activity).setTitle("安装包校验通过").setMessage("即将打开 Android 安装确认。如系统要求，请仅为 Koenami 允许安装应用。覆盖安装保留历史；无需卸载。")
            .setPositiveButton("继续安装") { _, _ -> stopBefore {
                if (activity.packageManager.canRequestPackageInstalls()) install(file)
                else {
                    installFile = file
                    try { installerPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))) }
                    catch (_: Exception) { installFile = null; message("无法打开安装授权，请在系统设置中允许 Koenami 安装应用") }
                }
            } }.setNegativeButton("稍后", null).create().apply { setOnDismissListener { if (dialog === this) dialog = null }; show() }
    }
    private fun install(file: File) {
        try {
            UpdateClient(activity).verifyPackage(file)
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
            activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Exception) { message("无法安装：文件可能已清理、版本/签名不符，或系统没有可用安装器。请重新检查更新。") }
    }
    private fun failure(error: Throwable, fallback: String): String =
        if (error is IllegalArgumentException || error is java.io.IOException && error.message?.startsWith("GitHub") == true || error.message == "仓库尚无可用的正式 Release") error.message ?: fallback else fallback
    private fun message(text: String) { if (alive && resumed) dialog = AlertDialog.Builder(activity).setTitle("应用更新").setMessage(text).setPositiveButton("知道了", null).create().apply { setOnDismissListener { if (dialog === this) dialog = null }; show() } }
}
