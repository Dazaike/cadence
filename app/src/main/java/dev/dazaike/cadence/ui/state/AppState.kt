package dev.dazaike.cadence.ui.state

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.analytics.Fmt
import dev.dazaike.cadence.analytics.Gran
import dev.dazaike.cadence.analytics.GoalKey
import dev.dazaike.cadence.analytics.Goals
import dev.dazaike.cadence.analytics.HealthView
import dev.dazaike.cadence.analytics.RangePref
import dev.dazaike.cadence.cloud.CloudSyncManager
import dev.dazaike.cadence.data.AppSettings
import dev.dazaike.cadence.data.DataSource
import dev.dazaike.cadence.data.DataSourcePreferences
import dev.dazaike.cadence.data.ExportManager
import dev.dazaike.cadence.data.HealthDataSource
import dev.dazaike.cadence.data.Layout
import dev.dazaike.cadence.data.LayoutState
import dev.dazaike.cadence.data.LoadedView
import dev.dazaike.cadence.data.Metric
import dev.dazaike.cadence.data.UnitSystem
import dev.dazaike.cadence.data.WidgetPreferences
import dev.dazaike.cadence.mcp.McpServerPreferences
import dev.dazaike.cadence.ui.theme.ThemeMode
import java.time.LocalDate

/** The web's sections, in sidebar order (`ROUTES` in `store.ts`). */
enum class Route(val label: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Rounded.Dashboard),
    ACTIVITY("Activity", Icons.Rounded.MonitorHeart),
    HEART("Heart", Icons.Rounded.Favorite),
    SLEEP("Sleep", Icons.Rounded.Bedtime),
    BODY("Body", Icons.Rounded.MonitorWeight),
    INSIGHTS("Insights", Icons.Rounded.Lightbulb),
    CALENDAR("Calendar", Icons.Rounded.CalendarMonth),
    SOURCES("Data Sources", Icons.Rounded.Storage),
    SETTINGS("Settings", Icons.Rounded.Settings);

    companion object {
        /** Phone bottom bar tabs (the rest live under More), as in the web's mobile bar. */
        val TABS = listOf(OVERVIEW, ACTIVITY, HEART, SLEEP, INSIGHTS)
        val MORE = entries.filter { it !in TABS }

        /** Where a widget tap on [metric] lands. */
        fun forMetric(metric: Metric): Route = when (metric) {
            Metric.STEPS, Metric.DISTANCE, Metric.ACTIVE_CALORIES, Metric.TOTAL_CALORIES, Metric.FLOORS, Metric.EXERCISE -> ACTIVITY
            Metric.HEART_RATE, Metric.RESTING_HR, Metric.HRV, Metric.OXYGEN, Metric.RESPIRATORY, Metric.VO2MAX, Metric.BLOOD_PRESSURE -> HEART
            Metric.SLEEP -> SLEEP
            Metric.WEIGHT, Metric.HEIGHT, Metric.HYDRATION, Metric.BLOOD_GLUCOSE -> BODY
        }
    }
}

/** Everything a page reads besides the health view (web `useStore` prefs). */
@Stable
class UiPrefs(
    val settings: AppSettings,
    val units: UnitSystem,
    /** Goals in canonical units (km, L, h), the sleep goal included. */
    val goals: Goals,
    val layouts: LayoutState,
) {
    val range: RangePref get() = settings.range
    val gran: Gran get() = settings.gran
    val maxHr: Int get() = settings.maxHr
    val layout: Layout get() = layouts.active
}

/** Long-lived objects the Data Sources and Settings pages drive directly. */
class AppServices(
    val manager: HealthDataSource,
    val exporter: ExportManager,
    val cloudSync: CloudSyncManager,
    val mcpPrefs: McpServerPreferences,
    val widgetPrefs: WidgetPreferences,
    val dataSourcePrefs: DataSourcePreferences,
    val source: DataSource,
    /** Opens the platform permission flow for the active data source. */
    val onManagePermissions: () -> Unit,
)

/** Every mutation a page can make; implemented by the shell on top of the view model. */
interface AppActions {
    fun navigate(route: Route)

    /** Select [day] and open the Calendar (web `setPrefs({selectedDay}); setRoute('calendar')`). */
    fun openDay(day: LocalDate)

    /** Select [day] without leaving the page. */
    fun selectDay(day: LocalDate?)

    fun setRange(range: RangePref)
    fun setGran(gran: Gran)
    fun setUnits(units: UnitSystem)

    /** [value] is in canonical units (km, L, h). */
    fun setGoal(key: GoalKey, value: Double)
    fun resetGoals()
    fun setMaxHr(bpm: Int)
    fun setThemeMode(mode: ThemeMode)
    fun setAccent(id: String)

    /** Appearance, range, goals, max HR and layouts return to defaults. Health data is untouched. */
    fun resetPreferences()

    fun updateLayout(id: String, change: (Layout) -> Layout)

    /** Re-reads the health data source. */
    fun refresh()
}

/** What every page receives through [LocalApp]. */
@Stable
class AppCtx(
    val view: HealthView,
    val loaded: LoadedView,
    val prefs: UiPrefs,
    val selectedDay: LocalDate?,
    val refreshing: Boolean,
    val services: AppServices,
    val actions: AppActions,
) {
    val fmt = Fmt(prefs.units)
}

val LocalApp = staticCompositionLocalOf<AppCtx> { error("AppCtx missing") }

/** True on tablets, foldables and landscape (width >= 840 dp): rail navigation, multi-column pages. */
val LocalWide = compositionLocalOf { false }

/** Space the floating bottom bar occupies; pages pad their scroll content with it. */
val LocalBottomInset = compositionLocalOf<Dp> { 0.dp }
