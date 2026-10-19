package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AddChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ai.GemmaModelUiState
import com.example.data.health.HealthConnectStatus
import com.example.data.model.ContextualPromptChip
import com.example.data.model.HealthSummarySnapshot
import com.example.data.model.InsightCategory
import com.example.data.model.TimeWindow
import com.example.ui.components.BiometricSparklineCanvas
import com.example.ui.components.DailyStepsExpressiveChart
import com.example.ui.components.ExpressiveMorphCard
import com.example.ui.components.ExpressiveSegmentedButtonGroup
import com.example.ui.components.ReadinessRingCanvas
import com.example.ui.components.SleepStagesExpressiveBar
import com.example.ui.theme.AmberSoftContainer
import com.example.ui.theme.CoralPulseTertiary
import com.example.ui.theme.CoralSoftContainer
import com.example.ui.theme.LavenderSleepAccent
import com.example.ui.theme.LavenderSoftContainer
import com.example.ui.theme.VitalityMintPrimary
import java.util.Locale

@Composable
fun PulseDashboardScreen(
    healthStatus: HealthConnectStatus,
    snapshot: HealthSummarySnapshot,
    selectedWindow: TimeWindow,
    contextualChips: List<ContextualPromptChip>,
    modelUiState: GemmaModelUiState,
    onSelectWindow: (TimeWindow) -> Unit,
    onRequestHealthPermissions: () -> Unit,
    onSeedSampleHealthConnectRecords: () -> Unit,
    onRefreshHealthData: () -> Unit,
    onOpenModelHub: () -> Unit,
    onChipSelected: (ContextualPromptChip) -> Unit,
    onDiscussCategory: (InsightCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedCard by remember { mutableStateOf<InsightCategory?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("pulse_dashboard_list"),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. M3 Expressive Connected ButtonGroup for Time Window (24H / 7D / 30D)
        item {
            ExpressiveSegmentedButtonGroup(
                items = TimeWindow.entries,
                selectedItem = selectedWindow,
                onItemSelected = onSelectWindow,
                labelProvider = { window ->
                    when (window) {
                        TimeWindow.HOURS_24 -> "24 Hours"
                        TimeWindow.DAYS_7 -> "7 Days"
                        TimeWindow.DAYS_30 -> "30 Days"
                    }
                },
                testTagPrefix = "time_window_btn"
            )
        }

        // 2. Luminous Light-Mode Hero Readiness Card
        item {
            HeroReadinessBanner(
                snapshot = snapshot,
                healthStatus = healthStatus,
                onRequestPermissions = onRequestHealthPermissions,
                onSeedHealthConnect = onSeedSampleHealthConnectRecords,
                onDiscussReadiness = { onDiscussCategory(InsightCategory.HOLISTIC) }
            )
        }

        // 3. Heart & Vitals Bento Card
        item {
            HeartVitalsExpressiveCard(
                snapshot = snapshot,
                expanded = expandedCard == InsightCategory.HEART,
                onToggleExpand = {
                    expandedCard = if (expandedCard == InsightCategory.HEART) null else InsightCategory.HEART
                },
                onDiscussWithGemma = { onDiscussCategory(InsightCategory.HEART) }
            )
        }

        // 4. Sleep & Recovery Bento Card
        item {
            SleepRecoveryExpressiveCard(
                snapshot = snapshot,
                expanded = expandedCard == InsightCategory.SLEEP,
                onToggleExpand = {
                    expandedCard = if (expandedCard == InsightCategory.SLEEP) null else InsightCategory.SLEEP
                },
                onDiscussWithGemma = { onDiscussCategory(InsightCategory.SLEEP) }
            )
        }

        // 5. Activity & Steps Bento Card
        item {
            ActivityWorkoutsExpressiveCard(
                snapshot = snapshot,
                expanded = expandedCard == InsightCategory.ACTIVITY,
                onToggleExpand = {
                    expandedCard = if (expandedCard == InsightCategory.ACTIVITY) null else InsightCategory.ACTIVITY
                },
                onDiscussWithGemma = { onDiscussCategory(InsightCategory.ACTIVITY) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HeroReadinessBanner(
    snapshot: HealthSummarySnapshot,
    healthStatus: HealthConnectStatus,
    onRequestPermissions: () -> Unit,
    onSeedHealthConnect: () -> Unit,
    onDiscussReadiness: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero_readiness_banner"),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.bg_vital_pulse_hero),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (snapshot.isFromLiveHealthConnect) {
                                "HEALTH CONNECT SYNCED"
                            } else {
                                "PREVIEW BIOMETRICS"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Daily Readiness",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val heroRhr = snapshot.sleepRecovery.restingHeartRateBpm.takeIf { it > 0 }
                            ?: snapshot.heartVitals.avgBpm
                        val heroParts = buildList {
                            if (heroRhr > 0) add("$heroRhr bpm RHR")
                            if (snapshot.heartVitals.hrvRmssdMs > 0.0) {
                                add("${String.format(Locale.US, "%.0f", snapshot.heartVitals.hrvRmssdMs)} ms HRV")
                            }
                            val sleepLabel = snapshot.sleepRecovery.formattedLatestSleep.takeIf { it != "--" }
                                ?: snapshot.sleepRecovery.formattedAvgSleep
                            if (sleepLabel != "--") add(sleepLabel)
                        }
                        Text(
                            text = heroParts.joinToString(" • ").ifEmpty { "Synced with Health Connect" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    ReadinessRingCanvas(score = snapshot.sleepRecovery.recoveryScore)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onDiscussReadiness,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("hero_discuss_button"),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Discuss Readiness",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    if (healthStatus == HealthConnectStatus.AVAILABLE_PERMISSIONS_NEEDED) {
                        FilledTonalButton(
                            onClick = onRequestPermissions,
                            shape = CircleShape,
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("grant_hc_permissions_button")
                        ) {
                            Text(
                                text = "Connect",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    } else {
                        FilledTonalIconButton(
                            onClick = onSeedHealthConnect,
                            modifier = Modifier
                                .size(46.dp)
                                .testTag("seed_hc_records_button"),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.78f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AddChart,
                                contentDescription = "Seed Sample Health Connect Records"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeartVitalsExpressiveCard(
    snapshot: HealthSummarySnapshot,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onDiscussWithGemma: () -> Unit
) {
    val hv = snapshot.heartVitals
    val displayBpm = hv.avgBpm.takeIf { it > 0 }
        ?: hv.latestBpm.takeIf { it > 0 }
        ?: snapshot.sleepRecovery.restingHeartRateBpm
    val displayRhr = snapshot.sleepRecovery.restingHeartRateBpm.takeIf { it > 0 } ?: hv.minBpm

    ExpressiveMorphCard(
        expanded = expanded,
        onClick = onToggleExpand,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        expandedContainerColor = CoralSoftContainer.copy(alpha = 0.55f),
        modifier = Modifier.testTag("card_heart_vitals")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CoralSoftContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Heart and Vitals",
                            tint = CoralPulseTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Heart & Vitals",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val rangeSubtitle = when {
                            hv.minBpm > 0 && hv.maxBpm > 0 && hv.latestBpm > 0 ->
                                "Latest ${hv.latestBpm} bpm • Range ${hv.minBpm}–${hv.maxBpm} bpm"
                            hv.minBpm > 0 && hv.maxBpm > 0 ->
                                "Range ${hv.minBpm}–${hv.maxBpm} bpm"
                            displayRhr > 0 ->
                                "Resting HR $displayRhr bpm"
                            else -> "Health Connect Vitals"
                        }
                        Text(
                            text = rangeSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onDiscussWithGemma,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = CoralSoftContainer,
                        contentColor = CoralPulseTertiary
                    ),
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("discuss_heart_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Ask Gemma about Heart & Vitals",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            ExpressiveBigStat(
                value = if (displayBpm > 0) "$displayBpm" else "--",
                unit = "${snapshot.window.shortLabel} bpm avg",
                accent = CoralPulseTertiary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (displayRhr > 0) {
                    CompactTonalPill("RHR $displayRhr bpm")
                }
                if (hv.hrvRmssdMs > 0.0) {
                    CompactTonalPill("HRV ${String.format(Locale.US, "%.1f", hv.hrvRmssdMs)} ms")
                }
                if (hv.systolicMmHg > 0 && hv.diastolicMmHg > 0) {
                    CompactTonalPill("BP ${hv.systolicMmHg}/${hv.diastolicMmHg} mmHg")
                }
                if (hv.spO2Percent > 0.0) {
                    CompactTonalPill("SpO2 ${String.format(Locale.US, "%.1f", hv.spO2Percent)}%")
                }
                if (hv.maxBpm > 0) {
                    CompactTonalPill("Peak ${hv.maxBpm} bpm")
                }
            }

            BiometricSparklineCanvas(
                values = hv.recentBpmSeries,
                lineColor = CoralPulseTertiary
            )

            AnimatedVisibility(visible = expanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "24H ${snapshot.horizon24h.avgBpm.takeIf { it > 0 } ?: displayBpm}bpm • 7D ${snapshot.horizon7d.avgBpm.takeIf { it > 0 } ?: displayBpm}bpm • 30D ${snapshot.horizon30d.avgBpm.takeIf { it > 0 } ?: displayBpm}bpm",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onDiscussWithGemma,
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CoralPulseTertiary,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Analyze Vitals",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepRecoveryExpressiveCard(
    snapshot: HealthSummarySnapshot,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onDiscussWithGemma: () -> Unit
) {
    val sl = snapshot.sleepRecovery
    val is24h = snapshot.window == TimeWindow.HOURS_24
    val primarySleepText = if (is24h) {
        sl.formattedLatestSleep.takeIf { it != "--" } ?: sl.formattedAvgSleep
    } else {
        sl.formattedAvgSleep.takeIf { it != "--" } ?: sl.formattedLatestSleep
    }

    ExpressiveMorphCard(
        expanded = expanded,
        onClick = onToggleExpand,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        expandedContainerColor = LavenderSoftContainer.copy(alpha = 0.65f),
        modifier = Modifier.testTag("card_sleep_recovery")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(LavenderSoftContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "Sleep and Recovery",
                            tint = LavenderSleepAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Sleep & Recovery",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "7D Avg ${snapshot.horizon7d.formattedAvgSleep} • 30D Avg ${snapshot.horizon30d.formattedAvgSleep}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onDiscussWithGemma,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = LavenderSoftContainer,
                        contentColor = LavenderSleepAccent
                    ),
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("discuss_sleep_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Ask Gemma about Sleep",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            ExpressiveBigStat(
                value = primarySleepText,
                unit = if (is24h) "last night (24H)" else "${snapshot.window.shortLabel} nightly avg",
                accent = LavenderSleepAccent
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactTonalPill(snapshot.sleepDelta7dVs30dLabel())
                CompactTonalPill("Range ${sl.formattedMinSleep}–${sl.formattedMaxSleep}")
                CompactTonalPill("${sl.deepSleepMinutes + sl.remSleepMinutes}m Deep+REM avg")
                if (sl.restingHeartRateBpm > 0) {
                    CompactTonalPill("${sl.restingHeartRateBpm} bpm RHR")
                }
            }

            SleepStagesExpressiveBar(
                deepMinutes = sl.deepSleepMinutes,
                remMinutes = sl.remSleepMinutes,
                lightMinutes = sl.lightSleepMinutes,
                awakeMinutes = sl.awakeMinutes
            )

            AnimatedVisibility(visible = expanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "24H ${sl.formattedLatestSleep} • 7D ${snapshot.horizon7d.formattedAvgSleep} • 30D ${snapshot.horizon30d.formattedAvgSleep}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onDiscussWithGemma,
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = LavenderSleepAccent,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Analyze 7D/30D",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityWorkoutsExpressiveCard(
    snapshot: HealthSummarySnapshot,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onDiscussWithGemma: () -> Unit
) {
    val act = snapshot.activity
    val is24h = snapshot.window == TimeWindow.HOURS_24
    val displaySteps = if (is24h) {
        act.latestDaySteps.takeIf { it > 0L } ?: act.dailyAvgSteps
    } else {
        act.dailyAvgSteps.takeIf { it > 0L } ?: act.latestDaySteps
    }
    val mostRecentWorkout = snapshot.allWorkouts30d.firstOrNull() ?: act.workouts.firstOrNull()

    ExpressiveMorphCard(
        expanded = expanded,
        onClick = onToggleExpand,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        expandedContainerColor = AmberSoftContainer.copy(alpha = 0.55f),
        modifier = Modifier.testTag("card_activity_steps")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsRun,
                            contentDescription = "Activity and Steps",
                            tint = VitalityMintPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Activity & Steps",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "7D ${String.format(Locale.US, "%,d", snapshot.horizon7d.dailyAvgSteps)}/d • 30D ${String.format(Locale.US, "%,d", snapshot.horizon30d.dailyAvgSteps)}/d",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onDiscussWithGemma,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = VitalityMintPrimary
                    ),
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("discuss_activity_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Ask Gemma about Activity",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            ExpressiveBigStat(
                value = String.format(Locale.US, "%,d", displaySteps),
                unit = if (is24h) "steps today (24H)" else "${snapshot.window.shortLabel} steps/day avg",
                accent = VitalityMintPrimary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!is24h) {
                    CompactTonalPill("Today ${String.format(Locale.US, "%,d", act.latestDaySteps)}")
                }
                CompactTonalPill("${act.daysAbove10kSteps}/${snapshot.window.days}d >=10k")
                CompactTonalPill("${act.dailyAvgActiveCaloriesKcal} active kcal/d")
                CompactTonalPill("${String.format(Locale.US, "%.1f", act.distanceKm)} km total")
                CompactTonalPill("${snapshot.allWorkouts30d.size.takeIf { it > 0 } ?: act.workouts.size} workouts (30D)")
            }

            if (mostRecentWorkout != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Last Workout: ${mostRecentWorkout.title}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = mostRecentWorkout.startTimeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${mostRecentWorkout.durationMinutes}m • ${mostRecentWorkout.caloriesKcal} kcal",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = VitalityMintPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            DailyStepsExpressiveChart(
                stepsSeries = act.dailyStepsSeries,
                barColor = VitalityMintPrimary
            )

            AnimatedVisibility(visible = expanded) {
                val previousWorkouts = snapshot.allWorkouts30d.ifEmpty { act.workouts }.drop(1).take(3)
                if (previousWorkouts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Previous Workouts",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        previousWorkouts.forEach { workout ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = workout.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = workout.startTimeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${workout.durationMinutes}m • ${workout.caloriesKcal} kcal",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = VitalityMintPrimary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveBigStat(
    value: String,
    unit: String,
    accent: Color
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge,
            color = accent,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.alignByBaseline()
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = unit,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.alignByBaseline()
        )
    }
}

@Composable
private fun CompactTonalPill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
