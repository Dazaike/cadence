package dev.dazaike.cadence.ui.theme

import android.app.Activity
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/** Accent id meaning "use Material You system colours" (Android 12+). */
const val ACCENT_SYSTEM = "system"

/** Entry point for the semantic tokens: `Cadence.colors`, `Cadence.type`. */
object Cadence {
    val colors: CadenceColors @Composable get() = LocalCadenceColors.current
    val type: CadenceType @Composable get() = LocalCadenceType.current
}

private fun systemRamp(base: Color): AccentRamp = AccentRamp(
    c50 = lerp(base, Color.White, 0.93f),
    c100 = lerp(base, Color.White, 0.85f),
    c300 = lerp(base, Color.White, 0.5f),
    c400 = lerp(base, Color.White, 0.3f),
    c500 = lerp(base, Color.White, 0.12f),
    c600 = base,
    c700 = lerp(base, Color.Black, 0.15f),
    c950 = lerp(base, Color.Black, 0.75f),
)

@Composable
fun CadenceTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    accentId: String = Accent.BLUE.id,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val ramp = if (accentId == ACCENT_SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        systemRamp((if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary)
    } else {
        Accent.fromId(accentId).ramp()
    }
    val colors = remember(dark, ramp) { if (dark) darkColors(ramp) else lightColors(ramp) }
    val type = remember { CadenceType() }
    val reduceMotion = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    val scheme = if (dark) {
        darkColorScheme(
            primary = ramp.c600, onPrimary = colors.onFill,
            primaryContainer = ramp.c950, onPrimaryContainer = ramp.c300,
            secondary = ramp.c500, tertiary = ramp.c400,
            background = colors.pageBg, onBackground = colors.textPrimary,
            surface = colors.card, onSurface = colors.textPrimary,
            surfaceVariant = colors.raised, onSurfaceVariant = colors.textSecondary,
            surfaceContainerLowest = colors.pageBg, surfaceContainerLow = colors.card,
            surfaceContainer = colors.card, surfaceContainerHigh = colors.raised, surfaceContainerHighest = colors.raisedHover,
            outline = colors.border, outlineVariant = colors.hairline,
            error = colors.error, onError = colors.onFill, errorContainer = colors.errorBg, onErrorContainer = colors.error,
            scrim = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = ramp.c600, onPrimary = colors.onFill,
            primaryContainer = ramp.c100, onPrimaryContainer = ramp.c700,
            secondary = ramp.c500, tertiary = ramp.c400,
            background = colors.pageBg, onBackground = colors.textPrimary,
            surface = colors.card, onSurface = colors.textPrimary,
            surfaceVariant = colors.raised, onSurfaceVariant = colors.textSecondary,
            surfaceContainerLowest = colors.card, surfaceContainerLow = colors.card,
            surfaceContainer = colors.card, surfaceContainerHigh = colors.raised, surfaceContainerHighest = colors.raisedHover,
            outline = colors.border, outlineVariant = colors.hairline,
            error = colors.error, onError = colors.onFill, errorContainer = colors.errorBg, onErrorContainer = colors.error,
            scrim = Color.Black,
        )
    }

    CompositionLocalProvider(
        LocalCadenceColors provides colors,
        LocalCadenceType provides type,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = materialTypography(type),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(12.dp),
                large = RoundedCornerShape(16.dp),
                extraLarge = RoundedCornerShape(24.dp),
            ),
            content = content,
        )
    }
}
