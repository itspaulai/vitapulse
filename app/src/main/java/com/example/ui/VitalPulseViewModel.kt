package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.GemmaLiteRtManager
import com.example.data.ai.GemmaModelPreset
import com.example.data.ai.GemmaModelUiState
import com.example.data.ai.HardwareAccelerator
import com.example.data.health.HealthConnectManager
import com.example.data.health.HealthConnectStatus
import com.example.data.local.AppDatabase
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.LocalVaultRepository
import com.example.data.local.PinnedInsightEntity
import com.example.data.model.ContextualPromptChip
import com.example.data.model.HealthSummarySnapshot
import com.example.data.model.InsightCategory
import com.example.data.model.TimeWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class VitalPulseDestination(val route: String, val label: String) {
    PULSE("pulse_dashboard", "Pulse"),
    DISCUSS("gemma_discuss", "Discuss"),
    VAULT("insights_vault", "Vault"),
    MODEL_HUB("gemma_model_hub", "Gemma 4 E2B")
}

class VitalPulseViewModel(application: Application) : AndroidViewModel(application) {

    val healthConnectManager = HealthConnectManager(application)
    val gemmaLiteRtManager = GemmaLiteRtManager(application)
    private val vaultRepository = LocalVaultRepository(
        AppDatabase.getInstance(application).vitalPulseDao()
    )

    private val _currentDestination = MutableStateFlow(VitalPulseDestination.PULSE)
    val currentDestination: StateFlow<VitalPulseDestination> = _currentDestination.asStateFlow()

    private val _selectedTimeWindow = MutableStateFlow(TimeWindow.DAYS_7)
    val selectedTimeWindow: StateFlow<TimeWindow> = _selectedTimeWindow.asStateFlow()

    private val _healthStatus = MutableStateFlow(HealthConnectStatus.AVAILABLE_PERMISSIONS_NEEDED)
    val healthStatus: StateFlow<HealthConnectStatus> = _healthStatus.asStateFlow()

    private val _grantedPermissions = MutableStateFlow<Set<String>>(emptySet())
    val grantedPermissions: StateFlow<Set<String>> = _grantedPermissions.asStateFlow()

    private val _healthSnapshot = MutableStateFlow(
        healthConnectManager.createPreviewSnapshot(TimeWindow.DAYS_7)
    )
    val healthSnapshot: StateFlow<HealthSummarySnapshot> = _healthSnapshot.asStateFlow()

    private val _contextualChips = MutableStateFlow<List<ContextualPromptChip>>(
        healthConnectManager.buildContextualPromptChips(_healthSnapshot.value)
    )
    val contextualChips: StateFlow<List<ContextualPromptChip>> = _contextualChips.asStateFlow()

    private val _isSyncingHealth = MutableStateFlow(false)
    val isSyncingHealth: StateFlow<Boolean> = _isSyncingHealth.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    // Chat & Room state
    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    private val _chatInputText = MutableStateFlow("")
    val chatInputText: StateFlow<String> = _chatInputText.asStateFlow()

    private val _attachHealthContext = MutableStateFlow(true)
    val attachHealthContext: StateFlow<Boolean> = _attachHealthContext.asStateFlow()

    private val _isGeneratingResponse = MutableStateFlow(false)
    val isGeneratingResponse: StateFlow<Boolean> = _isGeneratingResponse.asStateFlow()

    private val _streamingPreviewText = MutableStateFlow("")
    val streamingPreviewText: StateFlow<String> = _streamingPreviewText.asStateFlow()

    private val _streamingTokPerSec = MutableStateFlow(0.0)
    val streamingTokPerSec: StateFlow<Double> = _streamingTokPerSec.asStateFlow()

    private val _selectedVaultCategory = MutableStateFlow<InsightCategory?>(null)
    val selectedVaultCategory: StateFlow<InsightCategory?> = _selectedVaultCategory.asStateFlow()

    // Model downloader settings
    private val _selectedModelPreset = MutableStateFlow(GemmaLiteRtManager.MODEL_PRESETS.first())
    val selectedModelPreset: StateFlow<GemmaModelPreset> = _selectedModelPreset.asStateFlow()

    private val _customDownloadUrl = MutableStateFlow(GemmaLiteRtManager.MODEL_PRESETS.first().defaultDownloadUrl)
    val customDownloadUrl: StateFlow<String> = _customDownloadUrl.asStateFlow()

