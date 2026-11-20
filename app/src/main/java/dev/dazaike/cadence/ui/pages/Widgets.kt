package dev.dazaike.cadence.ui.pages

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.analytics.GoalKey
import dev.dazaike.cadence.analytics.Gran
import dev.dazaike.cadence.analytics.HealthView
import dev.dazaike.cadence.analytics.MetricKey
import dev.dazaike.cadence.analytics.addDays
import dev.dazaike.cadence.analytics.bmi
import dev.dazaike.cadence.analytics.dayKey
import dev.dazaike.cadence.analytics.fmtClock
import dev.dazaike.cadence.analytics.fmtDay
import dev.dazaike.cadence.analytics.fmtDayLong
import dev.dazaike.cadence.analytics.fmtDuration
import dev.dazaike.cadence.analytics.generateInsights
import dev.dazaike.cadence.analytics.heightAt
import dev.dazaike.cadence.analytics.mean
import dev.dazaike.cadence.analytics.minuteOfDay
import dev.dazaike.cadence.analytics.periodCompare
import dev.dazaike.cadence.analytics.readiness
import dev.dazaike.cadence.analytics.sleepConsistency
import dev.dazaike.cadence.ui.charts.BarsChart
import dev.dazaike.cadence.ui.charts.ChartPoint
import dev.dazaike.cadence.ui.charts.Gauge
import dev.dazaike.cadence.ui.charts.Heatmap
import dev.dazaike.cadence.ui.charts.Sparkline
import dev.dazaike.cadence.ui.components.ButtonKind
import dev.dazaike.cadence.ui.components.CadenceButton
import dev.dazaike.cadence.ui.components.DeltaText
import dev.dazaike.cadence.ui.components.Empty
import dev.dazaike.cadence.ui.components.LinearMeter
import dev.dazaike.cadence.ui.components.Pill
import dev.dazaike.cadence.ui.components.PillTone
import dev.dazaike.cadence.ui.components.Ring
import dev.dazaike.cadence.ui.components.Stat
import dev.dazaike.cadence.ui.components.StatusDot
import dev.dazaike.cadence.analytics.Category
import dev.dazaike.cadence.ui.state.LocalApp
import dev.dazaike.cadence.ui.state.Route
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.CatColor
import dev.dazaike.cadence.ui.theme.tabular
import dev.dazaike.cadence.analytics.Tone

/** One Overview widget (web `WidgetDef`). [content] is rendered inside a card by the Overview grid. */
class WidgetDef(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val info: String?,
    val content: @Composable () -> Unit,
)

/** Below this inner width a widget switches to its stacked, compact form (a half-width phone card). */
private val NARROW = 230.dp

/** Outer width of the card a widget is shown in, set by the Overview grid; unspecified when shown full screen. */
val LocalWidgetWidth = compositionLocalOf { Dp.Unspecified }

@Composable
private fun Responsive(content: @Composable (narrow: Boolean) -> Unit) {
    val w = LocalWidgetWidth.current
    content(w != Dp.Unspecified && w - 40.dp < NARROW)
}

private fun roundedDay(view: HealthView, t: Long) = dayKey(t, view.zone)

/** Two stats per row, or one when [narrow]. */
@Composable
private fun StatGrid(narrow: Boolean, items: List<@Composable (Modifier) -> Unit>) {
    val perRow = if (narrow) 1 else 2
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { it(Modifier.weight(1f)) }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DailyWidget() {
    val app = LocalApp.current
    val view = app.view
    val c = Cadence.colors
    val goals = app.prefs.goals
    val d = view.last
    class RingDef(val m: MetricKey, val g: GoalKey, val color: Color)
    val rings = listOf(
        RingDef(MetricKey.STEPS, GoalKey.STEPS, CatColor.activity),
        RingDef(MetricKey.ACTIVE_ENERGY, GoalKey.ACTIVE_ENERGY, CatColor.heart),
        RingDef(MetricKey.EXERCISE, GoalKey.EXERCISE, CatColor.recovery),
    )
    val have = rings.filter { d != null && view.daily[it.m]?.get(d) != null }
    if (d == null || have.isEmpty()) {
        Empty("No activity recorded", hint = "Allow access to activity data in Data Sources to see today’s rings.")
        return
    }
    val completion = mean(have.map { minOf(1.0, view.daily.getValue(it.m).getValue(d) / goals.getValue(it.g)) })
    val partial = view.asOf == d
    Responsive { narrow ->
        Column {
            val ring: @Composable () -> Unit = {
                Ring(completion, 1.0, c.accent.c600, size = 104.dp, stroke = 11.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${Math.round(completion * 100)}%", style = Cadence.type.title3Semibold.tabular(), color = c.textPrimary)
                        Text("of goals", style = Cadence.type.caption1Regular, color = c.textSecondary)
                    }
                }
            }
            val meters: @Composable (Modifier) -> Unit = { mod ->
                Column(mod, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    have.forEach { r ->
                        val v = view.daily.getValue(r.m).getValue(d)
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(r.m.label, style = Cadence.type.caption1Regular, color = c.textSecondary, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                Text(app.fmt.f(r.m, v), style = Cadence.type.caption1Regular.tabular(), color = c.textPrimary, maxLines = 1)
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearMeter(v / goals.getValue(r.g), r.color, height = 6.dp)
                        }
                    }
                }
            }
            if (narrow) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ring() }
                Spacer(Modifier.height(12.dp))
                meters(Modifier.fillMaxWidth())
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ring()
                    meters(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(fmtDayLong(d) + if (partial) " · day in progress" else "", style = Cadence.type.caption1Regular, color = c.textSecondary)
        }
    }
}

