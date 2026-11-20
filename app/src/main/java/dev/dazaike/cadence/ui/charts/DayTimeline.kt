package dev.dazaike.cadence.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.analytics.fmtClock
import dev.dazaike.cadence.ui.theme.CatColor
import dev.dazaike.cadence.ui.theme.Cadence
import kotlin.math.roundToInt

/** One heart-rate reading on the day timeline: [m] minutes since local midnight, [v] bpm. */
data class TimelinePoint(val m: Int, val v: Double)

/** An asleep stretch of the day, in minutes since local midnight (0..1440). */
data class TimelineBand(val from: Int, val to: Int)

private val TICKS = listOf(0, 180, 360, 540, 720, 900, 1080, 1260, 1440)

/**
 * The Calendar day timeline (web `DayPanel` chart): x = minutes 0..1440 with 3-hour ticks, heart
 * rate as a line with dots on its own y-scale, translucent sleep bands for asleep ranges and a
 * cursor bound to [minute]. Tap or drag on the chart to move the cursor ([onMinute], 5-minute steps).
 * Steps are not drawn: the on-device source has daily totals only.
 */
@Composable
fun DayTimeline(
    hr: List<TimelinePoint>,
    bands: List<TimelineBand>,
    minute: Int,
    onMinute: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
) {
    val c = Cadence.colors
    val measurer = rememberTextMeasurer()
    val axis = axisStyle()
    val density = LocalDensity.current
    val callback by rememberUpdatedState(onMinute)
    val reveal = rememberReveal(hr.hashCode())

    val scale = if (hr.isEmpty()) null else niceScale(hr.minOf { it.v } - 8.0, hr.maxOf { it.v } + 8.0, 4)
    val yLabels = scale?.let { s -> s.ticks.map { tickLabel(it, s.step) } } ?: emptyList()
    val yW = if (scale != null) measurer.widest(yLabels, axis) + with(density) { 10.dp.toPx() } else with(density) { 8.dp.toPx() }
    var widthPx by remember { mutableFloatStateOf(0f) }
    val heightPx = with(density) { height.toPx() }
    val pad = with(density) { 8.dp.toPx() }
    val xLabelH = with(density) { 20.dp.toPx() }
    val plot = Plot(yW, pad, (widthPx - pad).coerceAtLeast(yW + 1f), heightPx - xLabelH)

    fun minuteAt(x: Float): Int {
        val raw = (x - plot.left) / plot.w * 1440f
        return ((raw / 5f).roundToInt() * 5).coerceIn(0, 1439)
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .semantics { contentDescription = "Day timeline, cursor at ${fmtClock(minute)}. Touch or drag to move it." }
            .pointerInput(plot.left, plot.right) { detectTapGestures { callback(minuteAt(it.x)) } }
            .pointerInput(plot.left, plot.right) {
                detectHorizontalDragGestures(onDragStart = { callback(minuteAt(it.x)) }) { change, _ ->
                    change.consume()
                    callback(minuteAt(change.position.x))
                }
            },
    ) {
        fun x(m: Int): Float = plot.left + plot.w * m / 1440f

        // grid + y labels (heart rate scale)
        if (scale != null) {
            scale.ticks.forEachIndexed { i, t ->
                val y = plot.yAt(scale, t)
                drawLine(c.hairline, Offset(plot.left, y), Offset(plot.right, y), 1.dp.toPx())
                val sz = measurer.measure(yLabels[i], axis).size
                drawText(measurer, yLabels[i], Offset(plot.left - 8.dp.toPx() - sz.width, y - sz.height / 2f), axis)
            }
        } else {
            drawLine(c.hairline, Offset(plot.left, plot.bottom), Offset(plot.right, plot.bottom), 1.dp.toPx())
        }

        // asleep bands
        for (b in bands) {
            val x0 = x(b.from.coerceIn(0, 1440))
            val x1 = x(b.to.coerceIn(0, 1440))
            if (x1 > x0) drawRect(CatColor.sleep.copy(alpha = 0.14f), Offset(x0, plot.top), Size(x1 - x0, plot.h))
        }

        // x labels every 3 hours
        val tickLabels = TICKS.map { fmtClock(it).replace(":00", "") }
        val half = measurer.widest(tickLabels, axis) / 2f
        TICKS.forEachIndexed { i, t ->
            label(measurer, tickLabels[i], axis, x(t).clampLabel(plot.left + half, plot.right - half), plot.bottom + 4.dp.toPx(), TextAlign.Center)
        }

        // heart-rate line + dots
        if (scale != null && hr.isNotEmpty()) {
            val xs = FloatArray(hr.size) { x(hr[it].m) }
            val ys = FloatArray(hr.size) { plot.yAt(scale, hr[it].v) }
            clipRect(left = 0f, top = 0f, right = plot.left + plot.w * reveal + 6.dp.toPx(), bottom = size.height) {
                val line = Path().apply { monotoneRun(xs, ys) }
                drawPath(line, CatColor.heart, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                for (i in xs.indices) drawCircle(CatColor.heart, 2.5.dp.toPx(), Offset(xs[i], ys[i]))
            }
        }

        // cursor
        val cx = x(minute.coerceIn(0, 1440))
        drawLine(c.textPrimary, Offset(cx, plot.top), Offset(cx, plot.bottom), 1.5.dp.toPx())
        drawCircle(c.textPrimary, 4.dp.toPx(), Offset(cx, plot.top))
    }
}
