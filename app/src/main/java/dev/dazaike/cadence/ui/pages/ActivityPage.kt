package dev.dazaike.cadence.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
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
import dev.dazaike.cadence.analytics.addDays
import dev.dazaike.cadence.analytics.bucketize
import dev.dazaike.cadence.analytics.dailySeries
import dev.dazaike.cadence.analytics.fmtDay
import dev.dazaike.cadence.analytics.goalHitRate
import dev.dazaike.cadence.analytics.mean
import dev.dazaike.cadence.analytics.pearson
import dev.dazaike.cadence.analytics.resolveRange
import dev.dazaike.cadence.analytics.scatterData
import dev.dazaike.cadence.analytics.vals
import dev.dazaike.cadence.analytics.weekdayAverages
import dev.dazaike.cadence.analytics.weekdayIndex
import dev.dazaike.cadence.ui.charts.BarsChart
import dev.dazaike.cadence.ui.charts.ChartPoint
import dev.dazaike.cadence.ui.charts.Heatmap
import dev.dazaike.cadence.ui.charts.ScatterDot
import dev.dazaike.cadence.ui.charts.ScatterPlot
import dev.dazaike.cadence.ui.charts.TrendChart
import dev.dazaike.cadence.ui.components.CadenceCard
import dev.dazaike.cadence.ui.components.Ring
import dev.dazaike.cadence.ui.components.Segmented
import dev.dazaike.cadence.ui.components.SelectMenu
import dev.dazaike.cadence.ui.components.Stat
import dev.dazaike.cadence.ui.state.LocalApp
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.CatColor
import dev.dazaike.cadence.ui.theme.tabular
import java.time.LocalDate
import java.util.Locale

private val TABS = listOf(
    MetricKey.STEPS, MetricKey.DISTANCE, MetricKey.ACTIVE_ENERGY, MetricKey.EXERCISE,
    MetricKey.FLOORS, MetricKey.TOTAL_ENERGY, MetricKey.HYDRATION,
)

private val GOAL_RINGS = listOf(MetricKey.STEPS, MetricKey.ACTIVE_ENERGY, MetricKey.EXERCISE, MetricKey.DISTANCE, MetricKey.FLOORS)

/** Candidates for the relationship explorer's y axis: [MetricKey.id]s plus the derived "sleep" and "resting". */
private val Y_KEYS = listOf("activeEnergy", "exercise", "floors", "distance", "sleep", "heartRate", "resting")

private fun yName(key: String): String = when (key) {
    "sleep" -> "Sleep"
    "resting" -> "Resting HR"
    else -> MetricKey.fromId(key)?.label ?: key
}

/** One chart point per bucket with the value converted to display units. */
private fun bucketPoints(view: HealthView, m: MetricKey, from: LocalDate, to: LocalDate, gran: Gran, fmt: Fmt): List<ChartPoint> =
    bucketize(dailySeries(view, m, from, to), gran).map { b ->
        ChartPoint(d = b.d, label = b.label, v = b.v?.let { fmt.c(m, it) }, n = b.n)
    }

