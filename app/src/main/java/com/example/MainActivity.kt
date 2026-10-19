package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ai.GemmaModelUiState
import com.example.data.health.HealthConnectStatus
import com.example.ui.VitalPulseDestination
import com.example.ui.VitalPulseViewModel
import com.example.ui.components.ExpressiveStatusPill
import com.example.ui.screens.GemmaChatScreen
import com.example.ui.screens.GemmaModelHubScreen
import com.example.ui.screens.InsightsVaultScreen
import com.example.ui.screens.PulseDashboardScreen
import com.example.ui.theme.CyanOxygenAccent
import com.example.ui.theme.VitalPulseTheme
import com.example.ui.theme.VitalityMintPrimary
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VitalPulseTheme(darkTheme = false) {
                VitalPulseApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VitalPulseApp(
    viewModel: VitalPulseViewModel = viewModel()
) {
    val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()
    val selectedWindow by viewModel.selectedTimeWindow.collectAsStateWithLifecycle()
    val healthStatus by viewModel.healthStatus.collectAsStateWithLifecycle()
    val healthSnapshot by viewModel.healthSnapshot.collectAsStateWithLifecycle()
    val contextualChips by viewModel.contextualChips.collectAsStateWithLifecycle()
    val statusBannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeSessionMessages.collectAsStateWithLifecycle()
    val chatInputText by viewModel.chatInputText.collectAsStateWithLifecycle()
    val attachHealthContext by viewModel.attachHealthContext.collectAsStateWithLifecycle()
    val isGeneratingResponse by viewModel.isGeneratingResponse.collectAsStateWithLifecycle()
    val streamingPreviewText by viewModel.streamingPreviewText.collectAsStateWithLifecycle()
    val streamingTokPerSec by viewModel.streamingTokPerSec.collectAsStateWithLifecycle()

    val allPinnedInsights by viewModel.allPinnedInsights.collectAsStateWithLifecycle()
    val selectedVaultCategory by viewModel.selectedVaultCategory.collectAsStateWithLifecycle()

    val modelUiState by viewModel.modelUiState.collectAsStateWithLifecycle()
    val selectedAccelerator by viewModel.selectedAccelerator.collectAsStateWithLifecycle()
    val selectedModelPreset by viewModel.selectedModelPreset.collectAsStateWithLifecycle()
    val customDownloadUrl by viewModel.customDownloadUrl.collectAsStateWithLifecycle()
    val huggingFaceToken by viewModel.huggingFaceToken.collectAsStateWithLifecycle()

    var showModelBottomSheet by remember { mutableStateOf(false) }

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        viewModel.onPermissionsResult(grantedPermissions)
    }

    val modelFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importLocalModelUri(uri)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshHealthConnectData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showModelBottomSheet || currentDestination == VitalPulseDestination.MODEL_HUB) {
        ModalBottomSheet(
            onDismissRequest = {
                showModelBottomSheet = false
                if (currentDestination == VitalPulseDestination.MODEL_HUB) {
                    viewModel.navigateTo(VitalPulseDestination.PULSE)
                }
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            GemmaModelHubScreen(
                modelUiState = modelUiState,
                selectedAccelerator = selectedAccelerator,
                selectedPreset = selectedModelPreset,
                downloadUrl = customDownloadUrl,
                huggingFaceToken = huggingFaceToken,
                onSelectPreset = viewModel::selectModelPreset,
                onDownloadUrlChanged = viewModel::updateCustomDownloadUrl,
                onHuggingFaceTokenChanged = viewModel::updateHuggingFaceToken,
                onStartDownload = viewModel::startGemmaDownload,
                onCancelDownload = viewModel::cancelGemmaDownload,
                onPickLocalModelFile = {
                    modelFilePickerLauncher.launch(arrayOf("*/*"))
                },
                onSelectAccelerator = viewModel::switchHardwareAccelerator,
                onNavigateBack = {
                    showModelBottomSheet = false
                    viewModel.navigateTo(VitalPulseDestination.PULSE)
                }
            )
        }
    }

    val primaryNavTabs = listOf(
        VitalPulseDestination.PULSE,
        VitalPulseDestination.DISCUSS,
        VitalPulseDestination.VAULT
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "VitalPulse",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hcConnected = healthStatus == HealthConnectStatus.AVAILABLE_GRANTED
                        ExpressiveStatusPill(
                            icon = Icons.Default.HealthAndSafety,
                            label = if (hcConnected) "HC Live" else "HC",
                            isActive = hcConnected,
                            accentColor = VitalityMintPrimary,
                            onClick = {
                                runCatching {
                                    healthPermissionLauncher.launch(
                                        viewModel.healthConnectManager.requiredPermissions
                                    )
                                }.onFailure {
                                    viewModel.loadPreviewBiometricSnapshot()
                                }
                            },
                            testTag = "hc_status_pill"
                        )

                        val modelReady = modelUiState is GemmaModelUiState.Ready
                        val modelChipLabel = when (val state = modelUiState) {
                            is GemmaModelUiState.Ready -> "Gemma 4 (${state.accelerator.name})"
                            is GemmaModelUiState.Downloading -> "${(state.progressFraction * 100).roundToInt()}%"
                            is GemmaModelUiState.Initializing -> "Loading…"
                            else -> "Gemma 4"
                        }
                        ExpressiveStatusPill(
                            icon = Icons.Default.Memory,
                            label = modelChipLabel,
                            isActive = modelReady,
                            accentColor = CyanOxygenAccent,
                            onClick = { showModelBottomSheet = true },
                            testTag = "gemma_status_pill"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 0.dp
            ) {
                primaryNavTabs.forEach { dest ->
                    val selected = currentDestination == dest
                    NavigationBarItem(
                        selected = selected,
                        onClick = { viewModel.navigateTo(dest) },
                        icon = {
                            val icon = when (dest) {
                                VitalPulseDestination.PULSE ->
                                    if (selected) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
                                VitalPulseDestination.DISCUSS ->
                                    if (selected) Icons.AutoMirrored.Filled.Chat else Icons.AutoMirrored.Outlined.Chat
                                VitalPulseDestination.VAULT ->
                                    if (selected) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder
                                VitalPulseDestination.MODEL_HUB -> Icons.Default.Memory
                            }
                            Icon(imageVector = icon, contentDescription = dest.label)
                        },
                        label = {
                            Text(
                                text = dest.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_tab_${dest.route}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = statusBannerMessage != null) {
                statusBannerMessage?.let { msg ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.dismissBannerMessage() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss notification",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (currentDestination) {
                    VitalPulseDestination.PULSE,
                    VitalPulseDestination.MODEL_HUB -> {
                        PulseDashboardScreen(
                            healthStatus = healthStatus,
                            snapshot = healthSnapshot,
                            selectedWindow = selectedWindow,
                            contextualChips = contextualChips,
                            modelUiState = modelUiState,
                            onSelectWindow = viewModel::selectTimeWindow,
                            onRequestHealthPermissions = {
                                runCatching {
                                    healthPermissionLauncher.launch(
                                        viewModel.healthConnectManager.requiredPermissions
                                    )
                                }.onFailure {
                                    viewModel.loadPreviewBiometricSnapshot()
                                }
                            },
                            onSeedSampleHealthConnectRecords = viewModel::seedSampleRecordsIntoHealthConnect,
                            onRefreshHealthData = viewModel::refreshHealthConnectData,
                            onOpenModelHub = { showModelBottomSheet = true },
                            onChipSelected = viewModel::onContextualChipClicked,
                            onDiscussCategory = viewModel::askAboutSpecificCategory
                        )
                    }

                    VitalPulseDestination.DISCUSS -> {
                        GemmaChatScreen(
                            messages = activeMessages,
                            sessions = allSessions,
                            activeSessionId = activeSessionId,
                            chatInput = chatInputText,
                            attachHealthContext = attachHealthContext,
                            isGenerating = isGeneratingResponse,
                            streamingPreviewText = streamingPreviewText,
                            streamingTokPerSec = streamingTokPerSec,
                            healthSnapshot = healthSnapshot,
                            contextualChips = contextualChips,
                            modelUiState = modelUiState,
                            onInputChanged = viewModel::updateChatInput,
                            onSendClicked = { viewModel.sendMessage() },
                            onStopGeneration = viewModel::stopGeneration,
                            onToggleAttachContext = viewModel::toggleAttachHealthContext,
                            onSelectChip = viewModel::onContextualChipClicked,
                            onPinMessage = viewModel::pinMessageToVault,
                            onNewSession = viewModel::startNewChatSession,
                            onSelectSession = viewModel::selectChatSession,
                            onOpenModelHub = { showModelBottomSheet = true },
                            onNavigateBack = { viewModel.navigateTo(VitalPulseDestination.PULSE) }
                        )
                    }

                    VitalPulseDestination.VAULT -> {
                        InsightsVaultScreen(
                            pinnedInsights = allPinnedInsights,
                            chatSessions = allSessions,
                            selectedCategory = selectedVaultCategory,
                            onSelectCategory = viewModel::selectVaultCategoryFilter,
                            onDeletePinnedInsight = viewModel::deletePinnedInsight,
                            onOpenChatSession = viewModel::selectChatSession,
                            onDeleteChatSession = viewModel::deleteChatSession,
                            onNavigateBack = { viewModel.navigateTo(VitalPulseDestination.PULSE) }
                        )
                    }
                }
            }
        }
    }
}
