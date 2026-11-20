package dev.dazaike.cadence.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Axis-less trend line with a soft area, drawn in on first show (overview widgets, heart and body). */
@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier, height: Dp = 70.dp) {
    val reveal = rememberReveal(values.hashCode())
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.size < 2) return@Canvas
        val lo = values.min()
        val hi = values.max()
        val padY = 6.dp.toPx()
        val h = size.height - 2 * padY
        val xs = FloatArray(values.size) { size.width * it / (values.size - 1) }
        val ys = FloatArray(values.size) { padY + h * (1f - (if (hi == lo) 0.5f else ((values[it] - lo) / (hi - lo)).toFloat())) }
        val line = Path().apply { monotoneRun(xs, ys) }
        clipRect(right = size.width * reveal) {
            val area = Path().apply {
                addPath(line)
                lineTo(xs.last(), size.height)
                lineTo(xs.first(), size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0f))))
            drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(color, 3.dp.toPx(), Offset(xs.last(), ys.last()))
        }
    }
}
