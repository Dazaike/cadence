package dev.dazaike.cadence.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.analytics.DEFAULT_GOALS
import dev.dazaike.cadence.analytics.HealthView
import dev.dazaike.cadence.analytics.MetricKey
import dev.dazaike.cadence.analytics.WEEKDAYS
import dev.dazaike.cadence.analytics.addDays
import dev.dazaike.cadence.analytics.addMonths
import dev.dazaike.cadence.analytics.daysInMonth
import dev.dazaike.cadence.analytics.diffDays
import dev.dazaike.cadence.analytics.fmtClock
import dev.dazaike.cadence.analytics.fmtDay
import dev.dazaike.cadence.analytics.fmtDayLong
import dev.dazaike.cadence.analytics.fmtDuration
import dev.dazaike.cadence.analytics.fmtMonth
import dev.dazaike.cadence.analytics.fmtNum
import dev.dazaike.cadence.analytics.minuteOfDay
import dev.dazaike.cadence.analytics.startOfMonth
import dev.dazaike.cadence.analytics.weekdayIndex
import dev.dazaike.cadence.ui.charts.DayTimeline
import dev.dazaike.cadence.ui.charts.TimelineBand
import dev.dazaike.cadence.ui.charts.TimelinePoint
import dev.dazaike.cadence.ui.components.CadenceCard
import dev.dazaike.cadence.ui.components.CadenceIconButton
import dev.dazaike.cadence.ui.components.Empty
import dev.dazaike.cadence.ui.components.Pill
import dev.dazaike.cadence.ui.components.Segmented
import dev.dazaike.cadence.ui.components.Stat
import dev.dazaike.cadence.ui.state.LocalApp
import dev.dazaike.cadence.ui.state.LocalWide
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.CatColor
import dev.dazaike.cadence.ui.theme.Dimens
import dev.dazaike.cadence.ui.theme.tabular
import java.time.LocalDate
import kotlin.math.roundToInt

/** Metrics the month grid can shade (web `CalMetric`). */
private enum class CalMetric(val key: MetricKey) {
    STEPS(MetricKey.STEPS),
    ACTIVE(MetricKey.ACTIVE_ENERGY),
    EXERCISE(MetricKey.EXERCISE),
    SLEEP(MetricKey.SLEEP),
}

private fun valueOn(view: HealthView, m: CalMetric, d: LocalDate): Double? =
    if (m == CalMetric.SLEEP) view.sleep[d]?.hours else view.daily[m.key]?.get(d)

/** Port of the web `Calendar` page: month grid, day panel with timeline and scrubber, all records. */
@Composable
fun CalendarPage() {
    val app = LocalApp.current
    val view = app.view
    val first = view.first
    val last = view.last
    if (first == null || last == null) {
        PageColumn {
            PageHeader("Calendar")
            NoData("health data")
        }
        return
    }
    val sel = app.selectedDay
    val day = if (sel != null && sel >= first && sel <= last) sel else last
    var month by remember { mutableStateOf(day.startOfMonth()) }
    var metric by remember { mutableStateOf(CalMetric.STEPS) }
    // The month follows the selected day (records list, previous/next day).
    LaunchedEffect(day) { month = day.startOfMonth() }

    PageColumn {
        PageHeader("Calendar", subtitle = "Daily and historical health records")
        AdaptiveRow(
            listOf(1f, 2f),
            { MonthCard(first, last, day, month, { month = it }, metric, { metric = it }) },
            { DayPanel(first, last, day) },
        )
        RecordsCard(first, last, day)
    }
}

/* ----------------------------- month grid ----------------------------- */

