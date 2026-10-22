package local.koenami.android.files

import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import android.content.Intent

/** One bounded export, no page-supplied paths or network URLs. I/O is serialized off the UI thread. */
class ExportCoordinator(private val activity: AppCompatActivity, private val status: (String) -> Unit) {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val dir = File(activity.cacheDir, "exports").apply { mkdirs() }
    private data class Transfer(val id: String, val generation: String, val name: String, val mime: String,
        val size: Long, val file: File, val out: FileOutputStream, var seq: Int = 0, var received: Long = 0)
    private var transfer: Transfer? = null
    private var ready: File? = null
    @Volatile private var currentSession = ""
    private var saving = false
    private var awaitingChoice = false
    private var readyName = ""
    private var readyMime = ""
    @Volatile private var pickerOpen = false
    @Volatile private var closed = false
    private var offerDialog: AlertDialog? = null
    private var timeout: Runnable? = null
    private val saveLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pickerOpen = false
        val uri = if (result.resultCode == AppCompatActivity.RESULT_OK) result.data?.data else null
        enqueue {
            val file = ready
            if (file != null && uri != null && uri.scheme == "content") {
                try {
                    activity.contentResolver.openOutputStream(uri, "wt").use { output ->
                        requireNotNull(output); file.inputStream().use { it.copyTo(output) }
                    }
                    main.post { status("文件已保存（SHA-256 见本地诊断）") }
                } catch (_: Exception) {
                    try { android.provider.DocumentsContract.deleteDocument(activity.contentResolver, uri) } catch (_: Exception) { }
                    main.post { status("保存失败；官网录音保留，请重新导出") }
                }
            } else main.post { status("已取消保存") }
            file?.delete(); ready = null; saving = false
        }
    }
    @Volatile var lastDigest: String = ""
        private set
    init {
        dir.listFiles()?.forEach { it.delete() }
        File(activity.cacheDir,"share").listFiles()?.filter { System.currentTimeMillis()-it.lastModified()>86400000L }?.forEach { it.deleteRecursively() }
    }
    private fun enqueue(block: () -> Unit) { if (!closed) try { io.execute(block) } catch (_: RejectedExecutionException) { } }
    fun setSession(session: String) {
        currentSession = session
        offerDialog?.dismiss(); offerDialog = null
        enqueue { clearTransfer(); ready?.delete(); ready = null; awaitingChoice = false; saving = pickerOpen }
    }
    private fun clearTransfer() {
        transfer?.let { try { it.out.close() } catch (_: Exception) { }; it.file.delete() }
        transfer = null
    }
    fun receive(payload: String, session: String, reply: (String) -> Unit) {
        enqueue {
            var id = ""; var seq = -1
            try {
                require(payload.length <= 68000 && session == currentSession)
                val m = JSONObject(payload)
                require(m.getString("session") == session)
                id = m.getString("id"); seq = m.optInt("seq", -1)
                require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
                when (m.getString("op")) {
                    "begin" -> {
                        require(transfer == null && ready == null && !saving)
                        require(m.get("size") is Number && m.getDouble("size") == m.getLong("size").toDouble())
                        val size = m.getLong("size"); require(TransferPolicy.size(size,dir.usableSpace))
                        val name = m.getString("name")
                        val mime = m.getString("mime")
                        require(TransferPolicy.nameAndType(name,mime))
                        val file = File.createTempFile("export-", ".part", dir)
                        transfer = Transfer(id, session, name, mime, size, file, FileOutputStream(file))
                    }
                    "chunk" -> {
                        val t = requireNotNull(transfer)
                        require(t.id == id && t.generation == session && m.get("seq") is Number && m.getDouble("seq") == seq.toDouble())
                        val encoded = m.getString("data")
                        require(encoded.length <= 65536 && encoded.matches(Regex("[A-Za-z0-9+/]*={0,2}")))
                        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                        require(Base64.encodeToString(bytes,Base64.NO_WRAP) == encoded)
                        require(TransferPolicy.chunk(t.seq,seq,t.received,bytes.size,t.size))
                        require(dir.usableSpace > bytes.size + 1048576L)
                        t.out.write(bytes); t.received += bytes.size; t.seq++
                    }
                    "end" -> {
                        val t = requireNotNull(transfer)
                        require(t.id == id && t.generation == session && t.received == t.size && seq == t.seq)
                        t.out.fd.sync(); t.out.close()
                        val digest = MessageDigest.getInstance("SHA-256")
                        t.file.inputStream().use { input ->
                            val buf = ByteArray(65536)
                            while (true) { val n = input.read(buf); if (n == -1) break; digest.update(buf, 0, n) }
                        }
                        lastDigest = digest.digest().joinToString("") { "%02x".format(it) }
                        require(session == currentSession)
                        transfer = null; ready = t.file; readyName=t.name;readyMime=t.mime;saving=true;awaitingChoice=true
                        main.post { offer(session) }
                    }
                    "cancel" -> { require(transfer?.id == id); clearTransfer() }
                    else -> error("Unsupported export message")
                }
                val response = JSONObject().put("id", id).put("seq", seq).put("ok", true).toString()
                main.post { if (session == currentSession) reply(response); armTimeout() }
            } catch (_: Exception) {
                if (transfer?.id == id && transfer?.generation == session && session == currentSession) clearTransfer()
                main.post {
                    if (session == currentSession) reply(JSONObject().put("id", id).put("seq", seq).put("ok", false).toString())
                    status("导出失败或消息被拒绝；官网数据未清除")
                }
            }
        }
    }
    private fun armTimeout() {
        timeout?.let(main::removeCallbacks)
        timeout = Runnable { enqueue { if (transfer != null) { clearTransfer(); main.post { status("导出传输超时") } } } }
        main.postDelayed(timeout!!, 30000)
    }
    private fun discard() { enqueue { ready?.delete();ready=null;saving=false;awaitingChoice=false } }
    private fun offer(token: String) {
        if (closed || token != currentSession || activity.isFinishing) { discard();return }
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { status("导出已暂存，返回应用后选择保存或分享");return }
        if (offerDialog?.isShowing == true) return
        offerDialog=AlertDialog.Builder(activity).setTitle("导出文件").setMessage(readyName)
            .setPositiveButton("保存文件") { _, _ ->
                awaitingChoice=false;pickerOpen=true
                try {saveLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE);type=readyMime;putExtra(Intent.EXTRA_TITLE,readyName)
                })}catch(_:Exception){pickerOpen=false;discard();status("无法打开保存选择器")}
            }
            .setNeutralButton("分享") { _, _ -> share(token) }
            .setNegativeButton("取消") { _, _ -> discard();status("已取消导出") }
            .setOnCancelListener {discard();status("已取消导出")}.show()
    }
    private fun share(token: String) {
        awaitingChoice=false
        enqueue {
            try {
                val source=requireNotNull(ready);require(token == currentSession)
                val folder=File(File(activity.cacheDir,"share"),java.util.UUID.randomUUID().toString()).apply{mkdirs()}
                val file=File(folder,readyName);source.copyTo(file)
                val mime=readyMime
                source.delete();ready=null;saving=false
                main.post {
                    if (!closed && token == currentSession && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        try {
                            val uri=FileProvider.getUriForFile(activity,"${activity.packageName}.files",file)
                            activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type=mime;putExtra(Intent.EXTRA_STREAM,uri);clipData=android.content.ClipData.newRawUri("export",uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },"分享导出文件"))
                        }catch(_:Exception){status("无法打开分享；请重新导出并选择保存")}
                    }
                }
            }catch(_:Exception){discard();main.post{status("分享准备失败；官网数据保留")}}
        }
    }
    fun resume() { enqueue {if(awaitingChoice && ready != null){val token=currentSession;main.post{offer(token)}}} }
    fun close() {
        if (closed)return
        currentSession="";timeout?.let(main::removeCallbacks);offerDialog?.dismiss()
        io.execute {clearTransfer();ready?.delete()};closed=true;io.shutdown()
    }
}
