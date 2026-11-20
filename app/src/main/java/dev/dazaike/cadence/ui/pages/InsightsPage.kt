package dev.dazaike.cadence.ui.pages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.analytics.DEFAULT_GOALS
import dev.dazaike.cadence.analytics.GoalKey
import dev.dazaike.cadence.analytics.MetricKey
import dev.dazaike.cadence.analytics.Tone
import dev.dazaike.cadence.analytics.correlations
import dev.dazaike.cadence.analytics.fmtDay
import dev.dazaike.cadence.analytics.fmtMonth
import dev.dazaike.cadence.analytics.fmtNum
import dev.dazaike.cadence.analytics.generateInsights
import dev.dazaike.cadence.analytics.goalHitRate
import dev.dazaike.cadence.analytics.longestStreak
import dev.dazaike.cadence.analytics.maxDayOf
import dev.dazaike.cadence.analytics.monthSummary
import dev.dazaike.cadence.analytics.readiness
import dev.dazaike.cadence.analytics.sleepConsistency
import dev.dazaike.cadence.analytics.weekSummary
import dev.dazaike.cadence.ui.charts.Gauge
import dev.dazaike.cadence.ui.components.CadenceCard
import dev.dazaike.cadence.ui.components.DeltaText
import dev.dazaike.cadence.ui.components.Empty
import dev.dazaike.cadence.ui.components.Notice
import dev.dazaike.cadence.ui.components.Stat
import dev.dazaike.cadence.ui.components.StatusDot
import dev.dazaike.cadence.ui.state.LocalApp
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.CatColor
import dev.dazaike.cadence.ui.theme.Dimens
import dev.dazaike.cadence.ui.theme.tabular
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

private val SUMMARY = listOf(
    MetricKey.STEPS, MetricKey.DISTANCE, MetricKey.ACTIVE_ENERGY, MetricKey.EXERCISE,
    MetricKey.FLOORS, MetricKey.HEART_RATE, MetricKey.SPO2,
)

private val CORR_NAMES = mapOf("sleep" to "sleep", "steps" to "steps", "resting" to "resting HR", "heartRate" to "avg HR", "exercise" to "exercise")

private class SummaryRow(val metric: MetricKey, val cur: Double, val prev: Double, val nCur: Int, val nPrev: Int, val change: Double)

private class RecordRow(val label: String, val value: String, val d: LocalDate)

