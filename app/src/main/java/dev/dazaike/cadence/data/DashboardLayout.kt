package dev.dazaike.cadence.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dashboardLayoutDataStore by preferencesDataStore(name = "cadence_dashboard_layouts")

/** Widget footprint on Overview: on a phone S is half width, M and L are full width. */
enum class WidgetSize {
    S, M, L;

    val next: WidgetSize get() = when (this) { S -> M; M -> L; L -> S }
}

/** A named dashboard arrangement (web `Layout`): order, hidden widgets and per-widget sizes. */
data class Layout(
    val id: String,
    val name: String,
    val order: List<String>,
    val hidden: List<String>,
    val sizes: Map<String, WidgetSize>,
)

/** Overview widget ids and their defaults, matching the web store's `DEFAULT_ORDER` / `DEFAULT_SIZES`. */
object WidgetDefaults {
    val ORDER = listOf("daily", "steps", "heart", "sleep", "recovery", "body", "insights", "goals", "consistency")
    val SIZES = mapOf(
        "daily" to WidgetSize.S,
        "steps" to WidgetSize.M,
        "heart" to WidgetSize.S,
        "sleep" to WidgetSize.M,
        "recovery" to WidgetSize.S,
        "body" to WidgetSize.S,
        "goals" to WidgetSize.L,
        "insights" to WidgetSize.S,
        "consistency" to WidgetSize.L,
    )

    fun layout(name: String, id: String = "l-" + java.util.UUID.randomUUID().toString().take(6)) =
        Layout(id, name, ORDER, emptyList(), SIZES)
}

/** Layout with unknown ids removed and missing widgets appended, ready to render. */
fun Layout.normalized(): Layout {
    val known = order.filter { it in WidgetDefaults.ORDER }
    return copy(order = known + WidgetDefaults.ORDER.filter { it !in known }, sizes = WidgetDefaults.SIZES + sizes)
}

data class LayoutState(val layouts: List<Layout>, val activeId: String) {
    val active: Layout get() = layouts.firstOrNull { it.id == activeId } ?: layouts.first()
}

/**
 * Named dashboard layouts (web store `layouts` / `activeLayout`), persisted as JSON. Ids in a
 * stored order that no longer exist are dropped and new widgets are appended when read, so adding
 * a widget never needs a migration.
 */
class DashboardLayoutRepository(private val context: Context) {

    private val LAYOUTS = stringPreferencesKey("layouts")
    private val ACTIVE = stringPreferencesKey("active")

    val state: Flow<LayoutState> = context.dashboardLayoutDataStore.data.map { prefs ->
        val layouts = decode(prefs[LAYOUTS]).ifEmpty { listOf(WidgetDefaults.layout("Default", "default")) }
        LayoutState(layouts, prefs[ACTIVE] ?: layouts.first().id)
    }

    /** Back to a single default layout (Settings > Reset preferences). */
    suspend fun clear() {
        context.dashboardLayoutDataStore.edit { it.clear() }
    }

    suspend fun setActive(id: String) {
        context.dashboardLayoutDataStore.edit { it[ACTIVE] = id }
    }

    suspend fun update(id: String, change: (Layout) -> Layout) = mutate { state ->
        state.copy(layouts = state.layouts.map { if (it.id == id) change(it) else it })
    }

    suspend fun reset(id: String) = update(id) { it.copy(order = WidgetDefaults.ORDER, hidden = emptyList(), sizes = WidgetDefaults.SIZES) }

    /** Adds a layout copied from [copyFrom] (or the defaults) and makes it active. */
    suspend fun add(name: String, copyFrom: String?) = mutate { state ->
        val base = state.layouts.firstOrNull { it.id == copyFrom }
        val layout = base?.copy(id = "l-" + java.util.UUID.randomUUID().toString().take(6), name = name) ?: WidgetDefaults.layout(name)
        LayoutState(state.layouts + layout, layout.id)
    }

    suspend fun remove(id: String) = mutate { state ->
        if (state.layouts.size <= 1) return@mutate state
        val left = state.layouts.filter { it.id != id }
        LayoutState(left, if (state.activeId == id) left.first().id else state.activeId)
    }

    private suspend fun mutate(change: (LayoutState) -> LayoutState) {
        context.dashboardLayoutDataStore.edit { prefs ->
            val layouts = decode(prefs[LAYOUTS]).ifEmpty { listOf(WidgetDefaults.layout("Default", "default")) }
            val next = change(LayoutState(layouts, prefs[ACTIVE] ?: layouts.first().id))
            prefs[LAYOUTS] = encode(next.layouts)
            prefs[ACTIVE] = next.activeId
        }
    }

    private fun encode(layouts: List<Layout>): String = JSONArray().apply {
        layouts.forEach { l ->
            put(
                JSONObject()
                    .put("id", l.id).put("name", l.name)
                    .put("order", JSONArray(l.order)).put("hidden", JSONArray(l.hidden))
                    .put("sizes", JSONObject().apply { l.sizes.forEach { (k, v) -> put(k, v.name) } }),
            )
        }
    }.toString()

    private fun decode(raw: String?): List<Layout> {
        if (raw == null) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                fun strings(key: String) = o.getJSONArray(key).let { a -> (0 until a.length()).map(a::getString) }
                val sizes = o.getJSONObject("sizes")
                Layout(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    order = strings("order"),
                    hidden = strings("hidden"),
                    sizes = sizes.keys().asSequence().mapNotNull { k ->
                        runCatching { WidgetSize.valueOf(sizes.getString(k)) }.getOrNull()?.let { k to it }
                    }.toMap(),
                )
            }
        }.getOrDefault(emptyList())
    }
}
