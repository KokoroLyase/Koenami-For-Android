package local.koenami.android.web

import java.net.URI

object OriginPolicy {
    const val ORIGIN = "https://koe.transnavi.jp"
    const val HOME = "$ORIGIN/zh-CN/"
    private fun parse(value: String?): URI? = try { value?.let(::URI) } catch (_: Exception) { null }
    fun officialOrigin(value: String?): Boolean = parse(value)?.let {
        it.scheme.equals("https", true) && it.host.equals("koe.transnavi.jp", true) &&
            (it.port == -1 || it.port == 443) && it.rawUserInfo == null
    } ?: false
    fun training(value: String?): Boolean = officialOrigin(value) && parse(value)?.let {
        it.rawPath in listOf("/zh-CN/", "/zh-CN") && it.rawQuery == null
    } == true
    fun external(value: String?): Boolean = parse(value)?.let {
        it.scheme.equals("https", true) && !it.host.isNullOrEmpty() && it.rawUserInfo == null
    } ?: false
}
