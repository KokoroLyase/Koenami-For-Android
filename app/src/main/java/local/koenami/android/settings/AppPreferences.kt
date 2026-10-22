package local.koenami.android.settings

import android.content.Context

class AppPreferences(context: Context) {
    private val storage = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    var optimized: Boolean
        get() = storage.getBoolean("optimized", true)
        set(value) { storage.edit().putBoolean("optimized", value).apply() }
    var safeMode: Boolean
        get() = storage.getBoolean("safe", false)
        set(value) { storage.edit().putBoolean("safe", value).apply() }
    var interrupted: Boolean
        get() = storage.getBoolean("interrupted", false)
        set(value) { storage.edit().putBoolean("interrupted", value).apply() }
}
