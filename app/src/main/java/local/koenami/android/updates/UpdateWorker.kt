package local.koenami.android.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import local.koenami.android.MainActivity
import local.koenami.android.R
import java.util.concurrent.TimeUnit

class UpdateWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        if (!UpdateStore(applicationContext).automatic) return Result.success()
        return try {
            val result = UpdateClient(applicationContext).check(false)
            val store = UpdateStore(applicationContext)
            if (result.skipped && store.automatic && inputData.getBoolean("scheduled", false))
                UpdateSchedule.deferUntilDue(applicationContext)
            if (!result.skipped && result.release != null && store.automatic && !UpdateSchedule.visible && !store.snoozed(result.release.tag))
                UpdateSchedule.notify(applicationContext, result.release)
            Result.success()
        } catch (_: Exception) {
            // No background error dialog; the next daily attempt or a manual check can retry.
            Result.success()
        }
    }
}

object UpdateSchedule {
    private const val DAILY = "koenami-daily-updates"
    private const val CATCHUP = "koenami-update-catchup"
    private const val DEFERRED = "koenami-update-deferred"
    private const val CHANNEL = "app_updates"
    const val NOTIFICATION = 101
    @Volatile var visible = false

    fun configure(context: Context) {
        val manager = WorkManager.getInstance(context)
        if (!UpdateStore(context).automatic) {
            manager.cancelUniqueWork(DAILY); manager.cancelUniqueWork(CATCHUP); manager.cancelUniqueWork(DEFERRED)
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
            return
        }
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        manager.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequest.Builder(UpdateWorker::class.java, 24, TimeUnit.HOURS)
                .setInitialDelay(24, TimeUnit.HOURS).setInputData(Data.Builder().putBoolean("scheduled", true).build()).setConstraints(constraints).build())
        // Covers first launch and delayed background execution without resetting the daily schedule.
        manager.enqueueUniqueWork(CATCHUP, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequest.Builder(UpdateWorker::class.java).setConstraints(constraints).build())
    }
    fun deferUntilDue(context: Context) {
        // A manual check (or scheduler jitter) can put a periodic run just before the 24h limit.
        // Bridge that gap instead of skipping a whole additional day. APPEND also works when
        // the current deferred worker needs to defer again after a newer manual check.
        val remaining = (UpdateStore(context).lastAttempt + UpdatePolicy.DAY - System.currentTimeMillis()).coerceIn(1000, UpdatePolicy.DAY)
        WorkManager.getInstance(context).enqueueUniqueWork(DEFERRED, ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequest.Builder(UpdateWorker::class.java).setInitialDelay(remaining, TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putBoolean("scheduled", true).build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
    fun notify(context: Context, release: GitHubRelease) {
        val store = UpdateStore(context)
        if (store.notified == release.tag) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "应用版本更新", NotificationManager.IMPORTANCE_DEFAULT))
        if (!manager.areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 101, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(NOTIFICATION, NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_update)
            .setContentTitle("Koenami ${release.tag} 可更新").setContentText("点击查看更新内容，由你选择是否下载安装")
            .setContentIntent(pending).setAutoCancel(true).build())
        store.notified = release.tag
    }
}
