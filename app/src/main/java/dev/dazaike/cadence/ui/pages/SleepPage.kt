package dev.dazaike.cadence.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.analytics.GoalKey
import dev.dazaike.cadence.analytics.Point
import dev.dazaike.cadence.analytics.fmtClock
import dev.dazaike.cadence.analytics.fmtDay
import dev.dazaike.cadence.analytics.fmtDuration
import dev.dazaike.cadence.analytics.mean
import dev.dazaike.cadence.analytics.movingAverage
import dev.dazaike.cadence.analytics.resolveRange
import dev.dazaike.cadence.analytics.sleepConsistency
import dev.dazaike.cadence.analytics.stdev
import dev.dazaike.cadence.ui.charts.ChartPoint
import dev.dazaike.cadence.ui.charts.ScheduleChart
import dev.dazaike.cadence.ui.charts.SchedulePoint
import dev.dazaike.cadence.ui.charts.StagePoint
import dev.dazaike.cadence.ui.charts.StagesChart
import dev.dazaike.cadence.ui.charts.TrendChart
import dev.dazaike.cadence.ui.components.CadenceCard
import dev.dazaike.cadence.ui.components.Empty
import dev.dazaike.cadence.ui.components.Notice
import dev.dazaike.cadence.ui.components.Pill
import dev.dazaike.cadence.ui.components.PillTone
import dev.dazaike.cadence.ui.components.Stat
import dev.dazaike.cadence.ui.components.cardSurface
import dev.dazaike.cadence.ui.state.LocalApp
import dev.dazaike.cadence.ui.state.LocalWide
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.CatColor
import dev.dazaike.cadence.ui.theme.Dimens
import dev.dazaike.cadence.ui.theme.StageColors

/** Rounds to one decimal, like the web's `+x.toFixed(1)`. */
internal fun round1(x: Double): Double = Math.round(x * 10.0) / 10.0

/** Rows of equally weighted stats: [wideCols] columns on wide screens, 2 on phones. */
@Composable
internal fun StatRowsGrid(items: List<@Composable () -> Unit>, wideCols: Int, modifier: Modifier = Modifier) {
    val cols = if (LocalWide.current) wideCols else 2
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.PageGap)) {
        items.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.PageGap)) {
                for (i in 0 until cols) {
                    Box(Modifier.weight(1f)) { row.getOrNull(i)?.invoke() }
                }
            }
        }
    }
}

