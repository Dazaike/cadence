package dev.dazaike.cadence.analytics

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Port of the web dashboard's `lib/dates.ts`. Days are local calendar days ([LocalDate]); all
 * arithmetic is calendar arithmetic, so DST never shifts a day.
 */

val WEEKDAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
val LONG_DAYS = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

fun dayKey(ts: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()

/** Minutes since local midnight of an instant. */
fun minuteOfDay(ts: Long, zone: ZoneId): Int {
    val t = Instant.ofEpochMilli(ts).atZone(zone)
    return t.hour * 60 + t.minute
}

fun LocalDate.addDays(n: Int): LocalDate = plusDays(n.toLong())

fun diffDays(a: LocalDate, b: LocalDate): Int = ChronoUnit.DAYS.between(a, b).toInt()

fun dayRange(from: LocalDate, to: LocalDate): List<LocalDate> {
    val n = diffDays(from, to)
    if (n < 0) return emptyList()
    return List(n + 1) { from.plusDays(it.toLong()) }
}

/** 0 = Sunday ... 6 = Saturday, like JS `getUTCDay`. */
fun LocalDate.weekdayIndex(): Int = dayOfWeek.value % 7

/** Monday-start week. */
fun LocalDate.startOfWeek(): LocalDate = with(DayOfWeek.MONDAY)

fun LocalDate.startOfMonth(): LocalDate = withDayOfMonth(1)

fun LocalDate.daysInMonth(): Int = YearMonth.from(this).lengthOfMonth()

fun LocalDate.addMonths(n: Int): LocalDate = withDayOfMonth(1).plusMonths(n.toLong())

fun fmtDay(d: LocalDate, withYear: Boolean = false): String =
    "${MONTHS[d.monthValue - 1]} ${d.dayOfMonth}" + if (withYear) ", ${d.year}" else ""

fun fmtDayLong(d: LocalDate): String = "${WEEKDAYS[d.weekdayIndex()]}, ${fmtDay(d, true)}"

fun fmtMonth(d: LocalDate): String = "${MONTHS[d.monthValue - 1]} ${d.year}"

/** "7:05 PM" from minutes since midnight (wraps around 24 h). */
fun fmtClock(minutes: Number): String {
    val m = ((Math.round(minutes.toDouble()).toInt() % 1440) + 1440) % 1440
    val h = m / 60
    val mm = m % 60
    val ap = if (h >= 12) "PM" else "AM"
    return "${if (h % 12 == 0) 12 else h % 12}:${mm.toString().padStart(2, '0')} $ap"
}

/** "7h 05m" from hours. */
fun fmtDuration(hours: Double): String {
    val total = Math.round(hours * 60).toInt()
    return "${total / 60}h ${(total % 60).toString().padStart(2, '0')}m"
}

/** Relative age of a day: "today", "yesterday", "12 days ago", "5 months ago". */
fun fmtAgo(ts: Long, now: Long = System.currentTimeMillis()): String {
    val d = Math.floor((now - ts) / 86_400_000.0).toInt()
    return when {
        d <= 0 -> "today"
        d == 1 -> "yesterday"
        d < 60 -> "$d days ago"
        d < 730 -> "${Math.round(d / 30.0)} months ago"
        else -> "${Math.round(d / 365.0)} years ago"
    }
}

/** Sync recency like the web sidebar: "just now", "12 min ago", "3 h ago", else the date. */
fun fmtSince(ts: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val s = Math.max(0L, Math.round((now - ts) / 1000.0))
    if (s < 45) return "just now"
    val m = Math.round(s / 60.0)
    if (m < 60) return "$m min ago"
    val h = Math.round(m / 60.0)
    if (h < 24) return "$h h ago"
    return fmtDay(dayKey(ts, zone), true)
}

/** Exact local date and time, e.g. "Nov 19, 11:42 PM". */
fun fmtExact(ts: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    "${fmtDay(dayKey(ts, zone))}, ${fmtClock(minuteOfDay(ts, zone))}"
