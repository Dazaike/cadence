package dev.dazaike.cadence.samsung

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import dev.dazaike.cadence.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Samsung Health only pulls new data from the watch while its own app is running, and it has no
 * public "sync now" API. So before reading, Cadence briefly brings Samsung Health to the front,
 * waits for it to sync, then returns to itself.
 *
 * Android only lets an app start another app's screen while it is itself visible, so this does
 * nothing when Cadence is in the background ([activity] is maintained by [MainActivity]).
 */
class SamsungHealthWake(private val context: Context) {
    private val mutex = Mutex()
    private var lastWakeElapsed = 0L

    /** The visible Cadence screen, or null while the app is in the background. */
    @Volatile
    var activity: Activity? = null

    /**
     * Launches Samsung Health, gives it [SETTLE_MS] to sync from the watch, and brings Cadence back.
     * Skipped (returns false) when Cadence isn't visible, Samsung Health isn't installed, or it was
     * woken less than [minIntervalMs] ago.
     */
    suspend fun wake(minIntervalMs: Long = 0L): Boolean = mutex.withLock {
        val app = context.applicationContext
        val host = activity ?: return false
        val sinceLast = SystemClock.elapsedRealtime() - lastWakeElapsed
        if (lastWakeElapsed != 0L && sinceLast < minIntervalMs) return false
        val launch = runCatching { app.packageManager.getLaunchIntentForPackage(SHEALTH_PACKAGE) }.getOrNull()
            ?: return false

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
        true
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
        const val SETTLE_MS = 8_000L
        private const val TAG = "SamsungHealthWake"
    }
}
