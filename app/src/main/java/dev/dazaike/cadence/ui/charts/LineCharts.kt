package dev.dazaike.cadence.ui.charts

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.analytics.fmtDayLong
import dev.dazaike.cadence.analytics.fmtNum
import dev.dazaike.cadence.ui.theme.Cadence
import java.time.LocalDate
import kotlin.math.abs

private fun valueText(v: Double?, decimals: Int, unit: String): String =
    if (v == null) "no data" else "${fmtNum(v, decimals)}${if (unit.isBlank()) "" else " $unit"}"

/**
 * Line + area chart (web `TrendChart`): gaps for missing days, dotted moving average, dashed goal
 * line, optional brush to zoom/pan. Touch or drag to read values; [onPick] opens the picked day.
 */
@Composable
fun TrendChart(
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
    maName: String = "7-day avg",
) {
    val c = Cadence.colors
    val showBrush = brush && data.size > 14
    var start by remember(data.size) { mutableIntStateOf(0) }
    var end by remember(data.size) { mutableIntStateOf((data.size - 1).coerceAtLeast(0)) }
    val i0 = if (showBrush) start.coerceIn(0, (data.size - 1).coerceAtLeast(0)) else 0
    val i1 = if (showBrush) end.coerceIn(i0, (data.size - 1).coerceAtLeast(0)) else (data.size - 1).coerceAtLeast(0)
    val vis = if (data.isEmpty()) data else data.subList(i0, i1 + 1)
    var sel by remember(data.hashCode(), i0, i1) { mutableStateOf<Int?>(null) }
    val reveal = rememberReveal(data.hashCode())

    val measurer = rememberTextMeasurer()
    val axis = axisStyle()
    val density = LocalDensity.current
    val all = vis.flatMap { listOfNotNull(it.v, it.ma) } + listOfNotNull(goal)
    val scale = niceScale(all.minOrNull() ?: 0.0, all.maxOrNull() ?: 1.0, 4)
    val yLabels = scale.ticks.map { tickLabel(it, scale.step) }
    val yW = measurer.widest(yLabels, axis) + with(density) { 10.dp.toPx() }
    var widthPx by remember { mutableFloatStateOf(0f) }
    val heightPx = with(density) { height.toPx() }
    val pad = with(density) { 8.dp.toPx() }
    val xLabelH = with(density) { 20.dp.toPx() }
    val plot = Plot(yW, pad, (widthPx - pad).coerceAtLeast(yW + 1f), heightPx - xLabelH)
    val goalLabel = "goal"

    val s = sel?.coerceIn(0, (vis.size - 1).coerceAtLeast(0))
    Column(modifier) {
        val p = if (s != null && vis.isNotEmpty()) vis[s] else null
        Readout(
            title = p?.let { fmtDayLong(it.d) },
            rows = p?.let {
                buildList {
                    add(ReadoutRow(name, valueText(it.v, decimals, unit), color))
                    if (it.ma != null) add(ReadoutRow(maName, valueText(it.ma, decimals, unit)))
                }
            },
            onOpen = if (p != null && onPick != null) ({ onPick(p.d) }) else null,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .scrub(vis.size, plot.left, plot.right, slots = false) { sel = it },
        ) {
            // grid + y labels
            val hair = c.hairline
            scale.ticks.forEachIndexed { i, t ->
                val y = plot.yAt(scale, t)
                drawLine(hair, Offset(plot.left, y), Offset(plot.right, y), 1.dp.toPx())
                val sz = measurer.measure(yLabels[i], axis).size
                drawText(measurer, yLabels[i], Offset(plot.left - 8.dp.toPx() - sz.width, y - sz.height / 2f), axis)
            }
            // x labels
            if (vis.isNotEmpty()) {
                val widest = measurer.widest(vis.map { it.label }.distinct().take(40), axis)
                val idx = labelIndices(vis.size, plot.w, widest)
                idx.forEach { i ->
                    val x = plot.xAt(i, vis.size, false).clampLabel(plot.left + widest / 2, plot.right - widest / 2)
                    label(measurer, vis[i].label, axis, x, plot.bottom + 4.dp.toPx(), TextAlign.Center)
                }
            }
            // goal
            if (goal != null) {
                val y = plot.yAt(scale, goal)
                drawLine(color.copy(alpha = 0.6f), Offset(plot.left, y), Offset(plot.right, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                label(measurer, goalLabel, axis, plot.right - 2.dp.toPx(), y + 2.dp.toPx(), TextAlign.End)
            }
            val xs = FloatArray(vis.size) { plot.xAt(it, vis.size, false) }
            val values = vis.map { it.v }
            clipRect(left = 0f, top = 0f, right = plot.left + plot.w * reveal + 6.dp.toPx(), bottom = size.height) {
                val fill = Brush.verticalGradient(listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0f)), startY = plot.top, endY = plot.bottom)
                for (run in runs(values)) {
                    val rx = FloatArray(run.count()) { xs[run.first + it] }
                    val ry = FloatArray(run.count()) { plot.yAt(scale, values[run.first + it]!!) }
                    val line = Path().apply { monotoneRun(rx, ry) }
                    if (run.count() > 1) {
                        val area = Path().apply {
                            addPath(line)
                            lineTo(rx.last(), plot.bottom)
                            lineTo(rx.first(), plot.bottom)
                            close()
                        }
                        drawPath(area, fill)
                    }
                    drawPath(line, color, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                // dotted moving average
                val ma = vis.map { it.ma }
                for (run in runs(ma)) {
                    val rx = FloatArray(run.count()) { xs[run.first + it] }
                    val ry = FloatArray(run.count()) { plot.yAt(scale, ma[run.first + it]!!) }
                    val line = Path().apply { monotoneRun(rx, ry) }
                    drawPath(line, c.textPrimary.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
                }
                if (vis.size < 45) {
                    vis.forEachIndexed { i, pt -> if (pt.v != null) drawCircle(color, 2.5.dp.toPx(), Offset(xs[i], plot.yAt(scale, pt.v))) }
                }
            }
            // cursor
            if (s != null && vis.isNotEmpty()) {
                val x = xs[s]
                drawLine(color.copy(alpha = 0.4f), Offset(x, plot.top), Offset(x, plot.bottom), 1.dp.toPx())
                vis[s].v?.let {
                    drawCircle(c.card, 6.5.dp.toPx(), Offset(x, plot.yAt(scale, it)))
                    drawCircle(color, 4.5.dp.toPx(), Offset(x, plot.yAt(scale, it)))
                }
            }
        }
        if (showBrush) {
            BrushBar(data.map { it.v }, color, start, end, { a, b -> start = a; end = b }, Modifier.padding(top = 8.dp))
        }
    }
}

/**
 * Zoom/pan handles under a long series (web `Brush`): a mini overview with a draggable window.
 * Drag either handle to resize, or the window itself to pan.
 */
@Composable
internal fun BrushBar(values: List<Double?>, color: Color, start: Int, end: Int, onChange: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Cadence.colors
    val n = values.size
    val onChangeNow by rememberUpdatedState(onChange)
    val startNow by rememberUpdatedState(start)
    val endNow by rememberUpdatedState(end)
    val present = values.filterNotNull()
    val lo = present.minOrNull() ?: 0.0
    val hi = present.maxOrNull() ?: 1.0
    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(n) {
                var mode = 0 // 0 none, 1 left handle, 2 right handle, 3 pan
                val grab = 22.dp.toPx()
                fun idx(x: Float) = Math.round(x / size.width * (n - 1)).coerceIn(0, n - 1)
                detectDragGestures(
                    onDragStart = { o ->
                        val xs = startNow.toFloat() / (n - 1) * size.width
                        val xe = endNow.toFloat() / (n - 1) * size.width
                        mode = when {
                            abs(o.x - xs) <= grab && abs(o.x - xs) <= abs(o.x - xe) -> 1
                            abs(o.x - xe) <= grab -> 2
                            o.x in xs..xe -> 3
                            else -> 0
                        }
                    },
                ) { change, drag ->
                    val minSpan = 5
                    when (mode) {
                        1 -> onChangeNow(idx(change.position.x).coerceAtMost(endNow - minSpan), endNow)
                        2 -> onChangeNow(startNow, idx(change.position.x).coerceAtLeast(startNow + minSpan))
                        3 -> {
                            val shift = Math.round(drag.x / size.width * (n - 1))
                            val span = endNow - startNow
                            val ns = (startNow + shift).coerceIn(0, (n - 1 - span).coerceAtLeast(0))
                            onChangeNow(ns, ns + span)
                        }
                    }
                }
            },
    ) {
        val r = CornerRadius(8.dp.toPx())
        drawRoundRect(c.pageBg, size = size, cornerRadius = r)
        if (n > 1 && present.isNotEmpty()) {
            val xs = FloatArray(n) { it.toFloat() / (n - 1) * size.width }
            val path = Path()
            var pen = false
            for (i in 0 until n) {
                val v = values[i]
                if (v == null) {
                    pen = false
                    continue
                }
                val y = size.height - 6.dp.toPx() - (if (hi == lo) 0.5f else ((v - lo) / (hi - lo)).toFloat()) * (size.height - 12.dp.toPx())
                if (!pen) path.moveTo(xs[i], y) else path.lineTo(xs[i], y)
                pen = true
            }
            drawPath(path, color.copy(alpha = 0.7f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val xs0 = start.toFloat() / (n - 1) * size.width
            val xe0 = end.toFloat() / (n - 1) * size.width
            drawRoundRect(color.copy(alpha = 0.14f), Offset(xs0, 0f), Size(xe0 - xs0, size.height), r)
            val hw = 6.dp.toPx()
            for (hx in listOf(xs0, xe0)) {
                drawRoundRect(c.textTertiary, Offset(hx - hw / 2, size.height * 0.2f), Size(hw, size.height * 0.6f), CornerRadius(3.dp.toPx()))
            }
        }
    }
}

/** Daily min–max band with an average line (web `RangeChart`, heart rate). */
class RangePoint(val d: LocalDate, val label: String, val avg: Double?, val min: Double?, val max: Double?, val resting: Double? = null)

@Composable
fun RangeChart(
    data: List<RangePoint>,
    color: Color,
    unit: String,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
    brush: Boolean = false,
    onPick: ((LocalDate) -> Unit)? = null,
) {
    val c = Cadence.colors
    val showBrush = brush && data.size > 14
    var start by remember(data.size) { mutableIntStateOf(0) }
    var end by remember(data.size) { mutableIntStateOf((data.size - 1).coerceAtLeast(0)) }
    val i0 = if (showBrush) start.coerceIn(0, (data.size - 1).coerceAtLeast(0)) else 0
    val i1 = if (showBrush) end.coerceIn(i0, (data.size - 1).coerceAtLeast(0)) else (data.size - 1).coerceAtLeast(0)
    val vis = if (data.isEmpty()) data else data.subList(i0, i1 + 1)
    var sel by remember(data.size, i0, i1) { mutableStateOf<Int?>(null) }
    val reveal = rememberReveal(data.size * 31 + (data.firstOrNull { it.avg != null }?.avg?.hashCode() ?: 0))

    val measurer = rememberTextMeasurer()
    val axis = axisStyle()
    val density = LocalDensity.current
    val lows = vis.mapNotNull { it.min }
    val highs = vis.mapNotNull { it.max }
    val scale = niceScale((lows.minOrNull() ?: 0.0) - 5, (highs.maxOrNull() ?: 100.0) + 5, 4)
    val yLabels = scale.ticks.map { tickLabel(it, scale.step) }
    val yW = measurer.widest(yLabels, axis) + with(density) { 10.dp.toPx() }
    var widthPx by remember { mutableFloatStateOf(0f) }
    val heightPx = with(density) { height.toPx() }
    val pad = with(density) { 8.dp.toPx() }
    val plot = Plot(yW, pad, (widthPx - pad).coerceAtLeast(yW + 1f), heightPx - with(density) { 20.dp.toPx() })

    val s = sel?.coerceIn(0, (vis.size - 1).coerceAtLeast(0))
    Column(modifier) {
        val p = if (s != null && vis.isNotEmpty()) vis[s] else null
        Readout(
            title = p?.let { fmtDayLong(it.d) },
            rows = p?.let {
                buildList {
                    add(ReadoutRow("Average", if (it.avg == null) "—" else "${Math.round(it.avg)} $unit", color))
                    add(ReadoutRow("Range", if (it.min == null || it.max == null) "—" else "${Math.round(it.min)}–${Math.round(it.max)} $unit"))
                    if (it.resting != null) add(ReadoutRow("Lowest hourly / resting", "${Math.round(it.resting)} $unit"))
                }
            },
            onOpen = if (p != null && onPick != null) ({ onPick(p.d) }) else null,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .scrub(vis.size, plot.left, plot.right, slots = false) { sel = it },
        ) {
            scale.ticks.forEachIndexed { i, t ->
                val y = plot.yAt(scale, t)
                drawLine(c.hairline, Offset(plot.left, y), Offset(plot.right, y), 1.dp.toPx())
                val sz = measurer.measure(yLabels[i], axis).size
                drawText(measurer, yLabels[i], Offset(plot.left - 8.dp.toPx() - sz.width, y - sz.height / 2f), axis)
            }
            if (vis.isNotEmpty()) {
                val widest = measurer.widest(vis.map { it.label }.distinct().take(40), axis)
                labelIndices(vis.size, plot.w, widest).forEach { i ->
                    val x = plot.xAt(i, vis.size, false).clampLabel(plot.left + widest / 2, plot.right - widest / 2)
                    label(measurer, vis[i].label, axis, x, plot.bottom + 4.dp.toPx(), TextAlign.Center)
                }
            }
            val xs = FloatArray(vis.size) { plot.xAt(it, vis.size, false) }
            val avgs = vis.map { it.avg }
            clipRect(left = 0f, top = 0f, right = plot.left + plot.w * reveal + 6.dp.toPx(), bottom = size.height) {
                for (run in runs(avgs)) {
                    val idx = run.toList()
                    val topY = FloatArray(idx.size) { plot.yAt(scale, vis[idx[it]].max ?: vis[idx[it]].avg!!) }
                    val botY = FloatArray(idx.size) { plot.yAt(scale, vis[idx[it]].min ?: vis[idx[it]].avg!!) }
                    val rx = FloatArray(idx.size) { xs[idx[it]] }
                    val band = Path().apply {
                        monotoneRun(rx, topY)
                        monotoneRun(FloatArray(rx.size) { rx[rx.size - 1 - it] }, FloatArray(botY.size) { botY[botY.size - 1 - it] }, lineToFirst = true)
                        close()
                    }
                    drawPath(band, color.copy(alpha = 0.18f))
                    val ry = FloatArray(idx.size) { plot.yAt(scale, vis[idx[it]].avg!!) }
                    drawPath(Path().apply { monotoneRun(rx, ry) }, color, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                if (vis.size < 40) vis.forEachIndexed { i, pt -> if (pt.avg != null) drawCircle(color, 2.5.dp.toPx(), Offset(xs[i], plot.yAt(scale, pt.avg))) }
            }
            if (s != null && vis.isNotEmpty()) {
                val x = xs[s]
                drawLine(color.copy(alpha = 0.4f), Offset(x, plot.top), Offset(x, plot.bottom), 1.dp.toPx())
                vis[s].avg?.let {
                    drawCircle(c.card, 6.5.dp.toPx(), Offset(x, plot.yAt(scale, it)))
                    drawCircle(color, 4.5.dp.toPx(), Offset(x, plot.yAt(scale, it)))
                }
            }
        }
        if (showBrush) {
            BrushBar(data.map { it.avg }, color, start, end, { a, b -> start = a; end = b }, Modifier.padding(top = 8.dp))
        }
    }
}

/** One day on a scatter plot (web `ScatterPlot`). */
class ScatterDot(val d: LocalDate, val x: Double, val y: Double)

@Composable
fun ScatterPlot(
    data: List<ScatterDot>,
    color: Color,
    xLabel: String,
    yLabel: String,
    xUnit: String,
    yUnit: String,
    modifier: Modifier = Modifier,
    height: Dp = 260.dp,
    onPick: ((LocalDate) -> Unit)? = null,
) {
    val c = Cadence.colors
    var sel by remember(data) { mutableStateOf<Int?>(null) }
    val reveal = rememberReveal(data.hashCode())
    val measurer = rememberTextMeasurer()
    val axis = axisStyle()
    val density = LocalDensity.current
    val xs = niceScale(data.minOfOrNull { it.x } ?: 0.0, data.maxOfOrNull { it.x } ?: 1.0, 4)
    val ys = niceScale(data.minOfOrNull { it.y } ?: 0.0, data.maxOfOrNull { it.y } ?: 1.0, 4)
    val yLabels = ys.ticks.map { tickLabel(it, ys.step) }
    val yW = measurer.widest(yLabels, axis) + with(density) { 10.dp.toPx() }
    var widthPx by remember { mutableFloatStateOf(0f) }
    val heightPx = with(density) { height.toPx() }
    val pad = with(density) { 12.dp.toPx() }
    val plot = Plot(yW, pad, (widthPx - pad).coerceAtLeast(yW + 1f), heightPx - with(density) { 36.dp.toPx() })
    val xTickLabels = xs.ticks.map { tickLabel(it, xs.step) }

    Column(modifier) {
        val p = sel?.let { data.getOrNull(it) }
        Readout(
            title = p?.let { fmtDayLong(it.d) },
            rows = p?.let { listOf(ReadoutRow(xLabel, "${fmtNum(it.x, 1)} $xUnit", color), ReadoutRow(yLabel, "${fmtNum(it.y, 1)} $yUnit")) },
            onOpen = if (p != null && onPick != null) ({ onPick(p.d) }) else null,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .pointerInput(data, plot.left, plot.right, plot.top, plot.bottom) {
                    detectDragGestures(
                        onDragStart = { o -> sel = nearest(data, xs, ys, plot, o) },
                    ) { change, _ -> sel = nearest(data, xs, ys, plot, change.position) }
                }
                .pointerInput(data, plot.left, plot.right, plot.top, plot.bottom) {
                    detectTapGestures { o -> sel = nearest(data, xs, ys, plot, o) }
                },
        ) {
            ys.ticks.forEachIndexed { i, t ->
                val y = plot.bottom - plot.h * ys.frac(t)
                drawLine(c.hairline, Offset(plot.left, y), Offset(plot.right, y), 1.dp.toPx())
                val sz = measurer.measure(yLabels[i], axis).size
                drawText(measurer, yLabels[i], Offset(plot.left - 8.dp.toPx() - sz.width, y - sz.height / 2f), axis)
            }
            xs.ticks.forEachIndexed { i, t ->
                val x = plot.left + plot.w * xs.frac(t)
                drawLine(c.hairline, Offset(x, plot.top), Offset(x, plot.bottom), 1.dp.toPx())
                label(measurer, xTickLabels[i], axis, x, plot.bottom + 4.dp.toPx(), TextAlign.Center)
            }
            label(measurer, "$xLabel ($xUnit)", axis, (plot.left + plot.right) / 2, plot.bottom + 18.dp.toPx(), TextAlign.Center)
            data.forEachIndexed { i, d ->
                val o = Offset(plot.left + plot.w * xs.frac(d.x), plot.bottom - plot.h * ys.frac(d.y))
                drawCircle(color.copy(alpha = 0.7f * reveal), 4.dp.toPx() * (if (i == sel) 1.5f else 1f), o)
            }
        }
    }
}

private fun nearest(data: List<ScatterDot>, xs: Scale, ys: Scale, plot: Plot, at: Offset): Int? {
    var best: Int? = null
    var bestD = Float.MAX_VALUE
    data.forEachIndexed { i, d ->
        val dx = plot.left + plot.w * xs.frac(d.x) - at.x
        val dy = plot.bottom - plot.h * ys.frac(d.y) - at.y
        val dist = dx * dx + dy * dy
        if (dist < bestD) {
            bestD = dist
            best = i
        }
    }
    return best
}
