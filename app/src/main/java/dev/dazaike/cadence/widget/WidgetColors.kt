package dev.dazaike.cadence.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider

/**
 * Widget palette, the same neutral surfaces as the app (white / `#121212` cards, `#ebebeb` /
 * `#262626` hairlines, neutral greys for text). Glance cannot read the in-app Light/Dark choice,
 * so widgets follow the system day/night setting.
 */
internal object WidgetColors {
    val card = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF121212))
    val border = ColorProvider(day = Color(0xFFEBEBEB), night = Color(0xFF262626))
    val tile = ColorProvider(day = Color(0xFFF7F7F7), night = Color(0xFF191919))
    val textPrimary = ColorProvider(day = Color(0xFF0A0A0A), night = Color(0xFFFAFAFA))
    val textSecondary = ColorProvider(day = Color(0xFF737373), night = Color(0xFF737373))
}
