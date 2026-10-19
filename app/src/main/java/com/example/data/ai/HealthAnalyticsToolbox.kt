package com.example.data.ai

import com.example.data.model.DailyBiometricEntry
import com.example.data.model.HealthSummarySnapshot
import com.example.data.model.SleepRecoveryMetrics
import com.example.data.model.WorkoutSummary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class QueryGroundedContext(
    val badgeLabel: String,
    val compactPromptContext: String,
    val directDataAnswer: String
)

/**
 * Automatic, zero-configuration biometric query analyzer & calculator.
 * Inspects the user's natural-language question in <1ms, performs any required
 * calculations across the 24H, 7D (last week), and 30D (last month) daily records
 * (most recent workout vs previous workouts, specific days of the week like "Saturday",
 * rest-day vs workout-day averages, weekly/monthly averages, min/max dates, correlations),
 * and produces a compact data block so Gemma 4 E2B can answer quickly and accurately.
 */
class HealthAnalyticsToolbox {

    private val dayOfWeekMatchers = listOf(
        "monday" to "Mon",
        "tuesday" to "Tue",
        "wednesday" to "Wed",
        "thursday" to "Thu",
        "friday" to "Fri",
        "saturday" to "Sat",
        "sunday" to "Sun"
    )

    fun buildGroundedContextForQuery(
        userPrompt: String,
        snapshot: HealthSummarySnapshot
    ): QueryGroundedContext {
        val lower = userPrompt.lowercase(Locale.US)
        val h24 = snapshot.horizon24h
        val h7 = snapshot.horizon7d
        val h30 = snapshot.horizon30d
        val hv = snapshot.heartVitals
        val sl = snapshot.sleepRecovery
        val act = snapshot.activity
        val history = snapshot.dailyHistory // sorted oldest (daysAgo=29) to newest (daysAgo=0)
        // Strictly newest first: index 0 is ALWAYS the most recent / last workout!
        val workouts = snapshot.allWorkouts30d.ifEmpty { act.workouts }
            .sortedByDescending { it.endTimeEpochMs }

        // 1. Check if the user is asking about a specific day of the week (e.g. "Saturday", "Monday", "yesterday")
        val matchedDow = dayOfWeekMatchers.firstOrNull { (full, short) ->
            lower.contains(full) || Regex("\\b${short.lowercase(Locale.US)}\\b").containsMatchIn(lower)
        }
        val asksYesterday = lower.contains("yesterday")
        val asksTodayOrLastNight = lower.contains("last night") || lower.contains("today")

        val matchedDays: List<DailyBiometricEntry> = when {
            matchedDow != null -> {
                history.filter { it.dayOfWeekShort.equals(matchedDow.second, ignoreCase = true) }
                    .sortedBy { it.daysAgo }
            }
            asksYesterday -> {
                history.filter { it.daysAgo == 1 }.ifEmpty { history.takeLast(2).take(1) }
            }
            asksTodayOrLastNight -> {
                history.filter { it.daysAgo == 0 }.ifEmpty { history.takeLast(1) }
            }
            else -> emptyList()
        }

        // 2. Check if the user is asking about Rest Days vs Workout/Active Days
        val asksRestDays = lower.contains("rest day") || lower.contains("rest days") ||
            lower.contains("non-workout") || lower.contains("off day") || lower.contains("recovery day")

        val restDays30 = history.filter { it.workoutMinutes == 0 && it.steps < 8800L }
            .ifEmpty { history.filter { it.workoutMinutes == 0 } }
            .ifEmpty { history.sortedBy { it.steps }.take((history.size / 2).coerceAtLeast(1)) }

        val activeDays30 = history.filter { !restDays30.contains(it) }
            .ifEmpty { history.filter { it.workoutMinutes > 0 } }

        // 3. Determine primary topic (Workout, Sleep, Heart/HRV, Activity/Steps, Rest Days, or General)
        val isWorkoutQuery = lower.contains("workout") || lower.contains("exercise") ||
            lower.contains("session") || lower.contains("training") || lower.contains("run") ||
            lower.contains("running") || lower.contains("jog") || lower.contains("cycling") ||
            lower.contains("bike") || lower.contains("swim") || lower.contains("gym") ||
            lower.contains("strength") || lower.contains("lift") || lower.contains("hiit") ||
            lower.contains("yoga") || lower.contains("pilates") || lower.contains("hike") ||
            lower.contains("hiking") || lower.contains("sport")
        val isSleepQuery = lower.contains("sleep") || lower.contains("bed") ||
            lower.contains("night") || lower.contains("rem") || lower.contains("deep") ||
            lower.contains("awake") || lower.contains("nap") || lower.contains("woke")
        val isHeartQuery = lower.contains("heart") || lower.contains("hrv") ||
            lower.contains("rhr") || lower.contains("resting") || lower.contains("bpm") ||
            lower.contains("bp") || lower.contains("blood") || lower.contains("spo2") || lower.contains("pulse")
        val isActivityQuery = isWorkoutQuery || lower.contains("step") || lower.contains("walk") ||
            lower.contains("calor") || lower.contains("kcal") || lower.contains("distance") ||
            lower.contains("active") || lower.contains("km")
        val isCorrelationQuery = lower.contains("correlat") || lower.contains("affect") ||
            lower.contains("impact") || lower.contains("relationship") || lower.contains("why")

        val todaySteps = act.latestDaySteps.takeIf { it > 0L } ?: h24.totalSteps
        val todayHrAvg = hv.avgBpm.takeIf { it > 0 } ?: h24.avgBpm
        val todayHrLatest = hv.latestBpm.takeIf { it > 0 } ?: todayHrAvg
        val todayRhr = sl.restingHeartRateBpm.takeIf { it > 0 } ?: h24.avgRhrBpm
        val todayHrv = hv.hrvRmssdMs.takeIf { it > 0.0 } ?: h24.avgHrvMs

        // Build a focused, synchronized prompt context tailored to the exact question
        val compactContext = buildString {
            // If asking about workouts, put the chronological workout log FIRST with #1 MOST RECENT (LAST) WORKOUT clearly labeled!
            if (isWorkoutQuery) {
                if (workouts.isNotEmpty()) {
                    val latestW = workouts.first()
                    appendLine("MOST RECENT / LAST WORKOUT (#1 — Latest in Health Connect):")
                    appendLine(formatWorkoutLine(1, "MOST RECENT (LAST) WORKOUT", latestW))
                    if (workouts.size > 1) {
                        appendLine("PREVIOUS / OLDER WORKOUTS (for comparison only — NOT the last workout):")
                        workouts.drop(1).take(4).forEachIndexed { idx, w ->
                            val rank = idx + 2
                            val label = if (rank == 2) "PREVIOUS WORKOUT" else "OLDER WORKOUT"
                            appendLine(formatWorkoutLine(rank, label, w))
                        }
                    }
                    appendLine("Workout Totals: Last 7D = ${h7.workoutCount} workouts (${h7.totalWorkoutMinutes}m total) | Last 30D = ${h30.workoutCount} workouts (${h30.totalWorkoutMinutes}m total)")
                } else {
                    appendLine("Workouts in Health Connect (Last 30D): 0 exercise sessions recorded.")
                }
            }

            // If specific days were matched (e.g. "Saturday"), include those exact day logs
            if (matchedDays.isNotEmpty()) {
                val dayLabel = matchedDow?.first?.replaceFirstChar { it.uppercase() }
                    ?: if (asksYesterday) "Yesterday" else "Today"
                appendLine("Matching $dayLabel Records (most recent first):")
                matchedDays.take(4).forEach { d ->
                    appendLine(formatSingleDayLine(d))
                }
            }

            // If asking about rest days vs workout days, include pre-calculated 30D & 7D rest-day stats
            if (asksRestDays) {
                val rest7 = restDays30.filter { it.daysAgo < 7 }
                val avgRestSleep = restDays30.filter { it.sleepDurationMinutes > 0 }
                    .map { it.sleepDurationMinutes }.average().takeIf { !it.isNaN() }?.roundToInt() ?: h30.avgSleepMinutes
                val avgRestSteps = restDays30.map { it.steps }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0
                val avgRestKcal = restDays30.map { it.activeCaloriesKcal }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0
                val avgRestRhr = restDays30.filter { it.restingHeartRateBpm > 0 }
                    .map { it.restingHeartRateBpm }.average().takeIf { !it.isNaN() }?.roundToInt() ?: h30.avgRhrBpm
                val avgRestHrv = restDays30.filter { it.hrvRmssdMs > 0 }
                    .map { it.hrvRmssdMs }.average().takeIf { !it.isNaN() } ?: h30.avgHrvMs

                val avgActSteps = activeDays30.map { it.steps }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0
                val avgActKcal = activeDays30.map { it.activeCaloriesKcal }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0
                val avgActRhr = activeDays30.filter { it.restingHeartRateBpm > 0 }
                    .map { it.restingHeartRateBpm }.average().takeIf { !it.isNaN() }?.roundToInt() ?: h30.avgRhrBpm

                appendLine("Rest Days Analysis (Last 30D: ${restDays30.size} rest days vs ${activeDays30.size} workout/active days; Last 7D: ${rest7.size} rest days):")
                appendLine("- Rest Day Average (30D): ${String.format(Locale.US, "%,d", avgRestSteps)} steps/day, $avgRestKcal active kcal, Sleep ${SleepRecoveryMetrics.formatMinutesAsHoursMins(avgRestSleep)}, RHR ${avgRestRhr}bpm, HRV ${String.format(Locale.US, "%.0f", avgRestHrv)}ms")
                appendLine("- Active/Workout Day Average (30D): ${String.format(Locale.US, "%,d", avgActSteps)} steps/day, $avgActKcal active kcal, RHR ${avgActRhr}bpm")
            }

            // Include focused 24H / 7D / 30D horizon stats
            when {
                isSleepQuery -> {
                    val bestNight = history.filter { it.sleepDurationMinutes > 0 }.maxByOrNull { it.sleepDurationMinutes }
                    val worstNight = history.filter { it.sleepDurationMinutes > 0 }.minByOrNull { it.sleepDurationMinutes }
                    val recent7Str = history.filter { it.daysAgo < 7 }.sortedBy { it.daysAgo }.joinToString(", ") {
                        "${it.dayOfWeekShort} ${it.dateLabel}: ${SleepRecoveryMetrics.formatMinutesAsHoursMins(it.sleepDurationMinutes)}"
                    }
                    appendLine("Sleep Summary (24H / 7D / 30D):")
                    appendLine("- Last Night (24H): ${sl.formattedLatestSleep} (Deep ${sl.deepSleepMinutes}m, REM ${sl.remSleepMinutes}m, Light ${sl.lightSleepMinutes}m, Awake ${sl.awakeMinutes}m)")
                    appendLine("- Last Week (7D Avg): ${h7.formattedAvgSleep}/night (min ${h7.formattedMinSleep}, max ${h7.formattedMaxSleep}, Deep avg ${h7.avgDeepMinutes}m, REM avg ${h7.avgRemMinutes}m)")
                    appendLine("- Last Month (30D Avg): ${h30.formattedAvgSleep}/night (min ${h30.formattedMinSleep}, max ${h30.formattedMaxSleep}, Deep avg ${h30.avgDeepMinutes}m, REM avg ${h30.avgRemMinutes}m)")
                    if (bestNight != null && worstNight != null) {
                        appendLine("- 30D Best Night: ${bestNight.dayOfWeekShort} ${bestNight.dateLabel} (${SleepRecoveryMetrics.formatMinutesAsHoursMins(bestNight.sleepDurationMinutes)}) | Shortest: ${worstNight.dayOfWeekShort} ${worstNight.dateLabel} (${SleepRecoveryMetrics.formatMinutesAsHoursMins(worstNight.sleepDurationMinutes)})")
                    }
                    if (matchedDays.isEmpty() && recent7Str.isNotEmpty()) {
                        appendLine("- Last 7 Nights Log: $recent7Str")
                    }
                }

                isHeartQuery -> {
                    appendLine("Heart & Vitals Summary (24H / 7D / 30D):")
                    appendLine("- 24H (Today): Avg HR ${todayHrAvg}bpm (latest ${todayHrLatest}bpm, range ${hv.minBpm}-${hv.maxBpm}bpm), RHR ${todayRhr}bpm, HRV ${String.format(Locale.US, "%.1f", todayHrv)}ms, BP ${hv.systolicMmHg}/${hv.diastolicMmHg}mmHg, SpO2 ${String.format(Locale.US, "%.1f", hv.spO2Percent)}%")
                    appendLine("- Last Week (7D Avg): Avg HR ${h7.avgBpm}bpm (range ${h7.minBpm}-${h7.maxBpm}bpm), RHR ${h7.avgRhrBpm}bpm (range ${h7.minRhrBpm}-${h7.maxRhrBpm}), HRV ${String.format(Locale.US, "%.1f", h7.avgHrvMs)}ms, BP ${h7.avgSystolic}/${h7.avgDiastolic}")
                    appendLine("- Last Month (30D Avg): Avg HR ${h30.avgBpm}bpm, RHR ${h30.avgRhrBpm}bpm (range ${h30.minRhrBpm}-${h30.maxRhrBpm}), HRV ${String.format(Locale.US, "%.1f", h30.avgHrvMs)}ms (range ${String.format(Locale.US, "%.0f", h30.minHrvMs)}-${String.format(Locale.US, "%.0f", h30.maxHrvMs)}ms), BP ${h30.avgSystolic}/${h30.avgDiastolic}, SpO2 ${String.format(Locale.US, "%.1f", h30.avgSpO2)}%")
                }

                isActivityQuery -> {
                    val peakDay = history.maxByOrNull { it.steps }
                    val lowDay = history.filter { it.steps > 0L }.minByOrNull { it.steps }
                    val recent7Steps = history.filter { it.daysAgo < 7 }.sortedBy { it.daysAgo }.joinToString(", ") {
                        "${it.dayOfWeekShort} ${it.dateLabel}: ${it.steps} steps"
                    }
                    appendLine("Activity & Steps Summary (24H / 7D / 30D):")
                    appendLine("- Today (24H): ${String.format(Locale.US, "%,d", todaySteps)} steps, ${h24.totalActiveKcal} active kcal (${h24.totalKcal} total kcal), ${String.format(Locale.US, "%.2f", h24.totalDistanceKm)}km")
                    appendLine("- Last Week (7D): Avg ${String.format(Locale.US, "%,d", h7.dailyAvgSteps)} steps/day (Total ${String.format(Locale.US, "%,d", h7.totalSteps)}, ${h7.daysAbove10kSteps}/7 days >=10k), Avg ${h7.dailyAvgActiveKcal} active kcal/day, ${h7.workoutCount} workouts (${h7.totalWorkoutMinutes}m)")
                    appendLine("- Last Month (30D): Avg ${String.format(Locale.US, "%,d", h30.dailyAvgSteps)} steps/day (Total ${String.format(Locale.US, "%,d", h30.totalSteps)}, ${h30.daysAbove10kSteps}/30 days >=10k), Avg ${h30.dailyAvgActiveKcal} active kcal/day, ${h30.workoutCount} workouts (${h30.totalWorkoutMinutes}m)")
                    if (!isWorkoutQuery && workouts.isNotEmpty()) {
                        val latestW = workouts.first()
                        appendLine("- Most Recent Workout (#1): ${latestW.title} (${latestW.exerciseTypeLabel}) on ${latestW.startTimeLabel} (${latestW.durationMinutes}m, ${latestW.caloriesKcal} kcal)")
                    }
                    if (peakDay != null && lowDay != null) {
                        appendLine("- 30D Peak Steps: ${peakDay.dayOfWeekShort} ${peakDay.dateLabel} (${String.format(Locale.US, "%,d", peakDay.steps)}) | Lowest: ${lowDay.dayOfWeekShort} ${lowDay.dateLabel} (${String.format(Locale.US, "%,d", lowDay.steps)})")
                    }
                    if (!isWorkoutQuery && matchedDays.isEmpty() && recent7Steps.isNotEmpty()) {
                        appendLine("- Last 7 Days Steps: $recent7Steps")
                    }
                }

                else -> {
                    val latestW = workouts.firstOrNull()
                    appendLine("Health Connect Summary (24H / 7D / 30D):")
                    appendLine("- 24H (Today): Sleep ${sl.formattedLatestSleep} (Deep ${sl.deepSleepMinutes}m, REM ${sl.remSleepMinutes}m), HR ${todayHrAvg}bpm (RHR ${todayRhr}bpm), HRV ${String.format(Locale.US, "%.0f", todayHrv)}ms, Steps ${String.format(Locale.US, "%,d", todaySteps)}, Active ${h24.totalActiveKcal}kcal, Readiness ${sl.recoveryScore}/100")
                    appendLine("- 7D (Last Week Avg): Sleep ${h7.formattedAvgSleep}/night, HR ${h7.avgBpm}bpm (RHR ${h7.avgRhrBpm}bpm), HRV ${String.format(Locale.US, "%.1f", h7.avgHrvMs)}ms, Steps ${String.format(Locale.US, "%,d", h7.dailyAvgSteps)}/day, Active ${h7.dailyAvgActiveKcal}kcal/day, ${h7.workoutCount} workouts")
                    appendLine("- 30D (Last Month Avg): Sleep ${h30.formattedAvgSleep}/night, HR ${h30.avgBpm}bpm (RHR ${h30.avgRhrBpm}bpm), HRV ${String.format(Locale.US, "%.1f", h30.avgHrvMs)}ms, Steps ${String.format(Locale.US, "%,d", h30.dailyAvgSteps)}/day, Active ${h30.dailyAvgActiveKcal}kcal/day, ${h30.workoutCount} workouts (${restDays30.size} rest days)")
                    if (latestW != null) {
                        appendLine("- Most Recent (Last) Workout (#1): ${latestW.title} (${latestW.exerciseTypeLabel}) on ${latestW.startTimeLabel} (${latestW.durationMinutes}m, ${latestW.caloriesKcal} kcal)")
                    }
                }
            }

            if (isCorrelationQuery && history.size >= 4) {
                val medianSteps = history.map { it.steps }.sorted()[history.size / 2]
                val highStepDeep = history.filter { it.steps >= medianSteps }.map { it.deepSleepMinutes }.average().roundToInt()
                val lowStepDeep = history.filter { it.steps < medianSteps }.map { it.deepSleepMinutes }.average().roundToInt()
                val rStepsDeep = pearsonCorrelation(history.map { it.steps.toDouble() }, history.map { it.deepSleepMinutes.toDouble() })
                val rSleepHrv = pearsonCorrelation(history.map { it.sleepDurationMinutes.toDouble() }, history.map { it.hrvRmssdMs })
                appendLine("- 30D Correlations: Steps↔Deep Sleep r=${String.format(Locale.US, "%+.2f", rStepsDeep)} (active days avg ${highStepDeep}m Deep vs ${lowStepDeep}m on low-step days); Sleep↔HRV r=${String.format(Locale.US, "%+.2f", rSleepHrv)}")
            }
        }.trim()

        val badgeLabel = when {
            isWorkoutQuery -> "HC Workouts (Latest #1 + 30D)"
            matchedDow != null -> "HC ${matchedDow.first.replaceFirstChar { it.uppercase() }} + 7D/30D"
            asksRestDays -> "HC Rest Days (30D Calc)"
            isSleepQuery -> "HC Sleep (24H • 7D • 30D)"
            isHeartQuery -> "HC Heart & HRV (24H • 7D • 30D)"
            isActivityQuery -> "HC Activity (24H • 7D • 30D)"
            else -> "HC 24H • 7D • 30D"
        }

        val directAnswer = buildDirectDataAnswer(
            snapshot = snapshot,
            workouts = workouts,
            matchedDow = matchedDow,
            matchedDays = matchedDays,
            asksYesterday = asksYesterday,
            asksRestDays = asksRestDays,
            restDays30 = restDays30,
            activeDays30 = activeDays30,
            isWorkoutQuery = isWorkoutQuery,
            isSleepQuery = isSleepQuery,
            isHeartQuery = isHeartQuery,
            isActivityQuery = isActivityQuery
        )

        return QueryGroundedContext(
            badgeLabel = badgeLabel,
            compactPromptContext = compactContext,
            directDataAnswer = directAnswer
        )
    }

