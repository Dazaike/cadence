package dev.dazaike.cadence.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.Motion
import dev.dazaike.cadence.ui.theme.reduced
import dev.dazaike.cadence.ui.theme.tabular

/**
 * 270-degree arc gauge for a 0..100 score (the web uses Arc's `Gauge` for readiness). The arc
 * sweeps in on first show; [label] sits under the number and [detail] under the arc.
 */
@Composable
fun Gauge(
    value: Double,
    label: String,
    detail: String,
    modifier: Modifier = Modifier,
    color: Color = Cadence.colors.accent.c600,
) {
    val c = Cadence.colors
    val frac = (value / 100.0).coerceIn(0.0, 1.0).toFloat()
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(frac) { target = frac }
    val sweep by animateFloatAsState(target, reduced(Motion.ring()), label = "gauge")
    Column(
        modifier.semantics { contentDescription = "$label ${Math.round(value)} out of 100. $detail" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                val sw = size.width * 0.1f
                val arc = Size(size.width - sw, size.height - sw)
                val tl = Offset(sw / 2, sw / 2)
                drawArc(c.chartTrack, 135f, 270f, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
                if (sweep > 0f) drawArc(color, 135f, 270f * sweep, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Math.round(value).toString(), style = Cadence.type.title2Medium.tabular(), color = c.textPrimary)
                Text(label, style = Cadence.type.caption1Regular, color = c.textSecondary)
            }
        }
        Text(detail, style = Cadence.type.caption1Regular, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
    }
}
