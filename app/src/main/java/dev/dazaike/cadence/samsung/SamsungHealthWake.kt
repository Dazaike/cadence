package dev.dazaike.cadence.samsung

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import dev.dazaike.cadence.MainActivity
import dev.dazaike.cadence.data.StatusHub
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Samsung Health only pulls new data from the watch while one of its screens is running, and it has
 * no public "sync now" API. So before reading, Cadence makes it sync:
 *
 *  1. With Shizuku allowed, [ShizukuWake] runs Samsung Health on a hidden display: nothing shows on
 *     screen, and it works while Cadence is in the background.
 *  2. Otherwise Samsung Health is briefly brought to the front for [SETTLE_MS] and Cadence returns.
 *     Android only lets an app open another app while it is itself visible, so this does nothing
 *     when Cadence is in the background ([activity] is maintained by [MainActivity]).
 */
class SamsungHealthWake(
    private val context: Context,
    private val shizuku: ShizukuWake,
    private val status: StatusHub,
) {
    private val mutex = Mutex()
    private var lastWakeElapsed = 0L

    /** The visible Cadence screen, or null while the app is in the background. */
    @Volatile
    var activity: Activity? = null

    /**
     * Makes Samsung Health sync from the watch. Skipped (returns false) when it was woken less than
     * [minIntervalMs] ago, when Samsung Health isn't installed, or when the only available method
     * (a visible bounce) can't run because Cadence isn't on screen.
     */
    suspend fun wake(minIntervalMs: Long = 0L): Boolean = mutex.withLock {
        if (tooSoon(minIntervalMs)) return false
        val app = context.applicationContext
        val launch = runCatching { app.packageManager.getLaunchIntentForPackage(SHEALTH_PACKAGE) }.getOrNull()
            ?: return false

        try {
            status.set(StatusHub.WAKE, "Syncing Samsung Health with your watch…")
            val silent = shizuku.syncSilently()
            if (silent != null) {
                lastWakeElapsed = SystemClock.elapsedRealtime()
                return silent
            }
            status.set(StatusHub.WAKE, "Opening Samsung Health to sync your watch…")
            bounce(app, launch)
        } finally {
            status.clear(StatusHub.WAKE)
        }
    }

    /** The hidden-display method only: safe to call from a background job, never shows anything. */
    suspend fun wakeSilently(minIntervalMs: Long): Boolean = mutex.withLock {
        if (tooSoon(minIntervalMs)) return false
        try {
            status.set(StatusHub.WAKE, "Syncing Samsung Health with your watch…")
            val result = shizuku.syncSilently() ?: return false
            lastWakeElapsed = SystemClock.elapsedRealtime()
            result
        } finally {
            status.clear(StatusHub.WAKE)
        }
    }

    private fun tooSoon(minIntervalMs: Long): Boolean =
        lastWakeElapsed != 0L && SystemClock.elapsedRealtime() - lastWakeElapsed < minIntervalMs

    private suspend fun bounce(app: Context, launch: Intent): Boolean {
        val host = activity ?: return false
        // Opened inside Cadence's own task (no NEW_TASK) so Cadence stays in the foreground task's
        // back stack, which is what lets it bring itself back afterwards.
        launch.flags = 0
        val started = runCatching { host.startActivity(launch) }
        if (started.isFailure) {
            Log.w(TAG, "Could not open Samsung Health", started.exceptionOrNull())
            return false
        }
        lastWakeElapsed = SystemClock.elapsedRealtime()
        delay(SETTLE_MS)
        returnToCadence(app)
        return true
    }

    private fun returnToCadence(app: Context) {
        val intent = Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val started = runCatching { app.startActivity(intent) }
        if (started.isFailure) Log.w(TAG, "startActivity back to Cadence failed", started.exceptionOrNull())
        // Fallback for when the direct start is blocked as a background activity launch.
        runCatching {
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.appTasks.firstOrNull()?.moveToFront()
        }
    }

    companion object {
        const val SHEALTH_PACKAGE = "com.sec.android.app.shealth"

        // Samsung Health's watch sync finished 1 to 3 seconds after its home screen resumed.
        const val SETTLE_MS = 5_000L
        private const val TAG = "SamsungHealthWake"
    }
}