@Composable
private fun StepsWidget() {
    val app = LocalApp.current
    val view = app.view
    val last = view.last
    if (last == null || !view.has(MetricKey.STEPS)) {
        Empty("No step data")
        return
    }
    val data = seriesPoints(view, MetricKey.STEPS, last.addDays(-13), last, Gran.DAY, app.fmt)
    BarsChart(
        data, CatColor.activity, "steps", goal = app.fmt.c(MetricKey.STEPS, app.prefs.goals.getValue(GoalKey.STEPS)),
        height = 190.dp, name = "Steps", onPick = app.actions::openDay,
    )
}

@Composable
private fun HeartWidget() {
    val app = LocalApp.current
    val view = app.view
    val c = Cadence.colors
    val latest = view.latest(MetricKey.HEART_RATE)
    if (latest == null || view.hrByDay.isEmpty()) {
        Empty("No heart-rate data", hint = "Heart rate is only available when the source includes it.")
        return
    }
    val d = view.hrByDay.keys.max()
    val day = view.heartDay.getValue(d)
    val rest = view.resting(d)
    val spark = view.hrByDay[d].orEmpty().takeLast(24).map { it.v }
    Responsive { narrow ->
        Column {
            StatGrid(
                narrow,
                listOf(
                    { m -> Stat("Latest", Math.round(latest.v).toDouble(), m, unit = "bpm", sub = "${fmtDay(roundedDay(view, latest.t))}, ${fmtClock(minuteOfDay(latest.t, view.zone))}", color = CatColor.heart) },
                    { m -> Stat(if (rest?.derived == true) "Lowest hourly" else "Resting", rest?.let { Math.round(it.v).toDouble() }, m, unit = "bpm", sub = if (rest?.derived == true) "proxy for resting" else "measured") },
                    { m -> Stat("Average", Math.round(day.avg).toDouble(), m, unit = "bpm") },
                    { m -> Stat("Range", null, m, text = "${Math.round(day.min)}–${Math.round(day.max)} bpm") },
                ),
            )
            if (spark.size > 3) {
                Spacer(Modifier.height(8.dp))
                Sparkline(spark, CatColor.heart, height = 70.dp)
            }
            Text("Hourly averages", style = Cadence.type.caption2Medium, color = c.textTertiary)
        }
    }
}

@Composable
private fun SleepWidget() {
    val app = LocalApp.current
    val view = app.view
    val c = Cadence.colors
    val nights = view.sleep.values.sortedBy { it.d }
    if (nights.isEmpty()) {
        Empty("No sleep recorded")
        return
    }
    val last = nights.last()
    val cons = sleepConsistency(view, last.d)
    val data = nights.takeLast(14).map { ChartPoint(it.d, fmtDay(it.d), it.hours) }
    Responsive { narrow ->
        Column {
            StatGrid(
                narrow,
                listOf(
                    { m -> Stat("Last sleep", Math.round(last.hours * 10) / 10.0, m, decimals = 1, unit = "h", sub = fmtDuration(last.hours), color = CatColor.sleep) },
                    { m -> Stat("Schedule", null, m, text = "${fmtClock(minuteOfDay(last.bed, view.zone))} → ${fmtClock(minuteOfDay(last.wake, view.zone))}") },
                    { m -> Stat("Consistency", cons?.score?.toDouble(), m, sub = if (cons != null) "${cons.n} nights" else "needs 5+ nights") },
                ),
            )
            Spacer(Modifier.height(8.dp))
            BarsChart(data, CatColor.sleep, "h", decimals = 1, height = 120.dp, goal = app.prefs.goals.getValue(GoalKey.SLEEP), name = "Sleep")
            Spacer(Modifier.height(4.dp))
            Text("Bedtime = wake time minus recorded duration (derived).", style = Cadence.type.caption1Regular, color = c.textSecondary)
        }
    }
}

