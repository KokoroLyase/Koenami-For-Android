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
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var root: LinearLayout
    private lateinit var message: TextView
    private lateinit var enhancement: EnhancementController
    private lateinit var microphone: MicrophonePermissionCoordinator
    private lateinit var chooser: FileChooserCoordinator
    private lateinit var exports: ExportCoordinator
    private var foreground = false
    private var epoch = 0
    private var session = ""
    private var committed = false
    private var safe = false
    private val handler = Handler(Looper.getMainLooper())
    private var stopTimeout: Runnable? = null
    private fun status(value: String) { message.text = value }
    private fun trusted() = foreground && committed && OriginPolicy.training(web.url)
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(24, 24, 32)) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(TextView(this).apply { text = "Koenami · M0"; setTextColor(Color.WHITE); textSize = 17f; setPadding(16, 12, 4, 12) }, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(Button(this).apply { text = "恢复 / 工具"; contentDescription = "应用恢复及验证工具"; setOnClickListener { menu() } })
        root.addView(bar)
        message = TextView(this).apply { textSize = 12f; setTextColor(Color.LTGRAY); setPadding(16, 0, 16, 6); text = "M0 技术验证；兼容性与保存尚未确认" }
        root.addView(message); setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        microphone = MicrophonePermissionCoordinator(this, ::trusted, { epoch }, ::status)
        chooser = FileChooserCoordinator(this, { committed && OriginPolicy.training(web.url) }, { foreground })
        exports = ExportCoordinator(this, ::status)
        createWebView()
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
        web = WebView(this)
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            allowFileAccess = false; allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true; setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            safeBrowsingEnabled = true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return !OriginPolicy.training(request.url.toString())
                val url = request.url.toString()
                if (OriginPolicy.training(url)) return false
                if (request.hasGesture() && !request.isRedirect && OriginPolicy.external(url)) openExternal(url)
                else status("已拦截未认可导航")
                return true
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                epoch++; committed = false; session = UUID.randomUUID().toString()
                microphone.invalidate(); chooser.cancel(); exports.setSession(session)
                if (!OriginPolicy.training(url)) { view.stopLoading(); status("主文档地址不受信任，请从恢复菜单重新打开官网") }
            }
            override fun onPageCommitVisible(view: WebView, url: String?) {
                committed = OriginPolicy.training(url)
                if (committed) enhancement.bind(session)
            }
            override fun onPageFinished(view: WebView, url: String?) {
                if (committed && OriginPolicy.training(url)) enhancement.bind(session)
            }
            override fun onReceivedSslError(view: WebView, request: SslErrorHandler, error: SslError) {
                request.cancel(); status("证书错误，已取消连接；请检查网络和时间")
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) status("官网加载失败；数据保留，可从工具菜单重试")
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame || response.statusCode in listOf(403, 429, 500, 502, 503)) status("官网 HTTP ${response.statusCode}；请稍后重试")
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                committed = false; epoch++; microphone.invalidate(); chooser.cancel(); exports.setSession("")
                handler.post { rebuild(); status("网页进程已终止，已重新打开；未提交的录音可能丢失") }
                return true
            }
        }
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
        enhancement = EnhancementController(web).apply { this.safe = this@MainActivity.safe }
        if (!safe && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "KoenamiFiles", setOf(OriginPolicy.ORIGIN)) { _, msg, origin, mainFrame, proxy ->
                if (mainFrame && committed && OriginPolicy.training(web.url) && OriginPolicy.officialOrigin(origin.toString())) {
                    val data = msg.data
                    if (data != null && data.length <= 68000) exports.receive(data, session) { response -> proxy.postMessage(response) }
                }
            }
            enhancement.register()
        } else status("纯网页模式或 WebMessage 不支持：本地文件导出适配不可用")
        web.setDownloadListener { _, _, _, _, _ -> status("此下载未被安全文件适配接管；请保留录音并检查兼容性") }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        web.loadUrl(OriginPolicy.HOME)
    }
    private fun rebuild() {
        stopTimeout?.let(handler::removeCallbacks); stopTimeout = null
        microphone.invalidate(); chooser.cancel(); exports.setSession("")
        enhancement.dispose(); root.removeView(web); web.destroy(); createWebView()
    }
    private fun openExternal(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
        catch (_: Exception) { status("没有可用的系统浏览器") }
    }
    private fun menu() {
        AlertDialog.Builder(this).setTitle("M0 验证 / 恢复").setItems(arrayOf(
            "原始官方界面（保留文件适配）", "CSS 注入实验", "纯网页安全模式 / 恢复系统适配", "重新打开普通话官网",
            "兼容性诊断", "在系统浏览器打开官网", "隐私、备份与限制"
        )) { _, which -> when (which) {
            0 -> { enhancement.setVisual(false); status("原始官方界面；系统适配保留") }
            1 -> { if (safe) status("请先恢复系统适配") else { enhancement.setVisual(true); status("M0 CSS 实验；尚未完成正式 UI 验收") } }
            2 -> { safe = !safe; rebuild() }
            3 -> { web.loadUrl(OriginPolicy.HOME) }
            4 -> diagnostics()
            5 -> openExternal(OriginPolicy.HOME)
            6 -> AlertDialog.Builder(this).setTitle("非官方个人验证客户端").setMessage("训练内容、Worker/WASM、API 与官网原有统计由官网提供。应用不新增统计。实时模式不新增历史。普通录音后台保存尚在验证。卸载或清除数据会丢失本地历史；官网 ZIP 并非完整数据库恢复包。PoC 数据与正式包隔离。不要将本原型作为唯一音频副本。").setPositiveButton("知道了", null).show()
        } }.show()
    }
    private fun diagnostics() {
        val provider = WebViewCompat.getCurrentWebViewPackage(this)
        web.evaluateJavascript("JSON.stringify(window.__koenamiAndroid?.diagnostics() || {safe:true})") { value ->
            AlertDialog.Builder(this).setTitle("本地 M0 诊断").setMessage("Android ${android.os.Build.VERSION.RELEASE} / API ${android.os.Build.VERSION.SDK_INT}\n${provider?.packageName} ${provider?.versionName}\n主文档受信任：${OriginPolicy.training(web.url)}\n适配器：0.0.1-m0\n$value\n最近暂存文件 SHA-256：${exports.lastDigest}\n能力存在不等于音频测试通过").setPositiveButton("关闭", null).show()
        }
    }
    private fun exitPrompt() {
        AlertDialog.Builder(this).setMessage("结束当前会话并退出？如正在普通录音，将请求官网停止；保存结果需再次打开历史确认。").setPositiveButton("结束并退出") { _, _ ->
            requestStop(); handler.postDelayed({ finish() }, 2800)
        }.setNegativeButton("继续使用", null).show()
    }
    private fun requestStop() {
        if (safe || !committed) { if (!foreground) { rebuild(); status("安全模式离开前台，网页会话已重建；未提交录音可能丢失") }; return }
        val stoppingWeb = web
        val stoppingEpoch = epoch
        web.evaluateJavascript("window.__koenamiAndroid?.stopCapture()", null)
        stopTimeout?.let(handler::removeCallbacks)
        stopTimeout = Runnable {
            if (web !== stoppingWeb || epoch != stoppingEpoch) return@Runnable
            var responded = false
            handler.postDelayed({
                if (!responded && web === stoppingWeb && epoch == stoppingEpoch) {
                    rebuild(); status("网页未响应停止核查，已释放会话；本次未确认保存")
                }
            }, 1000)
            web.evaluateJavascript("JSON.stringify(window.__koenamiAndroid?.captureState() || {unknown:true})") { result ->
                responded = true
                if (result.contains("true")) { rebuild(); status("未确认采集结束，已释放网页会话；本次未确认保存") }
                else status("已请求官网停止；请在历史中确认是否保存（实时模式不新增历史）")
            }
        }
        handler.postDelayed(stopTimeout!!, 2500)
    }
    override fun onResume() {
        super.onResume(); foreground = true
        if (::web.isInitialized) {
            web.onResume()
            if (committed && !safe) web.evaluateJavascript("window.__koenamiAndroid?.foreground()", null)
        }
    }
    override fun onPause() {
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
        handler.removeCallbacksAndMessages(null); microphone.invalidate(); chooser.cancel()
        enhancement.dispose(); root.removeView(web); web.destroy(); exports.close(); super.onDestroy()
    }
}
