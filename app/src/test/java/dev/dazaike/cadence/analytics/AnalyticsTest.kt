package dev.dazaike.cadence.analytics

import dev.dazaike.cadence.data.UnitSystem
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the on-device port of the web dashboard's analytics (pure logic, no Android). */
class AnalyticsTest {

    private val utc = ZoneOffset.UTC
    private val d0 = LocalDate.of(2026, 3, 2) // a Monday

    private fun ms(day: LocalDate, hour: Int = 12, minute: Int = 0) = day.atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun sumSeries(values: List<Double?>, start: LocalDate = d0): List<Sample> =
        values.mapIndexedNotNull { i, v -> v?.let { Sample(ms(start.plusDays(i.toLong())), it, start.plusDays(i.toLong())) } }

    private fun view(
        series: Map<MetricKey, List<Sample>>,
        asOf: LocalDate? = null,
    ) = buildView(series, utc, asOf)

    private fun nights(n: Int, bedMinute: (Int) -> Int, wakeMinute: (Int) -> Int, start: LocalDate = d0): List<Sample> =
        (0 until n).map { i ->
            // Session ends at wakeMinute of day `start+i`; bedtime is derived as wake minus duration.
            val wake = ms(start.plusDays(i.toLong()), 0, 0) + wakeMinute(i) * 60_000L
            val hours = (wakeMinute(i) + 1440 - bedMinute(i)) % 1440 / 60.0
            Sample(wake, hours)
        }

    /* ------------------------------ basic stats ------------------------------ */

    @Test
    fun meanOfEmptyIsNaN() {
        assertTrue(mean(emptyList()).isNaN())
        assertEquals(2.0, mean(listOf(1.0, 2.0, 3.0)), 1e-9)
    }

    @Test
    fun stdevUsesSampleVariance() {
        assertTrue(stdev(listOf(5.0)).isNaN())
        assertEquals(1.0, stdev(listOf(1.0, 2.0, 3.0)), 1e-9)
    }

    @Test
    fun pearsonPerfectAndDegenerate() {
        val xs = (1..10).map { it.toDouble() }
        assertEquals(1.0, pearson(xs, xs.map { it * 2 + 1 }), 1e-9)
        assertEquals(-1.0, pearson(xs, xs.map { -it }), 1e-9)
        assertTrue(pearson(xs, List(10) { 3.0 }).isNaN())
        assertTrue(pearson(listOf(1.0, 2.0), listOf(1.0, 2.0)).isNaN())
    }

    @Test
    fun pctChangeIsNaNWithoutBaseline() {
        assertEquals(50.0, pctChange(150.0, 100.0), 1e-9)
        assertTrue(pctChange(5.0, 0.0).isNaN())
    }

    @Test
    fun strengthBands() {
        assertEquals("negligible", strength(0.1))
        assertEquals("weak negative", strength(-0.3))
        assertEquals("moderate positive", strength(0.5))
        assertEquals("strong negative", strength(-0.9))
    }

    /* ------------------------------ dates ------------------------------ */

