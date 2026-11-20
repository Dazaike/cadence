package dev.dazaike.cadence.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.analytics.fmtNum
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.Motion
import dev.dazaike.cadence.ui.theme.reduced
import dev.dazaike.cadence.ui.theme.tabular

/** Number that counts up from zero (web `CountUp`, 0.9 s). */
@Composable
fun CountUp(value: Double, decimals: Int, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0f) }
    val spec = reduced(Motion.countUp<Float>())
    LaunchedEffect(value) { anim.animateTo(value.toFloat(), spec) }
    Text(fmtNum(anim.value.toDouble(), decimals), style = style.tabular(), color = color, modifier = modifier)
}

/**
 * The web `Stat`: caption label, big tabular value (counts up) with an optional unit, optional
 * sub-line. [text] shows a pre-formatted value instead; a null [value] shows an em dash.
 */
@Composable
fun Stat(
    label: String,
    value: Double?,
    modifier: Modifier = Modifier,
    sub: String? = null,
    color: Color? = null,
    decimals: Int = 0,
    unit: String? = null,
    text: String? = null,
) {
    val c = Cadence.colors
    val t = Cadence.type
    Column(modifier) {
        Text(label, style = t.caption1Medium, color = c.textSecondary)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                text != null -> Text(text, style = t.headlineSemibold.tabular(), color = c.textPrimary, modifier = Modifier.alignByBaseline())
                value == null -> Text("—", style = t.title1Medium, color = c.textTertiary, modifier = Modifier.alignByBaseline())
                else -> CountUp(value, decimals, t.title1Medium, c.textPrimary, Modifier.alignByBaseline())
            }
            if (unit != null && value != null) Text(unit, style = t.caption1Regular, color = c.textSecondary, modifier = Modifier.alignByBaseline())
        }
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            Text(sub, style = t.caption1Regular, color = color ?: c.textSecondary)
        }
    }
}

/** Progress ring (web `Ring`): neutral track, rounded stroke, spring sweep, content centred. */
@Composable
fun Ring(
    value: Double,
    max: Double,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    stroke: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val track = Cadence.colors.chartTrack
    val frac = (if (max > 0) value / max else 0.0).coerceIn(0.0, 1.0).toFloat()
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(frac) { target = frac }
    val sweep by animateFloatAsState(target, reduced(Motion.ring()), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val tl = Offset(sw / 2, sw / 2)
            val arc = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, -90f, 360f, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
            if (sweep > 0f) drawArc(color, -90f, 360f * sweep, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Thin progress bar on the chart track colour (web daily-rings and goals bars). */
@Composable
fun LinearMeter(fraction: Double, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    var target by remember { mutableFloatStateOf(0f) }
    val f = fraction.coerceIn(0.0, 1.0).toFloat()
    LaunchedEffect(f) { target = f }
    val w by animateFloatAsState(target, reduced(Motion.bar()), label = "meter")
    val shape = RoundedCornerShape(50)
    Box(modifier.fillMaxWidth().height(height).clip(shape).background(Cadence.colors.chartTrack)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(w).clip(shape).background(color))
    }
}

/** Week-over-week change like the web `Delta`: green when good, red when bad, grey when under 1 %. */
@Composable
fun DeltaText(pct: Double, modifier: Modifier = Modifier, invert: Boolean = false, style: TextStyle = Cadence.type.caption1Regular) {
    val c = Cadence.colors
    if (!pct.isFinite()) {
        Text("n/a", style = style, color = c.textSecondary, modifier = modifier)
        return
    }
    val good = if (invert) pct < 0 else pct > 0
    val tone = when {
        kotlin.math.abs(pct) < 1 -> c.textSecondary
        good -> c.success
        else -> c.error
    }
    Text((if (pct > 0) "+" else "") + String.format(java.util.Locale.US, "%.1f%%", pct), style = style.tabular(), color = tone, modifier = modifier)
}

/** Centred big-value label used inside rings. */
@Composable
fun RingLabel(primary: String, secondary: String? = null) {
    val c = Cadence.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(primary, style = Cadence.type.title3Semibold.tabular(), color = c.textPrimary, textAlign = TextAlign.Center)
        if (secondary != null) Text(secondary, style = Cadence.type.caption1Regular, color = c.textSecondary, textAlign = TextAlign.Center)
    }
}
