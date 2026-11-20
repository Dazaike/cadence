package dev.dazaike.cadence.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.dazaike.cadence.ui.theme.CatColor
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.analytics.fmtClock
import dev.dazaike.cadence.analytics.fmtDayLong
import dev.dazaike.cadence.analytics.fmtNum
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.StageColors
import java.time.LocalDate
import kotlin.math.min

/** Maps values to pixels for a bar chart; [reversed] puts the low end at the top (sleep schedule). */
internal class BarPlot(val plot: Plot, val scale: Scale, val reversed: Boolean) {
    fun y(v: Double): Float = if (reversed) plot.top + plot.h * scale.frac(v) else plot.bottom - plot.h * scale.frac(v)
    fun slotW(count: Int) = plot.w / count.coerceAtLeast(1)
    fun slotLeft(i: Int, count: Int) = plot.left + slotW(count) * i
}

/**
 * Shared bar-chart frame: horizontal grid with y labels, thinned x labels, touch selection with a
 * soft slot highlight. [drawBars] paints the series; [reveal] is the 0..1 draw-in.
 */
@Composable
private fun BarFrame(
    count: Int,
    scale: Scale,
    yLabels: List<String>,
    xLabels: List<String>,
    modifier: Modifier,
    height: Dp,
    reversed: Boolean,
    selected: Int?,
    onSelect: (Int) -> Unit,
    drawBars: DrawScope.(BarPlot) -> Unit,
) {
    val c = Cadence.colors
    val measurer = rememberTextMeasurer()
    val axis = axisStyle()
    val density = LocalDensity.current
    val yW = measurer.widest(yLabels, axis) + with(density) { 10.dp.toPx() }
    var widthPx by remember { mutableFloatStateOf(0f) }
    val heightPx = with(density) { height.toPx() }
    val pad = with(density) { 8.dp.toPx() }
    val plot = Plot(yW, pad, (widthPx - pad).coerceAtLeast(yW + 1f), heightPx - with(density) { 20.dp.toPx() })
    val bp = BarPlot(plot, scale, reversed)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .scrub(count, plot.left, plot.right, slots = true, onIndex = onSelect),
    ) {
        scale.ticks.forEachIndexed { i, t ->
            val y = bp.y(t)
            drawLine(c.hairline, Offset(plot.left, y), Offset(plot.right, y), 1.dp.toPx())
            val sz = measurer.measure(yLabels[i], axis).size
            drawText(measurer, yLabels[i], Offset(plot.left - 8.dp.toPx() - sz.width, y - sz.height / 2f), axis)
        }
        if (selected != null && selected in 0 until count) {
            drawRoundRect(c.raised.copy(alpha = 0.7f), Offset(bp.slotLeft(selected, count), plot.top), Size(bp.slotW(count), plot.h), CornerRadius(6.dp.toPx()))
        }
        if (count > 0) {
            val widest = measurer.widest(xLabels.distinct().take(40), axis)
            labelIndices(count, plot.w, widest).forEach { i ->
                val x = (plot.xAt(i, count, true)).coerceIn(plot.left + widest / 2, plot.right - widest / 2)
                label(measurer, xLabels[i], axis, x, plot.bottom + 4.dp.toPx(), TextAlign.Center)
            }
        }
        drawBars(bp)
    }
}

private fun barWidth(slot: Float, maxPx: Float) = min(slot * 0.7f, maxPx)

private fun valueText(v: Double?, decimals: Int, unit: String): String =
    if (v == null) "no data" else "${fmtNum(v, decimals)}${if (unit.isBlank()) "" else " $unit"}"

/**
 * Bars per day/week/month (web `BarsChart`): bars reaching the goal are solid, others dimmed, the
 * [highlight] day is outlined. Touch a bar to read it; [onPick] opens that day.
 */
