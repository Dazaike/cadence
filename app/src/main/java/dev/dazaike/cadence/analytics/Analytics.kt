package dev.dazaike.cadence.analytics

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Port of the web dashboard's `lib/analytics.ts`. Same formulas, thresholds and wording, so a card
 * on the phone says what the same card says on the web. "Missing is missing": nothing here
 * zero-fills a day without a recording.
 */

/* ------------------------------ basic stats ------------------------------ */

fun sum(xs: List<Double>): Double = xs.sum()

fun mean(xs: List<Double>): Double = if (xs.isEmpty()) Double.NaN else xs.sum() / xs.size

fun stdev(xs: List<Double>): Double {
    if (xs.size < 2) return Double.NaN
    val m = mean(xs)
    return sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1))
}

fun pearson(xs: List<Double>, ys: List<Double>): Double {
    val n = xs.size
    if (n < 3) return Double.NaN
    val mx = mean(xs)
    val my = mean(ys)
    var num = 0.0
    var dx = 0.0
    var dy = 0.0
    for (i in 0 until n) {
        num += (xs[i] - mx) * (ys[i] - my)
        dx += (xs[i] - mx) * (xs[i] - mx)
        dy += (ys[i] - my) * (ys[i] - my)
    }
    return if (dx != 0.0 && dy != 0.0) num / sqrt(dx * dy) else Double.NaN
}

fun pctChange(now: Double, before: Double): Double = if (before != 0.0) (now - before) / before * 100 else Double.NaN

fun clamp(x: Double, lo: Double, hi: Double): Double = minOf(hi, maxOf(lo, x))

/* ------------------------------ ranges ------------------------------ */

enum class RangePreset(val id: String, val label: String) {
    D7("7d", "Week"),
    D30("30d", "Month"),
    D90("90d", "90 days"),
    Y1("1y", "Year"),
    ALL("all", "All"),
    CUSTOM("custom", "Custom");

    companion object {
        fun fromId(id: String?): RangePreset = entries.firstOrNull { it.id == id } ?: D30
    }
}

data class RangePref(val preset: RangePreset = RangePreset.D30, val from: LocalDate? = null, val to: LocalDate? = null)

enum class Gran(val id: String, val label: String) {
    DAY("day", "Day"),
    WEEK("week", "Week"),
    MONTH("month", "Month");

    companion object {
        fun fromId(id: String?): Gran = entries.firstOrNull { it.id == id } ?: DAY
    }
}

data class DateRange(val from: LocalDate, val to: LocalDate)

fun resolveRange(view: HealthView, pref: RangePref): DateRange {
    val to = if (pref.preset == RangePreset.CUSTOM && pref.to != null) pref.to else view.last ?: LocalDate.of(2000, 1, 1)
    val first = view.first ?: to
    var from = when (pref.preset) {
        RangePreset.D7 -> to.minusDays(6)
        RangePreset.D30 -> to.minusDays(29)
        RangePreset.D90 -> to.minusDays(89)
        RangePreset.Y1 -> to.minusDays(364)
        RangePreset.CUSTOM -> pref.from ?: to.minusDays(29)
        RangePreset.ALL -> first
    }
    if (from < first && pref.preset != RangePreset.CUSTOM) from = first
    if (from > to) from = to
    return DateRange(from, to)
}

data class Point(val d: LocalDate, val v: Double?)

fun dailySeries(view: HealthView, metric: MetricKey, from: LocalDate, to: LocalDate): List<Point> {
    val map = view.daily[metric]
    return dayRange(from, to).map { Point(it, map?.get(it)) }
}

data class Bucket(val label: String, val d: LocalDate, val v: Double?, val n: Int)

