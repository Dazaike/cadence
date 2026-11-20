package dev.dazaike.cadence.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import dev.dazaike.cadence.R
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.Dimens
import dev.dazaike.cadence.ui.theme.LocalReduceMotion
import dev.dazaike.cadence.ui.theme.Palette
import androidx.compose.runtime.getValue

/** Empty state: dashed 16 dp box with a title and a hint (web `Empty`). Nothing is ever faked. */
@Composable
fun Empty(title: String, modifier: Modifier = Modifier, hint: String? = null, icon: ImageVector? = null) {
    val c = Cadence.colors
    val dash = Dimens.InnerRadius
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    c.hairline,
                    topLeft = Offset(0.5f, 0.5f),
                    size = Size(size.width - 1f, size.height - 1f),
                    cornerRadius = CornerRadius(dash.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        if (icon != null) Icon(icon, null, tint = c.iconSecondary, modifier = Modifier.size(20.dp))
        Text(title, style = Cadence.type.bodyMedium, color = c.textPrimary, textAlign = TextAlign.Center)
        if (hint != null) Text(hint, style = Cadence.type.caption1Regular, color = c.textSecondary, textAlign = TextAlign.Center)
    }
}

/** Pulsing placeholder block while data loads (web `Skeleton`). Static when animations are off. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, radius: Dp = Dimens.CardRadius) {
    val c = Cadence.colors
    val reduce = LocalReduceMotion.current
    val animated by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    Box(modifier.clip(RoundedCornerShape(radius)).background(c.raised).alpha(if (reduce) 0.7f else animated))
}

/** Loading layout shown while the first read of the health data is in flight. */
@Composable
fun PageSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.padding(Dimens.PageHPad), verticalArrangement = Arrangement.spacedBy(Dimens.PageGap)) {
        SkeletonBlock(Modifier.height(40.dp).fillMaxWidth(0.6f), 12.dp)
        repeat(3) { SkeletonBlock(Modifier.height(200.dp).fillMaxWidth()) }
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** Rose error note (web `ErrorNote`). */
@Composable
fun ErrorNote(text: String, modifier: Modifier = Modifier) {
    val c = Cadence.colors
    Text(
        text,
        style = Cadence.type.bodyRegular,
        color = if (c.dark) Palette.Rose300 else Color(0xFFEC003F),
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.errorBg).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/**
 * The pixel-art Cadence mark: white rings on a transparent PNG, drawn over a theme-aware tile. The
 * 32 px source is shown at exactly 32 dp. Nearest-neighbour (filter quality none) keeps it crisp.
 */
@Composable
fun Logo(modifier: Modifier = Modifier, tile: Dp = 36.dp) {
    val c = Cadence.colors
    Box(
        modifier.size(tile).clip(RoundedCornerShape(12.dp)).background(if (c.dark) c.raised else c.textPrimary),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = ImageBitmap.imageResource(R.drawable.cadence_mark),
            contentDescription = null,
            filterQuality = FilterQuality.None,
            modifier = Modifier.size(32.dp),
        )
    }
}

/** Logo + wordmark + subtitle (sidebar header, onboarding, About). */
@Composable
fun BrandHeader(modifier: Modifier = Modifier) {
    val c = Cadence.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Logo()
        Spacer(Modifier.size(10.dp))
        Column {
            Text("Cadence", style = Cadence.type.bodySemibold, color = c.textPrimary)
            Text("Health dashboard", style = Cadence.type.caption1Regular, color = c.textSecondary)
        }
    }
}
