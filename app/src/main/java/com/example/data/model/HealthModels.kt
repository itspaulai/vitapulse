package com.example.data.model

import java.util.Locale
import kotlin.math.abs

enum class TimeWindow(val label: String, val shortLabel: String, val days: Int) {
    HOURS_24("Last 24 Hours", "24H", 1),
    DAYS_7("Last 7 Days", "7D", 7),
    DAYS_30("Last 30 Days", "30D", 30)
}

enum class InsightCategory(val label: String) {
    HEART("Heart & Vitals"),
    SLEEP("Sleep & Recovery"),
    ACTIVITY("Activity & Steps"),
    HOLISTIC("Holistic Synthesis")
}

data class HeartVitalsMetrics(
    val latestBpm: Int = 0,
    val avgBpm: Int = 0,
    val minBpm: Int = 0,
    val maxBpm: Int = 0,
    val hrvRmssdMs: Double = 0.0,
    val systolicMmHg: Int = 0,
    val diastolicMmHg: Int = 0,
    val spO2Percent: Double = 0.0,
    val sampleCount: Int = 0,
    val recentBpmSeries: List<Int> = emptyList()
)

data class SleepRecoveryMetrics(
    val avgSleepDurationMinutes: Int = 0,
    val latestSleepDurationMinutes: Int = 0,
    val minSleepDurationMinutes: Int = 0,
    val maxSleepDurationMinutes: Int = 0,
    val deepSleepMinutes: Int = 0,
    val remSleepMinutes: Int = 0,
    val lightSleepMinutes: Int = 0,
    val awakeMinutes: Int = 0,
    val restingHeartRateBpm: Int = 0,
    val sleepSessionsCount: Int = 0,
    val recoveryScore: Int = 0
) {
    val formattedLatestSleep: String
        get() = formatMinutesAsHoursMins(latestSleepDurationMinutes)

    val formattedAvgSleep: String
        get() = formatMinutesAsHoursMins(avgSleepDurationMinutes)

    val formattedMinSleep: String
        get() = formatMinutesAsHoursMins(minSleepDurationMinutes)

    val formattedMaxSleep: String
        get() = formatMinutesAsHoursMins(maxSleepDurationMinutes)

    companion object {
        fun formatMinutesAsHoursMins(minutes: Int): String {
            if (minutes <= 0) return "--"
            val h = minutes / 60
            val m = minutes % 60
            return "${h}h ${m}m"
        }
    }
}

data class WorkoutSummary(
    val title: String,
    val exerciseTypeLabel: String,
    val durationMinutes: Int,
    val caloriesKcal: Int,
    val startTimeLabel: String,
    val distanceKm: Double = 0.0,
    val steps: Long = 0L,
    val avgHeartRateBpm: Int = 0,
    val maxHeartRateBpm: Int = 0,
    val startTimeEpochMs: Long = 0L,
    val endTimeEpochMs: Long = 0L
)

data class ActivityWorkoutMetrics(
    val totalSteps: Long = 0L,
    val dailyAvgSteps: Long = 0L,
    val latestDaySteps: Long = 0L,
    val minDailySteps: Long = 0L,
    val maxDailySteps: Long = 0L,
    val daysAbove10kSteps: Int = 0,
    val distanceKm: Double = 0.0,
    val dailyAvgDistanceKm: Double = 0.0,
    val activeCaloriesKcal: Int = 0,
    val dailyAvgActiveCaloriesKcal: Int = 0,
    val totalCaloriesKcal: Int = 0,
    val dailyAvgTotalCaloriesKcal: Int = 0,
    val workouts: List<WorkoutSummary> = emptyList(),
    val dailyStepsSeries: List<Int> = emptyList()
)

data class DailyBiometricEntry(
    val daysAgo: Int,
    val dateLabel: String, // e.g., "Oct 19"
    val dayOfWeekShort: String, // e.g., "Sun"
    val sleepDurationMinutes: Int,
    val deepSleepMinutes: Int,
    val remSleepMinutes: Int,
    val lightSleepMinutes: Int,
    val awakeMinutes: Int,
    val restingHeartRateBpm: Int,
    val avgHeartRateBpm: Int,
    val minHeartRateBpm: Int = 0,
    val maxHeartRateBpm: Int,
    val hrvRmssdMs: Double,
    val systolicMmHg: Int,
    val diastolicMmHg: Int,
    val spO2Percent: Double,
    val steps: Long,
    val distanceKm: Double,
    val activeCaloriesKcal: Int,
    val totalCaloriesKcal: Int,
    val workoutTitle: String? = null,
    val workoutMinutes: Int = 0,
    val workoutCount: Int = 0,
    val recoveryScore: Int = 0
)

