package dev.dazaike.cadence.samsung

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.os.Looper
import android.view.Surface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Shizuku user service: runs as the `shell` user, in its own process, never inside the app.
 *
 * Samsung Health only syncs the watch while one of its screens is resumed, and Android only lets
 * the shell user put an activity on a virtual display it owns. So this creates a hidden virtual
 * display, starts Samsung Health on it, waits for the watch sync to finish (read from logcat, which
 * the shell user may read), then tears the display down. Nothing is shown on the real screen.
 *
 * Keep this class free of references to the rest of the app: it is instantiated by Shizuku.
 */
@SuppressLint("PrivateApi", "SoonBlockedPrivateApi", "DiscouragedPrivateApi")
class ShellWakeService : IWakeService.Stub() {

    @Volatile
    private var systemContext: Context? = null

    override fun destroy() {
        exitProcess(0)
    }

    override fun syncOnHiddenDisplay(component: String, timeoutMs: Int): Boolean {
        var result = false
        var failure: Throwable? = null
        // DisplayManager needs a Looper on the calling thread; binder threads don't have one.
        val worker = thread(name = "cadence-wake") {
            try {
                if (Looper.myLooper() == null) {
                    // prepareMainLooper() creates this thread's Looper before it notices a main one exists.
                    try { Looper.prepareMainLooper() } catch (_: Throwable) { if (Looper.myLooper() == null) Looper.prepare() }
                }
                result = runSync(component, timeoutMs)
            } catch (t: Throwable) {
                failure = t
            }
        }
        worker.join()
        failure?.let { throw IllegalStateException("Hidden-display sync failed: ${it.message}", it) }
        return result
    }

    private fun runSync(component: String, timeoutMs: Int): Boolean {
        val display = createDisplay()
        val syncDone = AtomicBoolean(false)
        val logcat = ProcessBuilder(
            "logcat", "-b", "main", "-v", "brief", "-T", SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date()), "-s", "$SYNC_TAG:I",
        ).redirectErrorStream(true).start()
        val reader = thread(name = "cadence-wake-log") {
            BufferedReader(InputStreamReader(logcat.inputStream)).use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    if (line.contains("SYNC_FLOW_MANAGE") && line.contains("RESULT")) {
                        syncDone.set(true)
                        break
                    }
                }
            }
        }
        try {
            val out = exec("am", "start", "--display", display.display.displayId.toString(), "-n", component)
            if (out.contains("Error", ignoreCase = true) || out.contains("Exception")) {
                throw IllegalStateException("Could not start Samsung Health: ${out.trim().take(200)}")
            }
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!syncDone.get() && System.currentTimeMillis() < deadline) Thread.sleep(150)
            // The result line is logged right after the data is written; leave a moment for it to land.
            if (syncDone.get()) Thread.sleep(SETTLE_AFTER_SYNC_MS)
            return syncDone.get()
        } finally {
            logcat.destroy()
            reader.interrupt()
            display.release()
        }
    }

    private fun createDisplay(): android.hardware.display.VirtualDisplay {
        val ctx = shellContext()
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val texture = SurfaceTexture(0).apply { setDefaultBufferSize(WIDTH, HEIGHT) }
        val method = DisplayManager::class.java.getMethod(
            "createVirtualDisplay", String::class.java, Int::class.java, Int::class.java, Int::class.java,
            Surface::class.java, Int::class.java,
        )
        return method.invoke(dm, "cadence-wake", WIDTH, HEIGHT, DPI, Surface(texture), DISPLAY_FLAGS)
            as android.hardware.display.VirtualDisplay
    }

    /** A context that claims the `shell` package, which is what the display service expects of uid 2000. */
    private fun shellContext(): Context {
        val base = systemContext ?: run {
            val at = Class.forName("android.app.ActivityThread")
            val thread = runCatching { at.getMethod("currentActivityThread").invoke(null) }.getOrNull()
                ?: at.getMethod("systemMain").invoke(null)
            (at.getMethod("getSystemContext").invoke(thread) as Context).also { systemContext = it }
        }
        return object : ContextWrapper(base) {
            override fun getPackageName() = SHELL_PACKAGE
            override fun getOpPackageName() = SHELL_PACKAGE
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? {
                if (name != Context.DISPLAY_SERVICE) return super.getSystemService(name)
                val ctor = DisplayManager::class.java.getDeclaredConstructor(Context::class.java)
                ctor.isAccessible = true
                return ctor.newInstance(this)
            }
        }
    }

    private fun exec(vararg cmd: String): String {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return text
    }

    private companion object {
        const val SHELL_PACKAGE = "com.android.shell"
        const val SYNC_TAG = "SHEALTH#WI#WearableSyncRequestManagerInternal"
        const val SETTLE_AFTER_SYNC_MS = 1_200L
        const val WIDTH = 1080
        const val HEIGHT = 1920
        const val DPI = 420

        // OWN_CONTENT_ONLY | SUPPORTS_TOUCH | DESTROY_CONTENT_ON_REMOVAL | TRUSTED | OWN_DISPLAY_GROUP |
        // ALWAYS_UNLOCKED | OWN_FOCUS: a private display that nothing mirrors onto the real screen.
        const val DISPLAY_FLAGS = (1 shl 3) or (1 shl 6) or (1 shl 8) or (1 shl 10) or (1 shl 11) or (1 shl 12) or (1 shl 14)
    }
}