/** Aggregates days into weeks/months. Value = mean of recorded days (so buckets stay comparable). */
fun bucketize(points: List<Point>, gran: Gran): List<Bucket> {
    if (gran == Gran.DAY) return points.map { Bucket(fmtDay(it.d), it.d, it.v, if (it.v == null) 0 else 1) }
    val groups = LinkedHashMap<LocalDate, MutableList<Double>>()
    for (p in points) {
        val k = if (gran == Gran.WEEK) p.d.startOfWeek() else p.d.startOfMonth()
        val g = groups.getOrPut(k) { ArrayList() }
        if (p.v != null) g.add(p.v)
    }
    return groups.map { (d, vals) ->
        Bucket(
            label = if (gran == Gran.WEEK) "Wk ${fmtDay(d)}" else "${fmtDay(d).substringBefore(' ')} ’${d.year.toString().takeLast(2)}",
            d = d,
            v = if (vals.isNotEmpty()) mean(vals) else null,
            n = vals.size,
        )
    }
}

fun movingAverage(points: List<Point>, window: Int = 7): List<Double?> =
    points.indices.map { i ->
        val vals = points.subList(maxOf(0, i - window + 1), i + 1).mapNotNull { it.v }
        if (vals.size >= Math.ceil(window / 2.0).toInt()) mean(vals) else null
    }

fun vals(points: List<Point>): List<Double> = points.mapNotNull { it.v }

data class WeekdayAvg(val name: String, val v: Double?, val n: Int)

fun weekdayAverages(view: HealthView, metric: MetricKey, from: LocalDate, to: LocalDate): List<WeekdayAvg> {
    val acc = List(7) { ArrayList<Double>() }
    for (p in dailySeries(view, metric, from, to)) if (p.v != null) acc[p.d.weekdayIndex()].add(p.v)
    return WEEKDAYS.mapIndexed { i, name -> WeekdayAvg(name, if (acc[i].isNotEmpty()) mean(acc[i]) else null, acc[i].size) }
}

data class PeriodCompare(val cur: Double, val prev: Double, val nCur: Int, val nPrev: Int, val change: Double)

/** Mean over the last [length] days up to [to] and the previous window of equal length. */
fun periodCompare(view: HealthView, metric: MetricKey, to: LocalDate, length: Int): PeriodCompare {
    val cur = vals(dailySeries(view, metric, to.minusDays((length - 1).toLong()), to))
    val prev = vals(dailySeries(view, metric, to.minusDays((2 * length - 1).toLong()), to.minusDays(length.toLong())))
    return PeriodCompare(mean(cur), mean(prev), cur.size, prev.size, pctChange(mean(cur), mean(prev)))
}

/* ------------------------------ sleep ------------------------------ */

data class SleepConsistency(val score: Int, val bedSd: Double, val wakeSd: Double, val n: Int)

fun sleepConsistency(view: HealthView, to: LocalDate, nights: Int = 14): SleepConsistency? {
    val rows = dayRange(to.minusDays((nights - 1).toLong()), to).mapNotNull { view.sleep[it] }
    if (rows.size < 5) return null
    val bedSd = stdev(rows.map { it.bedM.toDouble() })
    val wakeSd = stdev(rows.map { it.wakeM.toDouble() })
    // 0 min SD -> 100, 120 min SD -> 0 (average of bedtime and wake-time variability)
    val score = Math.round(clamp(100 - ((bedSd + wakeSd) / 2 / 120) * 100, 0.0, 100.0)).toInt()
    return SleepConsistency(score, bedSd, wakeSd, rows.size)
}

/* ------------------------------ goals & records ------------------------------ */

data class HitRate(val rate: Double, val n: Int)

fun goalHitRate(view: HealthView, metric: MetricKey, goal: Double, to: LocalDate, days: Int = 30): HitRate? {
    val v = vals(dailySeries(view, metric, to.minusDays((days - 1).toLong()), to))
    if (v.size < 5) return null
    return HitRate(v.count { it >= goal }.toDouble() / v.size, v.size)
}

data class Streak(val days: Int, val end: LocalDate)

