package dev.dazaike.cadence.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.dazaike.cadence.analytics.DEFAULT_GOALS
import dev.dazaike.cadence.analytics.GoalKey
import dev.dazaike.cadence.analytics.Gran
import dev.dazaike.cadence.analytics.RangePref
import dev.dazaike.cadence.data.AppPreferences
import dev.dazaike.cadence.data.DashboardLayoutRepository
import dev.dazaike.cadence.data.GoalsRepository
import dev.dazaike.cadence.data.HealthDataSource
import dev.dazaike.cadence.data.HealthViewLoader
import dev.dazaike.cadence.data.Layout
import dev.dazaike.cadence.data.LoadedView
import dev.dazaike.cadence.data.Metric
import dev.dazaike.cadence.data.UnitPreferences
import dev.dazaike.cadence.data.normalized
import dev.dazaike.cadence.data.UnitSystem
import dev.dazaike.cadence.samsung.SamsungHealthWake
import dev.dazaike.cadence.ui.theme.ThemeMode
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val GOAL_METRICS = mapOf(
    GoalKey.STEPS to Metric.STEPS,
    GoalKey.ACTIVE_ENERGY to Metric.ACTIVE_CALORIES,
    GoalKey.EXERCISE to Metric.EXERCISE,
    GoalKey.DISTANCE to Metric.DISTANCE,
    GoalKey.FLOORS to Metric.FLOORS,
    GoalKey.HYDRATION to Metric.HYDRATION,
)

/**
 * Holds the on-device [dev.dazaike.cadence.analytics.HealthView] (loaded once, refreshed on demand)
 * and the display preferences, and applies every mutation the pages request.
 */
class AppViewModel(
    private val source: HealthDataSource,
    private val samsungWake: SamsungHealthWake,
    private val appPrefs: AppPreferences,
    private val unitPrefs: UnitPreferences,
    private val goalsRepo: GoalsRepository,
    private val layoutRepo: DashboardLayoutRepository,
) : ViewModel() {

    private val _loaded = MutableStateFlow<LoadedView?>(null)
    val loaded: StateFlow<LoadedView?> = _loaded.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _selectedDay = MutableStateFlow<LocalDate?>(null)
    val selectedDay: StateFlow<LocalDate?> = _selectedDay.asStateFlow()

    val prefs: StateFlow<UiPrefs?> = combine(appPrefs.settings, unitPrefs.unitSystem, goalsRepo.goals, layoutRepo.state) { settings, units, goals, layouts ->
        val canonical = GoalKey.entries.associateWith { key ->
            if (key == GoalKey.SLEEP) settings.sleepGoal
            else (goals[GOAL_METRICS.getValue(key)] ?: DEFAULT_GOALS.getValue(key).toFloat()).toDouble()
        }
        UiPrefs(settings, units, canonical, layouts.copy(layouts = layouts.layouts.map { it.normalized() }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        refresh(userInitiated = false)
    }

    /** Reads the data source again. [userInitiated] first wakes Samsung Health so its data is current. */
    fun refresh(userInitiated: Boolean = true) {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                if (userInitiated) runCatching { samsungWake.wakeAndSettle() }
                _loaded.value = HealthViewLoader.load(source) { partial -> _loaded.value = partial }
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** Called when the app returns to the foreground: re-read when the view is over a minute old. */
    fun refreshIfStale() {
        val at = _loaded.value?.loadedAt ?: return
        if (System.currentTimeMillis() - at > STALE_MS) refresh(userInitiated = false)
    }

    fun selectDay(day: LocalDate?) {
        _selectedDay.value = day
    }

    fun setRange(range: RangePref) = launch { appPrefs.setRange(range) }
    fun setGran(gran: Gran) = launch { appPrefs.setGran(gran) }
    fun setUnits(units: UnitSystem) = launch { unitPrefs.setUnitSystem(units) }
    fun setMaxHr(bpm: Int) = launch { appPrefs.setMaxHr(bpm) }
    fun setThemeMode(mode: ThemeMode) = launch { appPrefs.setThemeMode(mode) }
    fun setAccent(id: String) = launch { appPrefs.setAccent(id) }

    fun setGoal(key: GoalKey, value: Double) = launch {
        if (key == GoalKey.SLEEP) appPrefs.setSleepGoal(value) else goalsRepo.setGoal(GOAL_METRICS.getValue(key), value.toFloat())
    }

    fun resetGoals() = launch {
        GOAL_METRICS.forEach { (key, metric) -> goalsRepo.setGoal(metric, DEFAULT_GOALS.getValue(key).toFloat()) }
        appPrefs.setSleepGoal(DEFAULT_GOALS.getValue(GoalKey.SLEEP))
    }

    fun resetPreferences() = launch {
        appPrefs.reset()
        GOAL_METRICS.forEach { (key, metric) -> goalsRepo.setGoal(metric, DEFAULT_GOALS.getValue(key).toFloat()) }
        unitPrefs.setUnitSystem(UnitSystem.IMPERIAL)
        layoutRepo.clear()
    }

    fun updateLayout(id: String, change: (Layout) -> Layout) = launch { layoutRepo.update(id, change) }
    fun setActiveLayout(id: String) = launch { layoutRepo.setActive(id) }
    fun addLayout(name: String, copyFrom: String?) = launch { layoutRepo.add(name, copyFrom) }
    fun removeLayout(id: String) = launch { layoutRepo.remove(id) }
    fun resetLayout(id: String) = launch { layoutRepo.reset(id) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        private const val STALE_MS = 60_000L

        fun factory(
            source: HealthDataSource,
            samsungWake: SamsungHealthWake,
            appPrefs: AppPreferences,
            unitPrefs: UnitPreferences,
            goalsRepo: GoalsRepository,
            layoutRepo: DashboardLayoutRepository,
        ) = viewModelFactory {
            initializer { AppViewModel(source, samsungWake, appPrefs, unitPrefs, goalsRepo, layoutRepo) }
        }
    }
}