    private fun formatWorkoutLine(rank: Int, roleLabel: String, w: WorkoutSummary): String {
        val distStr = if (w.distanceKm > 0.05) " | ${String.format(Locale.US, "%.2f", w.distanceKm)} km" else ""
        val stepsStr = if (w.steps > 0L) " | ${String.format(Locale.US, "%,d", w.steps)} steps" else ""
        val hrStr = if (w.avgHeartRateBpm > 0) " | Avg HR ${w.avgHeartRateBpm} bpm (Max ${w.maxHeartRateBpm} bpm)" else ""
        return "- #$rank $roleLabel: ${w.title} (${w.exerciseTypeLabel}) on ${w.startTimeLabel} | Duration: ${w.durationMinutes} min | ${w.caloriesKcal} kcal$distStr$stepsStr$hrStr"
    }

    private fun formatSingleDayLine(d: DailyBiometricEntry): String {
        val sleepStr = SleepRecoveryMetrics.formatMinutesAsHoursMins(d.sleepDurationMinutes)
        val workoutStr = if (d.workoutMinutes > 0) "${d.workoutTitle ?: "Workout"} (${d.workoutMinutes}m)" else "Rest day (0 workouts)"
        return "- ${d.dayOfWeekShort} ${d.dateLabel}: Sleep $sleepStr (Deep ${d.deepSleepMinutes}m, REM ${d.remSleepMinutes}m, Awake ${d.awakeMinutes}m), Steps ${String.format(Locale.US, "%,d", d.steps)}, Active ${d.activeCaloriesKcal}kcal, HR ${d.avgHeartRateBpm}bpm (RHR ${d.restingHeartRateBpm}bpm), HRV ${String.format(Locale.US, "%.0f", d.hrvRmssdMs)}ms, $workoutStr"
    }

