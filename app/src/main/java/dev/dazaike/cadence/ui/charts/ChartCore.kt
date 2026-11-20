package dev.dazaike.cadence.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.analytics.fmtNum
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.Motion
import dev.dazaike.cadence.ui.theme.reduced
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * One charted point (web `SeriesPoint`). [v] is null on a day without a record, which the charts
 * draw as a gap, never as zero. [ma] is the optional moving average drawn as a dotted line.
 */
data class ChartPoint(
    val d: LocalDate,
    val label: String,
    val v: Double?,
    val n: Int = 1,
    val ma: Double? = null,
)

/** One line of a chart's value readout (web tooltip row). */
data class ReadoutRow(val name: String, val value: String, val color: Color? = null)

/** Nice axis: [lo]..[hi] covering the data, with evenly spaced [ticks]. */
internal class Scale(val lo: Double, val hi: Double, val ticks: List<Double>, val step: Double) {
    fun frac(v: Double): Float = if (hi == lo) 0.5f else ((v - lo) / (hi - lo)).toFloat()
}

private fun niceStep(raw: Double): Double {
    if (raw <= 0 || !raw.isFinite()) return 1.0
    val exp = floor(log10(raw))
    val f = raw / 10.0.pow(exp)
    val nice = when {
        f <= 1.0 -> 1.0
        f <= 2.0 -> 2.0
        f <= 2.5 -> 2.5
        f <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * 10.0.pow(exp)
}

/** Axis with ~[target] ticks. [zeroBase] pins the low end to 0 (bars); otherwise the range hugs the data. */
internal fun niceScale(min: Double, max: Double, target: Int = 4, zeroBase: Boolean = false): Scale {
    var lo = if (zeroBase) 0.0 else min
    var hi = max
    if (hi - lo < 1e-9) {
        val pad = if (abs(hi) < 1e-9) 1.0 else abs(hi) * 0.1
        lo -= if (zeroBase) 0.0 else pad
        hi += pad
    }
    val step = niceStep((hi - lo) / target)
    val nLo = if (zeroBase) 0.0 else floor(lo / step) * step
    val nHi = ceil(hi / step) * step
    val ticks = ArrayList<Double>()
    var t = nLo
    var guard = 0
    while (t <= nHi + step * 1e-6 && guard++ < 50) {
        ticks.add(t)
        t += step
    }
    return Scale(nLo, nHi, ticks, step)
}

internal fun tickLabel(v: Double, step: Double): String {
    val decimals = if (step >= 1.0) 0 else ceil(-log10(step)).toInt().coerceIn(1, 3)
    return fmtNum(v, decimals)
}

/** Evenly spaced x-label indices that fit [avail] px when each label is [labelW] px wide. */
internal fun labelIndices(count: Int, avail: Float, labelW: Float): List<Int> {
    if (count <= 0) return emptyList()
    val slot = avail / count.coerceAtLeast(1)
    val per = ceil((labelW * 1.5f) / slot.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
    return (0 until count step per).toList()
}

internal class Plot(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val w: Float get() = right - left
    val h: Float get() = bottom - top
}

/** Pixel x of point [i] of [n] across [plot] (points at the edges) or of slot centres. */
internal fun Plot.xAt(i: Int, n: Int, centred: Boolean): Float =
    if (centred) left + w * (i + 0.5f) / n else if (n <= 1) left + w / 2 else left + w * i / (n - 1)

internal fun Plot.yAt(scale: Scale, v: Double): Float = bottom - h * scale.frac(v)

/** Axis text style (11 sp secondary). */
@Composable
internal fun axisStyle(): TextStyle = Cadence.type.caption2Medium.copy(color = Cadence.colors.textSecondary)

internal fun DrawScope.label(m: TextMeasurer, text: String, style: TextStyle, x: Float, y: Float, align: TextAlign = TextAlign.Start) {
    val size = m.measure(text, style).size
    val dx = when (align) {
        TextAlign.Center -> -size.width / 2f
        TextAlign.End, TextAlign.Right -> -size.width.toFloat()
        else -> 0f
    }
    drawText(m, text, Offset(x + dx, y), style)
}

/** Width in px of the widest of [labels] in [style]. */
internal fun TextMeasurer.widest(labels: List<String>, style: TextStyle): Float =
    labels.maxOfOrNull { measure(it, style).size.width } ?.toFloat() ?: 0f

/**
 * Restarts a 0..1 reveal (draw-in) whenever the charted content changes. Keyed on [key] (a content
 * hash) so a recomposition that rebuilds an equal list does not replay the animation.
 */
@Composable
internal fun rememberReveal(key: Any?): Float {
    val reveal = remember { Animatable(0f) }
    val spec = reduced(Motion.chart<Float>())
    LaunchedEffect(key) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, spec)
    }
    return reveal.value
}

