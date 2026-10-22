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
    private var timeout: Runnable? = null
    private val saveLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = if (result.resultCode == AppCompatActivity.RESULT_OK) result.data?.data else null
        io.execute {
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
    var lastDigest: String = ""
        private set
    init { dir.listFiles()?.forEach { it.delete() } }
    fun setSession(session: String) {
        currentSession = session
        io.execute { clearTransfer(); ready?.delete(); ready = null }
    }
    private fun clearTransfer() {
        transfer?.let { try { it.out.close() } catch (_: Exception) { }; it.file.delete() }
        transfer = null
    }
    fun receive(payload: String, session: String, reply: (String) -> Unit) {
        io.execute {
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
                        val size = m.getLong("size"); require(size in 1..536870912L)
                        val name = m.getString("name")
                        require(name.length in 1..160 && name != "." && name != ".." &&
                            !name.any { it < ' ' || it in "/\\:*?\"<>|" })
                        val mime = m.getString("mime")
                        require(mime in setOf("audio/wav", "audio/x-wav", "application/zip", "application/json", "image/png", "text/html"))
                        require(dir.usableSpace > size + 16777216L)
                        val file = File.createTempFile("export-", ".part", dir)
                        transfer = Transfer(id, session, name, mime, size, file, FileOutputStream(file))
                    }
                    "chunk" -> {
                        val t = requireNotNull(transfer)
                        require(t.id == id && t.generation == session && seq == t.seq)
                        val encoded = m.getString("data")
                        require(encoded.length <= 65536 && encoded.matches(Regex("[A-Za-z0-9+/]*={0,2}")))
                        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                        require(bytes.size in 1..49152 && t.received + bytes.size <= t.size)
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
                        transfer = null; ready = t.file; saving = true
                        main.post {
                            if (currentSession != session || activity.isFinishing) {
                                io.execute { ready?.delete(); ready = null; saving = false }
                            } else {
                                try { saveLauncher.launch(android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
                                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                                    type = t.mime; putExtra(android.content.Intent.EXTRA_TITLE, t.name)
                                }) } catch (_: Exception) { io.execute { ready?.delete(); ready = null; saving = false }; status("无法打开保存选择器") }
                            }
                        }
                    }
                    "cancel" -> { require(transfer?.id == id); clearTransfer() }
                    else -> error("Unsupported export message")
                }
                val response = JSONObject().put("id", id).put("seq", seq).put("ok", true).toString()
                main.post { if (session == currentSession) reply(response); armTimeout() }
            } catch (_: Exception) {
                clearTransfer()
                main.post {
                    if (session == currentSession) reply(JSONObject().put("id", id).put("seq", seq).put("ok", false).toString())
                    status("导出失败或消息被拒绝；官网数据未清除")
                }
            }
        }
    }
    private fun armTimeout() {
        timeout?.let(main::removeCallbacks)
        timeout = Runnable { io.execute { if (transfer != null) { clearTransfer(); main.post { status("导出传输超时") } } } }
        main.postDelayed(timeout!!, 30000)
    }
    fun close() { currentSession = ""; timeout?.let(main::removeCallbacks); io.execute { clearTransfer(); ready?.delete() }; io.shutdown() }
}