@Composable
private fun RecoveryWidget() {
    val app = LocalApp.current
    val view = app.view
    val c = Cadence.colors
    val r = readiness(view, app.prefs.goals.getValue(GoalKey.SLEEP))
    val spo2 = view.latest(MetricKey.SPO2)
    val hrv = view.latest(MetricKey.HRV)
    Responsive { narrow ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (r != null) {
                val gauge: @Composable () -> Unit = { Gauge(r.score.toDouble(), "Readiness", "Estimate, out of 100", Modifier.width(if (narrow) 120.dp else 144.dp)) }
                val parts: @Composable (Modifier) -> Unit = { m ->
                    Column(m) {
                        Pill("Estimate", tone = PillTone.YELLOW)
                        Spacer(Modifier.height(6.dp))
                        Text(r.parts.joinToString(" · ") { "${it.label}: ${it.value}" }, style = Cadence.type.caption1Regular, color = c.textSecondary)
                    }
                }
                if (narrow) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { gauge() }
                    parts(Modifier.fillMaxWidth())
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        gauge()
                        parts(Modifier.weight(1f))
                    }
                }
            } else {
                Empty("Readiness unavailable", hint = "Needs sleep plus resting HR or HRV history (7+ days).")
            }
            StatGrid(
                narrow,
                listOf(
                    { m -> Stat("Blood oxygen", spo2?.let { Math.round(it.v).toDouble() }, m, unit = "%", sub = spo2?.let { fmtDay(roundedDay(view, it.t), true) } ?: "not recorded") },
                    { m -> Stat("HRV", hrv?.let { Math.round(it.v).toDouble() }, m, unit = "ms", sub = hrv?.let { fmtDay(roundedDay(view, it.t), true) } ?: "not recorded") },
                ),
            )
        }
    }
}

@Composable
private fun BodyWidget() {
    val app = LocalApp.current
    val view = app.view
    val fmt = app.fmt
    val w = view.latest(MetricKey.WEIGHT)
    if (w == null) {
        Empty("No weight recorded", hint = "Weight appears when the source provides it.")
        return
    }
    val h = heightAt(view, w.t)
    val bmiValue = h?.let { bmi(w.v, it.v) }
    val hist = view.weight.map { fmt.c(MetricKey.WEIGHT, it.v) }
    Responsive { narrow ->
        Column {
            StatGrid(
                narrow,
                listOf(
                    { m -> Stat("Weight", Math.round(fmt.c(MetricKey.WEIGHT, w.v) * 10) / 10.0, m, decimals = 1, unit = fmt.u(MetricKey.WEIGHT), sub = fmtDay(roundedDay(view, w.t), true), color = CatColor.body) },
                    { m -> Stat("BMI", bmiValue?.let { Math.round(it * 10) / 10.0 }, m, decimals = 1, sub = if (h != null) "height ${fmt.f(MetricKey.HEIGHT, h.v)}" else "needs height") },
                ),
            )
            if (hist.size > 1) {
                Spacer(Modifier.height(8.dp))
                Sparkline(hist, CatColor.body, height = 80.dp)
            }
        }
    }
}

