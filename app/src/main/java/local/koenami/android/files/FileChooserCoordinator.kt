package local.koenami.android.files

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class FileChooserCoordinator(activity: AppCompatActivity, private val trusted: () -> Boolean, private val foreground: () -> Boolean) {
    private var callback: ValueCallback<Array<Uri>>? = null
    private var launched = false
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        launched = false
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        callback?.onReceiveValue(if (trusted() && uris?.all { it.scheme == "content" } == true) uris else null)
        callback = null
    }
    fun show(cb: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        if (!trusted() || !foreground()) { cb.onReceiveValue(null); return true }
        if (launched) { cancel(); cb.onReceiveValue(null); return true }
        cancel(); callback = cb
        try { launched = true; launcher.launch(params.createIntent().apply { addCategory(android.content.Intent.CATEGORY_OPENABLE) }) }
        catch (_: Exception) { launched = false; cancel() }
        return true
    }
    fun cancel() { callback?.onReceiveValue(null); callback = null }
}