/** Port of web `pages/Activity.tsx`. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityPage() {
    val app = LocalApp.current
    val view = app.view
    val fmt = app.fmt
    val color = CatColor.activity
    val c = Cadence.colors
    var metric by remember { mutableStateOf(MetricKey.STEPS) }
    var yKey by remember { mutableStateOf("activeEnergy") }
    var bars by remember { mutableStateOf(true) }

    val avail = TABS.filter { view.has(it) }
    val last = view.last
    if (avail.isEmpty() || last == null) {
        PageColumn {
            PageHeader("Activity")
            NoData("activity data")
        }
        return
    }

    val m = if (metric in avail) metric else avail.first()
    val gran = app.prefs.gran
    val range = resolveRange(view, app.prefs.range)
    val from = range.from
    val to = range.to
    val pick: (LocalDate) -> Unit = { app.actions.openDay(it) }

    val decimals = fmt.decimals(m)
    val unit = fmt.u(m)
    val data = seriesPoints(view, m, from, to, gran, fmt, withMa = true)
    val raw = vals(dailySeries(view, m, from, to))
    val goalCanon = m.goalKey?.let { app.prefs.goals[it] }
    val goal = goalCanon?.let { fmt.c(m, it) }
    val best = raw.maxOrNull()
    val hit = goalCanon?.let { goalHitRate(view, m, it, to, 30) }

    val weekly = bucketPoints(view, m, to.addDays(-7 * 12 + 1), to, Gran.WEEK, fmt)
    val monthly = bucketPoints(view, m, to.addDays(-364), to, Gran.MONTH, fmt)
    // Weekday bars carry no day of their own; the first matching date in the range keeps the readout's weekday right.
    val byWeekday = weekdayAverages(view, m, from, to).mapIndexed { i, x ->
        ChartPoint(d = from.addDays((i - from.weekdayIndex() + 7) % 7), label = x.name, v = x.v?.let { fmt.c(m, it) }, n = x.n)
    }

    val yOptions = Y_KEYS.filter { k ->
        when (k) {
            "sleep" -> view.sleep.isNotEmpty()
            "resting" -> view.heartDay.isNotEmpty() || view.has(MetricKey.RESTING_HR)
            else -> MetricKey.fromId(k)?.let { view.has(it) } == true
        }
    }
    val yk = if (yKey in yOptions) yKey else yOptions.firstOrNull()
    val yMetric = yk?.let { MetricKey.fromId(it) }
    val scat = if (yk == null) emptyList() else scatterData(view, m.id, yk, from, to).map { p ->
        ScatterDot(p.d, fmt.c(m, p.x), if (yk == "resting" || yMetric == null) p.y else fmt.c(yMetric, p.y))
    }
    val r = if (scat.size >= 14) pearson(scat.map { it.x }, scat.map { it.y }) else Double.NaN
    val yUnit = when {
        yk == "sleep" -> "h"
        yk == "resting" || yk == "heartRate" -> "bpm"
        yMetric != null -> fmt.u(yMetric)
        else -> ""
    }

    PageColumn {
        PageHeader("Activity", subtitle = "Steps, movement, energy and exercise", controls = { RangeBar() })
        Segmented(value = m, options = avail.map { it to it.label }, onChange = { metric = it }, contentDescription = "Metric")

        AdaptiveRow(
            listOf(3f, 1f),
            {
                CadenceCard(
                    Modifier.fillMaxHeight(),
                    title = "${m.label}${if (gran != Gran.DAY) " · average per day, by ${gran.id}" else ""}",
                    color = color,
                    expandable = true,
                    info = "Drag the handles under the chart to zoom and pan. Tap a point or bar to inspect it, then open that day. Days without a record are shown as gaps, never as zero.",
                    actions = {
                        Segmented(value = bars, options = listOf(true to "Bars", false to "Area"), onChange = { bars = it }, contentDescription = "Chart mode")
                    },
                ) { full ->
                    val h = if (full) 520.dp else 300.dp
                    if (bars) {
                        BarsChart(data, color, unit, decimals = decimals, goal = goal, brush = true, height = h, name = m.label, onPick = pick, highlight = app.selectedDay)
                    } else {
                        TrendChart(data, color, unit, decimals = decimals, goal = goal, brush = true, height = h, name = m.label, onPick = pick)
                    }
                }
            },
            {
                CadenceCard(Modifier.fillMaxHeight(), title = "Range summary", color = color) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Stat(
                            "Average / day",
                            if (raw.isNotEmpty()) fmt.c(m, mean(raw)) else null,
                            decimals = decimals,
                            unit = unit,
                            sub = "${raw.size} days with data",
                        )
                        Stat("Best day", best?.let { fmt.c(m, it) }, decimals = decimals, unit = unit)
                        if (hit != null) {
                            Stat("Goal hit rate · 30 d", Math.round(hit.rate * 100).toDouble(), unit = "%", sub = "${hit.n} days with data")
                        }
                    }
                }
            },
        )

        AdaptiveRow(
            listOf(1f, 1f, 1f),
            {
                CadenceCard(
                    Modifier.fillMaxHeight(),
                    title = "Weekly comparison · last 12 weeks",
                    color = color,
                    expandable = true,
                    info = "Each bar is the mean of recorded days in that Monday-start week, so partial weeks stay comparable.",
                ) { full ->
                    BarsChart(weekly, color, unit, decimals = decimals, goal = goal, height = if (full) 480.dp else 230.dp, name = "${m.label} / day", onPick = pick)
                }
            },
            {
                CadenceCard(Modifier.fillMaxHeight(), title = "Monthly comparison · last 12 months", color = color, expandable = true) { full ->
                    BarsChart(monthly, color, unit, decimals = decimals, goal = goal, height = if (full) 480.dp else 230.dp, name = "${m.label} / day", onPick = pick)
                }
            },
            {
                CadenceCard(Modifier.fillMaxHeight(), title = "By weekday", color = color, info = "Average for each weekday inside the selected range.") {
                    BarsChart(byWeekday, color, unit, decimals = decimals, height = 230.dp, name = m.label)
                }
            },
        )

        AdaptiveRow(
            listOf(1f, 1f),
            {
                CadenceCard(
                    Modifier.fillMaxHeight(),
                    title = "Consistency · ${m.label}",
                    color = color,
                    info = "Intensity is relative to your goal for this metric (or the best day when there is no goal).",
                ) {
                    Heatmap(
                        values = view.daily[m],
                        to = last,
                        goal = goalCanon ?: best ?: 1.0,
                        unit = m.unit,
                        label = "${m.label} per day",
                        weeks = 30,
                        color = color,
                        onPick = pick,
                        selected = app.selectedDay,
                        firstDay = view.first,
                    )
                }
            },
            {
                CadenceCard(
                    Modifier.fillMaxHeight(),
                    title = "Relationship explorer",
                    color = color,
                    info = "Each dot is one day. A pattern here is a correlation only; it does not show that one metric causes the other.",
                    actions = {
                        if (yk != null) {
                            SelectMenu(selected = yk, options = yOptions.map { it to yName(it) }, onSelect = { yKey = it }, contentDescription = "Compare with")
                        }
                    },
                ) {
                    if (yk != null && scat.size >= 5) {
                        ScatterPlot(scat, color, xLabel = m.label, yLabel = yName(yk), xUnit = unit, yUnit = yUnit, onPick = pick)
                        Text(
                            if (r.isFinite()) {
                                "r = ${String.format(Locale.US, "%.2f", r)} over ${scat.size} days (correlation, not causation)."
                            } else {
                                "${scat.size} days; too few for a correlation estimate."
                            },
                            style = Cadence.type.caption1Regular,
                            color = c.textSecondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else {
                        Text(
                            "Not enough overlapping days in this range (${scat.size}).",
                            style = Cadence.type.bodyRegular,
                            color = c.textSecondary,
                            modifier = Modifier.padding(vertical = 40.dp),
                        )
                    }
                }
            },
        )

        CadenceCard(title = "Goal progress · latest day", color = color) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (k in GOAL_RINGS) {
                    val v = view.daily[k]?.get(last) ?: continue
                    val g = k.goalKey?.let { app.prefs.goals[it] } ?: continue
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Ring(value = v, max = g, color = color, size = 78.dp, stroke = 8.dp) {
                            Text(
                                "${if (g > 0) Math.round(v / g * 100) else 0}%",
                                style = Cadence.type.bodySemibold.tabular(),
                                color = c.textPrimary,
                            )
                        }
                        Column {
                            Text(k.label, style = Cadence.type.caption1Medium, color = c.textPrimary)
                            Text("${fmt.f(k, v)} of ${fmt.f(k, g)}", style = Cadence.type.caption1Regular, color = c.textSecondary)
                        }
                    }
                }
            }
            Text(
                fmtDay(last, true) + if (view.asOf == last) " (day in progress)" else "",
                style = Cadence.type.caption1Regular,
                color = c.textSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
