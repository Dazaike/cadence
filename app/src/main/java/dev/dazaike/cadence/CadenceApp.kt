package dev.dazaike.cadence

import android.app.Application
import dev.dazaike.cadence.cloud.CloudApi
import dev.dazaike.cadence.data.AppPreferences
import dev.dazaike.cadence.cloud.CloudPreferences
import dev.dazaike.cadence.cloud.CloudSyncManager
import dev.dazaike.cadence.data.DashboardLayoutRepository
import dev.dazaike.cadence.data.ExportManager
import dev.dazaike.cadence.data.GoalsRepository
import dev.dazaike.cadence.data.HealthConnectManager
import dev.dazaike.cadence.data.DataSource
import dev.dazaike.cadence.data.DataSourcePreferences
import dev.dazaike.cadence.data.HealthDataSource
import dev.dazaike.cadence.mcp.McpServerPreferences
import dev.dazaike.cadence.data.WidgetPreferences
import dev.dazaike.cadence.data.UnitPreferences
import dev.dazaike.cadence.samsung.SamsungHealthManager
import dev.dazaike.cadence.samsung.SamsungHealthWake
import dev.dazaike.cadence.samsung.ShizukuWake
import dev.dazaike.cadence.widget.WidgetUpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CadenceApp : Application() {
    val healthConnect: HealthConnectManager by lazy { HealthConnectManager(this) }
    val appPrefs: AppPreferences by lazy { AppPreferences(this) }
    val samsungHealth: SamsungHealthManager by lazy { SamsungHealthManager(this) }
    val shizukuWake: ShizukuWake by lazy { ShizukuWake(this) }
    val samsungWake: SamsungHealthWake by lazy { SamsungHealthWake(this, shizukuWake) }
    val dataSourcePrefs: DataSourcePreferences by lazy { DataSourcePreferences(this) }
    val goals: GoalsRepository by lazy { GoalsRepository(this) }
    val exporter: ExportManager by lazy { ExportManager(this, ::activeDataSource, goals, unitPrefs) }
    val dashboardLayout: DashboardLayoutRepository by lazy { DashboardLayoutRepository(this) }
    val widgetPrefs: WidgetPreferences by lazy { WidgetPreferences(this) }
    val mcpPrefs: McpServerPreferences by lazy { McpServerPreferences(this) }
    val unitPrefs: UnitPreferences by lazy { UnitPreferences(this) }
    val cloudPrefs: CloudPreferences by lazy { CloudPreferences(this) }
    val cloudSync: CloudSyncManager by lazy { CloudSyncManager(this, cloudPrefs, CloudApi(), goals, ::activeDataSource) }

    suspend fun activeDataSource(): HealthDataSource =
        if (dataSourcePrefs.source.first() == DataSource.SAMSUNG_HEALTH) samsungHealth else healthConnect

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.Default).launch {
            WidgetUpdateScheduler.schedule(this@CadenceApp, widgetPrefs.refreshIntervalMinutes.first().toLong())
            cloudSync.ensureScheduled()
        }
        // Push goal edits to the cloud (no-op unless signed in with auto-sync on and goals changed).
        CoroutineScope(Dispatchers.Default).launch {
            goals.goals.distinctUntilChanged().collect { cloudSync.pushGoalsIfChanged() }
        }
    }
}