    /**
     * Direct, question-specific answer generator used only if the local LLM is not loaded
     * or fails, ensuring the user NEVER gets a generic unrelated summary.
     */
    private fun buildDirectDataAnswer(
        snapshot: HealthSummarySnapshot,
        workouts: List<WorkoutSummary>,
        matchedDow: Pair<String, String>?,
        matchedDays: List<DailyBiometricEntry>,
        asksYesterday: Boolean,
        asksRestDays: Boolean,
        restDays30: List<DailyBiometricEntry>,
        activeDays30: List<DailyBiometricEntry>,
        isWorkoutQuery: Boolean,
        isSleepQuery: Boolean,
        isHeartQuery: Boolean,
        isActivityQuery: Boolean
    ): String {
        val h24 = snapshot.horizon24h
        val h7 = snapshot.horizon7d
        val h30 = snapshot.horizon30d
        val hv = snapshot.heartVitals
        val sl = snapshot.sleepRecovery
        val act = snapshot.activity
        val history = snapshot.dailyHistory

        // 1. Workout Queries (e.g., "Tell me about my last workout")
        if (isWorkoutQuery) {
            if (workouts.isEmpty()) {
                return "• No ExerciseSessionRecord entries were found in your Health Connect store over the last 30 days.\n" +
                    "• Your step activity averaged ${String.format(Locale.US, "%,d", h7.dailyAvgSteps)} steps/day over the last 7 days and ${String.format(Locale.US, "%,d", h30.dailyAvgSteps)} steps/day over the last 30 days."
            }
            val latest = workouts.first()
            val prev = workouts.getOrNull(1)
            val distPart = if (latest.distanceKm > 0.05) ", covering ${String.format(Locale.US, "%.2f", latest.distanceKm)} km" else ""
            val hrPart = if (latest.avgHeartRateBpm > 0) " with an average heart rate of ${latest.avgHeartRateBpm} bpm (max ${latest.maxHeartRateBpm} bpm)" else ""
            val prevLine = if (prev != null) {
                "\n• Previous Workout (#2): ${prev.title} (${prev.exerciseTypeLabel}) on ${prev.startTimeLabel} for ${prev.durationMinutes} min (${prev.caloriesKcal} kcal)."
            } else ""
            return "• Most Recent Workout (#1): ${latest.title} (${latest.exerciseTypeLabel}) on ${latest.startTimeLabel} for ${latest.durationMinutes} minutes, burning ${latest.caloriesKcal} kcal$distPart$hrPart.$prevLine\n" +
                "• 7D & 30D Volume: You completed ${h7.workoutCount} workouts (${h7.totalWorkoutMinutes} min) in the last 7 days and ${h30.workoutCount} workouts (${h30.totalWorkoutMinutes} min) in the last 30 days."
        }

        // 2. Specific Day of Week or Yesterday (e.g., "What was my sleep time on Saturday?")
        if (matchedDays.isNotEmpty()) {
            val latestMatch = matchedDays.first()
            val dayName = matchedDow?.first?.replaceFirstChar { it.uppercase() }
                ?: if (asksYesterday) "Yesterday" else latestMatch.dayOfWeekShort
            return when {
                isSleepQuery -> {
                    val sleepStr = SleepRecoveryMetrics.formatMinutesAsHoursMins(latestMatch.sleepDurationMinutes)
                    val priorMatches = matchedDays.drop(1).take(2)
                    val priorText = if (priorMatches.isNotEmpty()) {
                        " Prior ${dayName}s: " + priorMatches.joinToString(", ") {
                            "${it.dateLabel} (${SleepRecoveryMetrics.formatMinutesAsHoursMins(it.sleepDurationMinutes)})"
                        } + "."
                    } else ""
                    "• On $dayName (${latestMatch.dateLabel}), your sleep time was $sleepStr (${latestMatch.deepSleepMinutes}m Deep, ${latestMatch.remSleepMinutes}m REM, ${latestMatch.lightSleepMinutes}m Light, ${latestMatch.awakeMinutes}m Awake).\n" +
                        "• Compared to your baselines: your 7-day average is ${h7.formattedAvgSleep}/night and your 30-day average is ${h30.formattedAvgSleep}/night.$priorText"
                }
                isActivityQuery -> {
                    "• On $dayName (${latestMatch.dateLabel}), you recorded ${String.format(Locale.US, "%,d", latestMatch.steps)} steps (${String.format(Locale.US, "%.2f", latestMatch.distanceKm)} km) and burned ${latestMatch.activeCaloriesKcal} active kcal.\n" +
                        "• Baseline comparison: 7-day average is ${String.format(Locale.US, "%,d", h7.dailyAvgSteps)} steps/day and 30-day average is ${String.format(Locale.US, "%,d", h30.dailyAvgSteps)} steps/day."
                }
                isHeartQuery -> {
                    "• On $dayName (${latestMatch.dateLabel}), your average Heart Rate was ${latestMatch.avgHeartRateBpm} bpm (max ${latestMatch.maxHeartRateBpm} bpm), Resting Heart Rate was ${latestMatch.restingHeartRateBpm} bpm, and HRV was ${String.format(Locale.US, "%.1f", latestMatch.hrvRmssdMs)} ms."
                }
                else -> {
                    "• $dayName (${latestMatch.dateLabel}): Sleep ${SleepRecoveryMetrics.formatMinutesAsHoursMins(latestMatch.sleepDurationMinutes)} (${latestMatch.deepSleepMinutes}m Deep, ${latestMatch.remSleepMinutes}m REM) • Steps ${String.format(Locale.US, "%,d", latestMatch.steps)} (${latestMatch.activeCaloriesKcal} active kcal) • HR ${latestMatch.avgHeartRateBpm} bpm (RHR ${latestMatch.restingHeartRateBpm} bpm), HRV ${String.format(Locale.US, "%.0f", latestMatch.hrvRmssdMs)} ms."
                }
            }
        }

        // 3. Rest Days in last 7D / 30D (e.g., "What was my rest day on average in the last 30 days?")
        if (asksRestDays) {
            val avgRestSleep = restDays30.filter { it.sleepDurationMinutes > 0 }
                .map { it.sleepDurationMinutes }.average().takeIf { !it.isNaN() }?.roundToInt() ?: h30.avgSleepMinutes
            val avgRestSteps = restDays30.map { it.steps }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0
            val avgRestKcal = restDays30.map { it.activeCaloriesKcal }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0
            val avgRestRhr = restDays30.filter { it.restingHeartRateBpm > 0 }
                .map { it.restingHeartRateBpm }.average().takeIf { !it.isNaN() }?.roundToInt() ?: h30.avgRhrBpm
            val avgRestHrv = restDays30.filter { it.hrvRmssdMs > 0 }
                .map { it.hrvRmssdMs }.average().takeIf { !it.isNaN() } ?: h30.avgHrvMs
            val avgActSteps = activeDays30.map { it.steps }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0
            val avgActKcal = activeDays30.map { it.activeCaloriesKcal }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0

            return "• 30-Day Rest Day Frequency: You had ${restDays30.size} rest days and ${activeDays30.size} workout/active days over the last 30 days.\n" +
                "• Rest Day Averages (30D): ${String.format(Locale.US, "%,d", avgRestSteps)} steps/day, $avgRestKcal active kcal/day, ${SleepRecoveryMetrics.formatMinutesAsHoursMins(avgRestSleep)} sleep, ${avgRestRhr} bpm RHR, and ${String.format(Locale.US, "%.1f", avgRestHrv)} ms HRV.\n" +
                "• Vs. Active/Workout Days: On training days you averaged ${String.format(Locale.US, "%,d", avgActSteps)} steps/day and $avgActKcal active kcal/day."
        }

        // 4. Sleep Averages (Last Week / Last Month / Best & Worst)
        if (isSleepQuery) {
            val diff = h7.avgSleepMinutes - h30.avgSleepMinutes
            val diffStr = if (diff == 0) "equal to" else if (diff > 0) "+${diff}m/night above" else "${abs(diff)}m/night below"
            val bestNight = history.filter { it.sleepDurationMinutes > 0 }.maxByOrNull { it.sleepDurationMinutes }
            val worstNight = history.filter { it.sleepDurationMinutes > 0 }.minByOrNull { it.sleepDurationMinutes }
            return "• Average Sleep Time: Last week (7D) averaged ${h7.formattedAvgSleep}/night (range ${h7.formattedMinSleep}–${h7.formattedMaxSleep}), which is $diffStr your last month (30D) average of ${h30.formattedAvgSleep}/night. Last night (24H) was ${sl.formattedLatestSleep}.\n" +
                "• Sleep Stages: 7D averaged ${h7.avgDeepMinutes}m Deep and ${h7.avgRemMinutes}m REM (30D averaged ${h30.avgDeepMinutes}m Deep and ${h30.avgRemMinutes}m REM).\n" +
                "• 30D Extremes: Longest sleep was ${bestNight?.dayOfWeekShort ?: ""} ${bestNight?.dateLabel ?: "N/A"} (${SleepRecoveryMetrics.formatMinutesAsHoursMins(bestNight?.sleepDurationMinutes ?: 0)}); shortest was ${worstNight?.dayOfWeekShort ?: ""} ${worstNight?.dateLabel ?: "N/A"} (${SleepRecoveryMetrics.formatMinutesAsHoursMins(worstNight?.sleepDurationMinutes ?: 0)})."
        }

        // 5. Activity / Steps / Calories
        if (isActivityQuery) {
            val peakDay = history.maxByOrNull { it.steps }
            val latestW = workouts.firstOrNull()
            val workoutTail = if (latestW != null) {
                " Most recent workout: ${latestW.title} on ${latestW.startTimeLabel} (${latestW.durationMinutes}m, ${latestW.caloriesKcal} kcal)."
            } else ""
            return "• Steps & Distance: Today (24H) ${String.format(Locale.US, "%,d", act.latestDaySteps)} steps • Last week (7D) averaged ${String.format(Locale.US, "%,d", h7.dailyAvgSteps)} steps/day (${String.format(Locale.US, "%,d", h7.totalSteps)} total, ${h7.daysAbove10kSteps}/7 days >=10k) • Last month (30D) averaged ${String.format(Locale.US, "%,d", h30.dailyAvgSteps)} steps/day (${String.format(Locale.US, "%,d", h30.totalSteps)} total).\n" +
                "• Calories & Workouts: 7D averaged ${h7.dailyAvgActiveKcal} active kcal/day across ${h7.workoutCount} workouts (${h7.totalWorkoutMinutes}m); 30D averaged ${h30.dailyAvgActiveKcal} active kcal/day across ${h30.workoutCount} workouts. Peak day was ${peakDay?.dateLabel ?: "N/A"} (${String.format(Locale.US, "%,d", peakDay?.steps ?: 0L)} steps).$workoutTail"
        }

        // 6. Heart Rate / RHR / HRV / BP
        if (isHeartQuery) {
            return "• Heart Rate & RHR: Today avg HR ${hv.avgBpm} bpm (latest ${hv.latestBpm} bpm, range ${hv.minBpm}–${hv.maxBpm} bpm), RHR ${sl.restingHeartRateBpm} bpm | Last week (7D) avg HR ${h7.avgBpm} bpm, RHR ${h7.avgRhrBpm} bpm (${h7.minRhrBpm}–${h7.maxRhrBpm}) | Last month (30D) avg HR ${h30.avgBpm} bpm, RHR ${h30.avgRhrBpm} bpm.\n" +
                "• HRV & Vitals: Current HRV ${String.format(Locale.US, "%.1f", hv.hrvRmssdMs)} ms | 7D avg ${String.format(Locale.US, "%.1f", h7.avgHrvMs)} ms | 30D avg ${String.format(Locale.US, "%.1f", h30.avgHrvMs)} ms • BP ${hv.systolicMmHg}/${hv.diastolicMmHg} mmHg • SpO2 ${String.format(Locale.US, "%.1f%%", hv.spO2Percent)}."
        }

        return "• Multi-Horizon Summary: Sleep 24H ${sl.formattedLatestSleep} | 7D avg ${h7.formattedAvgSleep}/night | 30D avg ${h30.formattedAvgSleep}/night.\n" +
            "• Steps & Activity: Today ${String.format(Locale.US, "%,d", act.latestDaySteps)} steps | 7D avg ${String.format(Locale.US, "%,d", h7.dailyAvgSteps)}/day | 30D avg ${String.format(Locale.US, "%,d", h30.dailyAvgSteps)}/day (${restDays30.size} rest days, ${activeDays30.size} active days in 30D).\n" +
            "• Heart & Recovery: Avg HR ${hv.avgBpm} bpm • RHR 7D ${h7.avgRhrBpm} bpm (30D ${h30.avgRhrBpm} bpm) • HRV 7D ${String.format(Locale.US, "%.1f", h7.avgHrvMs)} ms (30D ${String.format(Locale.US, "%.1f", h30.avgHrvMs)} ms)."
    }

    private fun pearsonCorrelation(xs: List<Double>, ys: List<Double>): Double {
        val n = minOf(xs.size, ys.size)
        if (n < 3) return 0.0
        val meanX = xs.take(n).average()
        val meanY = ys.take(n).average()
        var num = 0.0
        var denX = 0.0
        var denY = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - meanX
            val dy = ys[i] - meanY
            num += dx * dy
            denX += dx * dx
            denY += dy * dy
        }
        val den = sqrt(denX * denY)
        return if (den > 1e-6) (num / den).coerceIn(-0.99, 0.99) else 0.0
    }
}
