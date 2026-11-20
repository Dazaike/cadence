package dev.dazaike.cadence.data

import dev.dazaike.cadence.ui.theme.CatColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stairs
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** How a metric is summarised and charted. */
enum class MetricKind {
    /** Sums to a daily total (steps, distance, calories…). Charted as 7-day bars. */
    DAILY_TOTAL,

    /** A point sample where the most recent value matters (weight, SpO2…). Charted as a trend line. */
    LATEST,
}

/**
 * Catalog of every Health Connect metric the app surfaces. Order here is the dashboard order.
 */
enum class Metric(
    val key: String,
    val title: String,
    val metricUnit: String,
    val imperialUnit: String,
    val kind: MetricKind,
    val accent: Color,
    val icon: ImageVector,
) {
    STEPS("steps", "Steps", "steps", "steps", MetricKind.DAILY_TOTAL, CatColor.activity, Icons.Rounded.DirectionsWalk),
    DISTANCE("distance", "Distance", "km", "mi", MetricKind.DAILY_TOTAL, CatColor.activity, Icons.Rounded.Route),
    ACTIVE_CALORIES("active_calories", "Active energy", "kcal", "kcal", MetricKind.DAILY_TOTAL, CatColor.activity, Icons.Rounded.LocalFireDepartment),
    TOTAL_CALORIES("total_calories", "Total energy", "kcal", "kcal", MetricKind.DAILY_TOTAL, CatColor.activity, Icons.Rounded.Bolt),
    FLOORS("floors", "Floors climbed", "floors", "floors", MetricKind.DAILY_TOTAL, CatColor.activity, Icons.Rounded.Stairs),
    EXERCISE("exercise", "Exercise", "min", "min", MetricKind.DAILY_TOTAL, CatColor.activity, Icons.Rounded.DirectionsRun),
    HYDRATION("hydration", "Hydration", "L", "fl oz", MetricKind.DAILY_TOTAL, CatColor.body, Icons.Rounded.WaterDrop),
    HEART_RATE("heart_rate", "Heart rate", "bpm", "bpm", MetricKind.LATEST, CatColor.heart, Icons.Rounded.MonitorHeart),
    RESTING_HR("resting_heart_rate", "Resting heart rate", "bpm", "bpm", MetricKind.LATEST, CatColor.heart, Icons.Rounded.Favorite),
    HRV("hrv", "Heart rate variability", "ms", "ms", MetricKind.LATEST, CatColor.recovery, Icons.Rounded.Timeline),
    SLEEP("sleep", "Sleep", "h", "h", MetricKind.LATEST, CatColor.sleep, Icons.Rounded.Bedtime),
    OXYGEN("oxygen", "Blood oxygen", "%", "%", MetricKind.LATEST, CatColor.recovery, Icons.Rounded.Air),
    RESPIRATORY("respiratory", "Respiratory rate", "br/min", "br/min", MetricKind.LATEST, CatColor.recovery, Icons.Rounded.Air),
    VO2MAX("vo2max", "VO₂ max", "mL/kg/min", "mL/kg/min", MetricKind.LATEST, CatColor.recovery, Icons.Rounded.Speed),
    WEIGHT("weight", "Weight", "kg", "lbs", MetricKind.LATEST, CatColor.body, Icons.Rounded.MonitorWeight),
    HEIGHT("height", "Height", "cm", "in", MetricKind.LATEST, CatColor.body, Icons.Rounded.Height),
    BLOOD_PRESSURE("blood_pressure", "Blood pressure", "mmHg", "mmHg", MetricKind.LATEST, CatColor.heart, Icons.Rounded.Bloodtype),
    BLOOD_GLUCOSE("blood_glucose", "Blood glucose", "mg/dL", "mg/dL", MetricKind.LATEST, CatColor.body, Icons.Rounded.Bloodtype);

    val unit: String get() = imperialUnit

    fun unit(system: UnitSystem): String = when (system) {
        UnitSystem.IMPERIAL -> imperialUnit
        UnitSystem.METRIC -> metricUnit
    }

    companion object {
        fun fromKey(key: String): Metric? = entries.firstOrNull { it.key == key }
    }
}