private fun trimNum(x: Double): String = if (x % 1.0 == 0.0) x.toInt().toString() else x.toString()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepPage() {
    val app = LocalApp.current
    val view = app.view
    val c = Cadence.colors
    val color = CatColor.sleep
    PageColumn {
        if (view.sleep.isEmpty()) {
            PageHeader("Sleep")
            NoData("sleep data")
            return@PageColumn
        }
        val goal = app.prefs.goals[GoalKey.SLEEP] ?: 8.0
        val range = resolveRange(view, app.prefs.range)
        val from = range.from
        val to = range.to
        val all = view.sleep.values.sortedBy { it.d }
        val nights = all.filter { it.d >= from && it.d <= to }
        val last = all.last()
        val cons = sleepConsistency(view, view.last ?: last.d)
        val pts = nights.map { Point(it.d, it.hours) }
        val ma = movingAverage(pts, 7)
        val dur = nights.mapIndexed { i, n -> ChartPoint(n.d, fmtDay(n.d), n.hours, ma = ma[i]) }
        val withStages = nights.filter { it.stages != null }
        val eff = if (withStages.isEmpty()) null else mean(withStages.map { n -> val s = n.stages!!; n.hours / (n.hours + s.awake) })
        val stageData = withStages.map { n ->
            val s = n.stages!!
            StagePoint(n.d, fmtDay(n.d), s.deep, s.rem, s.light, s.awake)
        }
        val sched = nights.map { SchedulePoint(it.d, fmtDay(it.d), it.bedM, it.wakeM) }
        val avg = if (nights.isEmpty()) null else mean(nights.map { it.hours })
        val bedAvg = if (nights.isEmpty()) null else mean(nights.map { it.bedM.toDouble() })
        val wakeAvg = if (nights.isEmpty()) null else mean(nights.map { it.wakeM.toDouble() })
        fun clock(m: Double?): String? = m?.let { fmtClock(it + 18 * 60) }

        PageHeader("Sleep", subtitle = "Duration, schedule, stages and consistency", controls = { RangeBar(showGran = false) })

        StatRowsGrid(
            wideCols = 5,
            modifier = Modifier.cardSurface(),
            items = listOf(
                { Stat("Last recorded", round1(last.hours), sub = "${fmtDuration(last.hours)} · ${fmtDay(last.d, true)}", color = color, decimals = 1, unit = "h") },
                { Stat("Average in range", avg?.let { round1(it) }, sub = "${nights.size} nights recorded · goal ${trimNum(goal)} h", decimals = 1, unit = "h") },
                { Stat("Typical bedtime", null, text = clock(bedAvg) ?: "—") },
                { Stat("Typical wake", null, text = clock(wakeAvg) ?: "—") },
                {
                    Stat(
                        "Consistency", cons?.score?.toDouble(),
                        sub = if (cons != null) "bedtime ±${Math.round(cons.bedSd)} min · wake ±${Math.round(cons.wakeSd)} min" else "needs 5+ recent nights",
                    )
                },
            ),
        )

        Notice("Nights are dated by the day you woke up. Bedtime is derived as wake time minus recorded sleep duration (plus awake time when stages exist). Nights with no recording are gaps, not zero hours.")

        CadenceCard(
            title = "Sleep duration", color = color, expandable = true,
            info = "Bars/area = hours asleep per recorded night; dashed line = 7-night moving average. Tap a night to open that day.",
        ) { full ->
            if (nights.isNotEmpty()) {
                TrendChart(
                    data = dur, color = color, unit = "h", decimals = 1, goal = goal, brush = true,
                    height = if (full) 520.dp else 280.dp, name = "Sleep", maName = "7-night avg", onPick = { app.actions.openDay(it) },
                )
            } else Empty("No sleep in this range")
        }

        val effAction: (@Composable RowScope.() -> Unit)? = if (eff != null) {
            { Pill("efficiency ${Math.round(eff * 100)}%", tone = PillTone.PURPLE) }
        } else null

        AdaptiveRow(
            listOf(1f, 1f),
            {
                CadenceCard(
                    title = "Sleep schedule", color = color, expandable = true,
                    info = "Each bar spans from bedtime (top) to wake time (bottom). The axis starts at 6 PM.",
                ) { full ->
                    if (nights.isNotEmpty()) ScheduleChart(sched, height = if (full) 520.dp else 280.dp) else Empty("No sleep in this range")
                }
            },
            {
                CadenceCard(
                    title = "Sleep stages", color = color, expandable = true, actions = effAction,
                    info = "Sleep efficiency = time asleep ÷ (time asleep + time awake in bed), averaged over nights that have stage data.",
                ) { full ->
                    if (stageData.isNotEmpty()) {
                        StagesChart(stageData, height = if (full) 500.dp else 250.dp)
                        FlowRow(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            listOf(
                                "Deep" to StageColors.deep,
                                "REM" to StageColors.rem,
                                "Light" to StageColors.light,
                                "Awake" to StageColors.awake,
                            ).forEach { (name, col) ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(col))
                                    Text(name, style = Cadence.type.caption1Regular, color = c.textSecondary)
                                }
                            }
                        }
                    } else {
                        Empty(
                            "No sleep-stage data",
                            hint = "This source records total sleep duration only. Stages (REM, deep, light, awake) and efficiency need a source that provides them; none are inferred.",
                        )
                    }
                }
            },
        )

        if (nights.size >= 5) {
            CadenceCard(title = "Variability", color = color) {
                StatRowsGrid(
                    wideCols = 3,
                    items = listOf(
                        { Stat("Duration spread", round1(stdev(nights.map { it.hours })), sub = "lower = steadier", decimals = 1, unit = "h SD") },
                        { Stat("Bedtime spread", Math.round(stdev(nights.map { it.bedM.toDouble() })).toDouble(), unit = "min SD") },
                        { Stat("Wake-time spread", Math.round(stdev(nights.map { it.wakeM.toDouble() })).toDouble(), sub = "times in ${view.zone.id}", unit = "min SD") },
                    ),
                )
            }
        }
    }
}
