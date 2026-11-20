package dev.dazaike.cadence.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.analytics.MetricKey
import dev.dazaike.cadence.analytics.Point
import dev.dazaike.cadence.analytics.addDays
import dev.dazaike.cadence.analytics.dailySeries
import dev.dazaike.cadence.analytics.dayKey
import dev.dazaike.cadence.analytics.dayRange
import dev.dazaike.cadence.analytics.fmtClock
import dev.dazaike.cadence.analytics.fmtDay
import dev.dazaike.cadence.analytics.mean
import dev.dazaike.cadence.analytics.minuteOfDay
import dev.dazaike.cadence.analytics.movingAverage
import dev.dazaike.cadence.analytics.resolveRange
import dev.dazaike.cadence.ui.charts.ChartPoint
import dev.dazaike.cadence.ui.charts.RangeChart
import dev.dazaike.cadence.ui.charts.RangePoint
import dev.dazaike.cadence.ui.charts.TrendChart
import dev.dazaike.cadence.ui.components.CadenceCard
import dev.dazaike.cadence.ui.components.Empty
import dev.dazaike.cadence.ui.components.LinearMeter
import dev.dazaike.cadence.ui.components.Notice
import dev.dazaike.cadence.ui.components.Pill
import dev.dazaike.cadence.ui.components.Stat
import dev.dazaike.cadence.ui.components.cardSurface
import dev.dazaike.cadence.ui.state.LocalApp
import dev.dazaike.cadence.ui.state.LocalWide
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.CatColor
import dev.dazaike.cadence.ui.theme.Palette
import dev.dazaike.cadence.ui.theme.ZoneColors
import dev.dazaike.cadence.ui.theme.tabular
import java.time.LocalDate

/** A heart-rate zone as a share of max HR (web `ZONES`). */
private class Zone(val name: String, val lo: Double, val hi: Double, val color: Color)

