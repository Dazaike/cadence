package dev.dazaike.cadence.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Appearance choice in Settings. */
enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** Accent ramp stops in use (the web's `--color-accent-*`). */
@Immutable
class AccentRamp(
    val c50: Color,
    val c100: Color,
    val c300: Color,
    val c400: Color,
    val c500: Color,
    val c600: Color,
    val c700: Color,
    val c950: Color,
)

internal fun Accent.ramp() = AccentRamp(s50, s100, s300, s400, s500, s600, s700, s950)

/** A pill's colours: subtle fill with a readable label. */
@Immutable
class Tone(val bg: Color, val fg: Color)

/**
 * Semantic colour roles, named after the web dashboard's BoardUI tokens so the two stay easy to
 * compare: `pageBg` = background-secondary-default (body), `card` = background-primary-default,
 * `raised` = background-tertiary-default, `border` = border-button-default, `hairline` =
 * separator-border.
 */
@Immutable
class CadenceColors(
    val dark: Boolean,
    val pageBg: Color,
    val card: Color,
    val raised: Color,
    val raisedHover: Color,
    val inset: Color,
    val border: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val iconSecondary: Color,
    val chartTrack: Color,
    val success: Color,
    val error: Color,
    val errorBg: Color,
    val accent: AccentRamp,
    /** The web's `text-white`, for text over saturated fills. */
    val onFill: Color,
    val toneNeutral: Tone,
    val toneYellow: Tone,
    val toneLime: Tone,
    val toneRose: Tone,
    val tonePurple: Tone,
    val toneAccent: Tone,
    val scrim: Color,
) {
    /** The nav/tab "active" accent colour: accent-600 (the web's active icon tint). */
    val accentStrong: Color get() = accent.c600
}

internal fun lightColors(accent: AccentRamp) = CadenceColors(
    dark = false,
    pageBg = Palette.Neutral100,
    card = Palette.White,
    raised = Palette.Neutral200,
    raisedHover = Palette.Neutral300,
    inset = Palette.Neutral100,
    border = Palette.Neutral200,
    hairline = Palette.Neutral200,
    textPrimary = Palette.Neutral950,
    textSecondary = Palette.Neutral500,
    textTertiary = Palette.Neutral400,
    iconSecondary = Palette.Neutral500,
    chartTrack = Palette.Neutral200,
    success = Palette.Lime600,
    error = Palette.Red500,
    errorBg = Color(0xFFFFE4E6),
    accent = accent,
    onFill = Palette.White,
    toneNeutral = Tone(Palette.Neutral200, Palette.Neutral600),
    toneYellow = Tone(Color(0xFFFEF9C2), Color(0xFFA65F00)),
    toneLime = Tone(Color(0xFFECFCCA), Color(0xFF497D00)),
    toneRose = Tone(Color(0xFFFFE4E6), Color(0xFFA50036)),
    tonePurple = Tone(Color(0xFFF3E8FF), Color(0xFF8200DB)),
    toneAccent = Tone(accent.c100, accent.c700),
    scrim = Color(0x66000000),
)

internal fun darkColors(accent: AccentRamp) = CadenceColors(
    dark = true,
    pageBg = Palette.DarkPage,
    card = Palette.DarkCard,
    raised = Palette.DarkRaised,
    raisedHover = Palette.DarkRaisedHover,
    inset = Palette.DarkRaised.copy(alpha = 0.6f),
    border = Palette.DarkBorder,
    hairline = Palette.DarkHairline,
    textPrimary = Palette.Neutral50,
    textSecondary = Palette.Neutral500,
    textTertiary = Palette.Neutral600,
    iconSecondary = Palette.Neutral500,
    chartTrack = Palette.Neutral800,
    success = Palette.Lime300,
    error = Palette.Red400,
    errorBg = Color(0x80A50036).compositeOver(Palette.DarkCard),
    accent = accent,
    onFill = Palette.White,
    toneNeutral = Tone(Palette.Neutral800, Palette.Neutral400),
    toneYellow = Tone(Color(0xFF432004).copy(alpha = 0.6f), Color(0xFFFFDF20)),
    toneLime = Tone(Color(0xFF192E03).copy(alpha = 0.6f), Palette.Lime300),
    toneRose = Tone(Color(0xFF4D0218).copy(alpha = 0.6f), Palette.Rose300),
    tonePurple = Tone(Color(0xFF3C0366).copy(alpha = 0.6f), Color(0xFFDAB2FF)),
    toneAccent = Tone(accent.c950.copy(alpha = 0.6f), accent.c300),
    scrim = Color(0x99000000),
)

/** Category colours: the web's `CAT_COLOR`, plus sleep stage colours (`STAGE_COLORS`). */
object CatColor {
    val activity = Palette.Chart1
    val heart = Palette.Chart3
    val sleep = Palette.Chart9
    val body = Palette.Chart4
    val recovery = Palette.Chart7
    val insight = Palette.Chart8
    val warm = Palette.Chart8
}

object StageColors {
    val deep = Palette.Chart9
    val rem = Palette.Chart5
    val light = Palette.Chart4
    val awake = Palette.Chart8
}

/** Heart-rate zone colours (web `ZONES`). Rest uses the theme's neutral chart colour. */
object ZoneColors {
    val light = Palette.Chart4
    val fatBurn = Palette.Chart7
    val cardio = Palette.Chart8
    val peak = Palette.Chart3
}

/** Layout constants shared by the pages (web `rounded-3xl`, `rounded-xl`, spacing scale). */
object Dimens {
    val CardRadius: Dp = 24.dp
    val InnerRadius: Dp = 16.dp // rounded-2xl
    val ControlRadius: Dp = 12.dp // rounded-xl
    val CardPadding: Dp = 20.dp // p-5
    val PageGap: Dp = 20.dp // gap-5
    val PageHPad: Dp = 16.dp
    const val WideBreakpointDp = 840
}

val LocalCadenceColors = staticCompositionLocalOf<CadenceColors> { error("CadenceTheme missing") }

/** True when the "remove animations" system setting is on (animator scale 0). Set by [CadenceTheme]. */
val LocalReduceMotion = staticCompositionLocalOf { false }
