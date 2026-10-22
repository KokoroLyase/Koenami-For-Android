package local.koenami.android.enhancement

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import local.koenami.android.web.OriginPolicy
import org.json.JSONObject

class EnhancementController(private val web: WebView) {
    private fun asset(path: String) = web.context.assets.open("enhancement/$path").bufferedReader().use { it.readText() }
    private val manifest = JSONObject(asset("manifest.json"))
    private val source = buildString {
        append("(()=>{if(window!==window.top||location.origin!=='https://koe.transnavi.jp'||!['/zh-CN/','/zh-CN'].includes(location.pathname)||location.search||window.__koenamiAndroid)return;\n")
        val styles = JSONObject()
        val css = manifest.getJSONArray("styles")
        for (i in 0 until css.length()) styles.put(css.getString(i), asset(css.getString(i)))
        append("window.__koenamiStyles=").append(styles.toString()).append(";\n")
        val scripts = manifest.getJSONArray("scripts")
        for (i in 0 until scripts.length()) append(asset(scripts.getString(i))).append("\n")
        append("})();")
    }
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
