package local.koenami.android

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import local.koenami.android.enhancement.EnhancementController
import local.koenami.android.files.ExportCoordinator
import local.koenami.android.files.FileChooserCoordinator
import local.koenami.android.permissions.MicrophonePermissionCoordinator
import local.koenami.android.web.OriginPolicy
import local.koenami.android.web.WebViewFactory
import local.koenami.android.web.OfficialWebViewClient
import local.koenami.android.settings.AppPreferences
import local.koenami.android.bridge.TrustedMessageBridge
import java.util.UUID
import local.koenami.android.lifecycle.WebSessionCoordinator
import com.google.android.material.color.MaterialColors
import com.google.android.material.button.MaterialButton
import local.koenami.android.updates.UpdateController

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    private lateinit var message: TextView
    private lateinit var enhancement: EnhancementController
    private lateinit var microphone: MicrophonePermissionCoordinator
    private lateinit var chooser: FileChooserCoordinator
    private lateinit var exports: ExportCoordinator
    private lateinit var preferences: AppPreferences
    private lateinit var updates: UpdateController
    private var foreground = false
    private var epoch = 0
    private var session = ""
    private var committed = false
    private var safe = false
    private var bridgeAvailable = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var webSession: WebSessionCoordinator
    private fun status(value: String) {
        message.text = value
        if (foreground && listOf("失败", "不足", "不支持", "拒绝", "丢失", "未确认").any(value::contains))
            Toast.makeText(this,value,Toast.LENGTH_LONG).show()
    }
    private fun trusted() = foreground && committed && OriginPolicy.training(web.url)
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        preferences = AppPreferences(this)
        safe = preferences.safeMode
        root = FrameLayout(this).apply { keepScreenOn = true; setBackgroundColor(MaterialColors.getColor(this,com.google.android.material.R.attr.colorSurface)) }
        message = TextView(this).apply { textSize = 12f; setTextColor(MaterialColors.getColor(root,com.google.android.material.R.attr.colorOnSurfaceVariant)); setPadding(16, 0, 16, 6); text = "正在连接普通话官网…" }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        microphone = MicrophonePermissionCoordinator(this, ::trusted, { epoch }, ::status) { if (committed) web.evaluateJavascript("window.__koenamiAndroid?.permissionGranted()", null) }
        chooser = FileChooserCoordinator(this, { committed && OriginPolicy.training(web.url) }, { foreground })
        exports = ExportCoordinator(this, ::status)
        createWebView()
        updates = UpdateController(this, ::whenIdle, ::stopBefore)
        val menuSize = (48 * resources.displayMetrics.density).toInt()
        root.addView(androidx.appcompat.widget.AppCompatImageButton(this).apply {
            setImageResource(R.drawable.ic_more_vert)
            imageTintList = android.content.res.ColorStateList.valueOf(MaterialColors.getColor(root,com.google.android.material.R.attr.colorOnSurface))
            background = android.util.TypedValue().let { value -> theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,value,true); androidx.appcompat.content.res.AppCompatResources.getDrawable(this@MainActivity,value.resourceId) }
            contentDescription = "应用菜单：检查更新、地图全屏、界面模式及恢复"
            setOnClickListener { menu() }
        }, FrameLayout.LayoutParams(menuSize,(36 * resources.displayMetrics.density).toInt(),android.view.Gravity.TOP or android.view.Gravity.END))
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val insets = ViewCompat.getRootWindowInsets(root)
                if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
                    androidx.core.view.WindowInsetsControllerCompat(window, root).hide(WindowInsetsCompat.Type.ime()); return
                }
                if (safe || !committed) { exitPrompt(); return }
                web.evaluateJavascript("window.__koenamiAndroid?.back() || false") { value ->
                    if (value != "true") exitPrompt()
                }
            }
        })
    }
    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        committed = false
        web = WebViewFactory.create(this)
        val created = web
        webSession = WebSessionCoordinator(created,handler,{ epoch },{ web === created },{
            rebuild();status("未确认采集结束，已释放网页会话；本次保存需要核对")
        },::status)
        web.webViewClient = OfficialWebViewClient(
            pageStarted = {
                epoch++; committed = false; session = UUID.randomUUID().toString()
                microphone.invalidate(); chooser.cancel(); exports.setSession(session)
            },
            pageCommitted = { url ->
                committed = OriginPolicy.training(url)
                if (committed) {
                    enhancement.bind(session)
                    status(if (safe) "纯网页安全模式" else if (bridgeAvailable) "已连接普通话官网" else "WebView 能力不足：请先更新 Android System WebView / Chrome，再重新打开官网")
                }
            },
            pageFinished = { url -> if (committed && OriginPolicy.training(url)) enhancement.bind(session) },
            external = ::openExternal, status = ::status,
            rendererGone = {
                committed = false; epoch++; microphone.invalidate(); chooser.cancel(); exports.setSession("")
                handler.post { rebuild(); status("网页进程已终止；未提交录音可能丢失") }
            }
        )
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) { microphone.request(request) }
            override fun onPermissionRequestCanceled(request: PermissionRequest) { microphone.cancel(request) }
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) { callback.invoke(origin, false, false) }
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean = chooser.show(callback, params)
            override fun onJsAlert(view: WebView, url: String, text: String, result: JsResult): Boolean {
                if (!trusted()) { result.cancel(); return true }
                AlertDialog.Builder(this@MainActivity).setMessage(text).setPositiveButton("确定") { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }.show(); return true
            }
            override fun onJsConfirm(view: WebView, url: String, text: String, result: JsResult): Boolean {
                if (!trusted()) { result.cancel(); return true }
                AlertDialog.Builder(this@MainActivity).setMessage(text).setPositiveButton("确定") { _, _ -> result.confirm() }
                    .setNegativeButton("取消") { _, _ -> result.cancel() }.setOnCancelListener { result.cancel() }.show(); return true
            }
        }
        enhancement = EnhancementController(web).apply { this.safe = this@MainActivity.safe; optimized = preferences.optimized }
        bridgeAvailable = !safe && TrustedMessageBridge(web, { committed && OriginPolicy.training(web.url) }, { session }, exports::receive).register()
        if (bridgeAvailable) {
            enhancement.register()
        } else status("纯网页模式或 WebMessage 不支持：本地文件导出适配不可用")
        web.setDownloadListener { _, _, _, _, _ -> status("此下载未被安全文件适配接管；请保留录音并检查兼容性") }
        root.addView(web, 0, FrameLayout.LayoutParams(-1, -1))
        web.loadUrl(OriginPolicy.HOME)
    }
    private fun rebuild() {
        webSession.cancel()
        microphone.invalidate(); chooser.cancel(); exports.setSession("")
        enhancement.dispose(); root.removeView(web); web.destroy(); createWebView()
    }
    private fun openExternal(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
        catch (_: Exception) { status("没有可用的系统浏览器") }
    }
    private fun menu() {
        AlertDialog.Builder(this).setTitle("应用设置 / 恢复").setItems(arrayOf(
            "检查更新", "应用更新设置（每天自动检查）", "地图全屏 / 退出全屏",
            "原始官方界面（保留文件适配）", "Android 优化界面", "纯网页安全模式 / 恢复系统适配", "重新打开普通话官网",
            "兼容性诊断", "在系统浏览器打开官网", "隐私、备份与限制", "开源许可与署名", "更新 Android System WebView"
        )) { _, which -> when (which - 3) {
            0 -> { preferences.optimized = false; enhancement.setVisual(false); status("原始官方界面；系统适配保留") }
            1 -> { if (safe) status("请先恢复系统适配") else { preferences.optimized = true; enhancement.setVisual(true); status("Android 优化界面") } }
            2 -> stopBefore { safe = !safe; preferences.safeMode = safe; rebuild() }
            3 -> stopBefore { web.loadUrl(OriginPolicy.HOME) }
            4 -> diagnostics()
            5 -> openExternal(OriginPolicy.HOME)
            6 -> AlertDialog.Builder(this).setTitle("非官方开源客户端").setMessage("训练内容、Worker/WASM、API 与官网原有统计由官网提供。应用不新增统计。实时模式不新增历史。进入后台请求官网停止普通录音并保存；未提交录音遇到异常终止可能丢失。卸载或清除数据会丢失本地历史；官网 ZIP 并非完整数据库恢复包。建议定期将重要录音导出到应用之外。").setPositiveButton("知道了", null).show()
            7 -> licenses()
            8 -> openExternal("https://play.google.com/store/apps/details?id=com.google.android.webview")
            -3 -> updates.manual()
            -2 -> updates.settings()
            -1 -> if (safe || !preferences.optimized) status("当前模式不支持地图全屏，请先启用 Android 优化界面") else web.evaluateJavascript("document.getElementById('koenami-android-map-full')?.click()",null)
        } }.show()
    }
    private fun licenses() {
        val body = assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() }
        val text = TextView(this).apply { this.text = body; textSize = 13f; setPadding(24,16,24,16); setTextIsSelectable(true) }
        AlertDialog.Builder(this).setTitle("Koenami ${BuildConfig.VERSION_NAME} · 开源许可").setView(ScrollView(this).apply { addView(text) }).setPositiveButton("关闭",null).show()
    }
    private fun diagnostics() {
        val provider = WebViewCompat.getCurrentWebViewPackage(this)
        web.evaluateJavascript("JSON.stringify(window.__koenamiAndroid?.diagnostics() || {safe:true})") { value ->
            AlertDialog.Builder(this).setTitle("本地兼容性诊断").setMessage("Android ${android.os.Build.VERSION.RELEASE} / API ${android.os.Build.VERSION.SDK_INT}\n${provider?.packageName} ${provider?.versionName}\n${message.text}\n主文档受信任：${OriginPolicy.training(web.url)}\n适配器：1.0\n$value\n最近暂存文件 SHA-256：${exports.lastDigest}\n能力存在不等于音频测试通过").setPositiveButton("关闭", null).show()
        }
    }
    private fun stopBefore(action: () -> Unit) {
        if (safe || !committed) { action();return }
        web.evaluateJavascript("JSON.stringify(window.__koenamiAndroid?.captureState() || {unknown:true})") { value ->
            val active=try {
                val state=org.json.JSONObject(org.json.JSONTokener(value).nextValue() as String)
                listOf("record","live","starting","unknown").any{state.optBoolean(it)}
            }catch(_:Exception){true}
            if(active) AlertDialog.Builder(this).setMessage("此操作将结束当前采集。是否继续？保存结果需在历史中核对。")
                .setPositiveButton("结束并继续"){_,_->requestStop();handler.postDelayed({if(!isFinishing)action()},4000)}
                .setNegativeButton("取消",null).show()
            else action()
        }
    }
    private fun whenIdle(callback: (Boolean) -> Unit) {
        if (!foreground || microphone.promptInFlight || !hasWindowFocus()) { callback(false); return }
        if (safe || !committed) { callback(true); return }
        web.evaluateJavascript("JSON.stringify(window.__koenamiAndroid?.captureState() || {unknown:true})") { value ->
            val active = try {
                val state = org.json.JSONObject(org.json.JSONTokener(value).nextValue() as String)
                listOf("record", "live", "starting", "unknown", "settling").any { state.optBoolean(it) }
            } catch (_: Exception) { true }
            callback(foreground && !active && hasWindowFocus())
        }
    }
    private fun exitPrompt() {
        AlertDialog.Builder(this).setMessage("结束当前会话并退出？如正在普通录音，将请求官网停止；保存结果需再次打开历史确认。").setPositiveButton("结束并退出") { _, _ ->
            requestStop(); handler.postDelayed({ finish() }, 2800)
        }.setNegativeButton("继续使用", null).show()
    }
    private fun requestStop() {
        if (isFinishing || isDestroyed) return
        webSession.stop(!safe && committed)
    }
    override fun onResume() {
        super.onResume(); foreground = true
        if (::updates.isInitialized) updates.resume()
        if (::web.isInitialized) {
            web.onResume()
            exports.resume()
            if (committed && !safe) web.evaluateJavascript("window.__koenamiAndroid?.foreground()", null)
        }
    }
    override fun onPause() {
        if (::updates.isInitialized) updates.pause()
        // The system permission overlay pauses this activity without putting it in background.
        // Actual background during that overlay is handled by onStop and invalidates the request.
        if (!microphone.promptInFlight) { foreground = false; microphone.invalidate(); requestStop() }
        super.onPause()
        // Do not pause timers or destroy immediately: official stop/save is asynchronous.
    }
    override fun onStop() {
        foreground = false; microphone.invalidate(); requestStop()
        super.onStop()
    }
    override fun onDestroy() {
        if (::updates.isInitialized) updates.close()
        webSession.cancel(); handler.removeCallbacksAndMessages(null); microphone.invalidate(); chooser.cancel()
        enhancement.dispose(); root.removeView(web); web.destroy(); exports.close(); super.onDestroy()
    }
}
