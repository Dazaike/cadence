package dev.dazaike.cadence.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw colour primitives, resolved to sRGB hex from the web dashboard's `theme.css` (Tailwind v4
 * OKLCH defaults plus BoardUI's four overrides: neutral-100 `#f7f7f7`, neutral-200 `#ebebeb`,
 * neutral-925 `#121212`, blue-400 `#3392ff`). Semantic roles live in [CadenceColors].
 */
internal object Palette {
    val White = Color(0xFFFFFFFF)

    // neutral ramp (50..950); 100 and 200 are BoardUI overrides
    val Neutral50 = Color(0xFFFAFAFA)
    val Neutral100 = Color(0xFFF7F7F7)
    val Neutral200 = Color(0xFFEBEBEB)
    val Neutral300 = Color(0xFFD4D4D4)
    val Neutral400 = Color(0xFFA1A1A1)
    val Neutral500 = Color(0xFF737373)
    val Neutral600 = Color(0xFF525252)
    val Neutral700 = Color(0xFF404040)
    val Neutral800 = Color(0xFF262626)
    val Neutral900 = Color(0xFF171717)
    val Neutral950 = Color(0xFF0A0A0A)

    // dark-mode surface overrides from the web's index.css
    val DarkPage = Color(0xFF0B0B0B)
    val DarkCard = Color(0xFF121212)
    val DarkRaised = Color(0xFF191919)
    val DarkRaisedHover = Color(0xFF222222)
    val DarkBorder = Color(0xFF262626)
    val DarkHairline = Color(0xFF1C1C1C)

    // chart categorical tokens (chart-1 .. chart-9)
    val Chart1 = Color(0xFF00D5BE) // teal-400   activity
    val Chart2 = Color(0xFF9AE600) // lime-400
    val Chart3 = Color(0xFFFB64B6) // pink-400   heart
    val Chart4 = Color(0xFF00BCFF) // sky-400    body, light sleep
    val Chart5 = Color(0xFFC27AFF) // purple-400 REM
    val Chart6 = Color(0xFF3392FF) // blue-400
    val Chart7 = Color(0xFF00D492) // emerald-400 recovery
    val Chart8 = Color(0xFFFDC700) // yellow-400  awake, insights
    val Chart9 = Color(0xFF7C86FF) // indigo-400  sleep

    // status
    val Lime300 = Color(0xFFBBF451)
    val Lime600 = Color(0xFF5EA500)
    val Red400 = Color(0xFFFF6467)
    val Red500 = Color(0xFFFB2C36)
    val Rose300 = Color(0xFFFFA1AD)
    val Rose600 = Color(0xFFEC003F)
}

/** Selectable accent, the web's `ACCENTS`. Each carries the ramp stops the UI actually uses. */
enum class Accent(val id: String, val label: String, val s50: Color, val s100: Color, val s300: Color, val s400: Color, val s500: Color, val s600: Color, val s700: Color, val s950: Color) {
    BLUE("blue", "Blue", Color(0xFFEFF6FF), Color(0xFFDBEAFE), Color(0xFF8EC5FF), Color(0xFF3392FF), Color(0xFF2B7FFF), Color(0xFF155DFC), Color(0xFF1447E6), Color(0xFF162456)),
    VIOLET("violet", "Violet", Color(0xFFF5F3FF), Color(0xFFEDE9FE), Color(0xFFC4B4FF), Color(0xFFA684FF), Color(0xFF8E51FF), Color(0xFF7F22FE), Color(0xFF7008E7), Color(0xFF2F0D68)),
    ROSE("rose", "Rose", Color(0xFFFFF1F2), Color(0xFFFFE4E6), Color(0xFFFFA1AD), Color(0xFFFF637E), Color(0xFFFF2056), Color(0xFFEC003F), Color(0xFFC70036), Color(0xFF4D0218)),
    ORANGE("orange", "Orange", Color(0xFFFFF7ED), Color(0xFFFFEDD4), Color(0xFFFFB86A), Color(0xFFFF8904), Color(0xFFFF6900), Color(0xFFF54900), Color(0xFFCA3500), Color(0xFF441306)),
    EMERALD("emerald", "Emerald", Color(0xFFECFDF5), Color(0xFFD0FAE5), Color(0xFF5EE9B5), Color(0xFF00D492), Color(0xFF00BC7D), Color(0xFF009966), Color(0xFF007A55), Color(0xFF002C22)),
    AMBER("amber", "Amber", Color(0xFFFFFBEB), Color(0xFFFEF3C6), Color(0xFFFFD230), Color(0xFFFFB900), Color(0xFFFE9A00), Color(0xFFE17100), Color(0xFFBB4D00), Color(0xFF461901)),
    CYAN("cyan", "Cyan", Color(0xFFECFEFF), Color(0xFFCEFAFE), Color(0xFF53EAFD), Color(0xFF00D3F2), Color(0xFF00B8DB), Color(0xFF0092B8), Color(0xFF007595), Color(0xFF053345));

    companion object {
        fun fromId(id: String?): Accent = entries.firstOrNull { it.id == id } ?: BLUE
    }
}
