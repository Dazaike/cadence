package dev.easonhuang.heartwood.cloud

import android.content.Context
import android.util.Log
import dev.easonhuang.heartwood.data.GoalCatalog
import dev.easonhuang.heartwood.data.GoalsRepository
import dev.easonhuang.heartwood.data.HealthConnectManager
import dev.easonhuang.heartwood.data.HealthDataSource
import dev.easonhuang.heartwood.data.KM_TO_MILES
import dev.easonhuang.heartwood.data.LITERS_TO_FL_OZ
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.data.UnitSystem
import dev.easonhuang.heartwood.samsung.SamsungHealthManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed interface SyncOutcome {
    data class Synced(val points: Int, val unreadableMetrics: List<String>) : SyncOutcome

    /** Not signed in, or a periodic run while auto-sync is off. */
    data object Skipped : SyncOutcome

    /** The server rejected the session; the app has signed itself out locally. */
    data object SignedOut : SyncOutcome

    data class Failed(val message: String, val retry: Boolean) : SyncOutcome
}

data class CloudUiState(val settings: CloudSettings = CloudSettings(), val busy: Boolean = false)

/**
 * Reads the active data source (Health Connect or Samsung Health), converts it to the cloud wire
 * format (always metric/SI units, whatever the on-screen unit setting is) and uploads it.
 * Nothing runs unless the user has signed in.
 */
