package dev.dazaike.cadence.analytics

import dev.dazaike.cadence.data.UnitSystem
import java.util.Locale

/**
 * Port of the web dashboard's `lib/metrics.ts`: the metric catalog the analytics, charts and
 * pages are written against. Canonical units follow the app's data layer (metric/SI): km, kg, cm
 * and litres; the web's canonical units are imperial, so only the conversion direction differs.
 */
enum class Category { ACTIVITY, HEART, SLEEP, BODY, RECOVERY }

/** sum: one total per day. avg: mean of samples per day. last: latest reading. sleep: sessions. */
enum class Agg { SUM, AVG, LAST, SLEEP }

enum class GoalKey(val label: String) {
    STEPS("Steps"),
    ACTIVE_ENERGY("Active energy (kcal)"),
    EXERCISE("Exercise (min)"),
    DISTANCE("Distance"),
    FLOORS("Floors"),
    HYDRATION("Hydration"),
    SLEEP("Sleep (h)"),
}

enum class MetricKey(
    /** The key the web dashboard (and the cloud wire format) use for this metric. */
    val id: String,
    val label: String,
    /** Canonical unit of values held in a [HealthView]. */
    val unit: String,
    val agg: Agg,
    val cat: Category,
    val decimals: Int,
    val goalKey: GoalKey? = null,
) {
    STEPS("steps", "Steps", "steps", Agg.SUM, Category.ACTIVITY, 0, GoalKey.STEPS),
    DISTANCE("distance", "Distance", "km", Agg.SUM, Category.ACTIVITY, 2, GoalKey.DISTANCE),
    ACTIVE_ENERGY("activeEnergy", "Active energy", "kcal", Agg.SUM, Category.ACTIVITY, 0, GoalKey.ACTIVE_ENERGY),
    TOTAL_ENERGY("totalEnergy", "Total energy", "kcal", Agg.SUM, Category.ACTIVITY, 0),
    FLOORS("floors", "Floors climbed", "floors", Agg.SUM, Category.ACTIVITY, 0, GoalKey.FLOORS),
    EXERCISE("exercise", "Exercise", "min", Agg.SUM, Category.ACTIVITY, 0, GoalKey.EXERCISE),
    HYDRATION("hydration", "Hydration", "L", Agg.SUM, Category.BODY, 2, GoalKey.HYDRATION),
    HEART_RATE("heartRate", "Heart rate", "bpm", Agg.AVG, Category.HEART, 0),
    RESTING_HR("restingHr", "Resting heart rate", "bpm", Agg.AVG, Category.HEART, 0),
    HRV("hrv", "HRV", "ms", Agg.AVG, Category.RECOVERY, 0),
    SPO2("spo2", "Blood oxygen", "%", Agg.AVG, Category.RECOVERY, 0),
    SLEEP("sleep", "Sleep", "h", Agg.SLEEP, Category.SLEEP, 1, GoalKey.SLEEP),
    WEIGHT("weight", "Weight", "kg", Agg.LAST, Category.BODY, 1),
    HEIGHT("height", "Height", "cm", Agg.LAST, Category.BODY, 0),
    GLUCOSE("glucose", "Blood glucose", "mg/dL", Agg.AVG, Category.BODY, 0);

    companion object {
        fun fromId(id: String): MetricKey? = entries.firstOrNull { it.id == id }
    }
}

/** Metrics that sum to one value per local day (web `DAILY_SUM`). */
val DAILY_SUM = MetricKey.entries.filter { it.agg == Agg.SUM }

/** Goal values in canonical units (km, L, h). */
typealias Goals = Map<GoalKey, Double>

val DEFAULT_GOALS: Goals = mapOf(
    GoalKey.STEPS to 10_000.0,
    GoalKey.ACTIVE_ENERGY to 500.0,
    GoalKey.EXERCISE to 30.0,
    GoalKey.DISTANCE to 5.0,
    GoalKey.FLOORS to 10.0,
    GoalKey.HYDRATION to 2.0,
    GoalKey.SLEEP to 8.0,
)

const val DEFAULT_MAX_HR = 190

/* ------------------------------------------------------------------ */
/* Display conversion (user-selectable unit system)                    */
/* ------------------------------------------------------------------ */

private class Display(val f: Double, val unit: String, val decimals: Int)

private val IMPERIAL = mapOf(
    MetricKey.DISTANCE to Display(0.621371, "mi", 2),
    MetricKey.WEIGHT to Display(2.20462, "lb", 1),
    MetricKey.HEIGHT to Display(0.393701, "in", 1),
    MetricKey.HYDRATION to Display(33.814, "fl oz", 1),
)

const val KG_TO_LB = 2.20462
const val CM_TO_IN = 0.393701

/** Canonical value to the displayed value in [sys]. */
fun conv(metric: MetricKey, v: Double, sys: UnitSystem): Double =
    if (sys == UnitSystem.IMPERIAL) v * (IMPERIAL[metric]?.f ?: 1.0) else v

/** Displayed value back to canonical (goal entry). */
fun unconv(metric: MetricKey, v: Double, sys: UnitSystem): Double =
    if (sys == UnitSystem.IMPERIAL) v / (IMPERIAL[metric]?.f ?: 1.0) else v

fun unitOf(metric: MetricKey, sys: UnitSystem): String =
    if (sys == UnitSystem.IMPERIAL) IMPERIAL[metric]?.unit ?: metric.unit else metric.unit

fun decimalsOf(metric: MetricKey, sys: UnitSystem): Int =
    if (sys == UnitSystem.IMPERIAL) IMPERIAL[metric]?.decimals ?: metric.decimals else metric.decimals

fun fmtNum(v: Double, decimals: Int = 0): String = String.format(Locale.US, "%,.${decimals}f", v)

fun fmtMetric(metric: MetricKey, v: Double?, sys: UnitSystem, withUnit: Boolean = true): String {
    if (v == null || v.isNaN()) return "—"
    val s = fmtNum(conv(metric, v, sys), decimalsOf(metric, sys))
    return if (withUnit) "$s ${unitOf(metric, sys)}" else s
}

/** Display conversion bound to a unit system, the on-device twin of the web `useFmt()` hook. */
class Fmt(val units: UnitSystem) {
    fun f(m: MetricKey, v: Double?, withUnit: Boolean = true) = fmtMetric(m, v, units, withUnit)
    fun c(m: MetricKey, v: Double) = conv(m, v, units)
    fun u(m: MetricKey) = unitOf(m, units)
    fun decimals(m: MetricKey) = decimalsOf(m, units)
}
