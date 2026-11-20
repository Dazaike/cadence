package dev.dazaike.cadence.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.dazaike.cadence.data.DashboardLayoutRepository
import dev.dazaike.cadence.data.HealthDataSource
import dev.dazaike.cadence.data.Metric
import dev.dazaike.cadence.data.MetricDetail
import dev.dazaike.cadence.data.MetricSummary
import dev.dazaike.cadence.data.UnitPreferences
import dev.dazaike.cadence.samsung.SamsungHealthWake
import dev.dazaike.cadence.data.UnitSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val manager: HealthDataSource,
    private val layoutRepo: DashboardLayoutRepository,
    private val unitPrefs: UnitPreferences,
    private val samsungWake: SamsungHealthWake,
) : ViewModel() {
    private val _raw = MutableStateFlow<List<MetricSummary>?>(null)

    val summaries: StateFlow<List<MetricSummary>?> =
        combine(_raw, layoutRepo.layout) { raw, order ->
            raw?.let { list ->
                val byKey = list.associateBy { it.metric }
                order.mapNotNull { byKey[it] }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init {
        viewModelScope.launch {
            unitPrefs.unitSystem.collect {
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val system = unitPrefs.unitSystem.first()
                _raw.value = manager.readDashboard(system)
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun userRefresh() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                runCatching { samsungWake.wakeAndSettle() }
                val system = unitPrefs.unitSystem.first()
                _raw.value = manager.readDashboard(system)
            } finally {
                _refreshing.value = false
            }
        }
    }

    companion object {
        fun factory(
            manager: HealthDataSource,
            layoutRepo: DashboardLayoutRepository,
            unitPrefs: UnitPreferences,
            samsungWake: SamsungHealthWake,
        ) = viewModelFactory {
            initializer { DashboardViewModel(manager, layoutRepo, unitPrefs, samsungWake) }
        }
    }
}

class DetailViewModel(
    private val manager: HealthDataSource,
    private val metric: Metric,
    private val unitPrefs: UnitPreferences,
    private val samsungWake: SamsungHealthWake,
) : ViewModel() {
    private val _detail = MutableStateFlow<MetricDetail?>(null)
    val detail = _detail.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init {
        viewModelScope.launch {
            unitPrefs.unitSystem.collect {
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val system = unitPrefs.unitSystem.first()
                _detail.value = manager.readDetail(metric, system)
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun userRefresh() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                runCatching { samsungWake.wakeAndSettle() }
                val system = unitPrefs.unitSystem.first()
                _detail.value = manager.readDetail(metric, system)
            } finally {
                _refreshing.value = false
            }
        }
    }

    companion object {
        fun factory(
            manager: HealthDataSource,
            metric: Metric,
            unitPrefs: UnitPreferences,
            samsungWake: SamsungHealthWake,
        ) = viewModelFactory {
            initializer { DetailViewModel(manager, metric, unitPrefs, samsungWake) }
        }
    }
}