fun longestStreak(view: HealthView, metric: MetricKey, goal: Double): Streak? {
    val map = view.daily[metric]
    val first = view.first
    val last = view.last
    if (map == null || first == null || last == null) return null
    var best = 0
    var bestEnd: LocalDate? = null
    var cur = 0
    for (d in dayRange(first, view.completeThrough ?: last)) {
        if ((map[d] ?: 0.0) >= goal) {
            cur++
            if (cur > best) {
                best = cur
                bestEnd = d
            }
        } else cur = 0
    }
    return if (best > 0 && bestEnd != null) Streak(best, bestEnd) else null
}

data class DayValue(val d: LocalDate, val v: Double)

fun maxDayOf(view: HealthView, metric: MetricKey): DayValue? {
    val map = view.daily[metric]
    if (map.isNullOrEmpty()) return null
    var best: DayValue? = null
    for ((d, v) in map) if (best == null || v > best.v) best = DayValue(d, v)
    return best
}

/* ------------------------------ correlation ------------------------------ */

data class Correlation(val id: String, val label: String, val x: String, val y: String, val r: Double, val n: Int, val strength: String)

fun strength(r: Double): String {
    val a = abs(r)
    val dir = if (r >= 0) "positive" else "negative"
    return when {
        a < 0.2 -> "negligible"
        a < 0.4 -> "weak $dir"
        a < 0.6 -> "moderate $dir"
        else -> "strong $dir"
    }
}

typealias Getter = (LocalDate) -> Double?

private data class Aligned(val xs: List<Double>, val ys: List<Double>, val ds: List<LocalDate>)

private fun align(view: HealthView, a: Getter, b: Getter, lagB: Int = 0): Aligned {
    val xs = ArrayList<Double>()
    val ys = ArrayList<Double>()
    val ds = ArrayList<LocalDate>()
    val first = view.first
    val last = view.last
    if (first == null || last == null) return Aligned(xs, ys, ds)
    for (d in dayRange(first, view.completeThrough ?: last)) {
        val x = a(d)
        val y = b(d.plusDays(lagB.toLong()))
        if (x != null && y != null && x.isFinite() && y.isFinite()) {
            xs.add(x)
            ys.add(y)
            ds.add(d)
        }
    }
    return Aligned(xs, ys, ds)
}

/** `key` is a [MetricKey.id] or one of the derived series "sleep" and "resting". */
fun metricGetter(view: HealthView, key: String): Getter = when (key) {
    "sleep" -> { d -> view.sleep[d]?.hours }
    "resting" -> { d -> view.resting(d)?.v }
    else -> { d -> MetricKey.fromId(key)?.let { view.daily[it]?.get(d) } }
}

data class ScatterPoint(val d: LocalDate, val x: Double, val y: Double)

fun scatterData(view: HealthView, xKey: String, yKey: String, from: LocalDate, to: LocalDate): List<ScatterPoint> {
    val gx = metricGetter(view, xKey)
    val gy = metricGetter(view, yKey)
    val out = ArrayList<ScatterPoint>()
    for (d in dayRange(from, to)) {
        val x = gx(d)
        val y = gy(d)
        if (x != null && y != null) out.add(ScatterPoint(d, x, y))
    }
    return out
}

private class CorrDef(val id: String, val label: String, val a: String, val b: String, val lag: Int)

private val CORR_DEFS = listOf(
    CorrDef("sleep-steps", "Sleep duration → same-day steps", "sleep", "steps", 0),
    CorrDef("steps-sleep", "Steps → next night’s sleep", "steps", "sleep", 1),
    CorrDef("sleep-rhr", "Sleep duration → same-day resting HR", "sleep", "resting", 0),
    CorrDef("steps-hr", "Steps → same-day average HR", "steps", "heartRate", 0),
    CorrDef("exercise-rhr", "Exercise minutes → next-day resting HR", "exercise", "resting", 1),
)

