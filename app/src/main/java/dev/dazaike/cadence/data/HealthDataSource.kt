package dev.dazaike.cadence.data

/** Common read-only surface both Health Connect and Samsung Health managers implement. */
interface HealthDataSource {
    val isAvailable: Boolean
    fun permissionFor(metric: Metric): String
    suspend fun grantedPermissions(): Set<String>
    suspend fun isGranted(metric: Metric): Boolean
    suspend fun readDashboard(unitSystem: UnitSystem = UnitSystem.IMPERIAL): List<MetricSummary>
    suspend fun readDetail(metric: Metric, unitSystem: UnitSystem = UnitSystem.IMPERIAL): MetricDetail
    suspend fun readDailySeries(metric: Metric, days: Int, unitSystem: UnitSystem = UnitSystem.IMPERIAL): List<SeriesPoint>
    suspend fun exportRows(metric: Metric, days: Int, unitSystem: UnitSystem = UnitSystem.IMPERIAL): List<SeriesPoint>

    /**
     * Heart rate reduced to one average per local clock hour (point time = hour start), for the local days
     * `fromDaysAgo` through `toDaysAgo` inclusive (0 = today, `fromDaysAgo >= toDaysAgo`), oldest first.
     */
    suspend fun readHourlyHeartRateRange(fromDaysAgo: Int, toDaysAgo: Int): List<SeriesPoint>

    /** Cloud sync: hourly heart-rate averages for the last [days] days plus today. */
    suspend fun readHourlyHeartRate(days: Int): List<SeriesPoint> = readHourlyHeartRateRange(days, 0)

    /** Cloud sync: full blood-pressure readings (systolic and diastolic), oldest first. */
    suspend fun readBloodPressure(days: Int): List<BloodPressureReading>

    /** Cloud sync: sleep sessions with stage breakdown where the source provides one, oldest first. */
    suspend fun readSleepSessions(days: Int): List<SleepSessionData>
}