class CloudSyncManager(
    private val context: Context,
    private val prefs: CloudPreferences,
    private val api: CloudApi,
    private val goalsRepo: GoalsRepository,
    private val activeSource: suspend () -> HealthDataSource,
) {
    private val mutex = Mutex()
    private val running = MutableStateFlow(false)

    val ui: Flow<CloudUiState> = combine(
        prefs.settings,
        running,
        CloudSyncScheduler.oneTimePending(context),
    ) { settings, isRunning, queued -> CloudUiState(settings, isRunning || queued) }

    // ---- Account -------------------------------------------------------------------------------

    /** Signs in or registers. Returns a user-facing error message, or null on success. */
    suspend fun signIn(serverInput: String, email: String, password: String, create: Boolean): String? {
        val url = CloudApi.normalizeServerUrl(serverInput)
            ?: return "Enter the server address as https://… (plain http is only allowed for localhost)."
        val mail = email.trim()
        if (!EMAIL_PATTERN.matches(mail)) return "Enter a valid email address."
        if (password.isEmpty()) return "Enter your password."
        if (create && password.length < MIN_PASSWORD) return "Use a password of at least $MIN_PASSWORD characters."
        val result = if (create) api.register(url, mail, password) else api.login(url, mail, password)
        return when (result) {
            is CloudResult.Ok -> {
                prefs.saveSignIn(url, result.value.email, result.value.token)
                if (prefs.settings.first().autoSync) CloudSyncScheduler.schedulePeriodic(context)
                CloudSyncScheduler.syncNow(context, replace = true)
                null
            }
            is CloudResult.Failure -> authMessage(result, create)
        }
    }

    suspend fun signOut() = withContext(NonCancellable) {
        val token = prefs.token()
        val url = prefs.settings.first().serverUrl
        CloudSyncScheduler.cancelAll(context)
        prefs.clearSession()
        // Best effort: revoke the token server-side. Local sign-out already happened either way.
        if (token != null) api.logout(url, token)
        Unit
    }

    suspend fun setAutoSync(enabled: Boolean) {
        prefs.setAutoSync(enabled)
        if (enabled && prefs.settings.first().signedIn) CloudSyncScheduler.schedulePeriodic(context)
        if (!enabled) CloudSyncScheduler.cancelPeriodic(context)
    }

    // ---- Triggers ------------------------------------------------------------------------------

    fun syncNow() = CloudSyncScheduler.syncNow(context, replace = true)

    /** Called when the app comes to the foreground; at most one sync per [OPEN_THROTTLE_MS]. */
    suspend fun syncOnOpen() {
        val settings = prefs.settings.first()
        if (!settings.signedIn || !settings.autoSync) return
        val now = System.currentTimeMillis()
        if (now - prefs.lastOpenSync() < OPEN_THROTTLE_MS) return
        prefs.setLastOpenSync(now)
        CloudSyncScheduler.schedulePeriodic(context)
        CloudSyncScheduler.syncNow(context, replace = false)
    }

    /** Re-arms the periodic job after a process restart (KEEP makes this a no-op when scheduled). */
    suspend fun ensureScheduled() {
        val settings = prefs.settings.first()
        if (settings.signedIn && settings.autoSync) CloudSyncScheduler.schedulePeriodic(context)
    }

    // ---- Sync ----------------------------------------------------------------------------------

    /** Runs one full sync. Single-flight: concurrent callers queue behind the running one. */
    suspend fun sync(requireAutoSync: Boolean = false): SyncOutcome = mutex.withLock {
        val settings = prefs.settings.first()
        if (!settings.signedIn || (requireAutoSync && !settings.autoSync)) return SyncOutcome.Skipped
        val token = prefs.token() ?: return SyncOutcome.Skipped
        running.value = true
        try {
            val outcome = doSync(settings.serverUrl, token)
            record(token, outcome)
            outcome
        } finally {
            running.value = false
        }
    }

    private suspend fun record(token: String, outcome: SyncOutcome) {
        // A sign-out during the sync must not leave a stale status behind.
        if (prefs.token() != token) return
        when (outcome) {
            is SyncOutcome.Synced -> {
                val read = if (outcome.unreadableMetrics.isEmpty()) "" else " Could not read: ${outcome.unreadableMetrics.joinToString()}."
                val head = if (outcome.points == 0) "Up to date." else "Sent ${outcome.points} readings."
                prefs.recordSync(ok = true, message = head + read)
            }
            is SyncOutcome.Failed -> prefs.recordSync(ok = false, message = outcome.message)
            SyncOutcome.Skipped, SyncOutcome.SignedOut -> Unit
        }
    }

    private suspend fun doSync(base: String, token: String): SyncOutcome {
        val source = activeSource()
        if (!source.isAvailable) return SyncOutcome.Failed("Health data source is not available on this device.", retry = false)
        val granted = try {
            source.grantedPermissions()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read granted permissions", e)
            return SyncOutcome.Failed("Could not read health data access. Open Heartwood and check permissions.", retry = false)
        }

        val sourceId = if (source is SamsungHealthManager) "samsung" else "hc"
        // Without Health Connect's history permission only the last 30 days are readable.
        val maxDays = if (source is HealthConnectManager && HealthConnectManager.PERMISSION_READ_HISTORY !in granted) 30 else BACKFILL_DAYS
        val tier = if (maxDays == BACKFILL_DAYS) "full" else "h30"
        val synced = prefs.syncedDays().toMutableMap()
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()

        val rows = ArrayList<Row>()
        val readKeys = LinkedHashSet<String>()
        val unreadable = ArrayList<String>()
        for (metric in Metric.entries) {
            if (source.permissionFor(metric) !in granted) continue
            val stateKey = "$sourceId:${metric.key}:$tier"
            val last = synced[stateKey]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val days = when (last) {
                null -> maxDays
                else -> (ChronoUnit.DAYS.between(last, today).toInt() + INCREMENTAL_DAYS).coerceIn(INCREMENTAL_DAYS, maxDays)
            }
            try {
                rows += readMetric(source, metric, days, zone, stateKey)
                readKeys += stateKey
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Skipping ${metric.key}: read failed", e)
                unreadable += metric.title
            }
        }

        val points = finalize(rows)
        val remaining = HashMap<String, Int>()
        points.forEach { remaining.merge(it.stateKey, 1, Int::plus) }
        val failedKeys = HashSet<String>()
        var sent = 0
        var abort: SyncOutcome? = null

        for (batch in points.sortedBy { it.wire.key }.chunked(MAX_POINTS_PER_REQUEST)) {
            when (val result = send(base, token, batch, zone)) {
                is CloudResult.Ok -> {
                    sent += batch.size
                    batch.forEach { p -> remaining.merge(p.stateKey, -1, Int::plus) }
                }
                is CloudResult.Failure -> when (result.kind) {
                    CloudResult.Kind.UNAUTHORIZED -> {
                        expireSession(token)
                        return SyncOutcome.SignedOut
                    }
                    CloudResult.Kind.NETWORK, CloudResult.Kind.SERVER, CloudResult.Kind.RATE_LIMITED -> {
                        abort = SyncOutcome.Failed(networkMessage(result), retry = true)
                    }
                    else -> {
                        Log.w(TAG, "Batch rejected (${result.status}): ${result.message}")
                        batch.forEach { failedKeys += it.stateKey }
                    }
                }
            }
            if (abort != null) break
        }

        // Advance only metrics whose every point reached the server; the rest retry next time.
        readKeys.filter { it !in failedKeys && (remaining[it] ?: 0) <= 0 }.forEach { synced[it] = today.toString() }
        prefs.setSyncedDays(synced)

        abort?.let { return it }
        if (failedKeys.isNotEmpty()) {
            val names = failedKeys.map { key -> Metric.fromKey(key.split(':')[1])?.title ?: key }.distinct()
            if (sent == 0) return SyncOutcome.Failed("The server rejected the data (${names.joinToString()}).", retry = false)
            unreadable += names.map { "$it (rejected)" }
        }
        if (readKeys.isEmpty() && unreadable.isNotEmpty()) {
            return SyncOutcome.Failed("Could not read any health data. Check Heartwood's access in Health Connect.", retry = false)
        }

        when (val goals = pushGoals(base, token)) {
            is CloudResult.Failure -> if (goals.kind == CloudResult.Kind.UNAUTHORIZED) {
                expireSession(token)
                return SyncOutcome.SignedOut
            }
            else -> Unit
        }
        return SyncOutcome.Synced(sent, unreadable)
    }

    // ---- Reading -------------------------------------------------------------------------------

    private data class Wire(val key: String, val unit: String)

    private class Stages(val rem: Double, val deep: Double, val light: Double, val awake: Double)

    private class Row(
        val stateKey: String,
        val wire: Wire,
        val t: Long,
        val v: Double,
        val d: String,
        val stages: Stages? = null,
    )

    private fun wireFor(metric: Metric): Wire? = when (metric) {
        Metric.STEPS -> Wire("steps", "steps")
        Metric.DISTANCE -> Wire("distance", "km")
        Metric.ACTIVE_CALORIES -> Wire("activeEnergy", "kcal")
        Metric.TOTAL_CALORIES -> Wire("totalEnergy", "kcal")
        Metric.FLOORS -> Wire("floors", "floors")
        Metric.EXERCISE -> Wire("exercise", "min")
        Metric.HYDRATION -> Wire("hydration", "L")
        Metric.HEART_RATE -> Wire("heartRate", "bpm")
        Metric.RESTING_HR -> Wire("restingHr", "bpm")
        Metric.HRV -> Wire("hrv", "ms")
        Metric.SLEEP -> Wire("sleep", "h")
        Metric.OXYGEN -> Wire("spo2", "%")
        Metric.RESPIRATORY -> Wire("respiratory", "br/min")
        Metric.VO2MAX -> Wire("vo2max", "mL/kg/min")
        Metric.WEIGHT -> Wire("weight", "kg")
        Metric.HEIGHT -> Wire("height", "cm")
        Metric.BLOOD_GLUCOSE -> Wire("glucose", "mg/dL")
        Metric.BLOOD_PRESSURE -> null // split into bpSystolic / bpDiastolic below
    }

    private suspend fun readMetric(
        source: HealthDataSource,
        metric: Metric,
        days: Int,
        zone: ZoneId,
        stateKey: String,
    ): List<Row> {
        fun localDay(time: Instant): LocalDate = time.atZone(zone).toLocalDate()
        fun midnight(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()
        fun daily(wire: Wire, day: LocalDate, v: Float) = Row(stateKey, wire, midnight(day), v.wire(), day.toString())
        fun sample(wire: Wire, time: Instant, v: Float) = Row(stateKey, wire, time.toEpochMilli(), v.wire(), localDay(time).toString())

        if (metric == Metric.BLOOD_PRESSURE) {
            val systolic = Wire("bpSystolic", "mmHg")
            val diastolic = Wire("bpDiastolic", "mmHg")
            return source.readBloodPressure(days).flatMap {
                listOf(sample(systolic, it.time, it.systolic), sample(diastolic, it.time, it.diastolic))
            }
        }
        val wire = wireFor(metric) ?: return emptyList()
        return when (metric) {
            Metric.STEPS, Metric.DISTANCE, Metric.ACTIVE_CALORIES, Metric.TOTAL_CALORIES,
            Metric.FLOORS, Metric.EXERCISE, Metric.HYDRATION ->
                // exportRows returns a zero for every empty day; a zero total is "no data", not a reading.
                source.exportRows(metric, days, UnitSystem.METRIC)
                    .filter { it.value > 0f }
                    .map { daily(wire, localDay(it.time), it.value) }
            Metric.HEART_RATE -> source.readHourlyHeartRate(days).map { sample(wire, it.time, it.value) }
            Metric.RESTING_HR ->
                source.exportRows(metric, days, UnitSystem.METRIC)
                    .groupBy { localDay(it.time) }
                    .map { (day, pts) -> daily(wire, day, pts.map { it.value }.average().toFloat()) }
            Metric.SLEEP -> source.readSleepSessions(days).map {
                Row(
                    stateKey, wire, it.wake.toEpochMilli(), it.asleepHours.wire(2), localDay(it.wake).toString(),
                    it.stages?.let { s -> Stages(s.rem.wire(2), s.deep.wire(2), s.light.wire(2), s.awake.wire(2)) },
                )
            }
            else -> source.exportRows(metric, days, UnitSystem.METRIC).map { sample(wire, it.time, it.value) }
        }
    }

    /** Drops non-finite values, one point per (metric, t) and caps each metric to its newest points. */
    private fun finalize(rows: List<Row>): List<Row> {
        val unique = LinkedHashMap<Pair<String, Long>, Row>()
        for (row in rows) {
            if (row.v.isFinite() && kotlin.math.abs(row.v) < MAX_ABS_VALUE) unique[row.wire.key to row.t] = row
        }
        return unique.values.groupBy { it.wire.key }.values.flatMap { list ->
            list.sortedBy { it.t }.takeLast(MAX_POINTS_PER_METRIC)
        }
    }

    // ---- Uploading -----------------------------------------------------------------------------

    private suspend fun send(base: String, token: String, rows: List<Row>, zone: ZoneId): CloudResult<Int> {
        val body = buildBody(rows, zone)
        if (body.length > MAX_BODY_CHARS && rows.size > 1) {
            val half = rows.size / 2
            val first = send(base, token, rows.subList(0, half), zone)
            if (first is CloudResult.Failure) return first
            val second = send(base, token, rows.subList(half, rows.size), zone)
            return if (second is CloudResult.Ok && first is CloudResult.Ok) CloudResult.Ok(first.value + second.value) else second
        }
        return api.syncSamples(base, token, body)
    }

    private fun buildBody(rows: List<Row>, zone: ZoneId): String {
        val metrics = JSONArray()
        rows.groupBy { it.wire }.forEach { (wire, pts) ->
            val arr = JSONArray()
            pts.forEach { p ->
                val point = JSONObject().put("t", p.t).put("v", p.v).put("d", p.d)
                p.stages?.let {
                    point.put(
                        "stages",
                        JSONObject().put("rem", it.rem).put("deep", it.deep)
                            .put("light", it.light).put("awake", it.awake),
                    )
                }
                arr.put(point)
            }
            metrics.put(JSONObject().put("key", wire.key).put("unit", wire.unit).put("points", arr))
        }
        return JSONObject().put("src", SOURCE_LABEL).put("tz", zone.id).put("metrics", metrics).toString()
    }

    // ---- Goals ---------------------------------------------------------------------------------

    /** Pushes goals if they changed since the last push. Called after sync and when goals are edited. */
    suspend fun pushGoalsIfChanged() {
        val settings = prefs.settings.first()
        if (!settings.signedIn || !settings.autoSync) return
        val token = prefs.token() ?: return
        val result = pushGoals(settings.serverUrl, token)
        if (result is CloudResult.Failure) {
            if (result.kind == CloudResult.Kind.UNAUTHORIZED) expireSession(token)
            else Log.w(TAG, "Goal push failed (${result.status}): ${result.message}")
        }
    }

    /**
     * Writes our goals into the shared `goals` blob in the web's canonical units (distance in mi,
     * hydration in fl oz). The server copy is read only to preserve keys this app does not own
     * (sleep) and, on the very first push after sign-in, to avoid overwriting a goal the user set on
     * the web with this app's untouched default. Goals are never pulled into the app.
     */
    private suspend fun pushGoals(base: String, token: String): CloudResult<Unit>? {
        val goals = goalsRepo.goals.first()
        fun goal(m: Metric) = goals.getValue(m)
        val ours = JSONObject()
            .put("steps", goal(Metric.STEPS).wire(2))
            .put("activeEnergy", goal(Metric.ACTIVE_CALORIES).wire(2))
            .put("exercise", goal(Metric.EXERCISE).wire(2))
            .put("distance", (goal(Metric.DISTANCE) * KM_TO_MILES).wire(2))
            .put("floors", goal(Metric.FLOORS).wire(2))
            .put("hydration", (goal(Metric.HYDRATION) * LITERS_TO_FL_OZ).wire(2))
        val fingerprint = ours.toString()
        val previous = prefs.goalsPushed()
        if (previous == fingerprint) return null
        val firstPush = previous == null
        val defaultsByKey = mapOf(
            "steps" to GoalCatalog.defaults.getValue(Metric.STEPS).wire(2),
            "activeEnergy" to GoalCatalog.defaults.getValue(Metric.ACTIVE_CALORIES).wire(2),
            "exercise" to GoalCatalog.defaults.getValue(Metric.EXERCISE).wire(2),
            "distance" to (GoalCatalog.defaults.getValue(Metric.DISTANCE) * KM_TO_MILES).wire(2),
            "floors" to GoalCatalog.defaults.getValue(Metric.FLOORS).wire(2),
            "hydration" to (GoalCatalog.defaults.getValue(Metric.HYDRATION) * LITERS_TO_FL_OZ).wire(2),
        )

        repeat(2) {
            val current = when (val r = api.getSettings(base, token, "goals")) {
                is CloudResult.Ok -> r.value
                is CloudResult.Failure -> return r
            }
            val merged = JSONObject(current.json?.toString() ?: "{}")
            ours.keys().forEach { key ->
                val untouched = ours.getDouble(key) == defaultsByKey.getValue(key)
                if (!(firstPush && untouched && merged.has(key))) merged.put(key, ours.get(key))
            }
            when (val r = api.putSettings(base, token, "goals", merged, current.updatedAt)) {
                is CloudResult.Ok -> {
                    prefs.setGoalsPushed(fingerprint)
                    return r
                }
                is CloudResult.Failure -> if (r.kind != CloudResult.Kind.CONFLICT) return r
            }
        }
        return CloudResult.Failure(CloudResult.Kind.CONFLICT, 409, "Goals changed on the server while syncing.")
    }

    // ---- Errors --------------------------------------------------------------------------------

    /** 401 on a sync call: the token is dead. Sign out locally, unless the user already did. */
    private suspend fun expireSession(token: String) {
        if (prefs.token() != token) return
        CloudSyncScheduler.cancelAll(context)
        prefs.clearSession("Your session expired or was revoked. Please sign in again.")
    }

    private fun authMessage(f: CloudResult.Failure, create: Boolean): String = when (f.kind) {
        CloudResult.Kind.UNAUTHORIZED -> "Wrong email or password."
        CloudResult.Kind.CONFLICT -> "An account with this email already exists. Sign in instead."
        CloudResult.Kind.FORBIDDEN -> if (create) "This server is not accepting new accounts." else f.message
        CloudResult.Kind.RATE_LIMITED -> "Too many attempts. Wait a few minutes and try again."
        CloudResult.Kind.NETWORK -> "Could not reach the server. Check the address and your connection."
        CloudResult.Kind.SERVER -> "The server had a problem (${f.status}). Try again later."
        else -> f.message
    }

    private fun networkMessage(f: CloudResult.Failure): String = when (f.kind) {
        CloudResult.Kind.NETWORK -> "Could not reach the server. Will retry."
        CloudResult.Kind.RATE_LIMITED -> "The server is rate limiting requests. Will retry."
        else -> "The server had a problem (${f.status}). Will retry."
    }

    /** Float -> JSON-friendly double without binary noise (72.30000305 -> 72.3). */
    private fun Float.wire(decimals: Int = 3): Double {
        val scale = Math.pow(10.0, decimals.toDouble())
        return Math.round(toDouble() * scale) / scale
    }

    private companion object {
        const val TAG = "HeartwoodCloud"
        const val SOURCE_LABEL = "Heartwood (Android)"
        const val BACKFILL_DAYS = 365
        const val INCREMENTAL_DAYS = 3
        const val MAX_POINTS_PER_REQUEST = 5000
        const val MAX_POINTS_PER_METRIC = 10_000
        const val MAX_BODY_CHARS = 900_000
        const val MAX_ABS_VALUE = 1e9
        const val MIN_PASSWORD = 10
        const val OPEN_THROTTLE_MS = 5 * 60_000L
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
