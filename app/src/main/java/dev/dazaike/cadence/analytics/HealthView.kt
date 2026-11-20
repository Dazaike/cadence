package dev.dazaike.cadence.analytics

import java.time.LocalDate
import java.time.ZoneId

/**
 * Port of the web dashboard's `types.ts` + `lib/view.ts`: everything the pages read, indexed by
 * local calendar day. Built on-device from the active health data source. Missing is missing:
 * a day without a recording has no entry, and nothing is zero-filled.
 */
data class SleepStages(val rem: Double, val deep: Double, val light: Double, val awake: Double)

/**
 * One measurement in canonical units (km, kg, cm, L, kcal, h, bpm, ...).
 * - Daily-sum metrics carry [d] (local calendar day) assigned at read time.
 * - Sleep samples: [t] = wake time (end of session), [v] = hours asleep.
 */
data class Sample(val t: Long, val v: Double, val d: LocalDate? = null, val stages: SleepStages? = null)

data class HeartDay(val avg: Double, val min: Double, val max: Double, val n: Int)

data class SleepNight(
    val d: LocalDate,
    val hours: Double,
    val wake: Long,
    val bed: Long,
    val stages: SleepStages?,
    /** minutes since 18:00 of bedtime, so evenings and after-midnight bedtimes are contiguous */
    val bedM: Int,
    val wakeM: Int,
)

data class Resting(val v: Double, val derived: Boolean)

class HealthView(
    val zone: ZoneId,
    /** Every sample by metric, oldest first. */
    val series: Map<MetricKey, List<Sample>>,
    /** One value per local day: totals for sum metrics, means for avg metrics. */
    val daily: Map<MetricKey, Map<LocalDate, Double>>,
    val heartDay: Map<LocalDate, HeartDay>,
    /** Hourly heart-rate averages per day, oldest first. */
    val hrByDay: Map<LocalDate, List<Sample>>,
    val sleep: Map<LocalDate, SleepNight>,
    val weight: List<Sample>,
    val height: List<Sample>,
    val first: LocalDate?,
    val last: LocalDate?,
    /** Last day that is fully recorded (excludes the in-progress day). */
    val completeThrough: LocalDate?,
    /** The local day data was read; its totals may still be growing. */
    val asOf: LocalDate?,
) {
    fun has(m: MetricKey): Boolean = series[m]?.isNotEmpty() == true

    fun latest(m: MetricKey): Sample? = series[m]?.lastOrNull()

    val isEmpty: Boolean get() = first == null

    /** Measured resting HR for the day, else the lowest hourly average as a proxy (flagged derived). */
    fun resting(d: LocalDate): Resting? {
        daily[MetricKey.RESTING_HR]?.get(d)?.let { return Resting(it, false) }
        val h = heartDay[d]
        return if (h != null && h.n >= 6) Resting(h.min, true) else null
    }

    companion object {
        val EMPTY = buildView(emptyMap(), ZoneId.systemDefault(), null)
    }
}

private fun ev(m: Int) = (m - 18 * 60 + 1440) % 1440

fun buildView(series: Map<MetricKey, List<Sample>>, zone: ZoneId, asOf: LocalDate?): HealthView {
    val daily = LinkedHashMap<MetricKey, Map<LocalDate, Double>>()
    for ((key, rows) in series) {
        when (key.agg) {
            Agg.SUM -> {
                val best = LinkedHashMap<LocalDate, Double>()
                for (s in rows) best.putIfAbsent(s.d ?: dayKey(s.t, zone), s.v)
                daily[key] = best
            }
            Agg.AVG -> {
                val acc = LinkedHashMap<LocalDate, DoubleArray>()
                for (s in rows) {
                    val a = acc.getOrPut(dayKey(s.t, zone)) { DoubleArray(2) }
                    a[0] += s.v
                    a[1] += 1.0
                }
                daily[key] = acc.mapValues { it.value[0] / it.value[1] }
            }
            Agg.LAST, Agg.SLEEP -> Unit
        }
    }

    val hrByDay = LinkedHashMap<LocalDate, MutableList<Sample>>()
    for (s in series[MetricKey.HEART_RATE].orEmpty()) hrByDay.getOrPut(dayKey(s.t, zone)) { ArrayList() }.add(s)
    val heartDay = hrByDay.mapValues { (_, list) ->
        HeartDay(list.sumOf { it.v } / list.size, list.minOf { it.v }, list.maxOf { it.v }, list.size)
    }

    // Sleep: sessions are keyed by the local day they ended on; several sessions in one day are combined.
    val sleep = LinkedHashMap<LocalDate, SleepNight>()
    for (s in series[MetricKey.SLEEP].orEmpty()) {
        val d = dayKey(s.t, zone)
        val inBed = s.v + (s.stages?.awake ?: 0.0)
        val bed = s.t - (inBed * 3_600_000).toLong()
        val cur = sleep[d]
        sleep[d] = if (cur == null) {
            SleepNight(d, s.v, s.t, bed, s.stages, ev(minuteOfDay(bed, zone)), ev(minuteOfDay(s.t, zone)))
        } else {
            val bedAll = minOf(cur.bed, bed)
            val wakeAll = maxOf(cur.wake, s.t)
            val stages = when {
                cur.stages != null && s.stages != null -> SleepStages(
                    cur.stages.rem + s.stages.rem, cur.stages.deep + s.stages.deep,
                    cur.stages.light + s.stages.light, cur.stages.awake + s.stages.awake,
                )
                else -> cur.stages ?: s.stages
            }
            SleepNight(d, cur.hours + s.v, wakeAll, bedAll, stages, ev(minuteOfDay(bedAll, zone)), ev(minuteOfDay(wakeAll, zone)))
        }
    }

    val days = sortedSetOfDays(daily.values.map { it.keys } + listOf(heartDay.keys, sleep.keys))
    val first = days.firstOrNull()
    val last = days.lastOrNull()
    val completeThrough = if (last != null && asOf != null && last >= asOf) asOf.minusDays(1) else last

    return HealthView(
        zone = zone,
        series = series,
        daily = daily,
        heartDay = heartDay,
        hrByDay = hrByDay,
        sleep = sleep,
        weight = series[MetricKey.WEIGHT].orEmpty(),
        height = series[MetricKey.HEIGHT].orEmpty(),
        first = first,
        last = last,
        completeThrough = completeThrough,
        asOf = asOf,
    )
}

private fun sortedSetOfDays(sets: List<Set<LocalDate>>): List<LocalDate> {
    val all = java.util.TreeSet<LocalDate>()
    for (s in sets) all.addAll(s)
    return all.toList()
}
