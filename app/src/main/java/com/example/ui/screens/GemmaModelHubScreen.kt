package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ai.GemmaLiteRtManager
import com.example.data.ai.GemmaModelPreset
import com.example.data.ai.GemmaModelUiState
import com.example.data.ai.HardwareAccelerator
import com.example.ui.components.ExpressiveSegmentedButtonGroup
import com.example.ui.theme.CoralPulseTertiary
import com.example.ui.theme.VitalityMintPrimary
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun GemmaModelHubScreen(
    modelUiState: GemmaModelUiState,
    selectedAccelerator: HardwareAccelerator,
    selectedPreset: GemmaModelPreset,
    downloadUrl: String,
    huggingFaceToken: String,
    onSelectPreset: (GemmaModelPreset) -> Unit,
    onDownloadUrlChanged: (String) -> Unit,
    onHuggingFaceTokenChanged: (String) -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onPickLocalModelFile: () -> Unit,
    onSelectAccelerator: (HardwareAccelerator) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)
    var showAdvancedDownloadOptions by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag("gemma_model_hub_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Gemma 4 E2B Engine",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "On-device LiteRT-LM runtime • CPU XNNPACK & GPU support",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. Engine Status Card
        item {
            ModelEngineStatusCard(
                modelUiState = modelUiState,
                onCancelDownload = onCancelDownload
            )
        }

        // 2. Hardware Delegate Selection (CPU XNNPACK default vs GPU)
        item {
            ExpressiveSegmentedButtonGroup(
                items = HardwareAccelerator.entries,
                selectedItem = selectedAccelerator,
                onItemSelected = onSelectAccelerator,
                labelProvider = { it.badge },
                testTagPrefix = "accelerator_btn"
            )
        }

        // 3. Primary Actions: Only show download button if not already Ready
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val isAlreadyReady = modelUiState is GemmaModelUiState.Ready
                val isDownloading = modelUiState is GemmaModelUiState.Downloading

                if (!isAlreadyReady) {
                    FilledTonalButton(
                        onClick = if (isDownloading) onCancelDownload else onStartDownload,
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isDownloading) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("start_download_model_button")
                    ) {
                        Icon(
                            imageVector = if (isDownloading) Icons.Default.Stop else Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDownloading) {
                                "Pause Download"
                            } else {
                                "Load / Download ${selectedPreset.fileName}"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Button(
                    onClick = onPickLocalModelFile,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pick_local_model_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAlreadyReady) {
                            "Switch Local .litertlm File"
                        } else {
                            "Select Local .litertlm File"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 4. Collapsible Preset & Custom URL / Token Drawer
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvancedDownloadOptions = !showAdvancedDownloadOptions },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Model Presets & Hugging Face Token",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = if (showAdvancedDownloadOptions) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = showAdvancedDownloadOptions) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GemmaLiteRtManager.MODEL_PRESETS.forEach { preset ->
                                val isSelected = preset.id == selectedPreset.id
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onSelectPreset(preset) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLowest
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = preset.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = preset.approximateSizeGb,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = VitalityMintPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = downloadUrl,
                                onValueChange = onDownloadUrlChanged,
                                label = { Text("Model Bundle URL (.litertlm)") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("model_download_url_input")
                            )

                            OutlinedTextField(
                                value = huggingFaceToken,
                                onValueChange = onHuggingFaceTokenChanged,
                                label = { Text("Hugging Face Token (Optional)") },
                                placeholder = { Text("hf_…") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("hf_token_input")
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ModelEngineStatusCard(
    modelUiState: GemmaModelUiState,
    onCancelDownload: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 0.5.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (modelUiState) {
                is GemmaModelUiState.Ready -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = VitalityMintPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Ready on ${modelUiState.accelerator.badge} (Cached Locally)",
                                style = MaterialTheme.typography.titleMedium,
                                color = VitalityMintPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${modelUiState.modelFileName} (${modelUiState.fileSizeMb} MB) — No re-download needed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is GemmaModelUiState.Downloading -> {
                    val dlMb = modelUiState.downloadedBytes / (1024 * 1024)
                    val totMb = (modelUiState.totalBytes / (1024 * 1024)).coerceAtLeast(1)
                    val pct = (modelUiState.progressFraction * 100).roundToInt()

                    Text(
                        text = "Downloading ${modelUiState.targetFileName} ($pct%)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    LinearProgressIndicator(
                        progress = { modelUiState.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape),
                        color = VitalityMintPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$dlMb / $totMb MB (${String.format(Locale.US, "%.1f", modelUiState.speedMbps)} MB/s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = VitalityMintPrimary
                        )
                        OutlinedButton(
                            onClick = onCancelDownload,
                            shape = CircleShape
                        ) {
                            Text("Pause")
                        }
                    }
                }

                is GemmaModelUiState.Initializing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = VitalityMintPrimary,
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Initializing ${modelUiState.modelFileName} on ${modelUiState.accelerator.badge}…",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                is GemmaModelUiState.Error -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = CoralPulseTertiary
                        )
                        Text(
                            text = modelUiState.message,
                            style = MaterialTheme.typography.titleSmall,
                            color = CoralPulseTertiary
                        )
                    }
                    Text(
                        text = modelUiState.recoverableHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is GemmaModelUiState.NotLoaded -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = VitalityMintPrimary
                        )
                        Column {
                            Text(
                                text = "Pre-Model Biometric Analyzer Active",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = modelUiState.statusNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