/**
 * Tap or drag across the plot to pick a point (web hover). [count] items span [left]..[right];
 * [slots] true for bars (index = slot under the finger), false for line points (nearest point).
 */
@Composable
internal fun Modifier.scrub(count: Int, left: Float, right: Float, slots: Boolean, onIndex: (Int) -> Unit): Modifier {
    val callback by rememberUpdatedState(onIndex)
    fun idx(x: Float): Int {
        if (count <= 0) return 0
        val w = (right - left).coerceAtLeast(1f)
        val i = if (slots) floor((x - left) / w * count).toInt() else Math.round((x - left) / w * (count - 1))
        return i.coerceIn(0, count - 1)
    }
    return this
        .pointerInput(count, left, right, slots) { detectTapGestures { callback(idx(it.x)) } }
        .pointerInput(count, left, right, slots) {
            detectHorizontalDragGestures(onDragStart = { callback(idx(it.x)) }) { change, _ -> callback(idx(change.position.x)) }
        }
}

/**
 * The value readout above a chart: the picked point's title and rows, or a hint to touch the chart.
 * When [onOpen] is set the readout offers to open that day, the touch twin of the web click-through.
 */
@Composable
internal fun Readout(title: String?, rows: List<ReadoutRow>?, hint: String = "Touch the chart to inspect values", onOpen: (() -> Unit)? = null) {
    val c = Cadence.colors
    val t = Cadence.type
    Row(
        Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (title == null || rows == null) {
            Text(hint, style = t.caption1Regular, color = c.textTertiary)
        } else {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = t.caption1Semibold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                rows.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (r.color != null) Box(Modifier.size(8.dp).clip(CircleShape).background(r.color))
                        if (r.color != null) Box(Modifier.size(4.dp))
                        Text(r.value, style = t.caption1Medium, color = c.textPrimary, maxLines = 1)
                    }
                }
            }
            if (onOpen != null) {
                Text(
                    "Open day ›", style = t.caption1Semibold, color = c.accent.c600,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen).padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Monotone cubic path through the points of one gap-free run (web recharts `type="monotone"`). */
internal fun Path.monotoneRun(xs: FloatArray, ys: FloatArray, lineToFirst: Boolean = false) {
    val n = xs.size
    if (n == 0) return
    if (lineToFirst) lineTo(xs[0], ys[0]) else moveTo(xs[0], ys[0])
    if (n == 1) return
    if (n == 2) {
        lineTo(xs[1], ys[1])
        return
    }
    val d = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]).let { dx -> if (dx == 0f) 1e-6f else dx } }
    val m = FloatArray(n)
    m[0] = d[0]
    m[n - 1] = d[n - 2]
    for (k in 1 until n - 1) m[k] = if (d[k - 1] * d[k] <= 0f) 0f else (d[k - 1] + d[k]) / 2f
    for (k in 0 until n - 1) {
        if (d[k] == 0f) {
            m[k] = 0f
            m[k + 1] = 0f
        } else {
            val a = m[k] / d[k]
            val b = m[k + 1] / d[k]
            val s = a * a + b * b
            if (s > 9f) {
                val tau = 3f / sqrt(s)
                m[k] = tau * a * d[k]
                m[k + 1] = tau * b * d[k]
            }
        }
    }
    for (k in 0 until n - 1) {
        val dx = (xs[k + 1] - xs[k]) / 3f
        cubicTo(xs[k] + dx, ys[k] + m[k] * dx, xs[k + 1] - dx, ys[k + 1] - m[k + 1] * dx, xs[k + 1], ys[k + 1])
    }
}

/** Index ranges of consecutive non-null points (a null is a gap). */
internal fun runs(values: List<Double?>): List<IntRange> {
    val out = ArrayList<IntRange>()
    var start = -1
    for (i in values.indices) {
        if (values[i] != null) {
            if (start < 0) start = i
        } else if (start >= 0) {
            out.add(start until i)
            start = -1
        }
    }
    if (start >= 0) out.add(start until values.size)
    return out
}

/** `coerceIn` that never throws: when the plot is narrower than the label (empty range) the label is centered instead. */
internal fun Float.clampLabel(lo: Float, hi: Float): Float = if (lo > hi) (lo + hi) / 2f else coerceIn(lo, hi)
