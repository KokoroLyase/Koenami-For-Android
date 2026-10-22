package local.koenami.android.bridge

import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import local.koenami.android.web.OriginPolicy

class TrustedMessageBridge(
    private val web: WebView,
    private val trustedDocument: () -> Boolean,
    private val session: () -> String,
    private val receive: (String, String, (String) -> Unit) -> Unit
) {
    fun register(): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false
        WebViewCompat.addWebMessageListener(web,"KoenamiFiles",setOf(OriginPolicy.ORIGIN)) { _, message, origin, mainFrame, proxy ->
            if (trustedDocument() && OriginPolicy.bridge(origin.toString(),mainFrame,web.url) && message.type == WebMessageCompat.TYPE_STRING) {
                val token = session()
                message.data?.takeIf { it.length <= 68000 }?.let { data ->
                    receive(data,token) { result -> if (trustedDocument() && session() == token) proxy.postMessage(result) }
                }
            }
        }
        return true
    }
}