@Composable
private fun MonthCard(
    first: LocalDate,
    last: LocalDate,
    day: LocalDate,
    month: LocalDate,
    onMonth: (LocalDate) -> Unit,
    metric: CalMetric,
    onMetric: (CalMetric) -> Unit,
) {
    val app = LocalApp.current
    val view = app.view
    val c = Cadence.colors
    val opts = CalMetric.entries.filter { if (it == CalMetric.SLEEP) view.sleep.isNotEmpty() else view.has(it.key) }
    val m = if (metric in opts) metric else opts.firstOrNull()
    val color = if (m == CalMetric.SLEEP) CatColor.sleep else CatColor.activity

    CadenceCard(
        title = fmtMonth(month),
        color = color,
        actions = {
            CadenceIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month", onClick = { onMonth(month.addMonths(-1)) })
            CadenceIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month", onClick = { onMonth(month.addMonths(1)) })
        },
    ) {
        if (m == null) {
            Empty("No daily totals to shade", hint = "Steps, active energy, exercise and sleep are all missing from this dataset.")
        } else {
            val gk = m.key.goalKey
            val goal = gk?.let { app.prefs.goals[it] ?: DEFAULT_GOALS[it] } ?: 1.0
            Segmented(
                value = m,
                options = opts.map { it to it.key.label },
                onChange = onMetric,
                modifier = Modifier.padding(bottom = 12.dp),
                contentDescription = "Calendar metric",
            )
            val lead = (month.weekdayIndex() + 6) % 7
            val dim = month.daysInMonth()
            val rows = (lead + dim + 6) / 7
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (WEEKDAYS.drop(1) + WEEKDAYS[0]).forEach { w ->
                        Text(w, Modifier.weight(1f), style = Cadence.type.caption1Regular, color = c.textSecondary, textAlign = TextAlign.Center)
                    }
                }
                repeat(rows) { r ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(7) { col ->
                            val n = r * 7 + col - lead + 1
                            if (n in 1..dim) {
                                DayCell(Modifier.weight(1f), month.addDays(n - 1), m, goal, color, first, last, day) { app.actions.selectDay(it) }
                            } else {
                                Spacer(Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }
            }
            val shade = if (m == CalMetric.SLEEP) "${goalText(goal)} h sleep" else m.key.label.lowercase()
            Text(
                "Shade = share of your $shade goal. Blank = no record.",
                style = Cadence.type.caption1Regular, color = c.textSecondary, modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private fun goalText(g: Double): String = if (g % 1.0 == 0.0) g.toInt().toString() else g.toString()

@Composable
private fun DayCell(
    modifier: Modifier,
    d: LocalDate,
    m: CalMetric,
    goal: Double,
    color: Color,
    first: LocalDate,
    last: LocalDate,
    selectedDay: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val app = LocalApp.current
    val c = Cadence.colors
    val v = valueOn(app.view, m, d)
    val inData = d >= first && d <= last
    val a = if (v == null) 0f else (v / goal).coerceIn(0.12, 1.0).toFloat()
    val strong = v != null && a > 0.55f
    val shape = RoundedCornerShape(Dimens.ControlRadius)
    val desc = "${fmtDay(d, true)}: " + when {
        v == null -> "no data"
        m == CalMetric.SLEEP -> fmtDuration(v)
        else -> app.fmt.f(m.key, v)
    }
    Box(
        modifier
            .aspectRatio(1f)
            .alpha(if (inData) 1f else 0.25f)
            .clip(shape)
            .background(if (v == null) c.pageBg else color.copy(alpha = a * 0.85f))
            .then(if (d == selectedDay) Modifier.border(2.dp, c.textPrimary, shape) else Modifier)
            .clickable(enabled = inData) { onSelect(d) }
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            d.dayOfMonth.toString(),
            style = if (strong) Cadence.type.bodySemibold else Cadence.type.caption1Regular,
            color = if (strong) c.onFill else c.textPrimary,
        )
    }
}

/* ----------------------------- day panel ----------------------------- */

/** Lays out Calendar stats as 4 across on wide screens and 2 by 2 on phones. */
@Composable
private fun StatGrid(vararg items: @Composable (Modifier) -> Unit) {
    if (LocalWide.current) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items.forEach { it(Modifier.weight(1f)) }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items.toList().chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    pair.forEach { it(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private class TimelineEvent(val m: Int, val label: String)

@Composable
private fun DayPanel(first: LocalDate, last: LocalDate, day: LocalDate) {
    val app = LocalApp.current
    val view = app.view
    val fmt = app.fmt
    val c = Cadence.colors
    val t = Cadence.type
    val zone = view.zone
    var minute by remember { mutableIntStateOf(12 * 60) }

    val night = view.sleep[day]
    val nextNight = view.sleep[day.addDays(1)]
    val bedToday = nextNight?.takeIf { minuteOfDay(it.bed, zone) >= 12 * 60 }

    val hr = remember(view, day) {
        (view.hrByDay[day] ?: emptyList()).map { TimelinePoint(minuteOfDay(it.t, zone), it.v) }.sortedBy { it.m }
    }
    val wakeM = night?.let { minuteOfDay(it.wake, zone) }
    val bedM = bedToday?.let { minuteOfDay(it.bed, zone) }
    fun asleepAt(mm: Int): Boolean = when {
        wakeM != null && mm < wakeM -> true
        bedM != null -> mm >= bedM
        else -> false
    }
    val bands = buildList {
        if (wakeM != null) add(TimelineBand(0, wakeM))
        if (bedM != null) add(TimelineBand(bedM, 1440))
    }
    val hrAt = hr.lastOrNull { it.m <= minute && minute - it.m <= 120 }
    val total = view.daily[MetricKey.STEPS]?.get(day)
    val exercise = view.daily[MetricKey.EXERCISE]?.get(day)
    val heartDay = view.heartDay[day]
    val hasAnything = night != null || bedToday != null || hr.isNotEmpty() || total != null || exercise != null || heartDay != null

    val events = buildList {
        if (night != null && wakeM != null) add(TimelineEvent(wakeM, "Woke up ${fmtClock(wakeM)} after ${fmtDuration(night.hours)} of sleep"))
        if (hr.isNotEmpty()) {
            val pk = hr.reduce { a, b -> if (b.v > a.v) b else a }
            add(TimelineEvent(pk.m, "Heart rate peaked at ${Math.round(pk.v)} bpm around ${fmtClock(pk.m)}"))
        }
        if (bedM != null) add(TimelineEvent(bedM, "Went to sleep ${fmtClock(bedM)}"))
    }.sortedBy { it.m }

    CadenceCard(
        title = fmtDayLong(day),
        actions = {
            CadenceIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous day", enabled = day > first, onClick = { app.actions.selectDay(day.addDays(-1)) })
            CadenceIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next day", enabled = day < last, onClick = { app.actions.selectDay(day.addDays(1)) })
        },
    ) {
        if (!hasAnything) {
            Empty("No records for this day", hint = "Nothing was recorded, and nothing is filled in.")
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StatGrid(
                    { Stat("Steps (day)", total, it, sub = if (view.asOf == day) "in progress" else null, color = CatColor.activity) },
                    { Stat("Exercise", exercise, it, unit = "min", sub = "daily total (no session times)") },
                    {
                        Stat(
                            "Slept", night?.let { n -> Math.round(n.hours * 10) / 10.0 }, it, decimals = 1, unit = "h",
                            sub = if (night != null && wakeM != null) "${fmtClock(minuteOfDay(night.bed, zone))} → ${fmtClock(wakeM)}" else "no sleep recorded",
                            color = CatColor.sleep,
                        )
                    },
                    {
                        Stat(
                            "Heart rate", heartDay?.let { h -> Math.round(h.avg).toDouble() }, it, unit = "bpm",
                            sub = if (heartDay != null) "${Math.round(heartDay.min)}–${Math.round(heartDay.max)} bpm" else "no readings",
                            color = CatColor.heart,
                        )
                    },
                )

                if (hr.size > 1) {
                    Column(Modifier.fillMaxWidth()) {
                        DayTimeline(hr, bands, minute, { minute = it })
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LegendDot(CatColor.heart, "heart rate (hourly averages)")
                            if (night != null || bedToday != null) LegendDot(CatColor.sleep, "asleep")
                        }
                    }
                } else {
                    Empty("No intraday readings", hint = "This day only has daily totals, so there is no timeline to draw.")
                }

                val inner = RoundedCornerShape(Dimens.InnerRadius)
                Column(Modifier.fillMaxWidth().clip(inner).border(1.dp, c.hairline, inner).padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Scrub through the day", style = t.bodyMedium, color = c.textPrimary)
                        Pill(fmtClock(minute))
                    }
                    Slider(
                        value = minute.toFloat(),
                        onValueChange = { minute = ((it / 5f).roundToInt() * 5).coerceIn(0, 1439) },
                        valueRange = 0f..1439f,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).semantics { contentDescription = "Time of day" },
                        colors = SliderDefaults.colors(
                            thumbColor = c.accent.c600,
                            activeTrackColor = c.accent.c600,
                            inactiveTrackColor = c.chartTrack,
                        ),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("State", style = t.caption1Regular, color = c.textSecondary)
                            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (night != null || bedToday != null) {
                                    val asleep = asleepAt(minute)
                                    Icon(if (asleep) Icons.Rounded.Bedtime else Icons.Rounded.WbSunny, null, tint = c.iconSecondary, modifier = Modifier.size(16.dp))
                                    Text(if (asleep) "Asleep" else "Awake", style = t.bodyMedium, color = c.textPrimary)
                                } else {
                                    Text("unknown", style = t.bodyMedium, color = c.textSecondary)
                                }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Heart rate", style = t.caption1Regular, color = c.textSecondary)
                            if (hrAt != null) {
                                Text("${Math.round(hrAt.v)} bpm", Modifier.padding(top = 2.dp), style = t.bodyMedium.tabular(), color = c.textPrimary)
                                Text("reading at ${fmtClock(hrAt.m)}", style = t.caption1Regular, color = c.textSecondary)
                            } else {
                                Text("no reading", Modifier.padding(top = 2.dp), style = t.bodyMedium, color = c.textSecondary)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Steps so far", style = t.caption1Regular, color = c.textSecondary)
                            Text("n/a", Modifier.padding(top = 2.dp), style = t.bodyMedium, color = c.textSecondary)
                            Text(
                                "source has daily totals only" + (total?.let { " (${fmt.f(MetricKey.STEPS, it)})" } ?: ""),
                                style = t.caption1Regular, color = c.textSecondary,
                            )
                        }
                    }
                }

                if (events.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().drawBehind {
                            drawLine(c.hairline, Offset(4.dp.toPx(), 0f), Offset(4.dp.toPx(), size.height), 1.dp.toPx())
                        },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        events.forEach { e ->
                            Row(Modifier.fillMaxWidth()) {
                                Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(c.accent.c500))
                                Text(
                                    e.label,
                                    modifier = Modifier
                                        .padding(start = 12.dp)
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { minute = minOf(1439, e.m) },
                                    style = t.bodyRegular, color = c.textPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, Modifier.padding(start = 4.dp), style = Cadence.type.caption1Regular, color = Cadence.colors.textSecondary)
    }
}

/* ----------------------------- all records ----------------------------- */

private val ROW_H = 40.dp
private val VIEW_H = 380.dp
private val DATE_W = 104.dp
private val COL_W = 92.dp

private enum class RecCol(val label: String, val key: MetricKey?) {
    STEPS("Steps", MetricKey.STEPS),
    DISTANCE("Distance", MetricKey.DISTANCE),
    ACTIVE("Active", MetricKey.ACTIVE_ENERGY),
    EXERCISE("Exercise", MetricKey.EXERCISE),
    SLEEP("Sleep", null),
    HR("Avg HR", null),
}

@Composable
private fun RecordsCard(first: LocalDate, last: LocalDate, day: LocalDate) {
    val app = LocalApp.current
    val view = app.view
    val fmt = app.fmt
    val c = Cadence.colors
    val t = Cadence.type
    val days = remember(view) { List(diffDays(first, last) + 1) { last.addDays(-it) } }
    val cols = RecCol.entries.filter {
        when (it) {
            RecCol.SLEEP -> view.sleep.isNotEmpty()
            RecCol.HR -> view.heartDay.isNotEmpty()
            else -> it.key?.let { k -> view.has(k) } == true
        }
    }
    fun cell(d: LocalDate, col: RecCol): String = when (col) {
        RecCol.SLEEP -> view.sleep[d]?.let { fmtDuration(it.hours) } ?: "—"
        RecCol.HR -> view.heartDay[d]?.let { "${Math.round(it.avg)} bpm" } ?: "—"
        else -> col.key?.let { fmt.f(it, view.daily[it]?.get(d)) } ?: "—"
    }

    CadenceCard(title = "All records · ${fmtNum(days.size.toDouble())} days") {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tableW = maxOf(maxWidth, DATE_W + COL_W * cols.size)
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Column(Modifier.width(tableW)) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Date", Modifier.width(DATE_W).padding(horizontal = 8.dp), style = t.caption1Medium, color = c.textSecondary)
                        cols.forEach {
                            Text(it.label, Modifier.weight(1f).padding(horizontal = 8.dp), style = t.caption1Medium, color = c.textSecondary, textAlign = TextAlign.End, maxLines = 1)
                        }
                    }
                    val shape = RoundedCornerShape(Dimens.ControlRadius)
                    Box(Modifier.fillMaxWidth().height(VIEW_H).clip(shape).border(1.dp, c.hairline, shape)) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(count = days.size, key = { days[it].toEpochDay() }) { i ->
                                val d = days[i]
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(ROW_H)
                                        .background(if (d == day) c.raised else Color.Transparent)
                                        .clickable { app.actions.selectDay(d) }
                                        .drawBehind {
                                            drawLine(c.hairline, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx())
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(fmtDay(d, true), Modifier.width(DATE_W).padding(horizontal = 8.dp), style = t.caption1Regular.tabular(), color = c.textPrimary, maxLines = 1)
                                    cols.forEach { col ->
                                        Text(
                                            cell(d, col), Modifier.weight(1f).padding(horizontal = 8.dp),
                                            style = t.caption1Regular.tabular(), color = c.textSecondary,
                                            textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

