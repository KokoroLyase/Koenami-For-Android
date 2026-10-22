package local.koenami.android.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.webkit.PermissionRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import local.koenami.android.web.OriginPolicy

class MicrophonePermissionCoordinator(
    private val activity: AppCompatActivity,
    private val trusted: () -> Boolean,
    private val generation: () -> Int,
    private val status: (String) -> Unit,
    private val onGranted: () -> Unit = {}
) {
    private var pending: PermissionRequest? = null
    private var epoch = -1
    private var systemPrompt = false
    val promptInFlight: Boolean get() = systemPrompt
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        systemPrompt = false
        val request = pending
        pending = null
        if (request != null) {
            if (allowed && epoch == generation() && trusted() && OriginPolicy.officialOrigin(request.origin.toString())) {
                onGranted(); request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                status("麦克风已授权；录音由官网处理")
            } else { request.deny(); status("麦克风未授权或页面已离开") }
        }
    }
    fun request(request: PermissionRequest) {
        if (systemPrompt || pending != null || !trusted() || !OriginPolicy.officialOrigin(request.origin.toString()) ||
            !request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
            request.deny(); return
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            onGranted(); request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)); return
        }
        pending = request; epoch = generation(); systemPrompt = true
        status("等待系统麦克风授权")
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
    fun cancel(request: PermissionRequest) { if (pending === request) pending = null }
    fun invalidate() { pending?.deny(); pending = null }
}
