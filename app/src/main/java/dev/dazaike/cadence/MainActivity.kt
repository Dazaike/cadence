package dev.dazaike.cadence

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import dev.dazaike.cadence.data.AppSettings
import dev.dazaike.cadence.ui.CadenceRoot
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.CadenceTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Deep-link target from a metric widget tap; consumed once navigation happens.
    private var deepLinkMetric by mutableStateOf<String?>(null)

    // Appearance is read before the first frame so a saved Light/Dark choice never flashes the other theme.
    private var settings by mutableStateOf<AppSettings?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { settings == null }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkMetric = intent?.getStringExtra(EXTRA_METRIC)
        val app = application as CadenceApp
        lifecycleScope.launch { app.appPrefs.settings.collect { settings = it } }
        setContent {
            val current = settings
            if (current != null) {
                CadenceTheme(mode = current.themeMode, accentId = current.accentId) {
                    Box(Modifier.fillMaxSize().background(Cadence.colors.pageBg)) {
                        CadenceRoot(
                            healthConnect = app.healthConnect,
                            samsungHealth = app.samsungHealth,
                            goalsRepo = app.goals,
                            exporter = app.exporter,
                            dashboardLayout = app.dashboardLayout,
                            widgetPrefs = app.widgetPrefs,
                            mcpPrefs = app.mcpPrefs,
                            dataSourcePrefs = app.dataSourcePrefs,
                            unitPrefs = app.unitPrefs,
                            cloudSync = app.cloudSync,
                            deepLinkMetric = deepLinkMetric,
                            onDeepLinkConsumed = { deepLinkMetric = null },
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as CadenceApp).samsungWake.activity = this
        // Opening the app counts as a sync trigger (throttled and gated on sign-in inside).
        lifecycleScope.launch { (application as CadenceApp).cloudSync.syncOnOpen() }
    }

    override fun onStop() {
        (application as CadenceApp).samsungWake.let { if (it.activity === this) it.activity = null }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkMetric = intent.getStringExtra(EXTRA_METRIC)
    }

    companion object {
        const val EXTRA_METRIC = "dev.dazaike.cadence.extra.METRIC"
    }
}
