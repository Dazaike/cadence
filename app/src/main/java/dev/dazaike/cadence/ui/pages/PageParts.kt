package dev.dazaike.cadence.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.analytics.Fmt
import dev.dazaike.cadence.analytics.Gran
import dev.dazaike.cadence.analytics.HealthView
import dev.dazaike.cadence.analytics.MetricKey
import dev.dazaike.cadence.analytics.RangePref
import dev.dazaike.cadence.analytics.RangePreset
import dev.dazaike.cadence.analytics.bucketize
import dev.dazaike.cadence.analytics.dailySeries
import dev.dazaike.cadence.analytics.fmtDay
import dev.dazaike.cadence.analytics.movingAverage
import dev.dazaike.cadence.analytics.resolveRange
import dev.dazaike.cadence.ui.charts.ChartPoint
import dev.dazaike.cadence.ui.components.ButtonKind
import dev.dazaike.cadence.ui.components.CadenceButton
import dev.dazaike.cadence.ui.components.Segmented
import dev.dazaike.cadence.ui.components.cardSurface
import dev.dazaike.cadence.ui.state.LocalApp
import dev.dazaike.cadence.ui.state.LocalBottomInset
import dev.dazaike.cadence.ui.state.LocalWide
import dev.dazaike.cadence.ui.state.Route
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.Dimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Scrolling page body: 16 dp side padding, 20 dp gaps (web `grid gap-5`), clear of the floating
 * bottom bar, and capped at the web's 1400 px max width on very wide screens.
 */
@Composable
fun PageColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val inset = LocalBottomInset.current
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier
                .widthIn(max = 1400.dp)
                .fillMaxWidth()
                .padding(start = Dimens.PageHPad, end = Dimens.PageHPad, top = 16.dp, bottom = inset + 24.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.PageGap),
            content = content,
        )
    }
}

/** Page title with a subtitle line and an optional control row underneath (web `PageHeader`). */
@Composable
fun PageHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null, controls: @Composable (() -> Unit)? = null) {
    val c = Cadence.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(title, style = Cadence.type.title1Semibold, color = c.textPrimary)
                if (subtitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, style = Cadence.type.bodyRegular, color = c.textSecondary)
                }
            }
            trailing?.invoke()
        }
        controls?.invoke()
    }
}

/** Shown when a page's metric family has no data at all (web `NoData`). Nothing is estimated. */
@Composable
fun NoData(what: String, modifier: Modifier = Modifier) {
    val c = Cadence.colors
    val app = LocalApp.current
    Column(modifier.fillMaxWidth().cardSurface().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("No $what in this dataset", style = Cadence.type.headlineSemibold, color = c.textPrimary)
        Text(
            "Nothing is estimated or filled in for missing data. Make sure Cadence can read $what from your health data source, then refresh.",
            style = Cadence.type.bodyRegular, color = c.textSecondary,
        )
        CadenceButton("Go to Data Sources", onClick = { app.actions.navigate(Route.SOURCES) }, modifier = Modifier.padding(top = 8.dp))
    }
}

/**
 * Lays [items] out in a row with the given [weights] on wide screens and stacks them on phones.
 * Heights are equalised in a row so neighbouring cards line up.
 */
@Composable
fun AdaptiveRow(weights: List<Float>, vararg items: @Composable () -> Unit) {
    if (LocalWide.current) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Dimens.PageGap)) {
            items.forEachIndexed { i, item -> Box(Modifier.weight(weights.getOrElse(i) { 1f })) { item() } }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.PageGap)) { items.forEach { it() } }
    }
}

/**
 * The web `RangeBar`: range presets, optional granularity and the resolved dates. Custom opens a
 * date-range picker.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RangeBar(modifier: Modifier = Modifier, showGran: Boolean = true) {
    val app = LocalApp.current
    val c = Cadence.colors
    val range = app.prefs.range
    val resolved = resolveRange(app.view, range)
    var picking by remember { mutableStateOf(false) }

    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        Segmented(
            value = range.preset,
            options = RangePreset.entries.map { it to it.label },
            onChange = { preset ->
                app.actions.setRange(if (preset == RangePreset.CUSTOM) RangePref(preset, resolved.from, resolved.to) else RangePref(preset))
                if (preset == RangePreset.CUSTOM) picking = true
            },
            contentDescription = "Range",
        )
        if (showGran) {
            Segmented(
                value = app.prefs.gran,
                options = Gran.entries.map { it to it.label },
                onChange = { app.actions.setGran(it) },
                contentDescription = "Granularity",
            )
        }
        if (range.preset == RangePreset.CUSTOM) {
            CadenceButton("Pick dates", onClick = { picking = true }, kind = ButtonKind.SECONDARY, small = true)
        }
        Text("${fmtDay(resolved.from, true)} to ${fmtDay(resolved.to, true)}", style = Cadence.type.caption1Regular, color = c.textSecondary)
    }

    if (picking) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = resolved.from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = resolved.to.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                CadenceButton(
                    "Apply",
                    onClick = {
                        val a = state.selectedStartDateMillis
                        val b = state.selectedEndDateMillis
                        if (a != null && b != null) {
                            app.actions.setRange(RangePref(RangePreset.CUSTOM, utcDay(a), utcDay(b)))
                        }
                        picking = false
                    },
                    small = true,
                    enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                )
            },
            dismissButton = { CadenceButton("Cancel", onClick = { picking = false }, kind = ButtonKind.SECONDARY, small = true) },
        ) {
            DateRangePicker(state, modifier = Modifier.height(520.dp))
        }
    }
}

private fun utcDay(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

/** Port of the web `seriesPoints` helper: a metric over a range, bucketed, converted to display units. */
fun seriesPoints(view: HealthView, metric: MetricKey, from: LocalDate, to: LocalDate, gran: Gran, fmt: Fmt, withMa: Boolean = false): List<ChartPoint> {
    val pts = dailySeries(view, metric, from, to)
    val b = bucketize(pts, gran)
    val ma = if (gran == Gran.DAY && withMa) movingAverage(pts, 7) else emptyList()
    return b.mapIndexed { i, x ->
        ChartPoint(
            d = x.d,
            label = x.label,
            v = x.v?.let { fmt.c(metric, it) },
            n = x.n,
            ma = ma.getOrNull(i)?.let { fmt.c(metric, it) },
        )
    }
}
