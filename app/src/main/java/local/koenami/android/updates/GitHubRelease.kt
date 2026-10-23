package local.koenami.android.updates

import org.json.JSONObject

data class GitHubRelease(val tag: String, val page: String, val notes: String, val apk: String, val size: Long, val digest: String) {
    companion object {
        fun parse(raw: String): GitHubRelease {
            val json = JSONObject(raw)
            require(!json.getBoolean("draft") && !json.getBoolean("prerelease")) { "仅支持正式 Release" }
            val tag = json.getString("tag_name")
            require(UpdatePolicy.version(tag) != null) { "版本号格式不受支持" }
            val page = json.getString("html_url")
            require(UpdatePolicy.sourceUrl(page, false) && page.endsWith("/tag/$tag")) { "Release 来源不受信任" }
            val assets = json.getJSONArray("assets")
            val apks = (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.getString("name").endsWith(".apk", true) }
            require(apks.size == 1) { "Release 必须包含唯一的通用 APK" }
            val apk = apks.single()
            require(apk.getString("state") == "uploaded") { "安装包尚未上传完毕" }
            val url = apk.getString("browser_download_url")
            require(UpdatePolicy.sourceUrl(url, true) && url.startsWith("https://github.com/${UpdatePolicy.REPOSITORY}/releases/download/$tag/")) { "安装包来源不受信任" }
            val size = apk.getLong("size")
            require(size in 1..UpdatePolicy.MAX_APK) { "安装包大小异常" }
            val fullDigest = apk.getString("digest")
            require(fullDigest.startsWith("sha256:")) { "Release 缺少 SHA-256 摘要，暂不能安全安装" }
            val digest = fullDigest.removePrefix("sha256:")
            require(Regex("[0-9a-fA-F]{64}").matches(digest)) { "Release 缺少 SHA-256 摘要，暂不能安全安装" }
            return GitHubRelease(tag, page, json.optString("body").take(6000), url, size, digest.lowercase())
        }
    }
}
