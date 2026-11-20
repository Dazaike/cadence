package dev.dazaike.cadence.samsung

import android.content.ComponentName
import android.content.Context
import android.os.IBinder
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.util.Log
import dev.dazaike.cadence.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Optional [Shizuku](https://shizuku.rikka.app) integration: lets Cadence make Samsung Health sync
 * the watch without opening it on screen (see [ShellWakeService]). Everything here degrades to
 * "not ready" when Shizuku is missing, stopped or not allowed.
 */
class ShizukuWake(private val context: Context) {

    enum class State {
        /** The Shizuku app is not installed. */
        NOT_INSTALLED,

        /** Installed, but its service is not running (or is too old). */
        NOT_RUNNING,

        /** Running; Cadence has not been allowed yet. */
        NEEDS_PERMISSION,

        /** The user chose "deny" in Shizuku; it must be re-enabled from the Shizuku app. */
        DENIED,

        READY,
    }

    private val mutex = Mutex()

    @Volatile
    private var service: IWakeService? = null

    private val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellWakeService::class.java.name))
        .processNameSuffix("wake")
        .daemon(false)
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    fun state(): State {
        if (!installed()) return State.NOT_INSTALLED
        val running = runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() }.getOrDefault(false)
        if (!running) return State.NOT_RUNNING
        return runCatching {
            when {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> State.READY
                Shizuku.shouldShowRequestPermissionRationale() -> State.DENIED
                else -> State.NEEDS_PERMISSION
            }
        }.getOrDefault(State.NOT_RUNNING)
    }

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
            .onFailure { Log.w(TAG, "Shizuku permission request failed", it) }
    }

    /**
     * Opens Samsung Health on a hidden display until its watch sync finishes. Returns true when a
     * sync finished, false when none finished in time, and null when this path is unavailable
     * (Shizuku not ready, or the service failed), so the caller can fall back.
     */
    suspend fun syncSilently(timeoutMs: Int = SYNC_TIMEOUT_MS): Boolean? {
        if (state() != State.READY) return null
        return try {
            val svc = connect() ?: return null
            withContext(Dispatchers.IO) { svc.syncOnHiddenDisplay(LAUNCH_COMPONENT, timeoutMs) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Silent Samsung Health sync failed", e)
            service = null
            null
        }
    }

    private suspend fun connect(): IWakeService? = mutex.withLock {
        service?.takeIf { it.asBinder().pingBinder() }?.let { return it }
        withTimeoutOrNull(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
                        if (binder == null || !binder.pingBinder()) return
                        val bound = IWakeService.Stub.asInterface(binder)
                        service = bound
                        if (cont.isActive) cont.resume(bound)
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        service = null
                    }
                }
                Shizuku.bindUserService(args, connection)
                cont.invokeOnCancellation { runCatching { Shizuku.unbindUserService(args, connection, false) } }
            }
        }
    }

    private fun installed(): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val SHIZUKU_URL = "https://shizuku.rikka.app/download/"
        private const val REQUEST_CODE = 4242
        private const val BIND_TIMEOUT_MS = 8_000L
        private const val SYNC_TIMEOUT_MS = 15_000
        private const val TAG = "ShizukuWake"
        private const val LAUNCH_COMPONENT =
            "com.sec.android.app.shealth/com.samsung.android.app.shealth.home.HomeMainActivity"
    }
}
