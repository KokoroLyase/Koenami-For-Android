package local.koenami.android.updates

import android.content.Context

class UpdateStore(context: Context) {
    private val prefs = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    var automatic: Boolean
        get() = prefs.getBoolean("automatic", true)
        set(value) { prefs.edit().putBoolean("automatic", value).apply() }
    var notificationAsked: Boolean
        get() = prefs.getBoolean("notification_asked", false)
        set(value) { prefs.edit().putBoolean("notification_asked", value).apply() }
    var lastAttempt: Long
        get() = prefs.getLong("last_attempt", 0)
        set(value) { prefs.edit().putLong("last_attempt", value).apply() }
    var blockedUntil: Long
        get() = prefs.getLong("blocked_until", 0)
        set(value) { prefs.edit().putLong("blocked_until", value).apply() }
    var pending: String
        get() = prefs.getString("pending", "") ?: ""
        set(value) { prefs.edit().putString("pending", value).apply() }
    var notified: String
        get() = prefs.getString("notified", "") ?: ""
        set(value) { prefs.edit().putString("notified", value).apply() }
    fun snooze(tag: String) { prefs.edit().putString("snoozed_tag", tag).putLong("snoozed_at", System.currentTimeMillis()).apply() }
    fun snoozed(tag: String): Boolean = prefs.getString("snoozed_tag", "") == tag && !UpdatePolicy.due(prefs.getLong("snoozed_at", 0), System.currentTimeMillis())
    fun candidate(): GitHubRelease? = try { GitHubRelease.parse(pending) } catch (_: Exception) { null }
}
