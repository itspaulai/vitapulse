package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CoralPulseTertiary
import com.example.ui.theme.CyanOxygenAccent
import com.example.ui.theme.LavenderSleepAccent
import com.example.ui.theme.VitalityMintHighlight
import com.example.ui.theme.VitalityMintPrimary

/**
 * Material Design 3 Expressive Tonal Bento Card:
 * Uses pure tonal surface hierarchy and morphs its corner radius between 28.dp and 20.dp.
 */
@Composable
fun ExpressiveMorphCard(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    expandedContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (expanded) 20.dp else 28.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "morph_corner"
    )
    val bg by animateColorAsState(
        targetValue = if (expanded) expandedContainerColor else containerColor,
        label = "morph_bg"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(cornerRadius),
        color = bg,
        shadowElevation = if (expanded) 2.dp else 0.5.dp
    ) {
        content()
    }
}

/**
 * Material Design 3 Expressive Connected ButtonGroup:
 * Enforces single-line non-wrapping text and balanced width weights so labels never
 * shrink or wrap awkwardly on compact screens.
 */
@Composable
fun <T> ExpressiveSegmentedButtonGroup(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "segment"
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = item == selectedItem
            val weight by animateFloatAsState(
                targetValue = if (isSelected) 1.15f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "btn_group_weight_$index"
            )

            val startCorner by animateDpAsState(
                targetValue = when {
                    isSelected -> 24.dp
                    index == 0 -> 22.dp
                    else -> 10.dp
                },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                label = "start_corner_$index"
            )
            val endCorner by animateDpAsState(
                targetValue = when {
                    isSelected -> 24.dp
                    index == items.lastIndex -> 22.dp
                    else -> 10.dp
                },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                label = "end_corner_$index"
            )

            val bgColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                label = "seg_bg_$index"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "seg_fg_$index"
            )

            val shape = RoundedCornerShape(
                topStart = startCorner,
                bottomStart = startCorner,
                topEnd = endCorner,
                bottomEnd = endCorner
            )

            Box(
                modifier = Modifier
                    .weight(weight)
                    .minimumInteractiveComponentSize()
                    .clip(shape)
                    .background(bgColor)
                    .clickable { onItemSelected(item) }
                    .padding(vertical = 10.dp, horizontal = 8.dp)
                    .testTag("${testTagPrefix}_$index"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = labelProvider(item),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ExpressiveStatusPill(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "status_pill"
) {
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = CircleShape,
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) MaterialTheme.colorScheme.primary else accentColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ReadinessRingCanvas(
    score: Int,
    modifier: Modifier = Modifier
) {
    val animatedSweep by animateFloatAsState(
        targetValue = (score.coerceIn(0, 100) / 100f) * 300f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "readiness_sweep"
    )

    val trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
    val primaryTextColor = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = modifier.size(84.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(84.dp)) {
            val strokeWidth = 8.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

            drawArc(
                color = trackColor,
                startAngle = 120f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        VitalityMintPrimary,
                        VitalityMintHighlight,
                        VitalityMintPrimary
                    )
                ),
                startAngle = 120f,
                sweepAngle = animatedSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score",
                style = MaterialTheme.typography.headlineMedium,
                color = primaryTextColor,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Text(
                text = "SCORE",
                style = MaterialTheme.typography.labelSmall,
                color = primaryTextColor.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}

@Composable
fun BiometricSparklineCanvas(
    values: List<Int>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) return
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val minVal = (values.minOrNull() ?: 50).toFloat()
        val maxVal = (values.maxOrNull() ?: 150).coerceAtLeast(minVal.toInt() + 10).toFloat()
        val range = (maxVal - minVal).coerceAtLeast(1f)

        val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width
        val path = Path()
        val fillPath = Path()

        values.forEachIndexed { index, v ->
            val x = index * stepX
            val normalized = (v - minVal) / range
            val y = size.height - (normalized * (size.height * 0.76f) + size.height * 0.12f)
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                val prevX = (index - 1) * stepX
                val prevV = values[index - 1]
                val prevY = size.height - (((prevV - minVal) / range) * (size.height * 0.76f) + size.height * 0.12f)
                val controlX = (prevX + x) / 2f
                path.cubicTo(controlX, prevY, controlX, y, x, y)
                fillPath.cubicTo(controlX, prevY, controlX, y, x, y)
            }
            if (index == values.lastIndex) {
                fillPath.lineTo(x, size.height)
                fillPath.close()
            }
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.22f), Color.Transparent)
            )
        )
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun SleepStagesExpressiveBar(
    deepMinutes: Int,
    remMinutes: Int,
    lightMinutes: Int,
    awakeMinutes: Int,
    modifier: Modifier = Modifier
) {
    val total = (deepMinutes + remMinutes + lightMinutes + awakeMinutes).coerceAtLeast(1)
    val deepWeight = (deepMinutes.toFloat() / total).coerceAtLeast(0.06f)
    val remWeight = (remMinutes.toFloat() / total).coerceAtLeast(0.06f)
    val lightWeight = (lightMinutes.toFloat() / total).coerceAtLeast(0.06f)
    val awakeWeight = (awakeMinutes.toFloat() / total).coerceAtLeast(0.04f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(deepWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(LavenderSleepAccent)
            )
            Box(
                modifier = Modifier
                    .weight(remWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(CyanOxygenAccent)
            )
            Box(
                modifier = Modifier
                    .weight(lightWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(VitalityMintHighlight)
            )
            Box(
                modifier = Modifier
                    .weight(awakeWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(CoralPulseTertiary)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StageLegendDot("Deep ${deepMinutes}m", LavenderSleepAccent)
            StageLegendDot("REM ${remMinutes}m", CyanOxygenAccent)
            StageLegendDot("Light ${lightMinutes}m", VitalityMintPrimary)
        }
    }
}

@Composable
private fun StageLegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun DailyStepsExpressiveChart(
    stepsSeries: List<Int>,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    if (stepsSeries.isEmpty()) return
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val maxSteps = (stepsSeries.maxOrNull() ?: 10000).coerceAtLeast(8000).toFloat()
        val count = stepsSeries.size
        val spacing = 10.dp.toPx()
        val totalSpacing = spacing * (count - 1).coerceAtLeast(0)
        val barWidth = ((size.width - totalSpacing) / count.coerceAtLeast(1)).coerceAtLeast(8f)

        stepsSeries.forEachIndexed { index, steps ->
            val ratio = (steps.toFloat() / maxSteps).coerceIn(0.15f, 1f)
            val barHeight = size.height * ratio
            val x = index * (barWidth + spacing)
            val y = size.height - barHeight
            drawRoundRect(
                color = if (index == stepsSeries.lastIndex) barColor else barColor.copy(alpha = 0.28f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(20f, 20f)
            )
        }
    }
}
