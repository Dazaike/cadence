package dev.dazaike.cadence.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dazaike.cadence.data.HealthDataSource
import dev.dazaike.cadence.data.DashboardLayoutRepository
import dev.dazaike.cadence.data.UnitPreferences
import dev.dazaike.cadence.data.Metric
import dev.dazaike.cadence.CadenceApp
import dev.dazaike.cadence.ui.DashboardViewModel
import dev.dazaike.cadence.ui.components.MetricCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    manager: HealthDataSource,
    layoutRepo: DashboardLayoutRepository,
    unitPrefs: UnitPreferences,
    bottomInset: androidx.compose.ui.unit.Dp,
    onOpenMetric: (Metric) -> Unit,
    onManagePermissions: () -> Unit,
    onCustomize: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as CadenceApp
    val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(manager, layoutRepo, unitPrefs, app.samsungWake))
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Cadence") },
                actions = {
                    IconButton(onClick = onCustomize) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Customize dashboard")
                    }
                    IconButton(onClick = vm::userRefresh) {
                        if (refreshing) {
                            CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        val data = summaries
        if (data == null) {
            // initial load handled by spinner in app bar; keep grid space empty
            return@Scaffold
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = vm::userRefresh,
            modifier = Modifier.fillMaxSize().padding(top = inner.calculateTopPadding()),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = 16.dp,
                    bottom = bottomInset + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(data, key = { it.metric.key }) { summary ->
                    MetricCard(
                        summary = summary,
                        onClick = {
                            if (summary.granted) onOpenMetric(summary.metric) else onManagePermissions()
                        },
                    )
                }
            }
        }
    }
}