data class PeriodHorizonSummary(
    val window: TimeWindow,
    val avgSleepMinutes: Int = 0,
    val minSleepMinutes: Int = 0,
    val maxSleepMinutes: Int = 0,
    val avgDeepMinutes: Int = 0,
    val avgRemMinutes: Int = 0,
    val avgLightMinutes: Int = 0,
    val avgAwakeMinutes: Int = 0,
    val sleepSessionsCount: Int = 0,
    val avgRhrBpm: Int = 0,
    val minRhrBpm: Int = 0,
    val maxRhrBpm: Int = 0,
    val avgBpm: Int = 0,
    val minBpm: Int = 0,
    val maxBpm: Int = 0,
    val avgHrvMs: Double = 0.0,
    val minHrvMs: Double = 0.0,
    val maxHrvMs: Double = 0.0,
    val avgSystolic: Int = 0,
    val avgDiastolic: Int = 0,
    val avgSpO2: Double = 0.0,
    val totalSteps: Long = 0L,
    val dailyAvgSteps: Long = 0L,
    val minDailySteps: Long = 0L,
    val maxDailySteps: Long = 0L,
    val daysAbove10kSteps: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val dailyAvgDistanceKm: Double = 0.0,
    val totalActiveKcal: Int = 0,
    val dailyAvgActiveKcal: Int = 0,
    val totalKcal: Int = 0,
    val dailyAvgTotalKcal: Int = 0,
    val workoutCount: Int = 0,
    val totalWorkoutMinutes: Int = 0,
    val recoveryScore: Int = 0
) {
    val formattedAvgSleep: String
        get() = SleepRecoveryMetrics.formatMinutesAsHoursMins(avgSleepMinutes)

    val formattedMinSleep: String
        get() = SleepRecoveryMetrics.formatMinutesAsHoursMins(minSleepMinutes)

    val formattedMaxSleep: String
        get() = SleepRecoveryMetrics.formatMinutesAsHoursMins(maxSleepMinutes)
}