fun correlations(view: HealthView): List<Correlation> {
    val out = ArrayList<Correlation>()
    for (c in CORR_DEFS) {
        val al = align(view, metricGetter(view, c.a), metricGetter(view, c.b), c.lag)
        if (al.xs.size < 14) continue
        val r = pearson(al.xs, al.ys)
        if (!r.isFinite()) continue
        out.add(Correlation(c.id, c.label, c.a, c.b, r, al.xs.size, strength(r)))
    }
    return out
}

/* ------------------------------ readiness (estimate) ------------------------------ */

data class ReadinessPart(val label: String, val value: String, val score: Double, val weight: Double)

data class Readiness(val score: Int, val parts: List<ReadinessPart>, val date: LocalDate)

/**
 * Heuristic estimate, NOT a measurement. Combines last night's sleep vs the sleep goal, resting HR
 * vs the trailing 28-day baseline, and HRV vs its baseline (only components that exist are used).
 */
fun readiness(view: HealthView, goalSleep: Double): Readiness? {
    val d = view.last ?: return null
    val parts = ArrayList<ReadinessPart>()
    val night = view.sleep[d] ?: view.completeThrough?.let { view.sleep[it] }
    if (night != null) {
        parts.add(ReadinessPart("Sleep", "${fmt1(night.hours)} h", clamp(night.hours / goalSleep, 0.0, 1.0), 0.5))
    }
    val rhr = view.resting(d)
    if (rhr != null) {
        val base = dayRange(d.minusDays(28), d.minusDays(1)).mapNotNull { view.resting(it)?.v }
        if (base.size >= 7) {
            parts.add(
                ReadinessPart(
                    "Resting HR vs baseline",
                    "${Math.round(rhr.v)} vs ${Math.round(mean(base))} bpm",
                    clamp(0.5 - (rhr.v - mean(base)) / 10, 0.0, 1.0),
                    0.3,
                ),
            )
        }
    }
    val hrv = view.daily[MetricKey.HRV]?.get(d)
    if (hrv != null) {
        val base = vals(dailySeries(view, MetricKey.HRV, d.minusDays(28), d.minusDays(1)))
        if (base.size >= 7) {
            parts.add(
                ReadinessPart(
                    "HRV vs baseline",
                    "${Math.round(hrv)} vs ${Math.round(mean(base))} ms",
                    clamp(0.5 + (hrv / mean(base) - 1) * 2.5, 0.0, 1.0),
                    0.2,
                ),
            )
        }
    }
    if (parts.size < 2) return null
    val w = parts.sumOf { it.weight }
    return Readiness(Math.round(parts.sumOf { it.score * it.weight } / w * 100).toInt(), parts, d)
}

/* ------------------------------ insights ------------------------------ */

enum class Tone { GOOD, NEUTRAL, WATCH }

data class Insight(val id: String, val tone: Tone, val metric: String, val title: String, val detail: String)

private fun pct(x: Double) = "${Math.round(abs(x))}%"

private fun fmt1(x: Double) = String.format(java.util.Locale.US, "%.1f", x)

private fun thousands(x: Double) = String.format(java.util.Locale.US, "%,d", Math.round(x))

