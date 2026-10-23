package local.koenami.android.updates

import android.content.Context
import android.content.pm.PackageManager
import local.koenami.android.BuildConfig
import java.io.File
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class UpdateClient(private val context: Context) {
    data class Check(val release: GitHubRelease?, val skipped: Boolean = false)
    companion object { private val lock = Any() }
    fun check(manual: Boolean): Check = synchronized(lock) {
        val store = UpdateStore(context)
        val now = System.currentTimeMillis()
        if (now < store.blockedUntil) throw IOException("GitHub 暂时限制请求，请稍后重试")
        if (!manual && (!store.automatic || !UpdatePolicy.due(store.lastAttempt, now))) return@synchronized Check(null, true)
        store.lastAttempt = now
        val connection = open(UpdatePolicy.LATEST)
        try {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            when (connection.responseCode) {
                403, 429 -> {
                    val reset = connection.getHeaderField("X-RateLimit-Reset")?.toLongOrNull()?.times(1000)
                    val delay = connection.getHeaderField("Retry-After")?.toLongOrNull()?.times(1000)
                    store.blockedUntil = (reset ?: (now + (delay ?: 60 * 60 * 1000L))).coerceIn(now + 60_000, now + UpdatePolicy.DAY)
                    throw IOException("GitHub 暂时限制请求，请稍后重试")
                }
                404 -> throw IOException("仓库尚无可用的正式 Release")
                200 -> Unit
                else -> throw IOException("GitHub 暂不可用（HTTP ${connection.responseCode}），请稍后重试")
            }
            val raw = connection.inputStream.use { input ->
                val bytes = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    require(bytes.size() + count <= 512 * 1024) { "Release 信息过大" }
                    bytes.write(buffer, 0, count)
                }
                bytes.toString("UTF-8")
            }
            val release = GitHubRelease.parse(raw)
            if (UpdatePolicy.newer(release.tag, BuildConfig.VERSION_NAME)) { store.pending = raw; Check(release) }
            else { store.pending = ""; Check(null) }
        } finally { connection.disconnect() }
    }
    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000; readTimeout = 30_000; instanceFollowRedirects = false
        setRequestProperty("User-Agent", "Koenami-Android/${BuildConfig.VERSION_NAME}")
    }
    fun download(release: GitHubRelease, cancelled: () -> Boolean, progress: (Int) -> Unit): File {
        require(UpdatePolicy.sourceUrl(release.apk, true))
        val folder = File(context.cacheDir, "updates").apply { mkdirs() }
        // One download per controller; never share partial bytes with the package installer.
        val partial = File.createTempFile("download-", ".part", folder)
        var connection: HttpURLConnection? = null
        try {
            var url = release.apk
            repeat(6) { attempt ->
                if (cancelled()) throw IOException("下载已取消")
                require(UpdatePolicy.downloadHost(url)) { "下载跳转来源不受信任" }
                val next = open(url)
                connection = next
                val code = next.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val location = next.getHeaderField("Location") ?: throw IOException("下载跳转缺少地址")
                    url = URL(URL(url), location).toString()
                    next.disconnect()
                    if (attempt == 5) throw IOException("下载跳转过多")
                } else {
                    if (code != 200) throw IOException("下载失败（HTTP $code）")
                    val length = next.contentLengthLong
                    require(length == -1L || length == release.size) { "安装包长度与 Release 不符" }
                    var total = 0L
                    next.inputStream.use { input -> partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (cancelled()) throw IOException("下载已取消")
                            val count = input.read(buffer); if (count < 0) break
                            total += count
                            require(total <= release.size) { "安装包超过声明大小" }
                            output.write(buffer, 0, count); progress((total * 100 / release.size).toInt())
                        }
                    } }
                    require(total == release.size && UpdatePolicy.sha256(partial) == release.digest) { "安装包完整性校验失败，请重新检查更新" }
                    verifyPackage(partial, release.tag)
                    val verified = File.createTempFile("verified-", ".apk", folder)
                    require(partial.renameTo(verified)) { "无法保存已校验的安装包" }
                    return verified
                }
            }
            throw IOException("下载跳转过多")
        } finally { connection?.disconnect(); partial.delete() }
    }
    @Suppress("DEPRECATION")
    fun verifyPackage(file: File, expectedVersion: String? = null) {
        val manager = context.packageManager
        val archive = manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw IOException("无法读取 APK 安装信息")
        val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        require(archive.packageName == context.packageName) { "APK 包名与当前应用不符（Debug 包不能安装正式更新）" }
        require(archive.longVersionCode > installed.longVersionCode) { "安装包版本不高于当前版本" }
        if (expectedVersion != null) require(UpdatePolicy.version(archive.versionName ?: "") == UpdatePolicy.version(expectedVersion)) { "APK 版本与 Release 标签不符" }
        fun signers(info: android.content.pm.PackageInfo): Set<String> = info.signingInfo?.apkContentsSigners?.map {
            java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) }
        }?.toSet() ?: emptySet()
        val actual = signers(archive)
        require(actual.isNotEmpty() && actual == signers(installed)) { "APK 签名与当前应用不符，已拒绝安装" }
    }
}
