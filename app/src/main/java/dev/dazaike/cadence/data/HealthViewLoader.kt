package dev.dazaike.cadence.data

import dev.dazaike.cadence.analytics.HealthView
import dev.dazaike.cadence.analytics.MetricKey
import dev.dazaike.cadence.analytics.Sample
import dev.dazaike.cadence.analytics.SleepStages
import dev.dazaike.cadence.analytics.buildView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** What [HealthViewLoader.load] produced: the view plus how much of it could be read. */
data class LoadedView(
    val view: HealthView,
    val days: Int,
    val loadedAt: Long,
    /** Titles of granted metrics that failed to read; the rest of the view is still valid. */
    val unreadable: List<String>,
    val grantedCount: Int,
)

/**
 * Builds the on-device [HealthView] the pages render from the active [HealthDataSource]. Reads are
 * the same ones cloud sync uses (metric units, hourly heart rate, sleep sessions with stages), so
 * the phone and the web dashboard describe the same data. A daily total of zero is "no data",
 * never a reading.
 */
object HealthViewLoader {

    private const val FULL_DAYS = 365
    private const val RECENT_DAYS = 30
    private const val PARALLEL_READS = 4

    suspend fun load(source: HealthDataSource): LoadedView = coroutineScope {
        val zone = ZoneId.systemDefault()
        val granted = runCatching { source.grantedPermissions() }.getOrDefault(emptySet())
        // Without Health Connect's history permission only the last 30 days are readable.
        val days = if (source is HealthConnectManager && HealthConnectManager.PERMISSION_READ_HISTORY !in granted) RECENT_DAYS else FULL_DAYS
        val gate = Semaphore(PARALLEL_READS)
        val metrics = Metric.entries.filter { source.permissionFor(it) in granted && keyFor(it) != null }
        val unreadable = ArrayList<String>()

        val reads = metrics.map { metric ->
            async {
                gate.withPermit {
                    try {
                        metric to read(source, metric, days, zone)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        metric to null
                    }
                }
            }
        }.awaitAll()

        val series = LinkedHashMap<MetricKey, List<Sample>>()
        for ((metric, rows) in reads) {
            if (rows == null) unreadable += metric.title else if (rows.isNotEmpty()) series[keyFor(metric)!!] = rows
        }
        LoadedView(
            view = buildView(series, zone, LocalDate.now(zone)),
            days = days,
            loadedAt = System.currentTimeMillis(),
            unreadable = unreadable,
            grantedCount = metrics.size,
        )
    }

    fun keyFor(metric: Metric): MetricKey? = when (metric) {
        Metric.STEPS -> MetricKey.STEPS
        Metric.DISTANCE -> MetricKey.DISTANCE
        Metric.ACTIVE_CALORIES -> MetricKey.ACTIVE_ENERGY
        Metric.TOTAL_CALORIES -> MetricKey.TOTAL_ENERGY
        Metric.FLOORS -> MetricKey.FLOORS
        Metric.EXERCISE -> MetricKey.EXERCISE
        Metric.HYDRATION -> MetricKey.HYDRATION
        Metric.HEART_RATE -> MetricKey.HEART_RATE
        Metric.RESTING_HR -> MetricKey.RESTING_HR
        Metric.HRV -> MetricKey.HRV
        Metric.SLEEP -> MetricKey.SLEEP
        Metric.OXYGEN -> MetricKey.SPO2
        Metric.WEIGHT -> MetricKey.WEIGHT
        Metric.HEIGHT -> MetricKey.HEIGHT
        Metric.BLOOD_GLUCOSE -> MetricKey.GLUCOSE
        // Respiratory rate, VO2 max and blood pressure have no counterpart in the web dashboard's pages.
        Metric.RESPIRATORY, Metric.VO2MAX, Metric.BLOOD_PRESSURE -> null
    }

    private suspend fun read(source: HealthDataSource, metric: Metric, days: Int, zone: ZoneId): List<Sample> {
        fun localDay(time: Instant): LocalDate = time.atZone(zone).toLocalDate()
        fun midnight(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()
        fun sample(p: SeriesPoint) = Sample(p.time.toEpochMilli(), p.value.toDouble())

        return when (metric) {
            Metric.STEPS, Metric.DISTANCE, Metric.ACTIVE_CALORIES, Metric.TOTAL_CALORIES,
            Metric.FLOORS, Metric.EXERCISE, Metric.HYDRATION ->
                // exportRows returns a zero for every empty day; a zero total is "no data", not a reading.
                source.exportRows(metric, days, UnitSystem.METRIC)
                    .filter { it.value > 0f }
                    .map { val d = localDay(it.time); Sample(midnight(d), it.value.toDouble(), d) }
            Metric.HEART_RATE -> source.readHourlyHeartRate(days).map(::sample)
            Metric.SLEEP -> source.readSleepSessions(days).map {
                Sample(
                    t = it.wake.toEpochMilli(),
                    v = it.asleepHours.toDouble(),
                    stages = it.stages?.let { s -> SleepStages(s.rem.toDouble(), s.deep.toDouble(), s.light.toDouble(), s.awake.toDouble()) },
                )
            }
            else -> source.exportRows(metric, days, UnitSystem.METRIC).map(::sample)
        }.sortedBy { it.t }
    }
}
