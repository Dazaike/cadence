package dev.dazaike.cadence.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** WorkManager entry points for cloud sync. Everything here requires a network connection. */
object CloudSyncScheduler {
    private const val PERIODIC_WORK = "cadence_cloud_sync_periodic"
    private const val ONE_TIME_WORK = "cadence_cloud_sync_now"
    const val KEY_PERIODIC = "periodic"
    private const val PERIODIC_MINUTES = 15L
    private const val BACKOFF_SECONDS = 30L

    private val constraints: Constraints =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Idempotent: KEEP leaves an already-scheduled periodic job (and its timing) alone. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(PERIODIC_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setInputData(Data.Builder().putBoolean(KEY_PERIODIC, true).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * One expedited sync. [replace] = true for explicit user actions ("Sync now", right after
     * sign-in) so a pending backoff retry is superseded; false for the app-open trigger, which must
     * not disturb a sync that is already queued or running.
     */
    fun syncNow(context: Context, replace: Boolean) {
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }

    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(PERIODIC_WORK)
        wm.cancelUniqueWork(ONE_TIME_WORK)
    }

    /** True while a user-triggered sync is queued, waiting on the network, or running. */
    fun oneTimePending(context: Context): Flow<Boolean> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(ONE_TIME_WORK).map { infos ->
            infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.BLOCKED }
        }
}