@Composable
private fun GoalsWidget() {
    val app = LocalApp.current
    val view = app.view
    val c = Cadence.colors
    val to = view.completeThrough
    if (to == null) {
        Empty("No data")
        return
    }
    val rows = listOf(
        MetricKey.STEPS to GoalKey.STEPS, MetricKey.ACTIVE_ENERGY to GoalKey.ACTIVE_ENERGY, MetricKey.EXERCISE to GoalKey.EXERCISE,
        MetricKey.DISTANCE to GoalKey.DISTANCE, MetricKey.FLOORS to GoalKey.FLOORS, MetricKey.HYDRATION to GoalKey.HYDRATION,
    ).filter { view.has(it.first) }
    if (rows.isEmpty()) {
        Empty("No goal metrics recorded")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { (m, g) ->
            val cmp = periodCompare(view, m, to, 7)
            val goal = app.prefs.goals.getValue(g)
            val frac = if (cmp.cur.isFinite()) cmp.cur / goal else 0.0
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(m.label, style = Cadence.type.caption1Medium, color = c.textPrimary, modifier = Modifier.weight(1f, fill = false))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${app.fmt.f(m, cmp.cur)} / ${app.fmt.f(m, goal)} · ", style = Cadence.type.caption1Regular.tabular(), color = c.textSecondary)
                        DeltaText(cmp.change)
                    }
                }
                Spacer(Modifier.height(4.dp))
                val color = when (m.cat) {
                    Category.ACTIVITY -> CatColor.activity
                    Category.HEART -> CatColor.heart
                    Category.SLEEP -> CatColor.sleep
                    Category.BODY -> CatColor.body
                    Category.RECOVERY -> CatColor.recovery
                }
                LinearMeter(frac, color, height = 8.dp)
            }
        }
        Text("Daily average, last 7 complete days vs the 7 before.", style = Cadence.type.caption1Regular, color = c.textSecondary)
    }
}

@Composable
private fun InsightsWidget() {
    val app = LocalApp.current
    val c = Cadence.colors
    val list = generateInsights(app.view, app.prefs.goals).take(3)
    if (list.isEmpty()) {
        Empty("Not enough history yet", hint = "Insights appear after about two weeks of data.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        list.forEach { i ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, c.hairline, RoundedCornerShape(16.dp)).padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                StatusDot(
                    when (i.tone) {
                        Tone.GOOD -> c.success
                        Tone.WATCH -> CatColor.insight
                        Tone.NEUTRAL -> CatColor.sleep
                    },
                    Modifier.padding(top = 6.dp, end = 8.dp),
                )
                Text(i.title, style = Cadence.type.bodyRegular, color = c.textPrimary)
            }
        }
        CadenceButton("All insights", onClick = { app.actions.navigate(Route.INSIGHTS) }, kind = ButtonKind.SECONDARY, small = true)
    }
}

@Composable
private fun ConsistencyWidget() {
    val app = LocalApp.current
    val view = app.view
    val last = view.last
    if (last == null || !view.has(MetricKey.STEPS)) {
        Empty("No step data")
        return
    }
    Heatmap(
        values = view.daily[MetricKey.STEPS], to = last, goal = app.prefs.goals.getValue(GoalKey.STEPS),
        unit = "steps", label = "Steps per day", weeks = 26, firstDay = view.first, onPick = app.actions::openDay,
    )
}

/** The Overview widget catalog, in the web's default order (`WIDGETS` in `widgets.tsx`). */
val WIDGETS: List<WidgetDef> = listOf(
    WidgetDef("daily", "Daily activity", Icons.Rounded.MonitorHeart, CatColor.activity, "Rings show the latest recorded day against your goals (Settings). The big ring is the mean of capped goal fractions.") { DailyWidget() },
    WidgetDef("steps", "Steps · last 14 days", Icons.AutoMirrored.Rounded.DirectionsWalk, CatColor.activity, null) { StepsWidget() },
    WidgetDef("heart", "Heart", Icons.Rounded.Favorite, CatColor.heart, "Latest sample and the most recent day with heart-rate data. “Lowest hourly” approximates resting HR when no resting measurement exists.") { HeartWidget() },
    WidgetDef("sleep", "Sleep", Icons.Rounded.Bedtime, CatColor.sleep, "Consistency = 100 − average of bedtime and wake-time standard deviation (14 nights) scaled so 120 min ≈ 0.") { SleepWidget() },
    WidgetDef("recovery", "Recovery", Icons.Rounded.Eco, CatColor.recovery, "Readiness is a heuristic estimate: 50% sleep vs goal, 30% resting HR vs 28-day baseline, 20% HRV vs baseline (only available parts are used). It is not a measurement.") { RecoveryWidget() },
    WidgetDef("body", "Body", Icons.Rounded.MonitorWeight, CatColor.body, "BMI = weight (kg) ÷ height (m)², using the height nearest before the weight reading. A number, not a diagnosis.") { BodyWidget() },
    WidgetDef("goals", "Goals · this week", Icons.Rounded.TrackChanges, CatColor.activity, null) { GoalsWidget() },
    WidgetDef("insights", "Insights", Icons.Rounded.Lightbulb, CatColor.insight, null) { InsightsWidget() },
    WidgetDef("consistency", "Activity consistency", Icons.Rounded.CalendarMonth, CatColor.activity, "Darker squares are days closer to your daily step goal. Touch a day to read it, then open it.") { ConsistencyWidget() },
)
