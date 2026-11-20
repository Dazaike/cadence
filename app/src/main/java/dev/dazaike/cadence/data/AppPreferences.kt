package dev.dazaike.cadence.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.dazaike.cadence.analytics.DEFAULT_GOALS
import dev.dazaike.cadence.analytics.DEFAULT_MAX_HR
import dev.dazaike.cadence.analytics.Gran
import dev.dazaike.cadence.analytics.GoalKey
import dev.dazaike.cadence.analytics.RangePref
import dev.dazaike.cadence.analytics.RangePreset
import dev.dazaike.cadence.ui.theme.ACCENT_SYSTEM
import dev.dazaike.cadence.ui.theme.Accent
import dev.dazaike.cadence.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.appPrefsDataStore by preferencesDataStore(name = "cadence_app")

/** Display preferences from the web store that the data layer did not have yet. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** An [Accent] id, or [ACCENT_SYSTEM]. */
    val accentId: String = Accent.BLUE.id,
    val range: RangePref = RangePref(),
    val gran: Gran = Gran.DAY,
    val maxHr: Int = DEFAULT_MAX_HR,
    /** Sleep goal in hours. The cloud blob owns this key, so the app keeps it locally. */
    val sleepGoal: Double = DEFAULT_GOALS.getValue(GoalKey.SLEEP),
)

class AppPreferences(private val context: Context) {
    private val THEME = stringPreferencesKey("theme")
    private val ACCENT = stringPreferencesKey("accent")
    private val RANGE = stringPreferencesKey("range")
    private val RANGE_FROM = stringPreferencesKey("range_from")
    private val RANGE_TO = stringPreferencesKey("range_to")
    private val GRAN = stringPreferencesKey("gran")
    private val MAX_HR = intPreferencesKey("max_hr")
    private val SLEEP_GOAL = doublePreferencesKey("sleep_goal")

    val settings: Flow<AppSettings> = context.appPrefsDataStore.data.map { p ->
        AppSettings(
            themeMode = ThemeMode.fromId(p[THEME]),
            accentId = p[ACCENT]?.takeIf { it == ACCENT_SYSTEM || Accent.entries.any { a -> a.id == it } } ?: Accent.BLUE.id,
            range = RangePref(
                RangePreset.fromId(p[RANGE]),
                p[RANGE_FROM]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                p[RANGE_TO]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            ),
            gran = Gran.fromId(p[GRAN]),
            maxHr = p[MAX_HR] ?: DEFAULT_MAX_HR,
            sleepGoal = p[SLEEP_GOAL] ?: DEFAULT_GOALS.getValue(GoalKey.SLEEP),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.appPrefsDataStore.edit { it[THEME] = mode.id }.let { }

    suspend fun setAccent(id: String) = context.appPrefsDataStore.edit { it[ACCENT] = id }.let { }

    suspend fun setRange(range: RangePref) {
        context.appPrefsDataStore.edit {
            it[RANGE] = range.preset.id
            if (range.from != null) it[RANGE_FROM] = range.from.toString() else it.remove(RANGE_FROM)
            if (range.to != null) it[RANGE_TO] = range.to.toString() else it.remove(RANGE_TO)
        }
    }

    suspend fun setGran(gran: Gran) = context.appPrefsDataStore.edit { it[GRAN] = gran.id }.let { }

    suspend fun setMaxHr(bpm: Int) = context.appPrefsDataStore.edit { it[MAX_HR] = bpm }.let { }

    suspend fun setSleepGoal(hours: Double) = context.appPrefsDataStore.edit { it[SLEEP_GOAL] = hours }.let { }

    /** Settings > Reset preferences: appearance, range, max HR and sleep goal return to defaults. */
    suspend fun reset() = context.appPrefsDataStore.edit { it.clear() }.let { }
}
