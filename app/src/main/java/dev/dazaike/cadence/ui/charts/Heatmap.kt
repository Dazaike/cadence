package dev.dazaike.cadence.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.analytics.addDays
import dev.dazaike.cadence.analytics.dayRange
import dev.dazaike.cadence.analytics.diffDays
import dev.dazaike.cadence.analytics.fmtDay
import dev.dazaike.cadence.analytics.fmtNum
import dev.dazaike.cadence.analytics.startOfWeek
import dev.dazaike.cadence.analytics.weekdayIndex
import dev.dazaike.cadence.ui.theme.Cadence
import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.min

/**
 * Activity-consistency grid (web `Heatmap` / Arc activity heatmap): one square per day, a column
 * per Monday-start week. Intensity is the share of [goal]; a day without a record stays on the
 * neutral track, never a zero-valued colour. Touch a square to read the day, [onPick] opens it.
 */
@Composable
fun Heatmap(
    values: Map<LocalDate, Double>?,
    to: LocalDate,
    goal: Double,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    weeks: Int = 26,
    color: Color = dev.dazaike.cadence.ui.theme.CatColor.activity,
    onPick: ((LocalDate) -> Unit)? = null,
    selected: LocalDate? = null,
    firstDay: LocalDate? = null,
) {
    val c = Cadence.colors
    val from0 = to.startOfWeek().addDays(-(weeks - 1) * 7)
    val from = if (firstDay != null && firstDay > from0) firstDay else from0
    val days = remember(from, to) { dayRange(from, to) }
    val gridStart = from.startOfWeek()
    val cols = diffDays(gridStart, to.startOfWeek()) / 7 + 1
    val thresholds = doubleArrayOf(goal * 0.34, goal * 0.67, goal)
    var tapped by remember(values, to) { mutableStateOf<LocalDate?>(null) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val axis = axisStyle()
    val gap = with(density) { 3.dp.toPx() }
    val leftLabelW = with(density) { 26.dp.toPx() }
    val topLabelH = with(density) { 16.dp.toPx() }
    val cell = min(((widthPx - leftLabelW) - gap * (cols - 1)) / cols, with(density) { 20.dp.toPx() }).coerceAtLeast(1f)
    val heightPx = topLabelH + 7 * cell + 6 * gap

    fun level(d: LocalDate): Int {
        val v = values?.get(d) ?: return 0
        val count = Math.round(v)
        return when {
            count <= 0 -> 0
            count < thresholds[0] -> 1
            count < thresholds[1] -> 2
            count < thresholds[2] -> 3
            else -> 4
        }
    }
    val levelAlpha = floatArrayOf(0f, 0.28f, 0.5f, 0.75f, 1f)

    val shown = tapped ?: selected
    Column(modifier) {
        val v = shown?.let { values?.get(it) }
        Readout(
            title = tapped?.let { fmtDay(it, true) },
            rows = tapped?.let { listOf(ReadoutRow(label, if (v == null) "no data" else "${fmtNum(v, 0)} $unit", color)) },
            hint = "Touch a square to inspect that day",
            onOpen = if (tapped != null && onPick != null) ({ onPick(tapped!!) }) else null,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(with(density) { heightPx.toDp() })
                .onSizeChanged { widthPx = it.width.toFloat() }
                .pointerInput(cols, cell, from) {
                    detectTapGestures { o ->
                        val col = floor((o.x - leftLabelW) / (cell + gap)).toInt()
                        val row = floor((o.y - topLabelH) / (cell + gap)).toInt()
                        if (col in 0 until cols && row in 0..6) {
                            val d = gridStart.addDays(col * 7 + row)
                            if (d >= from && d <= to) tapped = d
                        }
                    }
                },
        ) {
            val r = CornerRadius(3.dp.toPx())
            var lastMonth = -1
            for (d in days) {
                val col = diffDays(gridStart, d) / 7
                val row = (d.weekdayIndex() + 6) % 7
                val x = leftLabelW + col * (cell + gap)
                val y = topLabelH + row * (cell + gap)
                val lv = level(d)
                drawRoundRect(if (lv == 0) c.chartTrack else color.copy(alpha = levelAlpha[lv]), Offset(x, y), Size(cell, cell), r)
                if (d == shown) drawRoundRect(c.textPrimary, Offset(x, y), Size(cell, cell), r, style = Stroke(1.5.dp.toPx()))
                if (row == 0 && d.monthValue != lastMonth) {
                    lastMonth = d.monthValue
                    label(measurer, fmtDay(d).substringBefore(' '), axis, x, 0f)
                }
            }
            listOf(0 to "Mon", 2 to "Wed", 4 to "Fri").forEach { (row, name) ->
                label(measurer, name, axis, 0f, topLabelH + row * (cell + gap) + (cell - 12.dp.toPx()) / 2f)
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Less", style = Cadence.type.caption2Medium, color = c.textSecondary)
            for (lv in 0..4) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (lv == 0) c.chartTrack else color.copy(alpha = levelAlpha[lv])),
                )
            }
            Text("More", style = Cadence.type.caption2Medium, color = c.textSecondary)
        }
    }
}