fun generateInsights(view: HealthView, goals: Goals): List<Insight> {
    val out = ArrayList<Insight>()
    val to = view.completeThrough ?: return out

    // week-over-week
    for (m in listOf(MetricKey.STEPS, MetricKey.ACTIVE_ENERGY, MetricKey.EXERCISE, MetricKey.DISTANCE, MetricKey.FLOORS)) {
        val c = periodCompare(view, m, to, 7)
        if (c.nCur < 4 || c.nPrev < 4 || !c.change.isFinite() || abs(c.change) < 5) continue
        val up = c.change > 0
        out.add(
            Insight(
                id = "wow-${m.id}",
                tone = if (up) Tone.GOOD else Tone.WATCH,
                metric = m.id,
                title = if (m == MetricKey.STEPS) "You walked ${pct(c.change)} ${if (up) "more" else "less"} this week than last week"
                else "${m.label} is ${if (up) "up" else "down"} ${pct(c.change)} week over week",
                detail = "Daily average over the last 7 recorded days vs the 7 before (${c.nCur} and ${c.nPrev} days with data).",
            ),
        )
    }

    // bedtime shift
    fun nights(a: LocalDate, b: LocalDate) = dayRange(a, b).mapNotNull { view.sleep[it] }
    val curN = nights(to.minusDays(13), to)
    val prevN = nights(to.minusDays(27), to.minusDays(14))
    if (curN.size >= 5 && prevN.size >= 5) {
        val shift = mean(curN.map { it.bedM.toDouble() }) - mean(prevN.map { it.bedM.toDouble() })
        if (abs(shift) >= 15) {
            out.add(
                Insight(
                    id = "bedtime-shift",
                    tone = Tone.NEUTRAL,
                    metric = "sleep",
                    title = "Your average bedtime shifted ${Math.round(abs(shift))} minutes ${if (shift > 0) "later" else "earlier"}",
                    detail = "Bedtime is derived as wake time minus sleep duration. Last 14 days (${curN.size} nights) vs the 14 before (${prevN.size} nights).",
                ),
            )
        }
    }

    // resting HR trend (14d vs prior 14d)
    fun rh(a: LocalDate, b: LocalDate) = dayRange(a, b).mapNotNull { view.resting(it) }
    val rc = rh(to.minusDays(13), to)
    val rp = rh(to.minusDays(27), to.minusDays(14))
    if (rc.size >= 5 && rp.size >= 5) {
        val diff = mean(rc.map { it.v }) - mean(rp.map { it.v })
        if (abs(diff) >= 1.5) {
            val derived = rc.any { it.derived }
            out.add(
                Insight(
                    id = "rhr-trend",
                    tone = if (diff < 0) Tone.GOOD else Tone.WATCH,
                    metric = MetricKey.RESTING_HR.id,
                    title = "Your resting heart rate has ${if (diff < 0) "decreased" else "increased"} by ${fmt1(abs(diff))} bpm over the past 2 weeks",
                    detail = if (derived) "Resting HR is approximated by the lowest hourly average of each day because the source has no resting-HR measurement. This is a trend of an approximation, not a medical reading."
                    else "Based on measured daily resting heart rate. A trend is not a diagnosis.",
                ),
            )
        }
    }

    // most active weekday
    if (view.first != null) {
        val w = weekdayAverages(view, MetricKey.STEPS, to.minusDays(89), to).filter { it.v != null && it.n >= 3 }
        if (w.size >= 5) {
            val best = w.reduce { a, b -> if (b.v!! > a.v!!) b else a }
            val overall = mean(w.map { it.v!! })
            out.add(
                Insight(
                    id = "best-weekday",
                    tone = Tone.NEUTRAL,
                    metric = MetricKey.STEPS.id,
                    title = "Your most active day is ${LONG_DAYS[WEEKDAYS.indexOf(best.name)]}",
                    detail = "${thousands(best.v!!)} steps on average (${best.n} days), ${pct(pctChange(best.v, overall))} above your weekday mean over the last 90 days.",
                ),
            )
        }
    }

    // sleep before busy days
    val steps = view.daily[MetricKey.STEPS]
    if (steps != null && view.sleep.isNotEmpty()) {
        class Row(val s: Double, val sl: Double)
        val rows = dayRange(to.minusDays(89), to).mapNotNull { d ->
            val s = steps[d]
            val sl = view.sleep[d]?.hours
            if (s != null && sl != null) Row(s, sl) else null
        }
        if (rows.size >= 24) {
            val sorted = rows.sortedByDescending { it.s }
            val q = rows.size / 4
            val busy = sorted.take(q)
            val rest = sorted.drop(q)
            val diff = (mean(busy.map { it.sl }) - mean(rest.map { it.sl })) * 60
            if (abs(diff) >= 20) {
                out.add(
                    Insight(
                        id = "sleep-busy",
                        tone = Tone.NEUTRAL,
                        metric = "correlation",
                        title = "Sleep was ${Math.round(abs(diff))} min ${if (diff < 0) "shorter" else "longer"} on your busiest days",
                        detail = "Compares the night ending on your top-quartile step days (${busy.size}) with all other days (${rest.size}). This is an association, not evidence that one causes the other.",
                    ),
                )
            }
        }
    }

    // goal consistency
    val stepGoal = goals[GoalKey.STEPS] ?: DEFAULT_GOALS.getValue(GoalKey.STEPS)
    goalHitRate(view, MetricKey.STEPS, stepGoal, to)?.let { hit ->
        out.add(
            Insight(
                id = "goal-hit",
                tone = if (hit.rate >= 0.5) Tone.GOOD else Tone.NEUTRAL,
                metric = MetricKey.STEPS.id,
                title = "You reached ${thousands(stepGoal)} steps on ${Math.round(hit.rate * 100)}% of days",
                detail = "Last 30 days, ${hit.n} days with data.",
            ),
        )
    }

    // sleep duration vs goal
    val sleepGoal = goals[GoalKey.SLEEP] ?: DEFAULT_GOALS.getValue(GoalKey.SLEEP)
    val sleepRecent = dayRange(to.minusDays(29), to).mapNotNull { view.sleep[it]?.hours }
    if (sleepRecent.size >= 5) {
        val avg = mean(sleepRecent)
        out.add(
            Insight(
                id = "sleep-avg",
                tone = if (avg >= sleepGoal - 0.5) Tone.GOOD else Tone.WATCH,
                metric = "sleep",
                title = "Average recorded sleep is ${fmt1(avg)} h (goal ${trimGoal(sleepGoal)} h)",
                detail = "${sleepRecent.size} nights with data in the last 30 days; nights without a recording are not counted as zero.",
            ),
        )
    }

    return out
}