data class HealthSummarySnapshot(
    val window: TimeWindow = TimeWindow.DAYS_7,
    val capturedAtEpochMs: Long = System.currentTimeMillis(),
    val isFromLiveHealthConnect: Boolean = false,
    val dataSourceDescription: String = "Awaiting Health Connect sync",
    val totalRecordCount: Int = 0,
    val heartVitals: HeartVitalsMetrics = HeartVitalsMetrics(),
    val sleepRecovery: SleepRecoveryMetrics = SleepRecoveryMetrics(),
    val activity: ActivityWorkoutMetrics = ActivityWorkoutMetrics(),
    val horizon24h: PeriodHorizonSummary = PeriodHorizonSummary(TimeWindow.HOURS_24),
    val horizon7d: PeriodHorizonSummary = PeriodHorizonSummary(TimeWindow.DAYS_7),
    val horizon30d: PeriodHorizonSummary = PeriodHorizonSummary(TimeWindow.DAYS_30),
    val allWorkouts30d: List<WorkoutSummary> = emptyList(),
    val dailyHistory: List<DailyBiometricEntry> = emptyList()
) {
    fun toCompactBadgeSummary(): String {
        val displayBpm = heartVitals.avgBpm.takeIf { it > 0 }
            ?: heartVitals.latestBpm.takeIf { it > 0 }
            ?: sleepRecovery.restingHeartRateBpm
        val hr = if (displayBpm > 0) "$displayBpm bpm" else "No HR"
        val sleep = sleepRecovery.formattedLatestSleep.takeIf { it != "--" } ?: sleepRecovery.formattedAvgSleep
        val steps = when {
            window == TimeWindow.HOURS_24 && activity.latestDaySteps > 0 ->
                "${String.format(Locale.US, "%,d", activity.latestDaySteps)} steps today"
            activity.dailyAvgSteps > 0 ->
                "${String.format(Locale.US, "%,d", activity.dailyAvgSteps)} steps/d"
            activity.latestDaySteps > 0 ->
                "${String.format(Locale.US, "%,d", activity.latestDaySteps)} steps"
            else -> "No steps"
        }
        return "${window.shortLabel}: $hr • $sleep sleep • $steps"
    }

    fun formatForGemmaPrompt(): String {
        val h24 = horizon24h
        val h7 = horizon7d
        val h30 = horizon30d
        val recentWorkouts = allWorkouts30d.ifEmpty { activity.workouts }

        return buildString {
            appendLine("=== HEALTH CONNECT LIVE TELEMETRY (24H / 7D / 30D) ===")
            appendLine("• 24H (Today/Last Night): Sleep ${sleepRecovery.formattedLatestSleep} (Deep ${sleepRecovery.deepSleepMinutes}m, REM ${sleepRecovery.remSleepMinutes}m) | HR ${heartVitals.avgBpm}bpm (latest ${heartVitals.latestBpm}bpm, range ${heartVitals.minBpm}-${heartVitals.maxBpm}), RHR ${sleepRecovery.restingHeartRateBpm}bpm, HRV ${String.format(Locale.US, "%.1f", heartVitals.hrvRmssdMs)}ms | Steps Today ${String.format(Locale.US, "%,d", activity.latestDaySteps)}, Active ${h24.totalActiveKcal}kcal, ${String.format(Locale.US, "%.2f", h24.totalDistanceKm)}km")
            appendLine("• 7D (Last Week): Sleep Avg ${h7.formattedAvgSleep}/night (min ${h7.formattedMinSleep}, max ${h7.formattedMaxSleep}) | HR Avg ${h7.avgBpm}bpm, RHR ${h7.avgRhrBpm}bpm, HRV ${String.format(Locale.US, "%.1f", h7.avgHrvMs)}ms | Steps Avg ${String.format(Locale.US, "%,d", h7.dailyAvgSteps)}/d (total ${String.format(Locale.US, "%,d", h7.totalSteps)}), Active ${h7.dailyAvgActiveKcal}kcal/d, Workouts: ${h7.workoutCount}")
            appendLine("• 30D (Last Month): Sleep Avg ${h30.formattedAvgSleep}/night (min ${h30.formattedMinSleep}, max ${h30.formattedMaxSleep}) | HR Avg ${h30.avgBpm}bpm, RHR ${h30.avgRhrBpm}bpm, HRV ${String.format(Locale.US, "%.1f", h30.avgHrvMs)}ms | Steps Avg ${String.format(Locale.US, "%,d", h30.dailyAvgSteps)}/d (total ${String.format(Locale.US, "%,d", h30.totalSteps)}), Active ${h30.dailyAvgActiveKcal}kcal/d, Workouts: ${h30.workoutCount}")
            if (recentWorkouts.isNotEmpty()) {
                appendLine("• Recent Workouts (Newest First — #1 is Most Recent / Last Workout):")
                recentWorkouts.take(5).forEachIndexed { idx, w ->
                    val roleTag = when (idx) {
                        0 -> "#1 MOST RECENT (LAST)"
                        1 -> "#2 PREVIOUS"
                        else -> "#${idx + 1}"
                    }
                    val distPart = if (w.distanceKm > 0.05) ", ${String.format(Locale.US, "%.2f", w.distanceKm)} km" else ""
                    val hrPart = if (w.avgHeartRateBpm > 0) ", Avg HR ${w.avgHeartRateBpm} bpm (Max ${w.maxHeartRateBpm} bpm)" else ""
                    appendLine("  - $roleTag: ${w.title} (${w.exerciseTypeLabel}) on ${w.startTimeLabel} | ${w.durationMinutes} min | ${w.caloriesKcal} kcal$distPart$hrPart")
                }
            } else {
                append("• Recent Workouts: none recorded in last 30 days")
            }
        }.trim()
    }

    fun sleepDelta7dVs30dLabel(): String {
        if (horizon7d.avgSleepMinutes <= 0 || horizon30d.avgSleepMinutes <= 0) {
            return "Avg ${sleepRecovery.formattedAvgSleep}"
        }
        val diff = horizon7d.avgSleepMinutes - horizon30d.avgSleepMinutes
        return if (diff == 0) "Matches 30D avg"
        else if (diff > 0) "+${diff}m vs 30D avg"
        else "-${abs(diff)}m vs 30D avg"
    }
}

data class ContextualPromptChip(
    val id: String,
    val title: String,
    val metricBadge: String,
    val fullPrompt: String,
    val category: InsightCategory
)
