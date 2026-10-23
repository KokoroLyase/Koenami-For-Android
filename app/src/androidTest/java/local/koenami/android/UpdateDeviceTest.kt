package local.koenami.android

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import local.koenami.android.updates.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry

@RunWith(AndroidJUnit4::class)
class UpdateDeviceTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun metadata(): JSONObject = JSONObject("""{
        "draft":false,"prerelease":false,"tag_name":"v1.1",
        "html_url":"https://github.com/KokoroLyase/Koenami-For-Android/releases/tag/v1.1",
        "body":"Changes", "assets":[{"name":"Koenami-v1.1.apk","state":"uploaded","size":1234,
        "browser_download_url":"https://github.com/KokoroLyase/Koenami-For-Android/releases/download/v1.1/Koenami-v1.1.apk",
        "digest":"sha256:${"a".repeat(64)}"}]}""")
    private fun rejected(value: JSONObject) { assertThrows(IllegalArgumentException::class.java) { GitHubRelease.parse(value.toString()) } }
    @Test fun stableReleaseMetadataRequiresOneTrustedCompleteApk() {
        val release = GitHubRelease.parse(metadata().toString())
        assertEquals("v1.1", release.tag); assertEquals(1234L, release.size)
        rejected(metadata().put("draft", true)); rejected(metadata().put("prerelease", true))
        rejected(metadata().put("tag_name", "v1.1-rc1"))
        rejected(metadata().put("html_url", "https://evil.invalid/releases/tag/v1.1"))
        rejected(metadata().apply { getJSONArray("assets").put(getJSONArray("assets").getJSONObject(0)) })
        rejected(metadata().apply { getJSONArray("assets").getJSONObject(0).put("state", "new") })
        rejected(metadata().apply { getJSONArray("assets").getJSONObject(0).put("size", UpdatePolicy.MAX_APK + 1) })
        rejected(metadata().apply { getJSONArray("assets").getJSONObject(0).put("digest", "sha256:wrong") })
        rejected(metadata().apply { getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://evil.invalid/file.apk") })
        rejected(metadata().apply { getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://github.com/KokoroLyase/Koenami-For-Android/releases/download/v1.0/file.apk") })
    }
    @Test fun dailyPreferencePersistsAndDisablingCancelsScheduledChecks() {
        val store = UpdateStore(context)
        val previous = store.automatic
        try {
            store.automatic = true
            UpdateSchedule.configure(context)
            val manager = WorkManager.getInstance(context)
            val work = manager.getWorkInfosForUniqueWork("koenami-daily-updates").get(10, TimeUnit.SECONDS)
            assertTrue(work.any { !it.state.isFinished })
            assertEquals(UpdatePolicy.DAY, work.first { !it.state.isFinished }.periodicityInfo?.repeatIntervalMillis)
            store.lastAttempt = System.currentTimeMillis()
            UpdateSchedule.deferUntilDue(context)
            val deferred = manager.getWorkInfosForUniqueWork("koenami-update-deferred").get(10, TimeUnit.SECONDS)
            assertTrue(deferred.any { !it.state.isFinished && it.initialDelayMillis in (UpdatePolicy.DAY - 60_000)..UpdatePolicy.DAY })
            assertTrue(UpdateStore(context).automatic)
            store.automatic = false; UpdateSchedule.configure(context)
            // A queued database transaction after cancel ensures the cancellation is visible.
            manager.cancelUniqueWork("koenami-daily-updates").result.get(10, TimeUnit.SECONDS)
            assertTrue(manager.getWorkInfosForUniqueWork("koenami-daily-updates").get(10, TimeUnit.SECONDS).all { it.state.isFinished })
            assertTrue(UpdateClient(context).check(false).skipped)
        } finally { store.automatic = previous; UpdateSchedule.configure(context) }
    }
    @Test fun backgroundNotificationContainsOnlyNativeAppEntry() {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        org.junit.Assume.assumeTrue(manager.areNotificationsEnabled())
        val store = UpdateStore(context)
        val old = store.notified
        store.notified = ""
        try {
            UpdateSchedule.notify(context, GitHubRelease.parse(metadata().toString()))
            val deadline = System.currentTimeMillis() + 5000
            while (manager.activeNotifications.none { it.id == UpdateSchedule.NOTIFICATION } && System.currentTimeMillis() < deadline) Thread.sleep(50)
            val notification = manager.activeNotifications.single { it.id == UpdateSchedule.NOTIFICATION }.notification
            assertEquals(context.packageName, notification.contentIntent.creatorPackage)
            assertEquals("v1.1", store.notified)
        } finally { manager.cancel(UpdateSchedule.NOTIFICATION); store.notified = old }
    }
    @Test fun latestPublishedReleaseIsReadableWithoutAccount() {
        // Integration test against the real public endpoint. No token and no user audio are used.
        val result = UpdateClient(context).check(true)
        val release = result.release
        if (release != null) assertTrue(UpdatePolicy.newer(release.tag, BuildConfig.VERSION_NAME))
        assertTrue(UpdateStore(context).lastAttempt > 0)
    }
    @Test fun automaticPromptAppearsAndLaterSnoozes() {
        val store = UpdateStore(context)
        val original = store.pending
        val originalAutomatic = store.automatic
        val tag = "v99.1.${System.currentTimeMillis() / 1000 % 10000}"
        val candidate = metadata().put("tag_name", tag)
            .put("html_url", "https://github.com/KokoroLyase/Koenami-For-Android/releases/tag/$tag")
        candidate.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://github.com/KokoroLyase/Koenami-For-Android/releases/download/$tag/Koenami-$tag.apk")
        store.lastAttempt = System.currentTimeMillis(); store.automatic = true; store.pending = candidate.toString()
        fun find(view: View, text: String): TextView? {
            if (view is TextView && view.text.toString() == text) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i), text)?.let { return it }
            return null
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                val end = System.currentTimeMillis() + 100_000
                var offered = false
                while (System.currentTimeMillis() < end && !offered) {
                    instrumentation.runOnMainSync {
                        offered = WindowInspector.getGlobalWindowViews().any { window -> find(window, "发现新版本 $tag") != null }
                        if (offered) WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { window -> find(window, "稍后") }?.performClick()
                    }
                    if (!offered) Thread.sleep(500)
                }
                assertTrue("Automatic update prompt never appeared after the page became idle", offered)
                instrumentation.waitForIdleSync()
                assertTrue(store.snoozed(tag))
            }
        } finally { store.pending = original; store.automatic = originalAutomatic }
    }
}