/** The web's 5-up stat card: 5 columns on wide screens, 2 per row on phones. */
@Composable
private fun StatGrid(items: List<@Composable (Modifier) -> Unit>) {
    val cols = if (LocalWide.current) items.size else 2
    Column(Modifier.fillMaxWidth().cardSurface(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        items.chunked(cols).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                rowItems.forEach { item -> item(Modifier.weight(1f)) }
                repeat(cols - rowItems.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** Port of web `pages/Heart.tsx`. Heart-rate samples are hourly averages, never raw readings. */
@Composable
fun HeartPage() {
    val app = LocalApp.current
    val view = app.view
    val color = CatColor.heart
    val c = Cadence.colors
    val latest = view.latest(MetricKey.HEART_RATE)
    if (view.heartDay.isEmpty() || latest == null) {
        PageColumn {
            PageHeader("Heart")
            NoData("heart-rate data")
        }
        return
    }

    val maxHr = app.prefs.maxHr
    val range = resolveRange(view, app.prefs.range)
    val from = range.from
    val to = range.to
    val zone = view.zone
    val pick: (LocalDate) -> Unit = { app.actions.openDay(it) }

    val days = dayRange(from, to)
    val band = days.map { d ->
        val h = view.heartDay[d]
        RangePoint(d, fmtDay(d), avg = h?.avg, min = h?.min, max = h?.max, resting = view.resting(d)?.v)
    }
    val inRange = band.filter { it.avg != null }
    val samples = days.flatMap { view.hrByDay[it].orEmpty() }
    val hrAvg = if (samples.isEmpty()) null else mean(samples.map { it.v })
    val derived = days.firstOrNull { view.resting(it) != null }?.let { view.resting(it)?.derived } ?: false
    val restingVals = inRange.mapNotNull { it.resting }

    val restingDaily = days.map { Point(it, view.resting(it)?.v) }
    val restingMa = movingAverage(restingDaily, 7)
    val restingPts = restingDaily.mapIndexed { i, p -> ChartPoint(p.d, fmtDay(p.d), p.v, ma = restingMa[i]) }

    // hourly readings for (at most) the last 14 days of the range
    val hrFrom = if (from < to.addDays(-13)) to.addDays(-13) else from
    val hourly = dayRange(hrFrom, to).flatMap { d ->
        view.hrByDay[d].orEmpty().map { s -> ChartPoint(d, "${fmtDay(d)} ${fmtClock(minuteOfDay(s.t, zone))}", s.v) }
    }

    val zones = listOf(
        Zone("Rest", 0.0, 0.5, if (c.dark) Palette.Neutral600 else Palette.Neutral300),
        Zone("Light", 0.5, 0.6, ZoneColors.light),
        Zone("Fat burn", 0.6, 0.7, ZoneColors.fatBurn),
        Zone("Cardio", 0.7, 0.85, ZoneColors.cardio),
        Zone("Peak", 0.85, 9.0, ZoneColors.peak),
    )
    val zoneCounts = zones.map { z -> samples.count { it.v / maxHr >= z.lo && it.v / maxHr < z.hi } }
    val total = if (samples.isEmpty()) 1 else samples.size

    val hrvPts = dailySeries(view, MetricKey.HRV, from, to).map { ChartPoint(it.d, fmtDay(it.d), it.v) }

    PageColumn {
        PageHeader("Heart", subtitle = "Heart rate, resting heart rate and HRV", controls = { RangeBar(showGran = false) })

        StatGrid(
            listOf(
                { m -> Stat("Latest", Math.round(latest.v).toDouble(), m, sub = "${fmtDay(dayKey(latest.t, zone), false)}, ${fmtClock(minuteOfDay(latest.t, zone))}", color = color, unit = "bpm") },
                { m ->
                    Stat(
                        if (derived) "Lowest hourly avg" else "Resting",
                        if (restingVals.isNotEmpty()) Math.round(mean(restingVals)).toDouble() else null,
                        m,
                        sub = if (derived) "proxy for resting HR" else "measured",
                        unit = "bpm",
                    )
                },
                { m -> Stat("Average", hrAvg?.let { Math.round(it).toDouble() }, m, sub = "${samples.size} readings", unit = "bpm") },
                { m -> Stat("Minimum", samples.minOfOrNull { it.v }?.let { Math.round(it).toDouble() }, m, unit = "bpm") },
                { m -> Stat("Maximum", samples.maxOfOrNull { it.v }?.let { Math.round(it).toDouble() }, m, unit = "bpm") },
            ),
        )
        if (derived) {
            Notice("This source has no resting heart-rate measurement, so “resting” is approximated by the lowest hourly average per day. Hourly averages hide short dips, so the true resting rate is likely lower.")
        }

        CadenceCard(
            title = "Daily heart rate",
            icon = Icons.Rounded.MonitorHeart,
            color = color,
            expandable = true,
            info = "Line = mean of the day’s readings. Band = lowest to highest reading. Drag the handles to zoom/pan; tap a day to inspect it, then open it.",
        ) { full ->
            if (inRange.isNotEmpty()) {
                RangeChart(band, color, "bpm", brush = true, height = if (full) 520.dp else 280.dp, onPick = pick)
            } else {
                Empty("No heart-rate readings in this range", hint = "Heart-rate history only reaches back as far as the source provides (about 30 days in this export).")
            }
        }

        AdaptiveRow(
            listOf(1f, 1f),
            {
                CadenceCard(Modifier.fillMaxHeight(), title = "Hourly readings · ${fmtDay(hrFrom)} – ${fmtDay(to)}", color = color, expandable = true) { full ->
                    if (hourly.size > 1) {
                        TrendChart(hourly, color, "bpm", height = if (full) 480.dp else 240.dp, name = "Heart rate", brush = true, onPick = pick)
                    } else {
                        Empty("No readings")
                    }
                }
            },
            {
                CadenceCard(
                    Modifier.fillMaxHeight(),
                    title = if (derived) "Lowest hourly average (resting proxy)" else "Resting heart rate",
                    color = color,
                    info = "Daily value with a 7-day moving average (dashed).",
                ) {
                    if (restingPts.any { it.v != null }) {
                        TrendChart(restingPts, color, "bpm", height = 240.dp, name = if (derived) "Lowest hourly" else "Resting", onPick = pick)
                    } else {
                        Empty("No resting heart rate")
                    }
                }
            },
        )

        AdaptiveRow(
            listOf(1f, 1f),
            {
                CadenceCard(
                    Modifier.fillMaxHeight(),
                    title = "Time in heart-rate zones",
                    color = color,
                    info = "Share of recorded readings by % of an assumed max HR of $maxHr bpm (change in Settings). This source only has hourly averages and no workout sessions, so this is an all-day distribution, not time-in-zone during workouts.",
                    actions = { Pill("max $maxHr bpm") },
                ) {
                    if (samples.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            zones.forEachIndexed { i, z ->
                                val share = zoneCounts[i].toDouble() / total
                                Column {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            buildAnnotatedString {
                                                append(z.name)
                                                append(" ")
                                                withStyle(SpanStyle(color = c.textSecondary)) {
                                                    append("(${Math.round(z.lo * maxHr)}–${if (z.hi > 1) "+" else Math.round(z.hi * maxHr).toString()})")
                                                }
                                            },
                                            style = Cadence.type.caption1Regular,
                                            color = c.textPrimary,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        Text(
                                            "${Math.round(share * 100)}% · ${zoneCounts[i]} readings",
                                            style = Cadence.type.caption1Regular.tabular(),
                                            color = c.textSecondary,
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                    LinearMeter(share, z.color, Modifier.padding(top = 4.dp), height = 8.dp)
                                }
                            }
                        }
                    } else {
                        Empty("No readings in range")
                    }
                }
            },
            {
                CadenceCard(Modifier.fillMaxHeight(), title = "Heart rate variability", color = CatColor.recovery) {
                    if (hrvPts.any { it.v != null }) {
                        TrendChart(hrvPts, CatColor.recovery, "ms", height = 220.dp, name = "HRV")
                    } else {
                        Empty("No HRV recorded", hint = "HRV is not present in this data source. Nothing is estimated in its place.")
                    }
                }
            },
        )
    }
}