    @Test
    fun weekStartsOnMonday() {
        assertEquals(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 8).startOfWeek()) // Sunday
        assertEquals(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2).startOfWeek())
        assertEquals(0, LocalDate.of(2026, 3, 8).weekdayIndex()) // Sunday = 0
    }

    @Test
    fun clockAndDurationFormatting() {
        assertEquals("12:05 AM", fmtClock(5))
        assertEquals("1:30 PM", fmtClock(13 * 60 + 30))
        assertEquals("12:00 AM", fmtClock(1440)) // wraps
        assertEquals("7h 05m", fmtDuration(7 + 5 / 60.0))
    }

    @Test
    fun dayRangeIsInclusive() {
        assertEquals(3, dayRange(d0, d0.plusDays(2)).size)
        assertTrue(dayRange(d0, d0.minusDays(1)).isEmpty())
        assertEquals(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 3, 17).addMonths(1))
    }

    /* ------------------------------ view building ------------------------------ */

    @Test
    fun dailyAveragesAndMissingDays() {
        val hr = listOf(
            Sample(ms(d0, 8), 60.0), Sample(ms(d0, 9), 80.0),
            Sample(ms(d0.plusDays(2), 8), 70.0),
        )
        val v = view(mapOf(MetricKey.HEART_RATE to hr))
        assertEquals(70.0, v.daily.getValue(MetricKey.HEART_RATE).getValue(d0), 1e-9)
        assertNull(v.daily.getValue(MetricKey.HEART_RATE)[d0.plusDays(1)]) // missing is missing
        assertEquals(60.0, v.heartDay.getValue(d0).min, 1e-9)
        assertEquals(80.0, v.heartDay.getValue(d0).max, 1e-9)
        assertEquals(d0, v.first)
        assertEquals(d0.plusDays(2), v.last)
    }

    @Test
    fun inProgressDayIsExcludedFromCompleteThrough() {
        val v = view(mapOf(MetricKey.STEPS to sumSeries(listOf(1000.0, 2000.0, 500.0))), asOf = d0.plusDays(2))
        assertEquals(d0.plusDays(2), v.last)
        assertEquals(d0.plusDays(1), v.completeThrough)
        val settled = view(mapOf(MetricKey.STEPS to sumSeries(listOf(1000.0, 2000.0))), asOf = d0.plusDays(5))
        assertEquals(d0.plusDays(1), settled.completeThrough)
    }

    @Test
    fun restingHrFallsBackToLowestHourlyOnlyWithEnoughReadings() {
        val few = (0 until 5).map { Sample(ms(d0, it), 60.0 + it) }
        val enough = (0 until 6).map { Sample(ms(d0, it), 60.0 + it) }
        assertNull(view(mapOf(MetricKey.HEART_RATE to few)).resting(d0))
        val r = view(mapOf(MetricKey.HEART_RATE to enough)).resting(d0)!!
        assertTrue(r.derived)
        assertEquals(60.0, r.v, 1e-9)
        val measured = view(mapOf(MetricKey.HEART_RATE to enough, MetricKey.RESTING_HR to listOf(Sample(ms(d0), 52.0)))).resting(d0)!!
        assertFalse(measured.derived)
        assertEquals(52.0, measured.v, 1e-9)
    }

    @Test
    fun sleepNightsAreDatedByWakeDayAndMerged() {
        val wake1 = Sample(ms(d0, 7, 0), 7.0)
        val nap = Sample(ms(d0, 15, 0), 1.0)
        val v = view(mapOf(MetricKey.SLEEP to listOf(wake1, nap)))
        val night = v.sleep.getValue(d0)
        assertEquals(8.0, night.hours, 1e-9)
        assertEquals(ms(d0, 15, 0), night.wake)
        // bedtime = wake minus in-bed duration: 07:00 - 7 h = 00:00 for the first session
        assertEquals(ms(d0, 0, 0), night.bed)
        assertEquals(360, night.bedM) // minutes since 18:00 -> 00:00 is 360
    }

    @Test
    fun awakeTimeCountsTowardBedtime() {
        val stages = SleepStages(rem = 1.0, deep = 1.0, light = 5.0, awake = 1.0)
        val night = view(mapOf(MetricKey.SLEEP to listOf(Sample(ms(d0, 7), 7.0, stages = stages)))).sleep.getValue(d0)
        assertEquals(ms(d0, 7) - 8 * 3_600_000L, night.bed)
    }

    /* ------------------------------ ranges and series ------------------------------ */

    @Test
    fun rangePresetsResolveAgainstLastDay() {
        val v = view(mapOf(MetricKey.STEPS to sumSeries(List(100) { 1000.0 })))
        val last = d0.plusDays(99)
        assertEquals(DateRange(last.minusDays(6), last), resolveRange(v, RangePref(RangePreset.D7)))
        assertEquals(DateRange(last.minusDays(29), last), resolveRange(v, RangePref(RangePreset.D30)))
        assertEquals(DateRange(d0, last), resolveRange(v, RangePref(RangePreset.Y1))) // clamped to first day
        assertEquals(DateRange(d0, last), resolveRange(v, RangePref(RangePreset.ALL)))
        val custom = resolveRange(v, RangePref(RangePreset.CUSTOM, d0.plusDays(10), d0.plusDays(20)))
        assertEquals(DateRange(d0.plusDays(10), d0.plusDays(20)), custom)
    }

    @Test
    fun bucketizeAveragesRecordedDaysOnly() {
        val v = view(mapOf(MetricKey.STEPS to sumSeries(listOf(1000.0, null, 3000.0, 4000.0, null, null, null, 10.0))))
        val pts = dailySeries(v, MetricKey.STEPS, d0, d0.plusDays(7))
        val weeks = bucketize(pts, Gran.WEEK)
        assertEquals(2, weeks.size)
        assertEquals(8000.0 / 3, weeks[0].v!!, 1e-9) // three recorded days, the gaps are not zeros
        assertEquals(3, weeks[0].n)
        assertEquals(10.0, weeks[1].v!!, 1e-9)
        assertEquals("Wk Mar 2", weeks[0].label)
    }

    @Test
    fun movingAverageNeedsHalfTheWindow() {
        val pts = listOf(10.0, null, null, null, 20.0, null, null, 40.0, 50.0, 60.0, 70.0).mapIndexed { i, v -> Point(d0.plusDays(i.toLong()), v) }
        val ma = movingAverage(pts, 7)
        assertNull(ma[0]) // 1 value is below ceil(7/2)=4
        assertNotNull(ma[10])
        assertEquals((20.0 + 40 + 50 + 60 + 70) / 5, ma[10]!!, 1e-9)
    }

    @Test
    fun periodCompareUsesWindowsOfEqualLength() {
        val v = view(mapOf(MetricKey.STEPS to sumSeries(List(14) { if (it < 7) 5000.0 else 10_000.0 })))
        val c = periodCompare(v, MetricKey.STEPS, d0.plusDays(13), 7)
        assertEquals(10_000.0, c.cur, 1e-9)
        assertEquals(5000.0, c.prev, 1e-9)
        assertEquals(100.0, c.change, 1e-9)
        assertEquals(7, c.nCur)
    }

    @Test
    fun weekdayAveragesGroupBySundayFirstIndex() {
        val v = view(mapOf(MetricKey.STEPS to sumSeries(List(14) { (it % 7 + 1) * 1000.0 })))
        val w = weekdayAverages(v, MetricKey.STEPS, d0, d0.plusDays(13))
        assertEquals("Sun", w[0].name)
        assertEquals(7000.0, w[0].v!!, 1e-9) // d0 + 6 is the Sunday, value (6+1)*1000
        assertEquals(1000.0, w[1].v!!, 1e-9) // Monday
        assertEquals(2, w[1].n)
    }

    /* ------------------------------ sleep, goals, records ------------------------------ */

    @Test
    fun sleepConsistencyIsPerfectForIdenticalNightsAndNullBelowFive() {
        val steady = view(mapOf(MetricKey.SLEEP to nights(10, { 23 * 60 }, { 7 * 60 })))
        val c = sleepConsistency(steady, steady.last!!)!!
        assertEquals(100, c.score)
        assertEquals(10, c.n)
        val short = view(mapOf(MetricKey.SLEEP to nights(4, { 23 * 60 }, { 7 * 60 })))
        assertNull(sleepConsistency(short, short.last!!))
    }

    @Test
    fun sleepConsistencyDropsWithVariableSchedule() {
        val erratic = view(mapOf(MetricKey.SLEEP to nights(10, { if (it % 2 == 0) 21 * 60 else 26 * 60 % 1440 }, { if (it % 2 == 0) 5 * 60 else 10 * 60 })))
        val c = sleepConsistency(erratic, erratic.last!!)!!
        assertTrue(c.score < 70)
        assertTrue(c.bedSd > 60)
    }

    @Test
    fun goalHitRateNeedsFiveDays() {
        val v = view(mapOf(MetricKey.STEPS to sumSeries(listOf(12_000.0, 9000.0, 11_000.0, 10_000.0, 3000.0, 15_000.0))))
        val hit = goalHitRate(v, MetricKey.STEPS, 10_000.0, v.last!!, 30)!!
        assertEquals(4.0 / 6, hit.rate, 1e-9)
        assertEquals(6, hit.n)
        val few = view(mapOf(MetricKey.STEPS to sumSeries(listOf(12_000.0, 9000.0, 11_000.0, 10_000.0))))
        assertNull(goalHitRate(few, MetricKey.STEPS, 10_000.0, few.last!!, 30))
    }

    @Test
    fun longestStreakAndRecord() {
        val v = view(mapOf(MetricKey.STEPS to sumSeries(listOf(10_000.0, 11_000.0, 2000.0, 12_000.0, 13_000.0, 14_000.0, 1000.0))))
        val s = longestStreak(v, MetricKey.STEPS, 10_000.0)!!
        assertEquals(3, s.days)
        assertEquals(d0.plusDays(5), s.end)
        val max = maxDayOf(v, MetricKey.STEPS)!!
        assertEquals(14_000.0, max.v, 1e-9)
        assertEquals(d0.plusDays(5), max.d)
        assertNull(longestStreak(v, MetricKey.STEPS, 99_999.0))
    }

    /* ------------------------------ correlations ------------------------------ */

    @Test
    fun correlationRequiresFourteenOverlappingDays() {
        val short = view(mapOf(MetricKey.STEPS to sumSeries(List(10) { it * 1000.0 }), MetricKey.EXERCISE to sumSeries(List(10) { it * 5.0 })))
        assertTrue(correlations(short).isEmpty())
    }

    @Test
    fun scatterAndCorrelationOnLinearData() {
        val v = view(
            mapOf(
                MetricKey.STEPS to sumSeries(List(20) { 4000.0 + it * 500 }),
                MetricKey.HEART_RATE to (0 until 20).flatMap { i -> (0 until 6).map { h -> Sample(ms(d0.plusDays(i.toLong()), h), 60.0 + i + h) } },
            ),
        )
        val c = correlations(v).firstOrNull { it.id == "steps-hr" }
        assertNotNull(c)
        assertEquals(1.0, c!!.r, 1e-6)
        assertEquals("strong positive", c.strength)
        val pts = scatterData(v, "steps", "heartRate", d0, d0.plusDays(19))
        assertEquals(20, pts.size)
    }

    /* ------------------------------ readiness ------------------------------ */

    @Test
    fun readinessNeedsTwoComponents() {
        val onlySleep = view(mapOf(MetricKey.SLEEP to nights(3, { 23 * 60 }, { 7 * 60 })))
        assertNull(readiness(onlySleep, 8.0))
    }

    @Test
    fun readinessCombinesSleepAndRestingBaseline() {
        val days = 20
        val sleep = nights(days, { 23 * 60 }, { 7 * 60 }) // 8 h every night
        val rhr = (0 until days).map { Sample(ms(d0.plusDays(it.toLong())), 55.0) }
        val r = readiness(view(mapOf(MetricKey.SLEEP to sleep, MetricKey.RESTING_HR to rhr)), 8.0)!!
        assertEquals(2, r.parts.size)
        // sleep score 1.0 (weight .5), resting at baseline 0.5 (weight .3) -> (0.5 + 0.15) / 0.8 = 81
        assertEquals(81, r.score)
    }

    /* ------------------------------ insights ------------------------------ */

    @Test
    fun weekOverWeekInsightWordingAndTone() {
        val v = view(mapOf(MetricKey.STEPS to sumSeries(List(14) { if (it < 7) 5000.0 else 7500.0 })), asOf = d0.plusDays(14))
        val goals = DEFAULT_GOALS
        val i = generateInsights(v, goals).first { it.id == "wow-steps" }
        assertEquals(Tone.GOOD, i.tone)
        assertEquals("You walked 50% more this week than last week", i.title)
        assertTrue(i.detail.contains("7 and 7 days"))
    }

    @Test
    fun goalAndSleepInsights() {
        val steps = sumSeries(List(30) { if (it % 2 == 0) 12_000.0 else 4000.0 })
        val v = view(mapOf(MetricKey.STEPS to steps, MetricKey.SLEEP to nights(30, { 23 * 60 }, { 6 * 60 })), asOf = d0.plusDays(40))
        val out = generateInsights(v, DEFAULT_GOALS)
        val goal = out.first { it.id == "goal-hit" }
        assertEquals("You reached 10,000 steps on 50% of days", goal.title)
        assertEquals(Tone.GOOD, goal.tone) // the web treats a hit rate of 50% or more as good
        val sleep = out.first { it.id == "sleep-avg" }
        assertEquals("Average recorded sleep is 7.0 h (goal 8 h)", sleep.title)
        assertEquals(Tone.WATCH, sleep.tone) // 7.0 < 8 - 0.5
    }

    @Test
    fun noInsightsWithoutCompleteDays() {
        assertTrue(generateInsights(HealthView.EMPTY, DEFAULT_GOALS).isEmpty())
    }

    @Test
    fun summariesOnlyListMetricsWithData() {
        val v = view(mapOf(MetricKey.STEPS to sumSeries(List(40) { 8000.0 })), asOf = d0.plusDays(60))
        val rows = weekSummary(v, v.completeThrough!!, listOf(MetricKey.STEPS, MetricKey.DISTANCE))
        assertEquals(listOf(MetricKey.STEPS), rows.map { it.metric })
        assertTrue(monthSummary(v, listOf(MetricKey.STEPS, MetricKey.DISTANCE)).all { it.metric == MetricKey.STEPS })
    }

    /* ------------------------------ units and derived measures ------------------------------ */

    @Test
    fun unitConversionRoundTrips() {
        assertEquals(3.10686, conv(MetricKey.DISTANCE, 5.0, UnitSystem.IMPERIAL), 1e-4)
        assertEquals(5.0, unconv(MetricKey.DISTANCE, conv(MetricKey.DISTANCE, 5.0, UnitSystem.IMPERIAL), UnitSystem.IMPERIAL), 1e-9)
        assertEquals(5.0, conv(MetricKey.DISTANCE, 5.0, UnitSystem.METRIC), 1e-9)
        assertEquals(1000.0, conv(MetricKey.STEPS, 1000.0, UnitSystem.IMPERIAL), 1e-9)
        assertEquals("lb", unitOf(MetricKey.WEIGHT, UnitSystem.IMPERIAL))
        assertEquals("kg", unitOf(MetricKey.WEIGHT, UnitSystem.METRIC))
    }

    @Test
    fun metricFormatting() {
        assertEquals("—", fmtMetric(MetricKey.STEPS, null, UnitSystem.METRIC))
        assertEquals("—", fmtMetric(MetricKey.STEPS, Double.NaN, UnitSystem.METRIC))
        assertEquals("12,345 steps", fmtMetric(MetricKey.STEPS, 12345.0, UnitSystem.IMPERIAL))
        assertEquals("3.11 mi", fmtMetric(MetricKey.DISTANCE, 5.0, UnitSystem.IMPERIAL))
        assertEquals("5.00 km", fmtMetric(MetricKey.DISTANCE, 5.0, UnitSystem.METRIC))
    }

    @Test
    fun bmiFromSiUnitsMatchesImperialFormula() {
        val kg = 80.0
        val cm = 180.0
        val imperial = (kg * KG_TO_LB) / ((cm * CM_TO_IN) * (cm * CM_TO_IN)) * 703
        assertTrue(abs(bmi(kg, cm) - imperial) < 0.05)
    }

    @Test
    fun heightIsTheLatestReadingOnOrBeforeTheWeight() {
        val v = view(mapOf(MetricKey.HEIGHT to listOf(Sample(ms(d0), 170.0), Sample(ms(d0.plusDays(30)), 171.0))))
        assertEquals(170.0, heightAt(v, ms(d0.plusDays(10)))!!.v, 1e-9)
        assertEquals(171.0, heightAt(v, ms(d0.plusDays(40)))!!.v, 1e-9)
        assertEquals(170.0, heightAt(v, ms(d0.minusDays(10)))!!.v, 1e-9) // earliest as a fallback
    }
}