@Composable
fun InsightsPage() {
    val app = LocalApp.current
    val view = app.view
    val fmt = app.fmt
    val c = Cadence.colors
    val t = Cadence.type
    PageColumn {
        val to = view.completeThrough
        if (to == null) {
            PageHeader("Insights")
            NoData("health data")
            return@PageColumn
        }
        val goals = app.prefs.goals
        val stepGoal = goals[GoalKey.STEPS] ?: DEFAULT_GOALS.getValue(GoalKey.STEPS)
        val sleepGoal = goals[GoalKey.SLEEP] ?: DEFAULT_GOALS.getValue(GoalKey.SLEEP)
        val insights = generateInsights(view, goals)
        val corr = correlations(view)
        val week = weekSummary(view, to, SUMMARY).map { SummaryRow(it.metric, it.cur, it.prev, it.nCur, it.nPrev, it.change) }
        val monthRaw = monthSummary(view, SUMMARY)
        val month = monthRaw.map { SummaryRow(it.metric, it.cur, it.prev, it.nCur, it.nPrev, it.change) }
        val ready = readiness(view, sleepGoal)
        val cons = sleepConsistency(view, to)
        val hit = goalHitRate(view, MetricKey.STEPS, stepGoal, to)
        val streak = longestStreak(view, MetricKey.STEPS, stepGoal)

        val records = ArrayList<RecordRow>()
        for (m in listOf(MetricKey.STEPS, MetricKey.DISTANCE, MetricKey.ACTIVE_ENERGY, MetricKey.EXERCISE, MetricKey.FLOORS)) {
            val r = maxDayOf(view, m) ?: continue
            records.add(RecordRow("Most ${m.label.lowercase()} in a day", fmt.f(m, r.v), r.d))
        }
        val longest = view.sleep.values.maxByOrNull { it.hours }
        if (longest != null) records.add(RecordRow("Longest sleep", "${fmtNum(longest.hours, 1)} h", longest.d))
        if (view.heartDay.isNotEmpty()) {
            var lowD: LocalDate? = null
            var lowV = 0.0
            for (d in view.heartDay.keys) {
                val r = view.resting(d) ?: continue
                if (lowD == null || r.v < lowV) {
                    lowD = d
                    lowV = r.v
                }
            }
            if (lowD != null) records.add(RecordRow("Lowest resting / hourly HR", "${Math.round(lowV)} bpm", lowD))
        }
        if (streak != null) {
            records.add(RecordRow("Longest ${fmtNum(stepGoal, 0)}-step streak", "${streak.days} day${if (streak.days > 1) "s" else ""}", streak.end))
        }

        PageHeader("Insights", subtitle = "Computed from your recorded data through ${fmtDay(to, true)} (complete days only)")

        Notice(
            "Everything here is calculated from your own imported data. Correlations describe how two measures moved together; they do not show that one causes the other. Nothing on this page is a medical diagnosis, so please talk to a clinician about health concerns.",
            icon = Icons.Rounded.Warning, raised = true,
        )

        AdaptiveRow(
            listOf(2f, 1f),
            {
                CadenceCard(title = "Observations", icon = Icons.Rounded.Lightbulb, color = CatColor.insight) {
                    if (insights.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            insights.forEach { i ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(Dimens.InnerRadius))
                                        .border(1.dp, c.hairline, RoundedCornerShape(Dimens.InnerRadius))
                                        .padding(14.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        val dot = when (i.tone) {
                                            Tone.GOOD -> c.success
                                            Tone.WATCH -> CatColor.insight
                                            Tone.NEUTRAL -> CatColor.sleep
                                        }
                                        StatusDot(dot, Modifier.padding(top = 6.dp))
                                        Text(i.title, style = t.bodyMedium, color = c.textPrimary, modifier = Modifier.padding(start = 12.dp))
                                    }
                                    Text(i.detail, style = t.caption1Regular, color = c.textSecondary, modifier = Modifier.padding(start = 20.dp, top = 4.dp))
                                }
                            }
                        }
                    } else {
                        Empty("Not enough data for observations", hint = "Most insights need at least two weeks of recent data.")
                    }
                }
            },
            {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.PageGap)) {
                    CadenceCard(
                        title = "Readiness (estimate)", color = CatColor.recovery,
                        info = "Heuristic: 50% last sleep vs goal, 30% resting HR vs 28-day baseline, 20% HRV vs baseline. Components that are missing are left out. This is an estimate, not a measurement.",
                    ) {
                        if (ready != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Gauge(ready.score.toDouble(), "Readiness", "Estimate, out of 100", Modifier.width(144.dp), color = CatColor.recovery)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ready.parts.forEach { p ->
                                        Text(
                                            buildAnnotatedString {
                                                append("${p.label}: ")
                                                withStyle(SpanStyle(color = c.textPrimary)) { append(p.value) }
                                            },
                                            style = t.caption1Regular, color = c.textSecondary,
                                        )
                                    }
                                }
                            }
                        } else {
                            Empty("Not enough inputs")
                        }
                    }
                    CadenceCard(
                        title = "Consistency scores", color = CatColor.sleep,
                        info = "Sleep: 100 minus the mean of bedtime and wake-time SD (120 min ≈ 0). Steps: share of the last 30 days that reached your goal.",
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Stat(
                                "Sleep schedule", cons?.score?.toDouble(), Modifier.weight(1f),
                                sub = if (cons != null) "${cons.n} nights" else "needs 5+ nights",
                            )
                            Stat(
                                "Step goal hit", hit?.let { Math.round(it.rate * 100).toDouble() }, Modifier.weight(1f),
                                sub = hit?.let { "${it.n} days" }, unit = "%",
                            )
                        }
                    }
                }
            },
        )

        val first = monthRaw.firstOrNull()
        AdaptiveRow(
            listOf(1f, 1f),
            {
                SummaryTable("Weekly summary", "Last 7 complete days vs previous 7", null, week)
            },
            {
                SummaryTable(
                    "Monthly progress", "Daily averages",
                    "${fmtMonth(first?.monthStart ?: to)} so far vs ${first?.let { fmtMonth(it.prevStart) } ?: "previous month"}",
                    month,
                )
            },
        )

        AdaptiveRow(
            listOf(1f, 1f),
            {
                CadenceCard(
                    title = "Correlations", color = CatColor.recovery,
                    info = "Pearson r over days where both measures exist (n ≥ 14). |r| < 0.2 is negligible, < 0.4 weak, < 0.6 moderate, otherwise strong. Association only.",
                ) {
                    if (corr.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            corr.forEach { k ->
                                Column {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(k.label, style = t.caption1Regular, color = c.textPrimary, modifier = Modifier.weight(1f))
                                        Text(
                                            "r = ${String.format(Locale.US, "%.2f", k.r)} · n = ${k.n}",
                                            style = t.caption1Regular.tabular(), color = c.textSecondary,
                                        )
                                    }
                                    val barColor = if (k.r >= 0) CatColor.recovery else CatColor.heart
                                    Canvas(
                                        Modifier
                                            .padding(top = 6.dp)
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(c.chartTrack),
                                    ) {
                                        val mid = size.width / 2f
                                        val w = (abs(k.r).toFloat()).coerceIn(0f, 1f) * mid
                                        val left = if (k.r >= 0) mid else mid - w
                                        drawRoundRect(barColor, Offset(left, 0f), Size(w, size.height), CornerRadius(size.height / 2f))
                                        drawRect(c.textTertiary, Offset(mid - 0.5f, 0f), Size(1f, size.height))
                                    }
                                    Text(
                                        "${k.strength} association between ${CORR_NAMES[k.x] ?: k.x} and ${CORR_NAMES[k.y] ?: k.y}",
                                        style = t.caption1Regular, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        Empty("Not enough overlapping data", hint = "Each pair needs at least 14 days where both measures were recorded.")
                    }
                }
            },
            {
                CadenceCard(title = "Personal records", icon = Icons.Rounded.EmojiEvents, color = CatColor.insight) {
                    if (records.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            records.forEach { r ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(Dimens.ControlRadius))
                                        .clickable { app.actions.openDay(r.d) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(r.label, style = t.bodyRegular, color = c.textPrimary)
                                        Text(fmtDay(r.d, true), style = t.caption1Regular, color = c.textSecondary)
                                    }
                                    Text(r.value, style = t.bodySemibold.tabular(), color = c.textPrimary, textAlign = TextAlign.End)
                                }
                            }
                        }
                    } else {
                        Empty("No records yet")
                    }
                }
            },
        )

        CadenceCard(title = "How metrics are calculated", color = c.accent.c600) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Days" to " are local calendar days in your selected time zone. Daily totals come from your health data source.",
                    "Missing days" to " are never counted as zero in averages.",
                    "Week/month comparisons" to " use the mean of recorded days in each window and exclude the in-progress day.",
                    "Resting HR" to " is the measured value when present; otherwise the lowest hourly average of a day (flagged as a proxy).",
                    "Bedtime" to " is derived: wake time minus recorded sleep duration (plus awake time when stages exist).",
                    "Readiness and consistency" to " are heuristics and are labelled as estimates.",
                ).forEach { (lead, rest) ->
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = c.textPrimary, fontWeight = FontWeight.SemiBold)) { append(lead) }
                            append(rest)
                        },
                        style = t.caption1Regular, color = c.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryTable(title: String, info: String, caption: String?, rows: List<SummaryRow>) {
    val app = LocalApp.current
    val fmt = app.fmt
    val c = Cadence.colors
    val t = Cadence.type
    CadenceCard(title = title, color = CatColor.activity, info = info) {
        if (caption != null) Text(caption, style = t.caption1Regular, color = c.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
        if (rows.isEmpty()) {
            Empty("No data")
            return@CadenceCard
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text("Metric", style = t.caption1Medium, color = c.textSecondary, modifier = Modifier.weight(1.5f))
            Text("Now", style = t.caption1Medium, color = c.textSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
            Text("Before", style = t.caption1Medium, color = c.textSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
            Text("Change", style = t.caption1Medium, color = c.textSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
        }
        rows.forEach { r ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
            val goal = r.metric.goalKey?.let { app.prefs.goals[it] }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1.5f)) {
                    Text(r.metric.label, style = t.bodyRegular, color = c.textPrimary)
                    Text(
                        fmt.u(r.metric) + if (goal != null) " · goal ${fmt.f(r.metric, goal, false)}" else "",
                        style = t.caption2Medium, color = c.textSecondary,
                    )
                }
                Column(Modifier.weight(1.1f), horizontalAlignment = Alignment.End) {
                    Text(if (r.cur.isFinite()) fmt.f(r.metric, r.cur, false) else "—", style = t.bodyRegular.tabular(), color = c.textPrimary)
                    Text("(${r.nCur}d)", style = t.caption2Medium, color = c.textTertiary)
                }
                Column(Modifier.weight(1.1f), horizontalAlignment = Alignment.End) {
                    Text(if (r.prev.isFinite()) fmt.f(r.metric, r.prev, false) else "—", style = t.bodyRegular.tabular(), color = c.textSecondary)
                    Text("(${r.nPrev}d)", style = t.caption2Medium, color = c.textTertiary)
                }
                Box(Modifier.weight(0.9f), contentAlignment = Alignment.TopEnd) {
                    DeltaText(r.change, invert = r.metric == MetricKey.HEART_RATE)
                }
            }
        }
    }
}
