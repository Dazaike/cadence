package dev.dazaike.cadence.cloud

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.dazaike.cadence.CadenceApp

/**
 * Runs one cloud sync. The 15-minute periodic job and the expedited one-time job share this class;
 * the periodic one only does anything while auto-sync is on.
 */
class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val manager = (applicationContext as CadenceApp).cloudSync
        val periodic = inputData.getBoolean(CloudSyncScheduler.KEY_PERIODIC, false)
        return when (val outcome = manager.sync(requireAutoSync = periodic)) {
            // Network / server trouble: WorkManager backs off exponentially; give up after a few
            // tries, the status line already says what failed and the next period tries again.
            is SyncOutcome.Failed -> if (outcome.retry && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
            else -> Result.success()
        }
    }

    /** Required for expedited work on Android 11, where it runs as a short foreground service. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Cloud sync", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Syncing health data")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private companion object {
        const val CHANNEL_ID = "cadence_cloud_sync"
        const val NOTIFICATION_ID = 4107
        const val MAX_ATTEMPTS = 4
    }
}