@Composable
fun BarsChart(
    data: List<ChartPoint>,
    color: Color,
    unit: String,
    modifier: Modifier = Modifier,
    decimals: Int = 0,
    height: Dp = 240.dp,
    goal: Double? = null,
    brush: Boolean = false,
    onPick: ((LocalDate) -> Unit)? = null,
    name: String = "Value",
    highlight: LocalDate? = null,
) {
    val c = Cadence.colors
    val showBrush = brush && data.size > 14
    var start by remember(data.size) { androidx.compose.runtime.mutableIntStateOf(0) }
    var end by remember(data.size) { androidx.compose.runtime.mutableIntStateOf((data.size - 1).coerceAtLeast(0)) }
    val i0 = if (showBrush) start.coerceIn(0, (data.size - 1).coerceAtLeast(0)) else 0
    val i1 = if (showBrush) end.coerceIn(i0, (data.size - 1).coerceAtLeast(0)) else (data.size - 1).coerceAtLeast(0)
    val vis = if (data.isEmpty()) data else data.subList(i0, i1 + 1)
    var sel by remember(data.hashCode(), i0, i1) { mutableStateOf<Int?>(null) }
    val reveal = rememberReveal(data.hashCode())
    val top = (vis.mapNotNull { it.v } + listOfNotNull(goal)).maxOrNull() ?: 1.0
    val scale = niceScale(0.0, if (top <= 0) 1.0 else top, 4, zeroBase = true)
    val yLabels = scale.ticks.map { tickLabel(it, scale.step) }
    val density = LocalDensity.current

    val s = sel?.coerceIn(0, (vis.size - 1).coerceAtLeast(0))
    Column(modifier) {
        val p = if (s != null && vis.isNotEmpty()) vis[s] else null
        Readout(
            title = p?.let { fmtDayLong(it.d) },
            rows = p?.let {
                buildList {
                    add(ReadoutRow(name, valueText(it.v, decimals, unit), color))
                    if (it.n > 1) add(ReadoutRow("Days averaged", it.n.toString()))
                }
            },
            onOpen = if (p != null && onPick != null) ({ onPick(p.d) }) else null,
        )
        BarFrame(vis.size, scale, yLabels, vis.map { it.label }, Modifier, height, false, s, { sel = it }) { bp ->
            val slot = bp.slotW(vis.size)
            val bw = barWidth(slot, with(density) { 36.dp.toPx() })
            vis.forEachIndexed { i, pt ->
                val v = pt.v ?: return@forEachIndexed
                val x = bp.slotLeft(i, vis.size) + (slot - bw) / 2
                val yTop = bp.y(0.0) - (bp.y(0.0) - bp.y(v)) * reveal
                val h = bp.y(0.0) - yTop
                if (h <= 0f) return@forEachIndexed
                val hl = highlight != null && pt.d == highlight || i == s
                val met = goal != null && v >= goal
                drawRoundRect(color.copy(alpha = if (hl) 1f else if (met) 0.95f else 0.62f), Offset(x, yTop), Size(bw, h), CornerRadius(4.dp.toPx()))
                if (hl) drawRoundRect(c.textPrimary, Offset(x, yTop), Size(bw, h), CornerRadius(4.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            }
            if (goal != null) {
                val y = bp.y(goal)
                drawLine(color.copy(alpha = 0.7f), Offset(bp.plot.left, y), Offset(bp.plot.right, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
            }
        }
        if (showBrush) BrushBar(data.map { it.v }, color, start, end, { a, b -> start = a; end = b }, Modifier.padding(top = 8.dp))
    }
}

/** One night's stage hours (web `StagesChart` rows). */
class StagePoint(val d: LocalDate, val label: String, val deep: Double, val rem: Double, val light: Double, val awake: Double)

/** Stacked deep / REM / light / awake hours per night. */
@Composable
fun StagesChart(data: List<StagePoint>, modifier: Modifier = Modifier, height: Dp = 240.dp) {
    var sel by remember(data.hashCode()) { mutableStateOf<Int?>(null) }
    val reveal = rememberReveal(data.hashCode())
    val top = data.maxOfOrNull { it.deep + it.rem + it.light + it.awake } ?: 1.0
    val scale = niceScale(0.0, if (top <= 0) 1.0 else top, 4, zeroBase = true)
    val yLabels = scale.ticks.map { tickLabel(it, scale.step) + "h" }
    val density = LocalDensity.current
    val s = sel?.coerceIn(0, (data.size - 1).coerceAtLeast(0))
    Column(modifier) {
        val p = if (s != null && data.isNotEmpty()) data[s] else null
        Readout(
            title = p?.let { fmtDayLong(it.d) },
            rows = p?.let {
                listOf(
                    ReadoutRow("Deep", "${fmtNum(it.deep, 1)} h", StageColors.deep),
                    ReadoutRow("REM", "${fmtNum(it.rem, 1)} h", StageColors.rem),
                    ReadoutRow("Light", "${fmtNum(it.light, 1)} h", StageColors.light),
                    ReadoutRow("Awake", "${fmtNum(it.awake, 1)} h", StageColors.awake),
                )
            },
        )
        BarFrame(data.size, scale, yLabels, data.map { it.label }, Modifier, height, false, s, { sel = it }) { bp ->
            val slot = bp.slotW(data.size)
            val bw = barWidth(slot, with(density) { 36.dp.toPx() })
            data.forEachIndexed { i, pt ->
                val x = bp.slotLeft(i, data.size) + (slot - bw) / 2
                val base = bp.y(0.0)
                // Stack bottom-up; draw top segment first so lower segments cover its square corners.
                val parts = listOf(pt.deep to StageColors.deep, pt.rem to StageColors.rem, pt.light to StageColors.light, pt.awake to StageColors.awake)
                var acc = 0.0
                val edges = parts.map { (v, col) ->
                    val lo = acc
                    acc += v
                    Triple(lo, acc, col)
                }
                edges.asReversed().forEachIndexed { k, (lo, hi, col) ->
                    if (hi <= lo) return@forEachIndexed
                    val yTop = base - (base - bp.y(hi)) * reveal
                    val yBot = base - (base - bp.y(lo)) * reveal
                    if (k == 0) {
                        // top-most drawn segment: rounded, extended down so the lower one hides its bottom corners
                        drawRoundRect(col, Offset(x, yTop), Size(bw, (yBot - yTop) + 4.dp.toPx()), CornerRadius(4.dp.toPx()))
                    } else {
                        drawRect(col, Offset(x, yTop), Size(bw, yBot - yTop))
                    }
                }
            }
        }
    }
}

/** One night's bedtime and wake time in minutes after 18:00 (web `ScheduleChart` rows). */
class SchedulePoint(val d: LocalDate, val label: String, val bedM: Int, val wakeM: Int)

private fun scheduleTick(m: Double): String {
    val h = (Math.round(m / 60.0).toInt() + 18) % 24
    return "${if (h % 12 == 0) 12 else h % 12}${if (h >= 12) "p" else "a"}"
}

/** Floating bars from bedtime to wake time. The axis starts at 6 PM and runs down. */
@Composable
fun ScheduleChart(data: List<SchedulePoint>, modifier: Modifier = Modifier, height: Dp = 260.dp) {
    var sel by remember(data.hashCode()) { mutableStateOf<Int?>(null) }
    val reveal = rememberReveal(data.hashCode())
    val scale = remember { Scale(0.0, 960.0, listOf(0.0, 240.0, 480.0, 720.0, 960.0), 240.0) }
    val yLabels = scale.ticks.map { scheduleTick(it) }
    val density = LocalDensity.current
    val s = sel?.coerceIn(0, (data.size - 1).coerceAtLeast(0))
    Column(modifier) {
        val p = if (s != null && data.isNotEmpty()) data[s] else null
        Readout(
            title = p?.let { fmtDayLong(it.d) },
            rows = p?.let {
                listOf(
                    ReadoutRow("Bedtime", fmtClock(it.bedM + 18 * 60), CatColor.sleep),
                    ReadoutRow("Wake", fmtClock(it.wakeM + 18 * 60)),
                )
            },
        )
        BarFrame(data.size, scale, yLabels, data.map { it.label }, Modifier, height, true, s, { sel = it }) { bp ->
            val slot = bp.slotW(data.size)
            val bw = barWidth(slot, with(density) { 28.dp.toPx() })
            data.forEachIndexed { i, pt ->
                val x = bp.slotLeft(i, data.size) + (slot - bw) / 2
                val top = bp.y(pt.bedM.toDouble().coerceIn(0.0, 960.0))
                val bottom = bp.y(maxOf(pt.wakeM, pt.bedM + 5).toDouble().coerceIn(0.0, 960.0))
                val h = (bottom - top) * reveal
                if (h > 0f) drawRoundRect(CatColor.sleep.copy(alpha = if (i == s) 1f else 0.8f), Offset(x, top), Size(bw, h), CornerRadius(4.dp.toPx()))
            }
        }
    }
}
