package local.koenami.android.enhancement

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import local.koenami.android.web.OriginPolicy
import org.json.JSONObject

class EnhancementController(private val web: WebView) {
    private val source = web.context.assets.open("enhancement/bootstrap.js").bufferedReader().use { it.readText() }
    private var script: ScriptHandler? = null
    var optimized = false
    var safe = false
    fun register() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            script = WebViewCompat.addDocumentStartJavaScript(web, source, setOf(OriginPolicy.ORIGIN))
        }
    }
    fun bind(session: String) {
        if (!safe && OriginPolicy.training(web.url)) {
            web.evaluateJavascript(source, null)
            web.evaluateJavascript("window.__koenamiAndroid?.bind(${JSONObject.quote(session)}, $optimized)", null)
            if (web.hasWindowFocus()) web.evaluateJavascript("window.__koenamiAndroid?.foreground()", null)
        }
    }
    fun setVisual(enabled: Boolean) {
        optimized = enabled
        if (!safe) web.evaluateJavascript("window.__koenamiAndroid?.visual($enabled)", null)
    }
    fun dispose() { script?.remove(); script = null }
}
