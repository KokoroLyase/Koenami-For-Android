package local.koenami.android.updates

import java.net.URI
import java.io.File
import java.security.MessageDigest

/** Only stable numeric releases from this repository are install candidates. */
object UpdatePolicy {
    const val REPOSITORY = "KokoroLyase/Koenami-For-Android"
    const val LATEST = "https://api.github.com/repos/$REPOSITORY/releases/latest"
    const val RELEASES = "https://github.com/$REPOSITORY/releases/latest"
    const val DAY = 24 * 60 * 60 * 1000L
    const val MAX_APK = 150 * 1024 * 1024L

    fun version(value: String): List<Int>? {
        if (!Regex("v?[0-9]{1,4}(\\.[0-9]{1,4}){0,2}").matches(value)) return null
        return value.removePrefix("v").split('.').map(String::toInt).let { it + List(3 - it.size) { 0 } }
    }
    fun newer(candidate: String, installed: String): Boolean {
        val a = version(candidate) ?: return false
        val b = version(installed.removeSuffix("-dev")) ?: return false
        return a.zip(b).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
    }
    fun sourceUrl(value: String, asset: Boolean): Boolean = try {
        val u = URI(value)
        u.scheme == "https" && u.host == "github.com" && u.port == -1 && u.rawUserInfo == null &&
            u.rawQuery == null && u.rawFragment == null &&
            Regex(if (asset) "/$REPOSITORY/releases/download/[^/]+/[^/]+" else "/$REPOSITORY/releases/tag/[^/]+").matches(u.rawPath) &&
            !u.rawPath.contains('%') && !u.rawPath.split('/').any { it == "." || it == ".." }
    } catch (_: Exception) { false }
    fun downloadHost(value: String): Boolean = try {
        val u = URI(value)
        u.scheme == "https" && u.port == -1 && u.rawUserInfo == null && u.rawFragment == null &&
            u.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    } catch (_: Exception) { false }
    fun due(last: Long, now: Long): Boolean = last == 0L || now < last || now - last >= DAY
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
