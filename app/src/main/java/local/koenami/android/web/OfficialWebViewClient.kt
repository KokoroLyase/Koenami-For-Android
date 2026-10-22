package local.koenami.android.web

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*

class OfficialWebViewClient(
    private val pageStarted: (String?) -> Unit,
    private val pageCommitted: (String?) -> Unit,
    private val pageFinished: (String?) -> Unit,
    private val external: (String) -> Unit,
    private val status: (String) -> Unit,
    private val rendererGone: () -> Unit
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (!request.isForMainFrame) return !OriginPolicy.officialOrigin(url)
        if (OriginPolicy.training(url)) return false
        if (request.hasGesture() && !request.isRedirect && OriginPolicy.external(url)) external(url)
        else status("已拦截未认可导航")
        return true
    }
    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        pageStarted(url)
        if (!OriginPolicy.training(url)) { view.stopLoading(); status("主文档地址不受信任，请重新打开普通话官网") }
    }
    override fun onPageCommitVisible(view: WebView, url: String?) { pageCommitted(url) }
    override fun onPageFinished(view: WebView, url: String?) { pageFinished(url) }
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel(); status("证书错误，连接已取消；请检查网络和系统时间")
    }
    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) status("官网加载失败；已有数据保留，请通过应用菜单重试")
    }
    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
        if (request.isForMainFrame || response.statusCode in listOf(403, 429, 500, 502, 503)) {
            status(if (response.statusCode == 429) "官网繁忙（429），请遵循官网提示稍后重试" else "官网 HTTP ${response.statusCode}；已有数据保留")
        }
    }
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        rendererGone()
        return true
    }
}
