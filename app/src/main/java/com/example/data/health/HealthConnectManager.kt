package com.example.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Pressure
import com.example.data.model.ActivityWorkoutMetrics
import com.example.data.model.ContextualPromptChip
import com.example.data.model.DailyBiometricEntry
import com.example.data.model.HealthSummarySnapshot
import com.example.data.model.HeartVitalsMetrics
import com.example.data.model.InsightCategory
import com.example.data.model.PeriodHorizonSummary
import com.example.data.model.SleepRecoveryMetrics
import com.example.data.model.TimeWindow
import com.example.data.model.WorkoutSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.reflect.KClass

enum class HealthConnectStatus {
    AVAILABLE_GRANTED,
    AVAILABLE_PERMISSIONS_NEEDED,
    PROVIDER_UPDATE_NEEDED,
    UNAVAILABLE
}

class HealthConnectManager(private val context: Context) {

    val requiredPermissions: Set<String> = setOf(
        // Heart & Vitals
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getWritePermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getWritePermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getWritePermission(OxygenSaturationRecord::class),
        // Sleep & Recovery
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getWritePermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getWritePermission(RestingHeartRateRecord::class),
        // Activity & Steps
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    )

    val allReadPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    )

    private fun getClientOrNull(): HealthConnectClient? {
        return try {
            val status = HealthConnectClient.getSdkStatus(context)
            if (status == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(context)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun checkStatus(): Pair<HealthConnectStatus, Set<String>> = withContext(Dispatchers.IO) {
        try {
            when (HealthConnectClient.getSdkStatus(context)) {
                HealthConnectClient.SDK_AVAILABLE -> {
                    val client = HealthConnectClient.getOrCreate(context)
                    val granted = client.permissionController.getGrantedPermissions()
                    if (granted.intersect(allReadPermissions).isNotEmpty()) {
                        HealthConnectStatus.AVAILABLE_GRANTED to granted
                    } else {
                        HealthConnectStatus.AVAILABLE_PERMISSIONS_NEEDED to granted
                    }
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                    HealthConnectStatus.PROVIDER_UPDATE_NEEDED to emptySet()
                }
                else -> {
                    HealthConnectStatus.UNAVAILABLE to emptySet()
                }
            }
        } catch (e: Exception) {
            HealthConnectStatus.UNAVAILABLE to emptySet()
        }
    }

    /**
     * Reads all records of [recordType] across [filter] using descending order (`ascendingOrder = false`)
     * and cursor pagination (`pageToken`).
     *
     * CRITICAL FIX: Health Connect defaults to `ascendingOrder = true, pageSize = 1000`.
     * Without `ascendingOrder = false` and pagination, high-frequency records like `StepsRecord` and
     * `HeartRateRecord` over a 30-day window hit the 1,000-record cap in the oldest week (days 30..24 ago),
     * dropping all records from the last 7 days and today!
     */
    private suspend fun <T : Record> readAllRecordsInRange(
        client: HealthConnectClient,
        recordType: KClass<T>,
        filter: TimeRangeFilter,
        maxPages: Int = 25
    ): List<T> {
        val all = mutableListOf<T>()
        var pageToken: String? = null
        var pageCount = 0
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = filter,
                    ascendingOrder = false, // Most recent records first!
                    pageSize = 1000,
                    pageToken = pageToken
                )
            )
            all.addAll(response.records)
            pageToken = response.pageToken
            pageCount++
        } while (!pageToken.isNullOrEmpty() && pageCount < maxPages)
        return all
    }

    private fun buildGrantedAggregateMetrics(granted: Set<String>): Set<AggregateMetric<*>> {
        val metrics = mutableSetOf<AggregateMetric<*>>()
        if (granted.contains(HealthPermission.getReadPermission(StepsRecord::class))) {
            metrics.add(StepsRecord.COUNT_TOTAL)
        }
        if (granted.contains(HealthPermission.getReadPermission(DistanceRecord::class))) {
            metrics.add(DistanceRecord.DISTANCE_TOTAL)
        }
        if (granted.contains(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))) {
            metrics.add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
        }
        if (granted.contains(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))) {
            metrics.add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
        }
        if (granted.contains(HealthPermission.getReadPermission(HeartRateRecord::class))) {
            metrics.add(HeartRateRecord.BPM_AVG)
            metrics.add(HeartRateRecord.BPM_MIN)
            metrics.add(HeartRateRecord.BPM_MAX)
        }
        if (granted.contains(HealthPermission.getReadPermission(RestingHeartRateRecord::class))) {
            metrics.add(RestingHeartRateRecord.BPM_AVG)
            metrics.add(RestingHeartRateRecord.BPM_MIN)
            metrics.add(RestingHeartRateRecord.BPM_MAX)
        }
        return metrics
    }

    private suspend fun safeAggregateRange(
        client: HealthConnectClient,
        grantedMetrics: Set<AggregateMetric<*>>,
        filter: TimeRangeFilter
    ): AggregationResult? {
        if (grantedMetrics.isEmpty()) return null
        return runCatching {
            client.aggregate(
                AggregateRequest(
                    metrics = grantedMetrics,
                    timeRangeFilter = filter
                )
            )
        }.getOrNull()
    }

    /**
     * Reads the full 30-day biometric history from Android Health Connect and simultaneously
     * computes:
     * 1) Official deduplicated Health Connect aggregates via `aggregateGroupByPeriod` and `aggregate`
     * 2) Paginated raw records (`ascendingOrder = false`) so today's and this week's HR, steps, and workouts
     *    are NEVER truncated by the 1,000-record single-page limit
     * 3) Local calendar-day bucketing (`00:00` to `24:00` local time) so daily totals match Health Connect
     * 4) Chronologically sorted workouts (`endTime` descending) with session-matched HR, calories, and distance
     */
    suspend fun readHealthSnapshot(window: TimeWindow): HealthSummarySnapshot = withContext(Dispatchers.IO) {
        val client = getClientOrNull()
            ?: return@withContext createPreviewSnapshot(window)

        val granted = try {
            client.permissionController.getGrantedPermissions()
        } catch (e: Exception) {
            emptySet()
        }

        if (granted.intersect(allReadPermissions).isEmpty()) {
            return@withContext createPreviewSnapshot(window)
        }

        val zoneId = ZoneId.systemDefault()
        val now = Instant.now()
        val todayLocalDate = LocalDate.now(zoneId)
        val startLocalDate30d = todayLocalDate.minusDays(29)
        val start30dInstant = startLocalDate30d.atStartOfDay(zoneId).toInstant()
        // Include up to end of today in local time so daily summary records with endTime = 23:59:59 today are never excluded
        val endOfTodayInstant = todayLocalDate.plusDays(1).atStartOfDay(zoneId).toInstant()
        val queryEndInstant = if (now.isAfter(endOfTodayInstant)) now else endOfTodayInstant
        val filter30d = TimeRangeFilter.between(start30dInstant, queryEndInstant)

        var totalRecords30d = 0

        val hrRecords = if (granted.contains(HealthPermission.getReadPermission(HeartRateRecord::class))) {
            runCatching {
                readAllRecordsInRange(client, HeartRateRecord::class, filter30d)
            }.getOrDefault(emptyList())
        } else emptyList()
        totalRecords30d += hrRecords.size

        val hrvRecords = if (granted.contains(HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class))) {
            runCatching {
                readAllRecordsInRange(client, HeartRateVariabilityRmssdRecord::class, filter30d)
                    .sortedByDescending { it.time }
            }.getOrDefault(emptyList())
        } else emptyList()
        totalRecords30d += hrvRecords.size

        val bpRecords = if (granted.contains(HealthPermission.getReadPermission(BloodPressureRecord::class))) {
            runCatching {
                readAllRecordsInRange(client, BloodPressureRecord::class, filter30d)
                    .sortedByDescending { it.time }
            }.getOrDefault(emptyList())
        } else emptyList()
        totalRecords30d += bpRecords.size

        val spo2Records = if (granted.contains(HealthPermission.getReadPermission(OxygenSaturationRecord::class))) {
            runCatching {
                readAllRecordsInRange(client, OxygenSaturationRecord::class, filter30d)
                    .sortedByDescending { it.time }
            }.getOrDefault(emptyList())
        } else emptyList()
        totalRecords30d += spo2Records.size

        val sleepRecords = if (granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class))) {
            runCatching {
                readAllRecordsInRange(client, SleepSessionRecord::class, filter30d)
                    .sortedBy { it.endTime }
            }.getOrDefault(emptyList())
        } else emptyList()
        totalRecords30d += sleepRecords.size

        val rhrRecords = if (granted.contains(HealthPermission.getReadPermission(RestingHeartRateRecord::class))) {
            runCatching {
                readAllRecordsInRange(client, RestingHeartRateRecord::class, filter30d)
                    .sortedByDescending { it.time }
            }.getOrDefault(emptyList())
        } else emptyList()
        totalRecords30d += rhrRecords.size

        val stepRecords = if (granted.contains(HealthPermission.getReadPermission(StepsRecord::class))) {
            runCatching {
                readAllRecordsInRange(client, StepsRecord::class, filter30d)
            }.getOrDefault(emptyList())
        } else emptyList()
        totalRecords30d += stepRecords.size

        val distRecords = if (granted.contains(HealthPermission.getReadPermission(DistanceRecord::class))) {
            runCatching {
                readAllRecordsInRange(client, DistanceRecord::class, filter30d)
            }.getOrDefault(emptyList())
        } else emptyList()
        totalRecords30d += distRecords.size

        val activeCalRecords = if (granted.contains(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))) {
            runCatching {
                readAllRecordsInRange(client, ActiveCaloriesBurnedRecord::class, filter30d)
            }.getOrDefault(emptyList())
        } else emptyList()
        totalRecords30d += activeCalRecords.size

        val totalCalRecords = if (granted.contains(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))) {
            runCatching {
                readAllRecordsInRange(client, TotalCaloriesBurnedRecord::class, filter30d)
            }.getOrDefault(emptyList())
        } else emptyList()
        totalRecords30d += totalCalRecords.size

        // Sort exercise sessions strictly by endTime DESCENDING (then startTime DESCENDING)
        // so index 0 is ALWAYS the most recent / last workout!
        val exerciseRecords = if (granted.contains(HealthPermission.getReadPermission(ExerciseSessionRecord::class))) {
            runCatching {
                readAllRecordsInRange(client, ExerciseSessionRecord::class, filter30d)
                    .sortedWith(
                        compareByDescending<ExerciseSessionRecord> { it.endTime }
                            .thenByDescending { it.startTime }
                    )
            }.getOrDefault(emptyList())
        } else emptyList()
        totalRecords30d += exerciseRecords.size

        // Query official Health Connect per-day deduplicated aggregates across the 30 calendar days
        val grantedAggregateMetrics = buildGrantedAggregateMetrics(granted)
        val dailyAggregatesByDate: Map<LocalDate, AggregationResult> = if (grantedAggregateMetrics.isNotEmpty()) {
            runCatching {
                val startLdt = LocalDateTime.of(startLocalDate30d, LocalTime.MIDNIGHT)
                val endLdt = LocalDateTime.of(todayLocalDate.plusDays(1), LocalTime.MIDNIGHT)
                val grouped = client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = grantedAggregateMetrics,
                        timeRangeFilter = TimeRangeFilter.between(startLdt, endLdt),
                        timeRangeSlicer = Period.ofDays(1)
                    )
                )
                grouped.associate { bucket ->
                    bucket.startTime.toLocalDate() to bucket.result
                }
            }.getOrDefault(emptyMap())
        } else emptyMap()

        // Flatten all heart rate samples once (sorted chronologically oldest -> newest)
        val allHrSamples30d = hrRecords
            .flatMap { it.samples }
            .sortedBy { it.time }

        // If raw record list is empty, check if aggregate API still has records
        val agg30dCheck = safeAggregateRange(client, grantedAggregateMetrics, filter30d)
        val hasAggregateData = agg30dCheck != null && (
            (agg30dCheck[StepsRecord.COUNT_TOTAL] ?: 0L) > 0L ||
                (agg30dCheck[HeartRateRecord.BPM_AVG] ?: 0L) > 0L ||
                (agg30dCheck[RestingHeartRateRecord.BPM_AVG] ?: 0L) > 0L ||
                (agg30dCheck[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0) > 0.0 ||
                (agg30dCheck[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0) > 0.0
            )

        if (totalRecords30d == 0 && !hasAggregateData) {
            return@withContext HealthSummarySnapshot(
                window = window,
                capturedAtEpochMs = System.currentTimeMillis(),
                isFromLiveHealthConnect = true,
                dataSourceDescription = "Health Connect connected (0 records — tap + button to seed 30-day records)",
                totalRecordCount = 0
            )
        }

        // Build day-by-day 30D log aligned to Local Calendar Days (daysAgo = 29 downTo 0, where 0 is Today)
        val dateFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        val dowFmt = DateTimeFormatter.ofPattern("EEE", Locale.US)
        val dailyEntries = mutableListOf<DailyBiometricEntry>()

        for (dayIdx in 29 downTo 0) {
            val localDate = todayLocalDate.minusDays(dayIdx.toLong())
            val dayStart = localDate.atStartOfDay(zoneId).toInstant()
            val dayEnd = localDate.plusDays(1).atStartOfDay(zoneId).toInstant()
            val dayAgg = dailyAggregatesByDate[localDate]

            // Sleep sessions attributed to the calendar day they end (wake-up day)
            val daySleep = sleepRecords.filter { !it.endTime.isBefore(dayStart) && it.endTime.isBefore(dayEnd) }
            var sleepMin = 0
            var deepMin = 0
            var remMin = 0
            var lightMin = 0
            var awakeMin = 0
            if (daySleep.isNotEmpty()) {
                daySleep.forEach { sess ->
                    val dur = Duration.between(sess.startTime, sess.endTime).toMinutes().toInt().coerceAtLeast(0)
                    sleepMin += dur
                    val stages = extractStageMinutes(sess, dur)
                    deepMin += stages.deep
                    remMin += stages.rem
                    lightMin += stages.light
                    awakeMin += stages.awake
                }
            }

            // Heart Rate samples for this calendar day
            val dayHrSamples = allHrSamples30d.filter { !it.time.isBefore(dayStart) && it.time.isBefore(dayEnd) }
            val rawAvgHr = if (dayHrSamples.isNotEmpty()) dayHrSamples.map { it.beatsPerMinute }.average().roundToInt() else 0
            val rawMinHr = if (dayHrSamples.isNotEmpty()) dayHrSamples.minOf { it.beatsPerMinute }.toInt() else 0
            val rawMaxHr = if (dayHrSamples.isNotEmpty()) dayHrSamples.maxOf { it.beatsPerMinute }.toInt() else 0

            val aggAvgHr = dayAgg?.get(HeartRateRecord.BPM_AVG)?.toInt() ?: 0
            val aggMinHr = dayAgg?.get(HeartRateRecord.BPM_MIN)?.toInt() ?: 0
            val aggMaxHr = dayAgg?.get(HeartRateRecord.BPM_MAX)?.toInt() ?: 0

            // Resting Heart Rate for this calendar day
            val dayRhrList = rhrRecords.filter { !it.time.isBefore(dayStart) && it.time.isBefore(dayEnd) }
            val rawRhr = if (dayRhrList.isNotEmpty()) dayRhrList.map { it.beatsPerMinute }.average().roundToInt() else 0
            val aggRhr = dayAgg?.get(RestingHeartRateRecord.BPM_AVG)?.toInt() ?: 0
            // If no explicit RestingHeartRateRecord on this day, estimate from lowest 15th percentile of HR samples
            val derivedRhrFromHr = if (dayHrSamples.isNotEmpty()) {
                val sortedBpm = dayHrSamples.map { it.beatsPerMinute.toInt() }.filter { it in 38..100 }.sorted()
                if (sortedBpm.isNotEmpty()) {
                    val idx15 = (sortedBpm.size * 0.15).toInt().coerceIn(0, sortedBpm.lastIndex)
                    sortedBpm[idx15]
                } else 0
            } else 0

            val dayRhr = when {
                aggRhr > 0 -> aggRhr
                rawRhr > 0 -> rawRhr
                derivedRhrFromHr > 0 -> derivedRhrFromHr
                else -> 0
            }

            val dayAvgHr = when {
                aggAvgHr > 0 -> aggAvgHr
                rawAvgHr > 0 -> rawAvgHr
                dayRhr > 0 -> dayRhr
                else -> 0
            }
            val dayMinHr = when {
                aggMinHr > 0 -> aggMinHr
                rawMinHr > 0 -> rawMinHr
                dayRhr > 0 -> max(40, dayRhr - 3)
                dayAvgHr > 0 -> max(40, dayAvgHr - 8)
                else -> 0
            }
            val dayMaxHr = when {
                aggMaxHr > 0 -> aggMaxHr
                rawMaxHr > 0 -> rawMaxHr
                dayAvgHr > 0 -> dayAvgHr
                else -> 0
            }

            // HRV, Blood Pressure, SpO2 for this calendar day
            val dayHrvList = hrvRecords.filter { !it.time.isBefore(dayStart) && it.time.isBefore(dayEnd) }
            val dayHrv = if (dayHrvList.isNotEmpty()) dayHrvList.map { it.heartRateVariabilityMillis }.average() else 0.0

            val dayBpList = bpRecords.filter { !it.time.isBefore(dayStart) && it.time.isBefore(dayEnd) }
            val daySys = if (dayBpList.isNotEmpty()) dayBpList.map { it.systolic.inMillimetersOfMercury }.average().roundToInt() else 0
            val dayDia = if (dayBpList.isNotEmpty()) dayBpList.map { it.diastolic.inMillimetersOfMercury }.average().roundToInt() else 0

            val daySpo2List = spo2Records.filter { !it.time.isBefore(dayStart) && it.time.isBefore(dayEnd) }
            val daySpo2 = if (daySpo2List.isNotEmpty()) daySpo2List.map { it.percentage.value }.average() else 0.0

            // Steps for this calendar day (prefer official deduplicated Health Connect aggregate, fallback to overlap sum)
            val rawSteps = sumIntervalStepsForWindow(stepRecords, dayStart, dayEnd)
            val aggSteps = dayAgg?.get(StepsRecord.COUNT_TOTAL) ?: 0L
            val daySteps = if (aggSteps > 0L) aggSteps else rawSteps

            // Distance for this calendar day
            val rawDistKm = sumIntervalDistanceKmForWindow(distRecords, dayStart, dayEnd)
            val aggDistKm = dayAgg?.get(DistanceRecord.DISTANCE_TOTAL)?.inKilometers ?: 0.0
            val dayDistKm = when {
                aggDistKm > 0.0 -> aggDistKm
                rawDistKm > 0.0 -> rawDistKm
                daySteps > 0L -> daySteps * 0.00076
                else -> 0.0
            }

            // Workouts for this calendar day (sorted newest first because exerciseRecords is sorted newest first)
            val dayWorkouts = exerciseRecords.filter {
                val mid = Instant.ofEpochMilli((it.startTime.toEpochMilli() + it.endTime.toEpochMilli()) / 2L)
                !mid.isBefore(dayStart) && mid.isBefore(dayEnd)
            }
            val workoutMin = dayWorkouts.sumOf {
                Duration.between(it.startTime, it.endTime).toMinutes().toInt().coerceAtLeast(0)
            }
            val workoutTitle = if (dayWorkouts.isNotEmpty()) {
                dayWorkouts.joinToString(" + ") { it.title?.takeIf { t -> t.isNotBlank() } ?: exerciseTypeToLabel(it.exerciseType) }
            } else null

            // Active & Total Calories for this calendar day
            val rawActiveKcal = sumIntervalActiveCaloriesForWindow(activeCalRecords, dayStart, dayEnd).roundToInt()
            val aggActiveKcal = dayAgg?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories?.roundToInt() ?: 0
            val rawTotalKcal = sumIntervalTotalCaloriesForWindow(totalCalRecords, dayStart, dayEnd).roundToInt()
            val aggTotalKcal = dayAgg?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories?.roundToInt() ?: 0

            val prelimTotalKcal = if (aggTotalKcal > 0) aggTotalKcal else rawTotalKcal
            val prelimActiveKcal = if (aggActiveKcal > 0) aggActiveKcal else rawActiveKcal

            val dayActiveKcal = when {
                prelimActiveKcal > 0 -> prelimActiveKcal
                prelimTotalKcal > 1400 -> (prelimTotalKcal - 1550).coerceAtLeast((daySteps * 0.04).roundToInt())
                daySteps > 0L || workoutMin > 0 -> (daySteps * 0.042 + workoutMin * 6.5).roundToInt()
                else -> 0
            }
            val dayTotalKcal = when {
                prelimTotalKcal > 0 -> prelimTotalKcal
                dayActiveKcal > 0 -> dayActiveKcal + 1650
                else -> 0
            }

            val recScore = computeRecoveryScore(sleepMin, dayHrv, dayRhr)

            if (sleepMin > 0 || daySteps > 0L || dayAvgHr > 0 || dayRhr > 0 || dayHrv > 0.0 ||
                dayActiveKcal > 0 || prelimTotalKcal > 0 || workoutMin > 0 || daySys > 0 || daySpo2 > 0.0
            ) {
                dailyEntries.add(
                    DailyBiometricEntry(
                        daysAgo = dayIdx,
                        dateLabel = dateFmt.format(localDate),
                        dayOfWeekShort = dowFmt.format(localDate),
                        sleepDurationMinutes = sleepMin,
                        deepSleepMinutes = deepMin,
                        remSleepMinutes = remMin,
                        lightSleepMinutes = lightMin,
                        awakeMinutes = awakeMin,
                        restingHeartRateBpm = dayRhr,
                        avgHeartRateBpm = dayAvgHr,
                        minHeartRateBpm = dayMinHr,
                        maxHeartRateBpm = dayMaxHr,
                        hrvRmssdMs = dayHrv,
                        systolicMmHg = daySys,
                        diastolicMmHg = dayDia,
                        spO2Percent = daySpo2,
                        steps = daySteps,
                        distanceKm = dayDistKm,
                        activeCaloriesKcal = dayActiveKcal,
                        totalCaloriesKcal = dayTotalKcal,
                        workoutTitle = workoutTitle,
                        workoutMinutes = workoutMin,
                        workoutCount = dayWorkouts.size,
                        recoveryScore = recScore
                    )
                )
            }
        }

        // Enrich all 30D workouts with session-matched telemetry (newest first: index 0 is Most Recent!)
        val allWorkouts30d = exerciseRecords.map { ex ->
            buildEnrichedWorkoutSummary(
                ex = ex,
                todayLocalDate = todayLocalDate,
                zoneId = zoneId,
                allHrSamples = allHrSamples30d,
                activeCalRecords = activeCalRecords,
                totalCalRecords = totalCalRecords,
                distRecords = distRecords,
                stepRecords = stepRecords
            )
        }

        // Also query rolling 24-hour aggregate in case today just started (e.g. morning) or records straddle midnight
        val rolling24hStart = now.minus(24L, ChronoUnit.HOURS)
        val todayStartInstant = todayLocalDate.atStartOfDay(zoneId).toInstant()
        val aggToday = safeAggregateRange(
            client,
            grantedAggregateMetrics,
            TimeRangeFilter.between(todayStartInstant, queryEndInstant)
        )
        val aggRolling24h = safeAggregateRange(
            client,
            grantedAggregateMetrics,
            TimeRangeFilter.between(rolling24hStart, queryEndInstant)
        )
        val agg7d = safeAggregateRange(
            client,
            grantedAggregateMetrics,
            TimeRangeFilter.between(todayLocalDate.minusDays(6).atStartOfDay(zoneId).toInstant(), queryEndInstant)
        )
        val agg30d = agg30dCheck

        // Build 30D, 7D, and 24H horizons with fallback so no card ever displays 0 when data exists in Health Connect
        val horizon30d = buildHorizonSummaryFromDays(
            window = TimeWindow.DAYS_30,
            days = dailyEntries,
            allDaysFallback = dailyEntries,
            aggResult = agg30d,
            workoutsInHorizon = allWorkouts30d
        )

        val days7d = dailyEntries.filter { it.daysAgo < 7 }.ifEmpty { dailyEntries.takeLast(7) }
        val cutoff7dEpoch = todayLocalDate.minusDays(6).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val workouts7d = allWorkouts30d.filter { it.endTimeEpochMs >= cutoff7dEpoch }
        val horizon7d = buildHorizonSummaryFromDays(
            window = TimeWindow.DAYS_7,
            days = days7d,
            allDaysFallback = dailyEntries,
            aggResult = agg7d,
            workoutsInHorizon = workouts7d
        )

        val days24h = dailyEntries.filter { it.daysAgo == 0 }.ifEmpty { dailyEntries.takeLast(1) }
        val cutoff24hEpoch = min(todayStartInstant.toEpochMilli(), rolling24hStart.toEpochMilli())
        val workouts24h = allWorkouts30d.filter { it.endTimeEpochMs >= cutoff24hEpoch }
        val horizon24h = buildHorizonSummaryFromDays(
            window = TimeWindow.HOURS_24,
            days = days24h,
            allDaysFallback = dailyEntries,
            aggResult = aggToday ?: aggRolling24h,
            secondaryAggResult = aggRolling24h,
            workoutsInHorizon = workouts24h
        )

        val activeHorizon = when (window) {
            TimeWindow.HOURS_24 -> horizon24h
            TimeWindow.DAYS_7 -> horizon7d
            TimeWindow.DAYS_30 -> horizon30d
        }

        // Heart rate samples for the selected window (with fallback to 30D samples if the window has none)
        val windowStartInstant = when (window) {
            TimeWindow.HOURS_24 -> minOf(todayStartInstant, rolling24hStart)
            TimeWindow.DAYS_7 -> todayLocalDate.minusDays(6).atStartOfDay(zoneId).toInstant()
            TimeWindow.DAYS_30 -> start30dInstant
        }
        val windowHrSamples = allHrSamples30d
            .filter { !it.time.isBefore(windowStartInstant) }
            .ifEmpty { allHrSamples30d.takeLast(48) }

        val recentBpmSeries = mutableListOf<Int>()
        if (windowHrSamples.isNotEmpty()) {
            val step = (windowHrSamples.size / 14).coerceAtLeast(1)
            for (i in windowHrSamples.indices step step) {
                recentBpmSeries.add(windowHrSamples[i].beatsPerMinute.toInt())
            }
        } else {
            val hrEntries = dailyEntries.filter { it.avgHeartRateBpm > 0 || it.restingHeartRateBpm > 0 }.takeLast(12)
            hrEntries.forEach { d ->
                recentBpmSeries.add(if (d.avgHeartRateBpm > 0) d.avgHeartRateBpm else d.restingHeartRateBpm)
            }
        }

        val latestHrSampleBpm = allHrSamples30d.lastOrNull()?.beatsPerMinute?.toInt()
            ?: rhrRecords.firstOrNull()?.beatsPerMinute?.toInt()
            ?: activeHorizon.avgBpm.takeIf { it > 0 }
            ?: activeHorizon.avgRhrBpm

        // Latest sleep session (most recent non-zero sleep session)
        val latestSleepEntry = dailyEntries.lastOrNull { it.sleepDurationMinutes > 0 } ?: dailyEntries.lastOrNull()

        // Today's steps (or rolling 24h steps, or most recent active step day if today has 0 steps so far)
        val todayEntry = dailyEntries.find { it.daysAgo == 0 }
        val rolling24hSteps = max(
            aggRolling24h?.get(StepsRecord.COUNT_TOTAL) ?: 0L,
            sumIntervalStepsForWindow(stepRecords, rolling24hStart, queryEndInstant)
        )
        val latestDaySteps = when {
            (todayEntry?.steps ?: 0L) > 0L -> todayEntry!!.steps
            (aggToday?.get(StepsRecord.COUNT_TOTAL) ?: 0L) > 0L -> aggToday!![StepsRecord.COUNT_TOTAL]!!
            rolling24hSteps > 0L -> rolling24hSteps
            else -> dailyEntries.lastOrNull { it.steps > 0L }?.steps ?: activeHorizon.dailyAvgSteps
        }

        val windowDays = dailyEntries.filter { it.daysAgo < window.days }.ifEmpty { dailyEntries }
        val windowWorkouts = when (window) {
            TimeWindow.HOURS_24 -> workouts24h.ifEmpty { allWorkouts30d.take(4) }
            TimeWindow.DAYS_7 -> workouts7d.ifEmpty { allWorkouts30d.take(6) }
            TimeWindow.DAYS_30 -> allWorkouts30d.take(12)
        }

        val resolvedAvgBpm = activeHorizon.avgBpm.takeIf { it > 0 }
            ?: horizon7d.avgBpm.takeIf { it > 0 }
            ?: horizon30d.avgBpm.takeIf { it > 0 }
            ?: latestHrSampleBpm
        val resolvedMinBpm = activeHorizon.minBpm.takeIf { it > 0 }
            ?: horizon7d.minBpm.takeIf { it > 0 }
            ?: horizon30d.minBpm.takeIf { it > 0 }
            ?: resolvedAvgBpm
        val resolvedMaxBpm = activeHorizon.maxBpm.takeIf { it > 0 }
            ?: horizon7d.maxBpm.takeIf { it > 0 }
            ?: horizon30d.maxBpm.takeIf { it > 0 }
            ?: resolvedAvgBpm
        val resolvedRhr = activeHorizon.avgRhrBpm.takeIf { it > 0 }
            ?: horizon7d.avgRhrBpm.takeIf { it > 0 }
            ?: horizon30d.avgRhrBpm.takeIf { it > 0 }
            ?: rhrRecords.firstOrNull()?.beatsPerMinute?.toInt()
            ?: resolvedMinBpm
        val resolvedHrv = activeHorizon.avgHrvMs.takeIf { it > 0.0 }
            ?: hrvRecords.firstOrNull()?.heartRateVariabilityMillis
            ?: horizon30d.avgHrvMs
        val resolvedSys = activeHorizon.avgSystolic.takeIf { it > 0 }
            ?: bpRecords.firstOrNull()?.systolic?.inMillimetersOfMercury?.roundToInt()
            ?: horizon30d.avgSystolic
        val resolvedDia = activeHorizon.avgDiastolic.takeIf { it > 0 }
            ?: bpRecords.firstOrNull()?.diastolic?.inMillimetersOfMercury?.roundToInt()
            ?: horizon30d.avgDiastolic
        val resolvedSpo2 = activeHorizon.avgSpO2.takeIf { it > 0.0 }
            ?: spo2Records.firstOrNull()?.percentage?.value
            ?: horizon30d.avgSpO2

        HealthSummarySnapshot(
            window = window,
            capturedAtEpochMs = System.currentTimeMillis(),
            isFromLiveHealthConnect = true,
            dataSourceDescription = "Android Health Connect Live Store (24H • 7D • 30D Synced)",
            totalRecordCount = max(totalRecords30d, dailyEntries.size),
            heartVitals = HeartVitalsMetrics(
                latestBpm = latestHrSampleBpm.takeIf { it > 0 } ?: resolvedAvgBpm,
                avgBpm = resolvedAvgBpm,
                minBpm = resolvedMinBpm,
                maxBpm = resolvedMaxBpm,
                hrvRmssdMs = resolvedHrv,
                systolicMmHg = resolvedSys,
                diastolicMmHg = resolvedDia,
                spO2Percent = resolvedSpo2,
                sampleCount = windowHrSamples.size,
                recentBpmSeries = recentBpmSeries
            ),
            sleepRecovery = SleepRecoveryMetrics(
                avgSleepDurationMinutes = activeHorizon.avgSleepMinutes.takeIf { it > 0 } ?: horizon30d.avgSleepMinutes,
                latestSleepDurationMinutes = latestSleepEntry?.sleepDurationMinutes ?: activeHorizon.avgSleepMinutes,
                minSleepDurationMinutes = activeHorizon.minSleepMinutes.takeIf { it > 0 } ?: horizon30d.minSleepMinutes,
                maxSleepDurationMinutes = activeHorizon.maxSleepMinutes.takeIf { it > 0 } ?: horizon30d.maxSleepMinutes,
                deepSleepMinutes = activeHorizon.avgDeepMinutes.takeIf { it > 0 } ?: (latestSleepEntry?.deepSleepMinutes ?: 0),
                remSleepMinutes = activeHorizon.avgRemMinutes.takeIf { it > 0 } ?: (latestSleepEntry?.remSleepMinutes ?: 0),
                lightSleepMinutes = activeHorizon.avgLightMinutes.takeIf { it > 0 } ?: (latestSleepEntry?.lightSleepMinutes ?: 0),
                awakeMinutes = activeHorizon.avgAwakeMinutes.takeIf { it > 0 } ?: (latestSleepEntry?.awakeMinutes ?: 0),
                restingHeartRateBpm = resolvedRhr,
                sleepSessionsCount = activeHorizon.sleepSessionsCount,
                recoveryScore = activeHorizon.recoveryScore.takeIf { it > 0 } ?: horizon7d.recoveryScore
            ),
            activity = ActivityWorkoutMetrics(
                totalSteps = activeHorizon.totalSteps.takeIf { it > 0L } ?: latestDaySteps,
                dailyAvgSteps = activeHorizon.dailyAvgSteps.takeIf { it > 0L }
                    ?: horizon7d.dailyAvgSteps.takeIf { it > 0L }
                    ?: horizon30d.dailyAvgSteps,
                latestDaySteps = latestDaySteps,
                minDailySteps = activeHorizon.minDailySteps,
                maxDailySteps = activeHorizon.maxDailySteps,
                daysAbove10kSteps = activeHorizon.daysAbove10kSteps,
                distanceKm = activeHorizon.totalDistanceKm.takeIf { it > 0.0 } ?: (latestDaySteps * 0.00076),
                dailyAvgDistanceKm = activeHorizon.dailyAvgDistanceKm.takeIf { it > 0.0 } ?: horizon7d.dailyAvgDistanceKm,
                activeCaloriesKcal = activeHorizon.totalActiveKcal.takeIf { it > 0 } ?: horizon7d.dailyAvgActiveKcal,
                dailyAvgActiveCaloriesKcal = activeHorizon.dailyAvgActiveKcal.takeIf { it > 0 }
                    ?: horizon7d.dailyAvgActiveKcal.takeIf { it > 0 }
                    ?: horizon30d.dailyAvgActiveKcal,
                totalCaloriesKcal = activeHorizon.totalKcal,
                dailyAvgTotalCaloriesKcal = activeHorizon.dailyAvgTotalKcal,
                workouts = windowWorkouts,
                dailyStepsSeries = windowDays.filter { it.steps > 0L }.ifEmpty { dailyEntries.filter { it.steps > 0L } }
                    .takeLast(14)
                    .map { it.steps.toInt() }
            ),
            horizon24h = horizon24h,
            horizon7d = horizon7d,
            horizon30d = horizon30d,
            allWorkouts30d = allWorkouts30d,
            dailyHistory = dailyEntries
        )
    }

    /**
     * Sums interval records proportionally across `[windowStart, windowEnd]` so records with
     * `endTime = 23:59:59` or records spanning midnight are never dropped.
     */
    private fun overlapFraction(recordStart: Instant, recordEnd: Instant, windowStart: Instant, windowEnd: Instant): Double {
        val rStart = recordStart.toEpochMilli()
        val rEnd = max(rStart, recordEnd.toEpochMilli())
        val wStart = windowStart.toEpochMilli()
        val wEnd = windowEnd.toEpochMilli()
        if (rEnd < wStart || rStart >= wEnd) return 0.0
        val totalDur = rEnd - rStart
        if (totalDur <= 0L) {
            return if (rStart in wStart until wEnd) 1.0 else 0.0
        }
        val overlapStart = max(rStart, wStart)
        val overlapEnd = min(rEnd, wEnd)
        if (overlapEnd <= overlapStart) return 0.0
        return ((overlapEnd - overlapStart).toDouble() / totalDur.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun sumIntervalStepsForWindow(records: List<StepsRecord>, windowStart: Instant, windowEnd: Instant): Long {
        if (records.isEmpty()) return 0L
        // Group by dataOrigin so if both a watch and phone write overlapping step records, we take the primary source instead of double-counting
        val byOrigin = records.groupBy { it.metadata.dataOrigin.packageName }
        return byOrigin.values.maxOfOrNull { originRecords ->
            originRecords.sumOf { rec ->
                val frac = overlapFraction(rec.startTime, rec.endTime, windowStart, windowEnd)
                if (frac > 0.0) (rec.count * frac).roundToInt().toLong() else 0L
            }
        } ?: 0L
    }

    private fun sumIntervalDistanceKmForWindow(records: List<DistanceRecord>, windowStart: Instant, windowEnd: Instant): Double {
        if (records.isEmpty()) return 0.0
        val byOrigin = records.groupBy { it.metadata.dataOrigin.packageName }
        return byOrigin.values.maxOfOrNull { originRecords ->
            originRecords.sumOf { rec ->
                val frac = overlapFraction(rec.startTime, rec.endTime, windowStart, windowEnd)
                if (frac > 0.0) rec.distance.inKilometers * frac else 0.0
            }
        } ?: 0.0
    }

    private fun sumIntervalActiveCaloriesForWindow(records: List<ActiveCaloriesBurnedRecord>, windowStart: Instant, windowEnd: Instant): Double {
        if (records.isEmpty()) return 0.0
        val byOrigin = records.groupBy { it.metadata.dataOrigin.packageName }
        return byOrigin.values.maxOfOrNull { originRecords ->
            originRecords.sumOf { rec ->
                val frac = overlapFraction(rec.startTime, rec.endTime, windowStart, windowEnd)
                if (frac > 0.0) rec.energy.inKilocalories * frac else 0.0
            }
        } ?: 0.0
    }

    private fun sumIntervalTotalCaloriesForWindow(records: List<TotalCaloriesBurnedRecord>, windowStart: Instant, windowEnd: Instant): Double {
        if (records.isEmpty()) return 0.0
        val byOrigin = records.groupBy { it.metadata.dataOrigin.packageName }
        return byOrigin.values.maxOfOrNull { originRecords ->
            originRecords.sumOf { rec ->
                val frac = overlapFraction(rec.startTime, rec.endTime, windowStart, windowEnd)
                if (frac > 0.0) rec.energy.inKilocalories * frac else 0.0
            }
        } ?: 0.0
    }

    private fun buildEnrichedWorkoutSummary(
        ex: ExerciseSessionRecord,
        todayLocalDate: LocalDate,
        zoneId: ZoneId,
        allHrSamples: List<HeartRateRecord.Sample>,
        activeCalRecords: List<ActiveCaloriesBurnedRecord>,
        totalCalRecords: List<TotalCaloriesBurnedRecord>,
        distRecords: List<DistanceRecord>,
        stepRecords: List<StepsRecord>
    ): WorkoutSummary {
        val durMin = Duration.between(ex.startTime, ex.endTime).toMinutes().toInt().coerceAtLeast(1)
        val typeLabel = exerciseTypeToLabel(ex.exerciseType)
        val cleanTitle = ex.title?.trim()?.takeIf { it.isNotEmpty() } ?: typeLabel

        val sessionHrSamples = allHrSamples.filter {
            !it.time.isBefore(ex.startTime.minusSeconds(30)) && !it.time.isAfter(ex.endTime.plusSeconds(30))
        }
        val avgHr = if (sessionHrSamples.isNotEmpty()) {
            sessionHrSamples.map { it.beatsPerMinute }.average().roundToInt()
        } else 0
        val maxHr = if (sessionHrSamples.isNotEmpty()) {
            sessionHrSamples.maxOf { it.beatsPerMinute }.toInt()
        } else 0

        val sessionActiveKcal = sumIntervalActiveCaloriesForWindow(activeCalRecords, ex.startTime, ex.endTime).roundToInt()
        val sessionTotalKcal = sumIntervalTotalCaloriesForWindow(totalCalRecords, ex.startTime, ex.endTime).roundToInt()
        val calories = when {
            sessionActiveKcal > 0 -> sessionActiveKcal
            sessionTotalKcal > 0 -> sessionTotalKcal
            else -> (durMin * 7.5).roundToInt()
        }

        val sessionDistKm = sumIntervalDistanceKmForWindow(distRecords, ex.startTime, ex.endTime)
        val sessionSteps = sumIntervalStepsForWindow(stepRecords, ex.startTime, ex.endTime)

        val workoutLocalDate = ex.endTime.atZone(zoneId).toLocalDate()
        val dateFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US).withZone(zoneId)
        val dowFmt = DateTimeFormatter.ofPattern("EEE", Locale.US).withZone(zoneId)
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US).withZone(zoneId)

        val dayPrefix = when (workoutLocalDate) {
            todayLocalDate -> "Today (${dateFmt.format(ex.startTime)})"
            todayLocalDate.minusDays(1) -> "Yesterday (${dateFmt.format(ex.startTime)})"
            else -> "${dowFmt.format(ex.startTime)} ${dateFmt.format(ex.startTime)}"
        }
        val startTimeLabel = "$dayPrefix, ${timeFmt.format(ex.startTime)}–${timeFmt.format(ex.endTime)}"

        return WorkoutSummary(
            title = cleanTitle,
            exerciseTypeLabel = typeLabel,
            durationMinutes = durMin,
            caloriesKcal = calories,
            startTimeLabel = startTimeLabel,
            distanceKm = sessionDistKm,
            steps = sessionSteps,
            avgHeartRateBpm = avgHr,
            maxHeartRateBpm = maxHr,
            startTimeEpochMs = ex.startTime.toEpochMilli(),
            endTimeEpochMs = ex.endTime.toEpochMilli()
        )
    }

    private data class StageBreakdown(val deep: Int, val rem: Int, val light: Int, val awake: Int)

    private fun extractStageMinutes(session: SleepSessionRecord, totalMin: Int): StageBreakdown {
        var deep = 0
        var rem = 0
        var light = 0
        var awake = 0
        session.stages.forEach { stage ->
            val mins = Duration.between(stage.startTime, stage.endTime).toMinutes().toInt().coerceAtLeast(0)
            when (stage.stage) {
                SleepSessionRecord.STAGE_TYPE_DEEP -> deep += mins
                SleepSessionRecord.STAGE_TYPE_REM -> rem += mins
                SleepSessionRecord.STAGE_TYPE_LIGHT,
                SleepSessionRecord.STAGE_TYPE_SLEEPING -> light += mins
                SleepSessionRecord.STAGE_TYPE_AWAKE,
                SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> awake += mins
            }
        }
        if (deep == 0 && rem == 0 && light == 0 && totalMin > 0) {
            deep = (totalMin * 0.21).roundToInt()
            rem = (totalMin * 0.23).roundToInt()
            light = (totalMin * 0.51).roundToInt()
            awake = (totalMin - deep - rem - light).coerceAtLeast(0)
        }
        return StageBreakdown(deep, rem, light, awake)
    }

    private fun computeRecoveryScore(sleepMin: Int, hrvMs: Double, rhrBpm: Int): Int {
        val effectiveSleep = if (sleepMin > 0) sleepMin else 430
        val effectiveHrv = if (hrvMs > 0.0) hrvMs else 58.0
        val sleepScorePart = ((effectiveSleep.toDouble() / 460.0).coerceIn(0.0, 1.0) * 45).roundToInt()
        val hrvScorePart = ((effectiveHrv / 70.0).coerceIn(0.0, 1.0) * 35).roundToInt()
        val rhrScorePart = when {
            rhrBpm in 42..58 -> 20
            rhrBpm in 59..65 -> 16
            rhrBpm > 65 -> 11
            else -> 15
        }
        return (sleepScorePart + hrvScorePart + rhrScorePart).coerceIn(0, 100)
    }

    private fun buildHorizonSummaryFromDays(
        window: TimeWindow,
        days: List<DailyBiometricEntry>,
        allDaysFallback: List<DailyBiometricEntry> = days,
        aggResult: AggregationResult? = null,
        secondaryAggResult: AggregationResult? = null,
        workoutsInHorizon: List<WorkoutSummary> = emptyList()
    ): PeriodHorizonSummary {
        if (days.isEmpty() && allDaysFallback.isEmpty()) return PeriodHorizonSummary(window)

        val sleepDays = days.filter { it.sleepDurationMinutes > 0 }
            .ifEmpty { allDaysFallback.filter { it.sleepDurationMinutes > 0 }.takeLast(window.days) }
        val avgSleep = if (sleepDays.isNotEmpty()) sleepDays.map { it.sleepDurationMinutes }.average().roundToInt() else 0
        val minSleep = if (sleepDays.isNotEmpty()) sleepDays.minOf { it.sleepDurationMinutes } else 0
        val maxSleep = if (sleepDays.isNotEmpty()) sleepDays.maxOf { it.sleepDurationMinutes } else 0
        val avgDeep = if (sleepDays.isNotEmpty()) sleepDays.map { it.deepSleepMinutes }.average().roundToInt() else 0
        val avgRem = if (sleepDays.isNotEmpty()) sleepDays.map { it.remSleepMinutes }.average().roundToInt() else 0
        val avgLight = if (sleepDays.isNotEmpty()) sleepDays.map { it.lightSleepMinutes }.average().roundToInt() else 0
        val avgAwake = if (sleepDays.isNotEmpty()) sleepDays.map { it.awakeMinutes }.average().roundToInt() else 0

        val rhrDays = days.filter { it.restingHeartRateBpm > 0 }
            .ifEmpty { allDaysFallback.filter { it.restingHeartRateBpm > 0 }.takeLast(window.days) }
        val aggRhr = aggResult?.get(RestingHeartRateRecord.BPM_AVG)?.toInt()
            ?: secondaryAggResult?.get(RestingHeartRateRecord.BPM_AVG)?.toInt()
            ?: 0
        val avgRhr = when {
            rhrDays.isNotEmpty() -> rhrDays.map { it.restingHeartRateBpm }.average().roundToInt()
            aggRhr > 0 -> aggRhr
            else -> 0
        }
        val minRhr = if (rhrDays.isNotEmpty()) rhrDays.minOf { it.restingHeartRateBpm } else avgRhr
        val maxRhr = if (rhrDays.isNotEmpty()) rhrDays.maxOf { it.restingHeartRateBpm } else avgRhr

        val hrDays = days.filter { it.avgHeartRateBpm > 0 }
            .ifEmpty { allDaysFallback.filter { it.avgHeartRateBpm > 0 }.takeLast(window.days) }
        val aggAvgHr = aggResult?.get(HeartRateRecord.BPM_AVG)?.toInt()
            ?: secondaryAggResult?.get(HeartRateRecord.BPM_AVG)?.toInt()
            ?: 0
        val aggMinHr = aggResult?.get(HeartRateRecord.BPM_MIN)?.toInt()
            ?: secondaryAggResult?.get(HeartRateRecord.BPM_MIN)?.toInt()
            ?: 0
        val aggMaxHr = aggResult?.get(HeartRateRecord.BPM_MAX)?.toInt()
            ?: secondaryAggResult?.get(HeartRateRecord.BPM_MAX)?.toInt()
            ?: 0

        val avgHr = when {
            aggAvgHr > 0 -> aggAvgHr
            hrDays.isNotEmpty() -> hrDays.map { it.avgHeartRateBpm }.average().roundToInt()
            avgRhr > 0 -> avgRhr
            else -> 0
        }
        val minHr = when {
            aggMinHr > 0 -> aggMinHr
            hrDays.any { it.minHeartRateBpm > 0 } -> hrDays.filter { it.minHeartRateBpm > 0 }.minOf { it.minHeartRateBpm }
            minRhr > 0 -> max(40, minRhr - 3)
            avgHr > 0 -> max(40, avgHr - 8)
            else -> 0
        }
        val maxHr = when {
            aggMaxHr > 0 -> aggMaxHr
            hrDays.isNotEmpty() -> hrDays.maxOf { it.maxHeartRateBpm }
            maxRhr > 0 -> maxRhr
            avgHr > 0 -> avgHr
            else -> 0
        }

        val hrvDays = days.filter { it.hrvRmssdMs > 0.0 }
            .ifEmpty { allDaysFallback.filter { it.hrvRmssdMs > 0.0 }.takeLast(window.days) }
        val avgHrv = if (hrvDays.isNotEmpty()) hrvDays.map { it.hrvRmssdMs }.average() else 0.0
        val minHrv = if (hrvDays.isNotEmpty()) hrvDays.minOf { it.hrvRmssdMs } else 0.0
        val maxHrv = if (hrvDays.isNotEmpty()) hrvDays.maxOf { it.hrvRmssdMs } else 0.0

        val bpDays = days.filter { it.systolicMmHg > 0 }
            .ifEmpty { allDaysFallback.filter { it.systolicMmHg > 0 }.takeLast(window.days) }
        val avgSys = if (bpDays.isNotEmpty()) bpDays.map { it.systolicMmHg }.average().roundToInt() else 0
        val avgDia = if (bpDays.isNotEmpty()) bpDays.map { it.diastolicMmHg }.average().roundToInt() else 0

        val spo2Days = days.filter { it.spO2Percent > 0.0 }
            .ifEmpty { allDaysFallback.filter { it.spO2Percent > 0.0 }.takeLast(window.days) }
        val avgSpo2 = if (spo2Days.isNotEmpty()) spo2Days.map { it.spO2Percent }.average() else 0.0

        val stepDays = days.filter { it.steps > 0L }
            .ifEmpty { allDaysFallback.filter { it.steps > 0L }.takeLast(window.days) }
        val rawTotalSteps = stepDays.sumOf { it.steps }
        val aggTotalSteps = aggResult?.get(StepsRecord.COUNT_TOTAL)?.takeIf { it > 0L }
            ?: secondaryAggResult?.get(StepsRecord.COUNT_TOTAL)?.takeIf { it > 0L }
            ?: 0L
        val totalSteps = if (rawTotalSteps > 0L) rawTotalSteps else aggTotalSteps
        val activeStepDayCount = stepDays.size.coerceAtLeast(1)
        val dailyAvgSteps = if (totalSteps > 0L) totalSteps / activeStepDayCount else 0L
        val minSteps = if (stepDays.isNotEmpty()) stepDays.minOf { it.steps } else totalSteps
        val maxSteps = if (stepDays.isNotEmpty()) stepDays.maxOf { it.steps } else totalSteps
        val daysAbove10k = stepDays.count { it.steps >= 10_000L }

        val distDays = days.filter { it.distanceKm > 0.0 }.ifEmpty { stepDays }
        val rawTotalDist = distDays.sumOf { it.distanceKm }
        val aggTotalDist = aggResult?.get(DistanceRecord.DISTANCE_TOTAL)?.inKilometers?.takeIf { it > 0.0 }
            ?: secondaryAggResult?.get(DistanceRecord.DISTANCE_TOTAL)?.inKilometers?.takeIf { it > 0.0 }
            ?: 0.0
        val totalDist = when {
            rawTotalDist > 0.0 -> rawTotalDist
            aggTotalDist > 0.0 -> aggTotalDist
            totalSteps > 0L -> totalSteps * 0.00076
            else -> 0.0
        }
        val avgDist = if (distDays.isNotEmpty()) totalDist / distDays.size else totalDist

        val activeKcalDays = days.filter { it.activeCaloriesKcal > 0 }.ifEmpty { stepDays }
        val rawTotalActiveKcal = activeKcalDays.sumOf { it.activeCaloriesKcal }
        val aggTotalActiveKcal = aggResult?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories?.roundToInt()?.takeIf { it > 0 }
            ?: secondaryAggResult?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories?.roundToInt()?.takeIf { it > 0 }
            ?: 0
        val totalActiveKcal = if (rawTotalActiveKcal > 0) rawTotalActiveKcal else aggTotalActiveKcal
        val avgActiveKcal = if (activeKcalDays.isNotEmpty()) {
            (totalActiveKcal.toDouble() / activeKcalDays.size).roundToInt()
        } else totalActiveKcal

        val totalKcalDays = days.filter { it.totalCaloriesKcal > 0 }.ifEmpty { activeKcalDays }
        val rawTotalKcal = totalKcalDays.sumOf { it.totalCaloriesKcal }
        val aggTotalKcal = aggResult?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories?.roundToInt()?.takeIf { it > 0 }
            ?: secondaryAggResult?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories?.roundToInt()?.takeIf { it > 0 }
            ?: 0
        val totalKcal = if (rawTotalKcal > 0) rawTotalKcal else aggTotalKcal
        val avgTotalKcal = if (totalKcalDays.isNotEmpty()) {
            (totalKcal.toDouble() / totalKcalDays.size).roundToInt()
        } else totalKcal

        val workoutCount = if (workoutsInHorizon.isNotEmpty()) {
            workoutsInHorizon.size
        } else {
            days.sumOf { if (it.workoutCount > 0) it.workoutCount else if (it.workoutMinutes > 0) 1 else 0 }
        }
        val totalWorkoutMin = if (workoutsInHorizon.isNotEmpty()) {
            workoutsInHorizon.sumOf { it.durationMinutes }
        } else {
            days.sumOf { it.workoutMinutes }
        }

        val recScore = computeRecoveryScore(avgSleep, avgHrv, avgRhr.takeIf { it > 0 } ?: avgHr)

        return PeriodHorizonSummary(
            window = window,
            avgSleepMinutes = avgSleep,
            minSleepMinutes = minSleep,
            maxSleepMinutes = maxSleep,
            avgDeepMinutes = avgDeep,
            avgRemMinutes = avgRem,
            avgLightMinutes = avgLight,
            avgAwakeMinutes = avgAwake,
            sleepSessionsCount = sleepDays.size,
            avgRhrBpm = avgRhr.takeIf { it > 0 } ?: minHr,
            minRhrBpm = minRhr.takeIf { it > 0 } ?: minHr,
            maxRhrBpm = maxRhr.takeIf { it > 0 } ?: maxHr,
            avgBpm = avgHr,
            minBpm = minHr,
            maxBpm = maxHr,
            avgHrvMs = avgHrv,
            minHrvMs = minHrv,
            maxHrvMs = maxHrv,
            avgSystolic = avgSys,
            avgDiastolic = avgDia,
            avgSpO2 = avgSpo2,
            totalSteps = totalSteps,
            dailyAvgSteps = dailyAvgSteps,
            minDailySteps = minSteps,
            maxDailySteps = maxSteps,
            daysAbove10kSteps = daysAbove10k,
            totalDistanceKm = totalDist,
            dailyAvgDistanceKm = avgDist,
            totalActiveKcal = totalActiveKcal,
            dailyAvgActiveKcal = avgActiveKcal,
            totalKcal = totalKcal,
            dailyAvgTotalKcal = avgTotalKcal,
            workoutCount = workoutCount,
            totalWorkoutMinutes = totalWorkoutMin,
            recoveryScore = recScore
        )
    }

    /**
     * Writes 30 full days of realistic biometric records directly into Android Health Connect
     * so 24-hour, 7-day (last week), and 30-day (last month) queries, trends, correlations,
     * and outlier scans work against real Health Connect storage.
     */
    suspend fun writeSampleRecordsToHealthConnect(): Result<Int> = withContext(Dispatchers.IO) {
        val client = getClientOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Health Connect SDK is not available on this device."))

        try {
            val zoneId = ZoneId.systemDefault()
            val now = Instant.now()
            val todayLocalDate = LocalDate.now(zoneId)
            val zoneOffset: ZoneOffset = zoneId.rules.getOffset(now)
            val recordsToInsert = mutableListOf<Record>()
            val metadata = Metadata.manualEntry()
            val generated30d = generateRealistic30DayHistory(now)

            generated30d.forEach { day ->
                val localDate = todayLocalDate.minusDays(day.daysAgo.toLong())
                val dayStart = localDate.atStartOfDay(zoneId).toInstant().plus(8, ChronoUnit.HOURS)
                val dayEnd = dayStart.plus(10, ChronoUnit.HOURS).coerceAtMost(now.minusSeconds(60))
                val effectiveStart = if (dayStart.isBefore(dayEnd)) dayStart else dayEnd.minus(4, ChronoUnit.HOURS)

                recordsToInsert.add(
                    StepsRecord(
                        count = day.steps,
                        startTime = effectiveStart,
                        endTime = dayEnd,
                        startZoneOffset = zoneOffset,
                        endZoneOffset = zoneOffset,
                        metadata = metadata
                    )
                )

                recordsToInsert.add(
                    DistanceRecord(
                        distance = Length.kilometers(day.distanceKm),
                        startTime = effectiveStart,
                        endTime = dayEnd,
                        startZoneOffset = zoneOffset,
                        endZoneOffset = zoneOffset,
                        metadata = metadata
                    )
                )

                recordsToInsert.add(
                    ActiveCaloriesBurnedRecord(
                        energy = Energy.kilocalories(day.activeCaloriesKcal.toDouble()),
                        startTime = effectiveStart,
                        endTime = dayEnd,
                        startZoneOffset = zoneOffset,
                        endZoneOffset = zoneOffset,
                        metadata = metadata
                    )
                )

                recordsToInsert.add(
                    TotalCaloriesBurnedRecord(
                        energy = Energy.kilocalories(day.totalCaloriesKcal.toDouble()),
                        startTime = effectiveStart,
                        endTime = dayEnd,
                        startZoneOffset = zoneOffset,
                        endZoneOffset = zoneOffset,
                        metadata = metadata
                    )
                )

                val sampleStepSec = max(60L, Duration.between(effectiveStart, dayEnd).seconds / 5L)
                val hrSamples = listOf(
                    HeartRateRecord.Sample(effectiveStart.plusSeconds(sampleStepSec), (day.restingHeartRateBpm + 4).toLong()),
                    HeartRateRecord.Sample(effectiveStart.plusSeconds(sampleStepSec * 2), day.avgHeartRateBpm.toLong()),
                    HeartRateRecord.Sample(effectiveStart.plusSeconds(sampleStepSec * 3), day.maxHeartRateBpm.toLong()),
                    HeartRateRecord.Sample(effectiveStart.plusSeconds(sampleStepSec * 4), (day.avgHeartRateBpm - 2).toLong())
                )
                recordsToInsert.add(
                    HeartRateRecord(
                        startTime = effectiveStart,
                        endTime = dayEnd,
                        startZoneOffset = zoneOffset,
                        endZoneOffset = zoneOffset,
                        samples = hrSamples,
                        metadata = metadata
                    )
                )

                recordsToInsert.add(
                    RestingHeartRateRecord(
                        time = effectiveStart.plus(10, ChronoUnit.MINUTES),
                        zoneOffset = zoneOffset,
                        beatsPerMinute = day.restingHeartRateBpm.toLong(),
                        metadata = metadata
                    )
                )

                recordsToInsert.add(
                    HeartRateVariabilityRmssdRecord(
                        time = effectiveStart.plus(15, ChronoUnit.MINUTES),
                        zoneOffset = zoneOffset,
                        heartRateVariabilityMillis = day.hrvRmssdMs,
                        metadata = metadata
                    )
                )

                recordsToInsert.add(
                    OxygenSaturationRecord(
                        time = effectiveStart.plus(20, ChronoUnit.MINUTES),
                        zoneOffset = zoneOffset,
                        percentage = Percentage(day.spO2Percent),
                        metadata = metadata
                    )
                )

                recordsToInsert.add(
                    BloodPressureRecord(
                        time = effectiveStart.plus(25, ChronoUnit.MINUTES),
                        zoneOffset = zoneOffset,
                        systolic = Pressure.millimetersOfMercury(day.systolicMmHg.toDouble()),
                        diastolic = Pressure.millimetersOfMercury(day.diastolicMmHg.toDouble()),
                        metadata = metadata
                    )
                )

                val sleepEnd = localDate.atStartOfDay(zoneId).toInstant().plus(7, ChronoUnit.HOURS)
                    .coerceAtMost(now.minus(30, ChronoUnit.MINUTES))
                val sleepStart = sleepEnd.minus(day.sleepDurationMinutes.toLong(), ChronoUnit.MINUTES)
                val t1 = sleepStart.plus(day.deepSleepMinutes.toLong(), ChronoUnit.MINUTES)
                val t2 = t1.plus(day.remSleepMinutes.toLong(), ChronoUnit.MINUTES)
                val t3 = t2.plus(day.lightSleepMinutes.toLong(), ChronoUnit.MINUTES)

                recordsToInsert.add(
                    SleepSessionRecord(
                        startTime = sleepStart,
                        endTime = sleepEnd,
                        startZoneOffset = zoneOffset,
                        endZoneOffset = zoneOffset,
                        title = "Overnight Recovery Sleep",
                        stages = listOf(
                            SleepSessionRecord.Stage(sleepStart, t1, SleepSessionRecord.STAGE_TYPE_DEEP),
                            SleepSessionRecord.Stage(t1, t2, SleepSessionRecord.STAGE_TYPE_REM),
                            SleepSessionRecord.Stage(t2, t3, SleepSessionRecord.STAGE_TYPE_LIGHT),
                            SleepSessionRecord.Stage(t3, sleepEnd, SleepSessionRecord.STAGE_TYPE_AWAKE)
                        ),
                        metadata = metadata
                    )
                )

                if (day.workoutMinutes > 0 && day.workoutTitle != null) {
                    val workoutEnd = effectiveStart.plus(2, ChronoUnit.HOURS).coerceAtMost(now.minusSeconds(120))
                    val workoutStart = workoutEnd.minus(day.workoutMinutes.toLong(), ChronoUnit.MINUTES)
                    recordsToInsert.add(
                        ExerciseSessionRecord(
                            startTime = workoutStart,
                            endTime = workoutEnd,
                            startZoneOffset = zoneOffset,
                            endZoneOffset = zoneOffset,
                            exerciseType = if (day.workoutTitle.contains("Run", ignoreCase = true)) {
                                ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
                            } else {
                                ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
                            },
                            title = day.workoutTitle,
                            metadata = metadata
                        )
                    )
                }
            }

            client.insertRecords(recordsToInsert)
            Result.success(recordsToInsert.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateRealistic30DayHistory(now: Instant = Instant.now()): List<DailyBiometricEntry> {
        val zoneId = ZoneId.systemDefault()
        val todayLocalDate = LocalDate.now(zoneId)
        val dateFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        val dowFmt = DateTimeFormatter.ofPattern("EEE", Locale.US)

        val entries = mutableListOf<DailyBiometricEntry>()
        for (daysAgo in 29 downTo 0) {
            val localDate = todayLocalDate.minusDays(daysAgo.toLong())
            val isRecentWeek = daysAgo < 7
            val cycle = daysAgo % 7

            val baseSleep = if (isRecentWeek) {
                when (cycle) {
                    0 -> 468 // Last night: 7h 48m
                    1 -> 454 // 7h 34m
                    2 -> 438 // 7h 18m
                    3 -> 482 // 8h 02m
                    4 -> 422 // 7h 02m
                    5 -> 460 // 7h 40m
                    else -> 446 // 7h 26m
                }
            } else {
                when (cycle) {
                    0 -> 442
                    1 -> 428
                    2 -> 384
                    3 -> 464
                    4 -> 412
                    5 -> 448
                    else -> 430
                } - (if (daysAgo in 18..22) 12 else 0)
            }

            val steps = if (isRecentWeek) {
                when (cycle) {
                    0 -> 10420L
                    1 -> 9580L
                    2 -> 6840L
                    3 -> 11840L
                    4 -> 8190L
                    5 -> 10650L
                    else -> 9120L
                }
            } else {
                when (cycle) {
                    0 -> 9640L
                    1 -> 8820L
                    2 -> 6210L
                    3 -> 11250L
                    4 -> 7540L
                    5 -> 10180L
                    else -> 8490L
                } - (if (daysAgo in 15..24) 450L else 0L)
            }

            val isHighActivity = steps >= 9500L
            val deepMin = ((baseSleep * 0.215).roundToInt() + (if (isHighActivity) 12 else -4)).coerceAtLeast(55)
            val remMin = (baseSleep * 0.235).roundToInt()
            val awakeMin = if (baseSleep < 400) 26 else 18
            val lightMin = (baseSleep - deepMin - remMin - awakeMin).coerceAtLeast(150)

            val hrvBase = if (isRecentWeek) 66.5 else 61.8
            val sleepBonusHrv = (baseSleep - 430) * 0.11
            val hrvMs = (hrvBase + sleepBonusHrv + (if (isHighActivity) 1.8 else -1.2)).coerceIn(46.0, 82.0)

            val rhrBase = if (isRecentWeek) 56 else 58
            val rhrBpm = (rhrBase - ((baseSleep - 430) / 28) + (if (baseSleep < 395) 3 else 0)).coerceIn(51, 65)

            val workoutPair: Pair<String?, Int> = when {
                daysAgo == 0 -> "Morning Zone-2 Trail Run" to 44
                daysAgo == 2 -> "Kettlebell & Core Mobility" to 38
                daysAgo == 4 -> "Tempo Interval Run" to 40
                daysAgo == 6 -> "Full-Body Functional Strength" to 46
                daysAgo % 3 == 0 -> "Aerobic Conditioning & Mobility" to 36
                daysAgo % 5 == 0 -> "Progressive Strength Session" to 42
                else -> null to 0
            }

            val activeKcal = ((steps * 0.046).roundToInt() + workoutPair.second * 6).coerceAtLeast(240)
            val totalKcal = activeKcal + 1710
            val distKm = (steps * 0.00076)
            val avgHr = (rhrBpm + 11 + (workoutPair.second / 8)).coerceIn(62, 79)
            val minHr = (rhrBpm - 3).coerceAtLeast(48)
            val maxHr = if (workoutPair.second > 0) 152 + (daysAgo % 14) else 118 + (daysAgo % 9)
            val sys = 116 + (daysAgo % 5) - (if (isRecentWeek) 1 else 0)
            val dia = 74 + (daysAgo % 4)
            val spo2 = 97.6 + ((daysAgo % 3) * 0.4)
            val recScore = computeRecoveryScore(baseSleep, hrvMs, rhrBpm)

            entries.add(
                DailyBiometricEntry(
                    daysAgo = daysAgo,
                    dateLabel = dateFmt.format(localDate),
                    dayOfWeekShort = dowFmt.format(localDate),
                    sleepDurationMinutes = baseSleep,
                    deepSleepMinutes = deepMin,
                    remSleepMinutes = remMin,
                    lightSleepMinutes = lightMin,
                    awakeMinutes = awakeMin,
                    restingHeartRateBpm = rhrBpm,
                    avgHeartRateBpm = avgHr,
                    minHeartRateBpm = minHr,
                    maxHeartRateBpm = maxHr,
                    hrvRmssdMs = hrvMs,
                    systolicMmHg = sys,
                    diastolicMmHg = dia,
                    spO2Percent = spo2,
                    steps = steps,
                    distanceKm = distKm,
                    activeCaloriesKcal = activeKcal,
                    totalCaloriesKcal = totalKcal,
                    workoutTitle = workoutPair.first,
                    workoutMinutes = workoutPair.second,
                    workoutCount = if (workoutPair.second > 0) 1 else 0,
                    recoveryScore = recScore
                )
            )
        }
        return entries
    }

    /**
     * Generates a complete Multi-Horizon (24H, 7D, 30D) Preview Biometric Snapshot with 30 days
     * of daily history so all weekly/monthly comparisons and calculation tools work out-of-the-box.
     */
    fun createPreviewSnapshot(window: TimeWindow): HealthSummarySnapshot {
        val nowMs = System.currentTimeMillis()
        val dailyEntries = generateRealistic30DayHistory()

        val allWorkouts30d = dailyEntries
            .filter { it.workoutTitle != null && it.workoutMinutes > 0 }
            .sortedBy { it.daysAgo } // daysAgo = 0 is newest (Today)!
            .map { d ->
                val isRun = d.workoutTitle?.contains("Run", ignoreCase = true) == true
                val endEpoch = nowMs - d.daysAgo * 86_400_000L
                val startEpoch = endEpoch - d.workoutMinutes * 60_000L
                WorkoutSummary(
                    title = d.workoutTitle ?: "Workout",
                    exerciseTypeLabel = if (isRun) "Running" else "Strength Training",
                    durationMinutes = d.workoutMinutes,
                    caloriesKcal = (d.workoutMinutes * 7.8).roundToInt(),
                    startTimeLabel = when (d.daysAgo) {
                        0 -> "Today (${d.dateLabel}), 07:30–08:14"
                        1 -> "Yesterday (${d.dateLabel}), 17:30–18:10"
                        else -> "${d.dayOfWeekShort} ${d.dateLabel}, 18:00"
                    },
                    distanceKm = if (isRun) (d.workoutMinutes * 0.145) else 0.0,
                    steps = if (isRun) (d.workoutMinutes * 135L) else (d.workoutMinutes * 38L),
                    avgHeartRateBpm = if (isRun) 146 else 124,
                    maxHeartRateBpm = if (isRun) 168 else 148,
                    startTimeEpochMs = startEpoch,
                    endTimeEpochMs = endEpoch
                )
            }

        val horizon24h = buildHorizonSummaryFromDays(
            window = TimeWindow.HOURS_24,
            days = dailyEntries.filter { it.daysAgo == 0 },
            allDaysFallback = dailyEntries,
            workoutsInHorizon = allWorkouts30d.filter { (nowMs - it.endTimeEpochMs) <= 86_400_000L }
        )
        val horizon7d = buildHorizonSummaryFromDays(
            window = TimeWindow.DAYS_7,
            days = dailyEntries.filter { it.daysAgo < 7 },
            allDaysFallback = dailyEntries,
            workoutsInHorizon = allWorkouts30d.filter { (nowMs - it.endTimeEpochMs) <= 7L * 86_400_000L }
        )
        val horizon30d = buildHorizonSummaryFromDays(
            window = TimeWindow.DAYS_30,
            days = dailyEntries,
            allDaysFallback = dailyEntries,
            workoutsInHorizon = allWorkouts30d
        )

        val activeHorizon = when (window) {
            TimeWindow.HOURS_24 -> horizon24h
            TimeWindow.DAYS_7 -> horizon7d
            TimeWindow.DAYS_30 -> horizon30d
        }
        val latest = dailyEntries.last()
        val windowDays = dailyEntries.filter { it.daysAgo < window.days }
        val windowWorkouts = allWorkouts30d.filter { (nowMs - it.endTimeEpochMs) <= window.days * 86_400_000L }
            .ifEmpty { allWorkouts30d.take(4) }

        return HealthSummarySnapshot(
            window = window,
            capturedAtEpochMs = nowMs,
            isFromLiveHealthConnect = false,
            dataSourceDescription = "Multi-Horizon Biometric Preview (24H • 7D • 30D Loaded)",
            totalRecordCount = 11 * window.days,
            heartVitals = HeartVitalsMetrics(
                latestBpm = 64,
                avgBpm = activeHorizon.avgBpm,
                minBpm = activeHorizon.minBpm,
                maxBpm = activeHorizon.maxBpm,
                hrvRmssdMs = activeHorizon.avgHrvMs,
                systolicMmHg = activeHorizon.avgSystolic,
                diastolicMmHg = activeHorizon.avgDiastolic,
                spO2Percent = activeHorizon.avgSpO2,
                sampleCount = 48 * window.days,
                recentBpmSeries = listOf(62, 59, 64, 72, 118, 146, 98, 74, 67, 63, 61, 64)
            ),
            sleepRecovery = SleepRecoveryMetrics(
                avgSleepDurationMinutes = activeHorizon.avgSleepMinutes,
                latestSleepDurationMinutes = latest.sleepDurationMinutes,
                minSleepDurationMinutes = activeHorizon.minSleepMinutes,
                maxSleepDurationMinutes = activeHorizon.maxSleepMinutes,
                deepSleepMinutes = activeHorizon.avgDeepMinutes,
                remSleepMinutes = activeHorizon.avgRemMinutes,
                lightSleepMinutes = activeHorizon.avgLightMinutes,
                awakeMinutes = activeHorizon.avgAwakeMinutes,
                restingHeartRateBpm = activeHorizon.avgRhrBpm,
                sleepSessionsCount = activeHorizon.sleepSessionsCount,
                recoveryScore = activeHorizon.recoveryScore
            ),
            activity = ActivityWorkoutMetrics(
                totalSteps = activeHorizon.totalSteps,
                dailyAvgSteps = activeHorizon.dailyAvgSteps,
                latestDaySteps = latest.steps,
                minDailySteps = activeHorizon.minDailySteps,
                maxDailySteps = activeHorizon.maxDailySteps,
                daysAbove10kSteps = activeHorizon.daysAbove10kSteps,
                distanceKm = activeHorizon.totalDistanceKm,
                dailyAvgDistanceKm = activeHorizon.dailyAvgDistanceKm,
                activeCaloriesKcal = activeHorizon.totalActiveKcal,
                dailyAvgActiveCaloriesKcal = activeHorizon.dailyAvgActiveKcal,
                totalCaloriesKcal = activeHorizon.totalKcal,
                dailyAvgTotalCaloriesKcal = activeHorizon.dailyAvgTotalKcal,
                workouts = windowWorkouts.take(6),
                dailyStepsSeries = windowDays.takeLast(14).map { it.steps.toInt() }
            ),
            horizon24h = horizon24h,
            horizon7d = horizon7d,
            horizon30d = horizon30d,
            allWorkouts30d = allWorkouts30d,
            dailyHistory = dailyEntries
        )
    }

    fun buildContextualPromptChips(snapshot: HealthSummarySnapshot): List<ContextualPromptChip> {
        val h24 = snapshot.horizon24h
        val h7 = snapshot.horizon7d
        val h30 = snapshot.horizon30d
        val latestWorkout = snapshot.allWorkouts30d.firstOrNull() ?: snapshot.activity.workouts.firstOrNull()

        return listOf(
            ContextualPromptChip(
                id = "chip_sleep_7d_30d",
                title = "7D vs 30D Sleep & Stages",
                metricBadge = "7D ${h7.formattedAvgSleep} • 30D ${h30.formattedAvgSleep}",
                fullPrompt = "What was my average sleep time in the last week compared to the last 30 days, and how did my Deep and REM sleep compare?",
                category = InsightCategory.SLEEP
            ),
            ContextualPromptChip(
                id = "chip_last_workout",
                title = "Last Workout & Activity",
                metricBadge = latestWorkout?.let { "${it.title} (${it.durationMinutes}m)" }
                    ?: "7D ${String.format(Locale.US, "%,d", h7.dailyAvgSteps)} steps/d",
                fullPrompt = "Tell me about my most recent workout and how my steps and calories this week compare to my 30-day average.",
                category = InsightCategory.ACTIVITY
            ),
            ContextualPromptChip(
                id = "chip_heart_hrv_baseline",
                title = "Heart Rate, RHR & HRV",
                metricBadge = "HR ${snapshot.heartVitals.avgBpm}bpm • RHR ${h7.avgRhrBpm}bpm",
                fullPrompt = "How are my Heart Rate, Resting Heart Rate, and HRV trending over the last 24 hours, 7 days, and 30 days?",
                category = InsightCategory.HEART
            ),
            ContextualPromptChip(
                id = "chip_rest_days_recovery",
                title = "Rest Days & Recovery",
                metricBadge = "Readiness ${h24.recoveryScore}/100",
                fullPrompt = "What were my rest days on average in the last 30 days compared to my workout days, and how is my daily readiness?",
                category = InsightCategory.HOLISTIC
            )
        )
    }

    private fun exerciseTypeToLabel(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Treadmill Running"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hiking"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Cycling"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Indoor Cycling"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength Training"
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Weightlifting"
        ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "Calisthenics"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Pilates"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Pool Swimming"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Open Water Swimming"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING -> "Rowing"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Rowing Machine"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "Stair Climbing"
        ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "Stretching"
        ExerciseSessionRecord.EXERCISE_TYPE_EXERCISE_CLASS -> "Fitness Class"
        ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP -> "Boot Camp"
        ExerciseSessionRecord.EXERCISE_TYPE_BOXING -> "Boxing"
        ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS -> "Martial Arts"
        ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "Dancing"
        ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "Tennis"
        ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON -> "Badminton"
        ExerciseSessionRecord.EXERCISE_TYPE_RACQUETBALL -> "Racquetball"
        ExerciseSessionRecord.EXERCISE_TYPE_SQUASH -> "Squash"
        ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS -> "Table Tennis"
        ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> "Basketball"
        ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "Soccer"
        ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING -> "Rock Climbing"
        ExerciseSessionRecord.EXERCISE_TYPE_SKIING -> "Skiing"
        ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING -> "Snowboarding"
        else -> {
            ExerciseSessionRecord.EXERCISE_TYPE_INT_TO_STRING_MAP[type]
                ?.replace("_", " ")
                ?.lowercase(Locale.US)
                ?.split(" ")
                ?.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
                ?: "Workout"
        }
    }
}
