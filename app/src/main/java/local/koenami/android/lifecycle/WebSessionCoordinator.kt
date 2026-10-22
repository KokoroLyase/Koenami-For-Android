package local.koenami.android.lifecycle

import android.os.Handler
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener

/** Bounded stop request; public controls indicate capture, never prove IndexedDB commit. */
class WebSessionCoordinator(
    private val web: WebView,
    private val handler: Handler,
    private val generation: () -> Int,
    private val current: () -> Boolean,
    private val releaseUnconfirmed: () -> Unit,
    private val status: (String) -> Unit
) {
    private var check: Runnable? = null
    private var deadline: Runnable? = null
    fun stop(trusted: Boolean) {
        cancel()
        if (!trusted) { releaseUnconfirmed();return }
        val epoch=generation()
        fun valid()=current() && generation()==epoch
        web.evaluateJavascript("window.__koenamiAndroid?.stopCapture()",null)
        check=Runnable {
            if(!valid())return@Runnable
            var responded=false
            deadline=Runnable{if(!responded&&valid())releaseUnconfirmed()}
            handler.postDelayed(deadline!!,1000)
            web.evaluateJavascript("JSON.stringify(window.__koenamiAndroid?.captureState() || {unknown:true})") { result ->
                responded=true
                if(!valid())return@evaluateJavascript
                val state=try {JSONObject(JSONTokener(result).nextValue() as String)}catch(_:Exception){JSONObject().put("unknown",true)}
                if(listOf("record","live","unknown","starting").any{state.optBoolean(it)})releaseUnconfirmed()
                else status("已请求官网停止；普通录音请在历史中确认，实时模式不新增录音")
            }
        }
        handler.postDelayed(check!!,2500)
    }
    fun cancel(){check?.let(handler::removeCallbacks);deadline?.let(handler::removeCallbacks);check=null;deadline=null}
}
