package dev.easonhuang.heartwood.data

import java.time.Instant

/** One dashboard tile: the headline value for a metric plus a sparkline of recent points. */
data class MetricSummary(
    val metric: Metric,
    val value: String,          // formatted headline, e.g. "8,432" or "-"
    val caption: String?,       // secondary line, e.g. "avg 72, 54-138 bpm"
    val hasData: Boolean,
    val granted: Boolean,
    val spark: List<Float> = emptyList(),
    val unit: String = metric.unit,
)

/** A single charted data point. */
data class SeriesPoint(
    val time: Instant,
    val value: Float,
    val label: String,          // x-axis label, e.g. "Mon" or "14:30"
)

/** Full detail payload for one metric. */
data class MetricDetail(
    val metric: Metric,
    val headline: String,
    val caption: String?,
    val points: List<SeriesPoint>,
    val stats: List<Pair<String, String>> = emptyList(),  // label -> value rows
    val recent: List<RecordRow> = emptyList(),
    val unit: String = metric.unit,
)

/** A row in the "recent records" list on a detail screen. */
data class RecordRow(
    val primary: String,
    val secondary: String,
)

/** One blood-pressure reading; the display-oriented APIs only expose the systolic half. */
data class BloodPressureReading(
    val time: Instant,
    val systolic: Float,
    val diastolic: Float,
)

/** Hours spent in each sleep stage for one session. */
data class SleepStageHours(
    val rem: Float,
    val deep: Float,
    val light: Float,
    val awake: Float,
)

/**
 * One sleep session: [wake] is when it ended, [asleepHours] excludes time awake when the source
 * reports stages, and [stages] is null when the source gave no REM/deep/light breakdown.
 */
data class SleepSessionData(
    val wake: Instant,
    val asleepHours: Float,
    val stages: SleepStageHours?,
)