    private val _huggingFaceToken = MutableStateFlow("")
    val huggingFaceToken: StateFlow<String> = _huggingFaceToken.asStateFlow()

    val modelUiState: StateFlow<GemmaModelUiState> = gemmaLiteRtManager.modelState
    val selectedAccelerator: StateFlow<HardwareAccelerator> = gemmaLiteRtManager.selectedAccelerator

    val allSessions: StateFlow<List<ChatSessionEntity>> = vaultRepository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPinnedInsights: StateFlow<List<PinnedInsightEntity>> = vaultRepository.allPinnedInsights
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeSessionMessages: StateFlow<List<ChatMessageEntity>> = _activeSessionId
        .flatMapLatest { id ->
            if (id != null) {
                vaultRepository.getMessagesForSession(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var activeGenerationJob: Job? = null

    init {
        viewModelScope.launch {
            gemmaLiteRtManager.scanAndAutoInitializeExistingModel()
        }
        viewModelScope.launch {
            refreshHealthConnectData()
        }
        viewModelScope.launch {
            allSessions.collect { sessions ->
                if (_activeSessionId.value == null && sessions.isNotEmpty()) {
                    _activeSessionId.value = sessions.first().id
                }
            }
        }
    }

    fun navigateTo(destination: VitalPulseDestination) {
        _currentDestination.value = destination
        if (destination == VitalPulseDestination.PULSE || destination == VitalPulseDestination.DISCUSS) {
            refreshHealthConnectData()
        }
    }

    fun dismissBannerMessage() {
        _statusBannerMessage.value = null
    }

    fun selectTimeWindow(window: TimeWindow) {
        if (_selectedTimeWindow.value == window) return
        _selectedTimeWindow.value = window
        refreshHealthConnectData()
    }

    private suspend fun fetchLatestSnapshotInternal(window: TimeWindow): HealthSummarySnapshot {
        val (status, granted) = healthConnectManager.checkStatus()
        _healthStatus.value = status
        _grantedPermissions.value = granted

        val snapshot = when (status) {
            HealthConnectStatus.AVAILABLE_GRANTED -> {
                val live = healthConnectManager.readHealthSnapshot(window)
                if (live.totalRecordCount > 0) {
                    live
                } else {
                    healthConnectManager.createPreviewSnapshot(window)
                }
            }
            else -> {
                healthConnectManager.createPreviewSnapshot(window)
            }
        }
        _healthSnapshot.value = snapshot
        _contextualChips.value = healthConnectManager.buildContextualPromptChips(snapshot)
        return snapshot
    }

    fun refreshHealthConnectData() {
        viewModelScope.launch {
            _isSyncingHealth.value = true
            fetchLatestSnapshotInternal(_selectedTimeWindow.value)
            _isSyncingHealth.value = false
        }
    }

    fun onPermissionsResult(granted: Set<String>) {
        _grantedPermissions.value = granted
        refreshHealthConnectData()
        if (granted.isNotEmpty()) {
            _statusBannerMessage.value = "Health Connect permissions granted (${granted.size} scopes active)."
        }
    }

    fun seedSampleRecordsIntoHealthConnect() {
        viewModelScope.launch {
            _isSyncingHealth.value = true
            val result = healthConnectManager.writeSampleRecordsToHealthConnect()
            result.onSuccess { count ->
                val live = healthConnectManager.readHealthSnapshot(_selectedTimeWindow.value)
                _healthSnapshot.value = live
                _contextualChips.value = healthConnectManager.buildContextualPromptChips(live)
                _statusBannerMessage.value = "Wrote $count records (30 full days) into Android Health Connect!"
            }.onFailure { err ->
                val preview = healthConnectManager.createPreviewSnapshot(_selectedTimeWindow.value)
                _healthSnapshot.value = preview
                _contextualChips.value = healthConnectManager.buildContextualPromptChips(preview)
                _statusBannerMessage.value = "Loaded 30-day biometric preview (${err.localizedMessage ?: "Health Connect write permission needed"})."
            }
            _isSyncingHealth.value = false
        }
    }

    fun loadPreviewBiometricSnapshot() {
        val preview = healthConnectManager.createPreviewSnapshot(_selectedTimeWindow.value)
        _healthSnapshot.value = preview
        _contextualChips.value = healthConnectManager.buildContextualPromptChips(preview)
        _statusBannerMessage.value = "Loaded 30-day biometric telemetry."
    }

    fun updateChatInput(text: String) {
        _chatInputText.value = text
    }

    fun toggleAttachHealthContext() {
        _attachHealthContext.value = !_attachHealthContext.value
    }

    fun startNewChatSession() {
        viewModelScope.launch {
            gemmaLiteRtManager.resetConversationSession()
            val newId = vaultRepository.createSession(
                title = "Health Discussion",
                windowBadge = "HC Live"
            )
            _activeSessionId.value = newId
        }
    }

    fun selectChatSession(sessionId: Long) {
        _activeSessionId.value = sessionId
        _currentDestination.value = VitalPulseDestination.DISCUSS
    }

    fun deleteChatSession(sessionId: Long) {
        viewModelScope.launch {
            vaultRepository.deleteSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                _activeSessionId.value = allSessions.value.firstOrNull { it.id != sessionId }?.id
            }
        }
    }

    fun askAboutSpecificCategory(category: InsightCategory) {
        val chip = _contextualChips.value.firstOrNull { it.category == category }
            ?: _contextualChips.value.firstOrNull()
        if (chip != null) {
            _currentDestination.value = VitalPulseDestination.DISCUSS
            sendMessage(chip.fullPrompt)
        }
    }

    fun onContextualChipClicked(chip: ContextualPromptChip) {
        _currentDestination.value = VitalPulseDestination.DISCUSS
        sendMessage(chip.fullPrompt)
    }

    fun sendMessage(overridePrompt: String? = null) {
        val prompt = (overridePrompt ?: _chatInputText.value).trim()
        if (prompt.isEmpty() || _isGeneratingResponse.value) return

        if (overridePrompt == null) {
            _chatInputText.value = ""
        }

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            _isGeneratingResponse.value = true
            _streamingPreviewText.value = ""
            _streamingTokPerSec.value = 0.0

            // Always sync fresh Health Connect snapshot before answering so recent workouts/steps/HR are up to date
            val snapshot = runCatching {
                fetchLatestSnapshotInternal(_selectedTimeWindow.value)
            }.getOrDefault(_healthSnapshot.value)
            val includeContext = _attachHealthContext.value
            val sessionId = _activeSessionId.value ?: vaultRepository.createSession(
                title = prompt.take(36),
                windowBadge = "HC Live"
            ).also { _activeSessionId.value = it }

            val groundedPreview = gemmaLiteRtManager.analyticsToolbox.buildGroundedContextForQuery(prompt, snapshot)

            // Save User Message
            vaultRepository.insertMessage(
                ChatMessageEntity(
                    sessionId = sessionId,
                    role = "USER",
                    content = prompt,
                    attachedHealthBadge = if (includeContext) groundedPreview.badgeLabel else "",
                    attachedHealthContext = if (includeContext) groundedPreview.compactPromptContext else ""
                )
            )

            // Placeholder Assistant Message in Room
            val assistantMsgId = vaultRepository.insertMessage(
                ChatMessageEntity(
                    sessionId = sessionId,
                    role = "ASSISTANT",
                    content = "…",
                    attachedHealthBadge = if (includeContext) groundedPreview.badgeLabel else "",
                    attachedHealthContext = if (includeContext) groundedPreview.compactPromptContext else ""
                )
            )

            var finalContent = ""
            var finalTokPerSec = 0.0
            var finalBackend = "LiteRT GPU"
            var finalBadge = if (includeContext) groundedPreview.badgeLabel else ""
            var finalContext = if (includeContext) groundedPreview.compactPromptContext else ""

            gemmaLiteRtManager.streamDiscussion(
                userPrompt = prompt,
                healthSnapshot = snapshot,
                includeHealthContext = includeContext
            ).collect { chunk ->
                _streamingPreviewText.value = chunk.accumulatedText
                _streamingTokPerSec.value = chunk.tokensPerSec
                finalContent = chunk.accumulatedText
                finalTokPerSec = chunk.tokensPerSec
                finalBackend = chunk.backendLabel
                if (chunk.toolBadgeLabel.isNotBlank()) {
                    finalBadge = chunk.toolBadgeLabel
                }
                if (chunk.toolCalculationsContext.isNotBlank()) {
                    finalContext = chunk.toolCalculationsContext
                }

                if (chunk.isCompleted) {
                    vaultRepository.updateMessage(
                        messageId = assistantMsgId,
                        content = finalContent,
                        tokPerSec = finalTokPerSec,
                        backend = finalBackend,
                        badge = finalBadge,
                        contextText = finalContext
                    )
                }
            }

            val shortTitle = prompt.take(38).let { if (prompt.length > 38) "$it…" else it }
            val preview = finalContent.replace("#", "").replace("*", "").trim().take(70)
            vaultRepository.updateSession(
                sessionId = sessionId,
                title = shortTitle,
                preview = preview
            )

            _isGeneratingResponse.value = false
            _streamingPreviewText.value = ""
        }
    }

    fun stopGeneration() {
        gemmaLiteRtManager.cancelActiveGeneration()
        activeGenerationJob?.cancel()
        _isGeneratingResponse.value = false
        _streamingPreviewText.value = ""
    }

    fun pinMessageToVault(message: ChatMessageEntity) {
        viewModelScope.launch {
            val newPinnedState = !message.isPinned
            vaultRepository.markMessagePinned(message.id, newPinnedState)
            if (newPinnedState) {
                val category = inferCategoryFromContent(message.content)
                val firstLine = message.content.lines()
                    .firstOrNull { it.isNotBlank() && !it.startsWith(">") }
                    ?.replace("#", "")
                    ?.replace("*", "")
                    ?.trim()
                    ?.take(48)
                    ?: "Gemma 4 Biometric Insight"

                vaultRepository.pinInsight(
                    PinnedInsightEntity(
                        title = firstLine,
                        content = message.content,
                        category = category.name,
                        metricsBadge = message.attachedHealthBadge.ifBlank {
                            _healthSnapshot.value.toCompactBadgeSummary()
                        }
                    )
                )
                _statusBannerMessage.value = "Pinned insight to your local Vault."
            }
        }
    }

    fun deletePinnedInsight(insightId: Long) {
        viewModelScope.launch {
            vaultRepository.removePinnedInsight(insightId)
        }
    }

    fun selectVaultCategoryFilter(category: InsightCategory?) {
        _selectedVaultCategory.value = category
    }

    // Model Hub controls
    fun selectModelPreset(preset: GemmaModelPreset) {
        _selectedModelPreset.value = preset
        _customDownloadUrl.value = preset.defaultDownloadUrl
    }

    fun updateCustomDownloadUrl(url: String) {
        _customDownloadUrl.value = url
    }

    fun updateHuggingFaceToken(token: String) {
        _huggingFaceToken.value = token
    }

    fun startGemmaDownload() {
        val preset = _selectedModelPreset.value
        val url = _customDownloadUrl.value.trim()
        if (url.isEmpty()) return
        gemmaLiteRtManager.startModelDownload(
            scope = viewModelScope,
            downloadUrl = url,
            targetFileName = preset.fileName,
            huggingFaceToken = _huggingFaceToken.value
        )
    }

    fun cancelGemmaDownload() {
        gemmaLiteRtManager.cancelDownload()
    }

    fun importLocalModelUri(uri: Uri) {
        viewModelScope.launch {
            gemmaLiteRtManager.importModelFromUri(uri)
        }
    }

    fun switchHardwareAccelerator(accelerator: HardwareAccelerator) {
        viewModelScope.launch {
            gemmaLiteRtManager.switchAccelerator(accelerator)
        }
    }

    private fun inferCategoryFromContent(text: String): InsightCategory {
        val lower = text.lowercase()
        return when {
            lower.contains("sleep") || lower.contains("rem") || lower.contains("recovery") -> InsightCategory.SLEEP
            lower.contains("hrv") || lower.contains("blood pressure") || lower.contains("spo2") || lower.contains("bpm") -> InsightCategory.HEART
            lower.contains("step") || lower.contains("workout") || lower.contains("calorie") -> InsightCategory.ACTIVITY
            else -> InsightCategory.HOLISTIC
        }
    }

    override fun onCleared() {
        super.onCleared()
        gemmaLiteRtManager.closeEngine()
    }
}
