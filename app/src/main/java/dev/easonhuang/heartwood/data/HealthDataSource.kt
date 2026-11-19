package dev.easonhuang.heartwood.data

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

    /** Cloud sync: heart rate reduced to one average per local clock hour (point time = hour start). */
    suspend fun readHourlyHeartRate(days: Int): List<SeriesPoint>

    /** Cloud sync: full blood-pressure readings (systolic and diastolic), oldest first. */
    suspend fun readBloodPressure(days: Int): List<BloodPressureReading>

    /** Cloud sync: sleep sessions with stage breakdown where the source provides one, oldest first. */
    suspend fun readSleepSessions(days: Int): List<SleepSessionData>
}