private fun trimGoal(x: Double) = if (x % 1.0 == 0.0) x.toInt().toString() else fmt1(x)

data class WeekRow(val metric: MetricKey, val cur: Double, val prev: Double, val nCur: Int, val nPrev: Int, val change: Double)

fun weekSummary(view: HealthView, to: LocalDate, metrics: List<MetricKey>): List<WeekRow> =
    metrics.filter { view.has(it) }.map {
        val c = periodCompare(view, it, to, 7)
        WeekRow(it, c.cur, c.prev, c.nCur, c.nPrev, c.change)
    }

data class MonthRow(
    val metric: MetricKey,
    val cur: Double,
    val prev: Double,
    val nCur: Int,
    val nPrev: Int,
    val change: Double,
    val monthStart: LocalDate,
    val prevStart: LocalDate,
)

fun monthSummary(view: HealthView, metrics: List<MetricKey>): List<MonthRow> {
    val to = view.completeThrough ?: return emptyList()
    val mStart = to.startOfMonth()
    val prevEnd = mStart.minusDays(1)
    val prevStart = prevEnd.startOfMonth()
    return metrics.filter { view.has(it) }.map { m ->
        val cur = vals(dailySeries(view, m, mStart, to))
        val prev = vals(dailySeries(view, m, prevStart, prevEnd))
        MonthRow(m, mean(cur), mean(prev), cur.size, prev.size, pctChange(mean(cur), mean(prev)), mStart, prevStart)
    }
}

/* ------------------------------ derived measures ------------------------------ */

/** BMI from kilograms and centimetres (the web's 703 x lb / in^2 expressed in SI). */
fun bmi(weightKg: Double, heightCm: Double): Double {
    val m = heightCm / 100.0
    return weightKg / (m * m)
}

/** Height reading nearest on or before [t] (plus a day of slack), else the earliest reading. */
fun heightAt(view: HealthView, t: Long): Sample? =
    view.height.lastOrNull { it.t <= t + 86_400_000 } ?: view.height.firstOrNull()
