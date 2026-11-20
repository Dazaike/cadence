package dev.dazaike.cadence.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.dazaike.cadence.R

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val InterFamily = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(R.font.inter_variable, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

private fun style(size: Int, line: Int, weight: Int, tracking: Float = 0f) = TextStyle(
    fontFamily = InterFamily,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.sp,
)

/** Numbers use tabular figures so counting values and aligned tables do not jitter. */
const val TABULAR = "tnum"

/**
 * The web type scale (`typography.css`): title-1 24/34, title-2 20/26, title-3 18/26, headline
 * 16/22, body 14/20, caption-1 12/16 (+0.15), caption-2 11/15 (+0.2).
 */
@Immutable
class CadenceType(
    val title1Semibold: TextStyle = style(24, 34, 600),
    val title1Medium: TextStyle = style(24, 34, 500),
    val title2Medium: TextStyle = style(20, 26, 500),
    val title3Semibold: TextStyle = style(18, 26, 600),
    val headlineSemibold: TextStyle = style(16, 22, 600),
    val headlineMedium: TextStyle = style(16, 22, 500),
    val bodyRegular: TextStyle = style(14, 20, 400),
    val bodyMedium: TextStyle = style(14, 20, 500),
    val bodySemibold: TextStyle = style(14, 20, 600),
    val caption1Regular: TextStyle = style(12, 16, 400, 0.15f),
    val caption1Medium: TextStyle = style(12, 16, 500, 0.15f),
    val caption1Semibold: TextStyle = style(12, 16, 600, 0.15f),
    val caption2Medium: TextStyle = style(11, 15, 500, 0.2f),
    val caption2Semibold: TextStyle = style(11, 15, 600, 0.2f),
)

val LocalCadenceType = staticCompositionLocalOf { CadenceType() }

/** Same scale mapped onto Material 3 roles, so stock M3 widgets (dialogs, fields, menus) match. */
internal fun materialTypography(t: CadenceType) = Typography(
    displayLarge = t.title1Semibold,
    displayMedium = t.title1Semibold,
    displaySmall = t.title1Semibold,
    headlineLarge = t.title1Semibold,
    headlineMedium = t.title2Medium,
    headlineSmall = t.title3Semibold,
    titleLarge = t.title3Semibold,
    titleMedium = t.headlineSemibold,
    titleSmall = t.bodySemibold,
    bodyLarge = t.bodyRegular,
    bodyMedium = t.bodyRegular,
    bodySmall = t.caption1Regular,
    labelLarge = t.bodyMedium,
    labelMedium = t.caption1Medium,
    labelSmall = t.caption2Medium,
)

fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR)

fun TextStyle.sized(size: TextUnit, line: TextUnit = this.lineHeight): TextStyle = copy(fontSize = size, lineHeight = line)
