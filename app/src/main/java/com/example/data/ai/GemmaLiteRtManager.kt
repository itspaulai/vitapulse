package com.example.data.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.HealthSummarySnapshot
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

enum class HardwareAccelerator(val label: String, val badge: String) {
    GPU("GPU Delegate (OpenCL)", "LiteRT GPU"),
    CPU("CPU XNNPACK (Multi-Thread)", "LiteRT CPU")
}

sealed class GemmaModelUiState {
    data class NotLoaded(
        val statusNote: String = "Select a local .litertlm file or download Gemma 4 E2B (~2.58 GB) for 100% local inference."
    ) : GemmaModelUiState()

    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedMbps: Double,
        val targetFileName: String
    ) : GemmaModelUiState() {
        val progressFraction: Float
            get() = if (totalBytes > 0L) {
                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else 0f
    }

    data class Initializing(
        val modelFileName: String,
        val accelerator: HardwareAccelerator
    ) : GemmaModelUiState()

    data class Ready(
        val modelFileName: String,
        val modelFilePath: String,
        val fileSizeMb: Int,
        val accelerator: HardwareAccelerator
    ) : GemmaModelUiState()

    data class Error(
        val message: String,
        val recoverableHint: String = "Verify the .litertlm model file integrity or switch between GPU and CPU delegates."
    ) : GemmaModelUiState()
}

data class StreamingInferenceChunk(
    val accumulatedText: String,
    val tokensPerSec: Double,
    val backendLabel: String,
    val toolBadgeLabel: String = "",
    val toolCalculationsContext: String = "",
    val isCompleted: Boolean
)

data class GemmaModelPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val fileName: String,
    val defaultDownloadUrl: String,
    val approximateSizeGb: String
)

class GemmaLiteRtManager(private val context: Context) {

    companion object {
        const val MODELS_DIR_NAME = "litert_models"
        const val DEFAULT_MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
        private const val MAX_CONTEXT_TOKENS = 2048
        private const val MAX_ANSWER_WORDS = 120

        private const val SYSTEM_INSTRUCTION =
            "You are VitalPulse, a direct on-device health assistant. " +
                "Answer the user's exact question in 1 to 3 short bullet points using the provided Health Connect numbers. " +
                "When asked about the last, latest, or most recent workout, always use #1 MOST RECENT (LAST) WORKOUT and never confuse it with #2 PREVIOUS WORKOUT. " +
                "Never write introductions, conclusions, or medical disclaimers."

        val MODEL_PRESETS = listOf(
            GemmaModelPreset(
                id = "gemma_4_e2b_it",
                title = "Gemma 4 E2B Instruct (Int4 LiteRT-LM)",
                subtitle = "Official 2B Effective parameter edge model (.litertlm)",
                fileName = "gemma-4-E2B-it.litertlm",
                defaultDownloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true",
                approximateSizeGb = "2.58 GB"
            ),
            GemmaModelPreset(
                id = "gemma_3n_e2b_it",
                title = "Gemma 3n E2B Instruct (LiteRT-LM Compatible)",
                subtitle = "Compact edge-optimized E2B bundle (.litertlm)",
                fileName = "gemma-3n-E2B-it-int4.litertlm",
                defaultDownloadUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4.litertlm?download=true",
                approximateSizeGb = "2.41 GB"
            )
        )
    }

    private val _modelState = MutableStateFlow<GemmaModelUiState>(GemmaModelUiState.NotLoaded())
    val modelState: StateFlow<GemmaModelUiState> = _modelState.asStateFlow()

    private val _selectedAccelerator = MutableStateFlow(HardwareAccelerator.GPU)
    val selectedAccelerator: StateFlow<HardwareAccelerator> = _selectedAccelerator.asStateFlow()

    val analyticsToolbox = HealthAnalyticsToolbox()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var activeModelFile: File? = null
    private var downloadJob: Job? = null
    private var turnsInCurrentConversation = 0

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun optimalCpuThreads(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return (cores - 2).coerceIn(4, 6)
    }

    private fun buildFastConversationConfig(): ConversationConfig {
        return ConversationConfig(
            systemInstruction = Contents.of(SYSTEM_INSTRUCTION),
            samplerConfig = SamplerConfig(
                topK = 20,
                topP = 0.88,
                temperature = 0.25,
                seed = 42
            ),
            automaticToolCalling = false
        )
    }

    private fun getModelsDirectory(): File {
        val dir = File(context.filesDir, MODELS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun listInstalledModelFiles(): List<File> {
        val internalFiles = getModelsDirectory().listFiles()?.filter {
            it.isFile && (it.name.endsWith(".litertlm", ignoreCase = true) ||
                it.name.endsWith(".task", ignoreCase = true) ||
                it.name.endsWith(".bin", ignoreCase = true)) && it.length() > 1024 * 1024
        }.orEmpty()
        return internalFiles.sortedByDescending { it.lastModified() }
    }

    suspend fun scanAndAutoInitializeExistingModel() = withContext(Dispatchers.IO) {
        val existing = listInstalledModelFiles().firstOrNull()
        if (existing != null && engine == null) {
            initializeEngineFromFile(existing, _selectedAccelerator.value)
        }
    }

    suspend fun switchAccelerator(newAccelerator: HardwareAccelerator) {
        _selectedAccelerator.value = newAccelerator
        val currentFile = activeModelFile ?: listInstalledModelFiles().firstOrNull()
        if (currentFile != null && currentFile.exists()) {
            initializeEngineFromFile(currentFile, newAccelerator)
        }
    }

    suspend fun importModelFromUri(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val fileName = queryDisplayName(uri) ?: DEFAULT_MODEL_FILENAME
            val safeName = if (fileName.endsWith(".litertlm", true) ||
                fileName.endsWith(".task", true) ||
                fileName.endsWith(".bin", true)
            ) {
                fileName
            } else {
                "$fileName.litertlm"
            }

            val destFile = File(getModelsDirectory(), safeName)
            val totalSize = queryFileSize(uri) ?: 0L

            _modelState.value = GemmaModelUiState.Downloading(
                downloadedBytes = 0L,
                totalBytes = totalSize,
                speedMbps = 0.0,
                targetFileName = "Importing $safeName…"
            )

            val startNs = System.nanoTime()
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var copied = 0L
                    var read: Int
                    var lastReportTime = System.currentTimeMillis()
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        copied += read
                        val now = System.currentTimeMillis()
                        if (now - lastReportTime > 200) {
                            val elapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.1)
                            val mbps = (copied / (1024.0 * 1024.0)) / elapsedSec
                            _modelState.value = GemmaModelUiState.Downloading(
                                downloadedBytes = copied,
                                totalBytes = totalSize.coerceAtLeast(copied),
                                speedMbps = mbps,
                                targetFileName = "Importing $safeName"
                            )
                            lastReportTime = now
                        }
                    }
                    output.flush()
                }
            } ?: throw IllegalStateException("Could not open selected file stream.")

            initializeEngineFromFile(destFile, _selectedAccelerator.value)
        } catch (e: Exception) {
            _modelState.value = GemmaModelUiState.Error(
                message = "Failed to import local model file: ${e.localizedMessage ?: "Unknown error"}",
                recoverableHint = "Ensure the selected file is a valid Gemma 4 E2B .litertlm bundle."
            )
        }
    }

    fun startModelDownload(
        scope: CoroutineScope,
        downloadUrl: String,
        targetFileName: String = DEFAULT_MODEL_FILENAME,
        huggingFaceToken: String = ""
    ) {
        downloadJob?.cancel()
        downloadJob = scope.launch(Dispatchers.IO) {
            val finalFile = File(getModelsDirectory(), targetFileName)
            if (finalFile.exists() && finalFile.length() > 100L * 1024L * 1024L) {
                initializeEngineFromFile(finalFile, _selectedAccelerator.value)
                return@launch
            }

            val partFile = File(getModelsDirectory(), "$targetFileName.part")

            try {
                val existingBytes = if (partFile.exists()) partFile.length() else 0L
                val requestBuilder = Request.Builder()
                    .url(downloadUrl.trim())
                    .header("User-Agent", "VitalPulse-Android-LiteRT/1.0")

                if (huggingFaceToken.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer ${huggingFaceToken.trim()}")
                }
                if (existingBytes > 0L) {
                    requestBuilder.header("Range", "bytes=$existingBytes-")
                }

                _modelState.value = GemmaModelUiState.Downloading(
                    downloadedBytes = existingBytes,
                    totalBytes = 2_580_000_000L,
                    speedMbps = 0.0,
                    targetFileName = targetFileName
                )

                okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        val codeHint = when (response.code) {
                            401, 403 -> "HTTP ${response.code}: Paste a Hugging Face Access Token or select a local .litertlm file."
                            404 -> "HTTP 404: Model URL not found."
                            else -> "HTTP ${response.code}: ${response.message}"
                        }
                        _modelState.value = GemmaModelUiState.Error(
                            message = "Model download failed ($codeHint)",
                            recoverableHint = "Paste a Hugging Face read token if downloading from a gated repo, or use 'Select Local .litertlm File'."
                        )
                        return@use
                    }

                    val isResumed = response.code == 206
                    val contentLength = response.body?.contentLength() ?: -1L
                    val totalBytes = if (contentLength > 0) {
                        if (isResumed) existingBytes + contentLength else contentLength
                    } else {
                        2_580_000_000L
                    }

                    val bodyStream = response.body?.byteStream()
                        ?: throw IllegalStateException("Empty response body from model server.")

                    FileOutputStream(partFile, isResumed).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var bytesDownloaded = if (isResumed) existingBytes else 0L
                        var bytesInSession = 0L
                        val startTimeNs = System.nanoTime()
                        var lastUiUpdateMs = System.currentTimeMillis()
                        var read: Int

                        while (bodyStream.read(buffer).also { read = it } != -1) {
                            if (!isActive) throw CancellationException("Download cancelled")
                            output.write(buffer, 0, read)
                            bytesDownloaded += read
                            bytesInSession += read

                            val nowMs = System.currentTimeMillis()
                            if (nowMs - lastUiUpdateMs >= 250) {
                                val elapsedSec = ((System.nanoTime() - startTimeNs) / 1_000_000_000.0).coerceAtLeast(0.1)
                                val speedMbps = (bytesInSession / (1024.0 * 1024.0)) / elapsedSec
                                _modelState.value = GemmaModelUiState.Downloading(
                                    downloadedBytes = bytesDownloaded,
                                    totalBytes = totalBytes,
                                    speedMbps = speedMbps,
                                    targetFileName = targetFileName
                                )
                                lastUiUpdateMs = nowMs
                            }
                        }
                        output.flush()
                    }
                }

                if (finalFile.exists()) {
                    finalFile.delete()
                }
                partFile.renameTo(finalFile)
                initializeEngineFromFile(finalFile, _selectedAccelerator.value)
            } catch (ce: CancellationException) {
                _modelState.value = GemmaModelUiState.NotLoaded(
                    statusNote = "Download paused (${(partFile.length() / (1024 * 1024))} MB cached). Tap Download to resume."
                )
            } catch (e: Exception) {
                _modelState.value = GemmaModelUiState.Error(
                    message = "Download interrupted: ${e.localizedMessage ?: "Network error"}",
                    recoverableHint = "Tap Download to resume from ${(partFile.length() / (1024 * 1024))} MB, or pick a local .litertlm file."
                )
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    suspend fun initializeEngineFromFile(
        modelFile: File,
        requestedAccelerator: HardwareAccelerator
    ) = withContext(Dispatchers.IO) {
        if (!modelFile.exists() || modelFile.length() < 1024) {
            _modelState.value = GemmaModelUiState.Error(
                message = "Model file '${modelFile.name}' is missing or empty.",
                recoverableHint = "Please select or download a complete Gemma 4 E2B .litertlm bundle."
            )
            return@withContext
        }

        _modelState.value = GemmaModelUiState.Initializing(
            modelFileName = modelFile.name,
            accelerator = requestedAccelerator
        )

        closeEngine()

        try {
            val backend = when (requestedAccelerator) {
                HardwareAccelerator.GPU -> Backend.GPU()
                HardwareAccelerator.CPU -> Backend.CPU(optimalCpuThreads())
            }

            val engineConfig = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = backend,
                visionBackend = null,
                audioBackend = null,
                maxNumTokens = MAX_CONTEXT_TOKENS,
                cacheDir = context.cacheDir.absolutePath
            )

            val newEngine = Engine(engineConfig)
            newEngine.initialize()
            val newConversation = newEngine.createConversation(buildFastConversationConfig())

            engine = newEngine
            conversation = newConversation
            activeModelFile = modelFile
            turnsInCurrentConversation = 0
            _selectedAccelerator.value = requestedAccelerator

            val sizeMb = (modelFile.length() / (1024L * 1024L)).toInt().coerceAtLeast(1)
            _modelState.value = GemmaModelUiState.Ready(
                modelFileName = modelFile.name,
                modelFilePath = modelFile.absolutePath,
                fileSizeMb = sizeMb,
                accelerator = requestedAccelerator
            )
        } catch (gpuError: Throwable) {
            if (requestedAccelerator == HardwareAccelerator.GPU) {
                initializeCpuFallback(modelFile)
            } else {
                _modelState.value = GemmaModelUiState.Error(
                    message = "LiteRT-LM Engine error: ${gpuError.localizedMessage ?: gpuError.javaClass.simpleName}",
                    recoverableHint = "Check available RAM (~3 GB free recommended for Gemma 4 E2B) and valid .litertlm format."
                )
            }
        }
    }

    private fun initializeCpuFallback(modelFile: File): Boolean {
        return try {
            closeEngine()
            val cpuConfig = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(optimalCpuThreads()),
                visionBackend = null,
                audioBackend = null,
                maxNumTokens = MAX_CONTEXT_TOKENS,
                cacheDir = context.cacheDir.absolutePath
            )
            val cpuEngine = Engine(cpuConfig)
            cpuEngine.initialize()
            val cpuConv = cpuEngine.createConversation(buildFastConversationConfig())
            engine = cpuEngine
            conversation = cpuConv
            activeModelFile = modelFile
            turnsInCurrentConversation = 0
            _selectedAccelerator.value = HardwareAccelerator.CPU
            val sizeMb = (modelFile.length() / (1024L * 1024L)).toInt().coerceAtLeast(1)
            _modelState.value = GemmaModelUiState.Ready(
                modelFileName = modelFile.name,
                modelFilePath = modelFile.absolutePath,
                fileSizeMb = sizeMb,
                accelerator = HardwareAccelerator.CPU
            )
            true
        } catch (cpuError: Throwable) {
            _modelState.value = GemmaModelUiState.Error(
                message = "LiteRT-LM Engine failed to initialize '${modelFile.name}': ${cpuError.localizedMessage ?: cpuError.javaClass.simpleName}",
                recoverableHint = "Verify the file is a valid .litertlm bundle compiled for LiteRT-LM."
            )
            false
        }
    }

    fun cancelActiveGeneration() {
        runCatching { conversation?.cancelProcess() }
    }

    fun resetConversationSession() {
        val activeEngine = engine ?: return
        runCatching {
            conversation?.close()
            conversation = activeEngine.createConversation(buildFastConversationConfig())
            turnsInCurrentConversation = 0
        }
    }

    private fun sanitizeModelText(raw: String): String {
        return raw
            .replace(Regex("<\\|thought\\|>[\\s\\S]*?<\\|/thought\\|>"), "")
            .replace(Regex("<think>[\\s\\S]*?</think>"), "")
            .replace(Regex("^<\\|thought\\|>[\\s\\S]*"), "")
            .trimStart()
    }

    private fun hasReachedConciseLimit(cleanText: String): Boolean {
        val nonEmptyLines = cleanText.lines().filter { it.isNotBlank() }
        val bulletLines = nonEmptyLines.count {
            val t = it.trimStart()
            t.startsWith("-") || t.startsWith("•") || t.startsWith("*") || t.matches(Regex("^\\d+\\..*"))
        }
        val wordCount = cleanText.split(Regex("\\s+")).size
        return (bulletLines >= 4 && wordCount >= 55 && cleanText.endsWith(".")) ||
            (wordCount >= MAX_ANSWER_WORDS && (cleanText.endsWith(".") || cleanText.endsWith("\n")))
    }

    /**
     * Streams a direct response from Gemma 4 E2B:
     * - Automatically computes the exact 24H, 7D, and 30D metrics (or specific day of week / rest day stats)
     *   relevant to the user's question in ~70 tokens so prefill is fast and accurate.
     * - Properly handles `cancelProcess()` when `stoppedByWordLimit` is reached so the streamed
     *   Gemma 4 response is NEVER overwritten by a fallback catch block.
     */
    fun streamDiscussion(
        userPrompt: String,
        healthSnapshot: HealthSummarySnapshot?,
        includeHealthContext: Boolean
    ): Flow<StreamingInferenceChunk> = flow {
        val currentState = _modelState.value
        val activeSnapshot = healthSnapshot ?: HealthSummarySnapshot()

        val grounded = analyticsToolbox.buildGroundedContextForQuery(userPrompt, activeSnapshot)

        val promptToSend = if (includeHealthContext && healthSnapshot != null) {
            "${grounded.compactPromptContext}\n\nQuestion: $userPrompt\nAnswer directly in 1 to 3 short bullet points:"
        } else {
            "Question: $userPrompt\nAnswer directly in 1 to 3 short bullet points:"
        }

        val fastExtraContext = mapOf<String, Any>("enable_thinking" to false)

        // Always create a fresh lightweight Conversation on the loaded Engine (<2ms)
        // so stale turns in the KV cache never override the latest Health Connect telemetry or slow down prefill.
        val activeEngine = engine
        if (activeEngine != null && (conversation == null || turnsInCurrentConversation >= 1)) {
            runCatching {
                conversation?.close()
                conversation = activeEngine.createConversation(buildFastConversationConfig())
                turnsInCurrentConversation = 0
            }
        }

        val currentConv = conversation
        if (currentConv != null && currentState is GemmaModelUiState.Ready) {
            val accumulated = StringBuilder()
            var chunkCount = 0
            var firstTokenTimeNs = 0L
            var stoppedByWordLimit = false
            var activeBadge = "Gemma 4 (${currentState.accelerator.badge})"

            try {
                turnsInCurrentConversation++
                currentConv.sendMessageAsync(promptToSend, fastExtraContext).collect { messageChunk ->
                    val tokenText = messageChunk.toString()
                    if (tokenText.isNotEmpty()) {
                        if (firstTokenTimeNs == 0L) {
                            firstTokenTimeNs = System.nanoTime()
                        }
                        accumulated.append(tokenText)
                        chunkCount++
                        val decodeElapsedSec = ((System.nanoTime() - firstTokenTimeNs) / 1_000_000_000.0).coerceAtLeast(0.04)
                        val cleanText = sanitizeModelText(accumulated.toString())
                        if (cleanText.isNotEmpty()) {
                            val reachedLimit = hasReachedConciseLimit(cleanText)
                            emit(
                                StreamingInferenceChunk(
                                    accumulatedText = cleanText,
                                    tokensPerSec = chunkCount / decodeElapsedSec,
                                    backendLabel = activeBadge,
                                    toolBadgeLabel = if (includeHealthContext) grounded.badgeLabel else "",
                                    toolCalculationsContext = if (includeHealthContext) grounded.compactPromptContext else "",
                                    isCompleted = reachedLimit
                                )
                            )
                            if (reachedLimit && !stoppedByWordLimit) {
                                stoppedByWordLimit = true
                                runCatching { currentConv.cancelProcess() }
                            }
                        }
                    }
                }
                val totalDecodeSec = if (firstTokenTimeNs > 0L) {
                    ((System.nanoTime() - firstTokenTimeNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                } else 0.1
                val finalClean = sanitizeModelText(accumulated.toString()).ifBlank {
                    grounded.directDataAnswer
                }
                emit(
                    StreamingInferenceChunk(
                        accumulatedText = finalClean,
                        tokensPerSec = chunkCount / totalDecodeSec,
                        backendLabel = activeBadge,
                        toolBadgeLabel = if (includeHealthContext) grounded.badgeLabel else "",
                        toolCalculationsContext = if (includeHealthContext) grounded.compactPromptContext else "",
                        isCompleted = true
                    )
                )
                return@flow
            } catch (firstError: Throwable) {
                // CRITICAL FIX: When cancelProcess() is called after reaching the word limit,
                // LiteRT-LM throws CancellationException. Preserve the streamed Gemma 4 answer!
                val partialClean = sanitizeModelText(accumulated.toString())
                if (stoppedByWordLimit || partialClean.length >= 24) {
                    val totalDecodeSec = if (firstTokenTimeNs > 0L) {
                        ((System.nanoTime() - firstTokenTimeNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                    } else 0.1
                    emit(
                        StreamingInferenceChunk(
                            accumulatedText = partialClean,
                            tokensPerSec = chunkCount / totalDecodeSec,
                            backendLabel = activeBadge,
                            toolBadgeLabel = if (includeHealthContext) grounded.badgeLabel else "",
                            toolCalculationsContext = if (includeHealthContext) grounded.compactPromptContext else "",
                            isCompleted = true
                        )
                    )
                    return@flow
                }

                // If 0 tokens were generated, first try resetting the Conversation on the existing Engine
                val loadedEngine = engine
                if (loadedEngine != null) {
                    var retryStoppedByLimit = false
                    try {
                        runCatching { conversation?.close() }
                        val freshConv = loadedEngine.createConversation(buildFastConversationConfig())
                        conversation = freshConv
                        turnsInCurrentConversation = 1
                        accumulated.clear()
                        chunkCount = 0
                        firstTokenTimeNs = 0L

                        freshConv.sendMessageAsync(promptToSend, fastExtraContext).collect { messageChunk ->
                            val tokenText = messageChunk.toString()
                            if (tokenText.isNotEmpty()) {
                                if (firstTokenTimeNs == 0L) {
                                    firstTokenTimeNs = System.nanoTime()
                                }
                                accumulated.append(tokenText)
                                chunkCount++
                                val decodeElapsedSec = ((System.nanoTime() - firstTokenTimeNs) / 1_000_000_000.0).coerceAtLeast(0.04)
                                val cleanText = sanitizeModelText(accumulated.toString())
                                if (cleanText.isNotEmpty()) {
                                    val reachedLimit = hasReachedConciseLimit(cleanText)
                                    emit(
                                        StreamingInferenceChunk(
                                            accumulatedText = cleanText,
                                            tokensPerSec = chunkCount / decodeElapsedSec,
                                            backendLabel = activeBadge,
                                            toolBadgeLabel = if (includeHealthContext) grounded.badgeLabel else "",
                                            toolCalculationsContext = if (includeHealthContext) grounded.compactPromptContext else "",
                                            isCompleted = reachedLimit
                                        )
                                    )
                                    if (reachedLimit && !retryStoppedByLimit) {
                                        retryStoppedByLimit = true
                                        runCatching { freshConv.cancelProcess() }
                                    }
                                }
                            }
                        }
                        val totalDecodeSec = if (firstTokenTimeNs > 0L) {
                            ((System.nanoTime() - firstTokenTimeNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                        } else 0.1
                        val finalClean = sanitizeModelText(accumulated.toString()).ifBlank {
                            grounded.directDataAnswer
                        }
                        emit(
                            StreamingInferenceChunk(
                                accumulatedText = finalClean,
                                tokensPerSec = chunkCount / totalDecodeSec,
                                backendLabel = activeBadge,
                                toolBadgeLabel = if (includeHealthContext) grounded.badgeLabel else "",
                                toolCalculationsContext = if (includeHealthContext) grounded.compactPromptContext else "",
                                isCompleted = true
                            )
                        )
                        return@flow
                    } catch (_: Throwable) {
                        val retryClean = sanitizeModelText(accumulated.toString())
                        if (retryStoppedByLimit || retryClean.length >= 24) {
                            val totalDecodeSec = if (firstTokenTimeNs > 0L) {
                                ((System.nanoTime() - firstTokenTimeNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                            } else 0.1
                            emit(
                                StreamingInferenceChunk(
                                    accumulatedText = retryClean,
                                    tokensPerSec = chunkCount / totalDecodeSec,
                                    backendLabel = activeBadge,
                                    toolBadgeLabel = if (includeHealthContext) grounded.badgeLabel else "",
                                    toolCalculationsContext = if (includeHealthContext) grounded.compactPromptContext else "",
                                    isCompleted = true
                                )
                            )
                            return@flow
                        }
                    }
                }

                // Only if GPU failed to execute at all, fall back to CPU engine
                val modelFile = activeModelFile ?: listInstalledModelFiles().firstOrNull()
                if (currentState.accelerator == HardwareAccelerator.GPU &&
                    modelFile != null && modelFile.exists() && initializeCpuFallback(modelFile)
                ) {
                    val cpuConv = conversation
                    if (cpuConv != null) {
                        var cpuStoppedByLimit = false
                        try {
                            accumulated.clear()
                            chunkCount = 0
                            firstTokenTimeNs = 0L
                            activeBadge = "Gemma 4 (LiteRT CPU)"
                            cpuConv.sendMessageAsync(promptToSend, fastExtraContext).collect { messageChunk ->
                                val tokenText = messageChunk.toString()
                                if (tokenText.isNotEmpty()) {
                                    if (firstTokenTimeNs == 0L) {
                                        firstTokenTimeNs = System.nanoTime()
                                    }
                                    accumulated.append(tokenText)
                                    chunkCount++
                                    val decodeElapsedSec = ((System.nanoTime() - firstTokenTimeNs) / 1_000_000_000.0).coerceAtLeast(0.04)
                                    val cleanText = sanitizeModelText(accumulated.toString())
                                    if (cleanText.isNotEmpty()) {
                                        val reachedLimit = hasReachedConciseLimit(cleanText)
                                        emit(
                                            StreamingInferenceChunk(
                                                accumulatedText = cleanText,
                                                tokensPerSec = chunkCount / decodeElapsedSec,
                                                backendLabel = activeBadge,
                                                toolBadgeLabel = if (includeHealthContext) grounded.badgeLabel else "",
                                                toolCalculationsContext = if (includeHealthContext) grounded.compactPromptContext else "",
                                                isCompleted = reachedLimit
                                            )
                                        )
                                        if (reachedLimit && !cpuStoppedByLimit) {
                                            cpuStoppedByLimit = true
                                            runCatching { cpuConv.cancelProcess() }
                                        }
                                    }
                                }
                            }
                            val totalDecodeSec = if (firstTokenTimeNs > 0L) {
                                ((System.nanoTime() - firstTokenTimeNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                            } else 0.1
                            val finalClean = sanitizeModelText(accumulated.toString()).ifBlank {
                                grounded.directDataAnswer
                            }
                            emit(
                                StreamingInferenceChunk(
                                    accumulatedText = finalClean,
                                    tokensPerSec = chunkCount / totalDecodeSec,
                                    backendLabel = activeBadge,
                                    toolBadgeLabel = if (includeHealthContext) grounded.badgeLabel else "",
                                    toolCalculationsContext = if (includeHealthContext) grounded.compactPromptContext else "",
                                    isCompleted = true
                                )
                            )
                            return@flow
                        } catch (_: Throwable) {
                            val cpuClean = sanitizeModelText(accumulated.toString())
                            if (cpuStoppedByLimit || cpuClean.length >= 24) {
                                val totalDecodeSec = if (firstTokenTimeNs > 0L) {
                                    ((System.nanoTime() - firstTokenTimeNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                                } else 0.1
                                emit(
                                    StreamingInferenceChunk(
                                        accumulatedText = cpuClean,
                                        tokensPerSec = chunkCount / totalDecodeSec,
                                        backendLabel = activeBadge,
                                        toolBadgeLabel = if (includeHealthContext) grounded.badgeLabel else "",
                                        toolCalculationsContext = if (includeHealthContext) grounded.compactPromptContext else "",
                                        isCompleted = true
                                    )
                                )
                                return@flow
                            }
                        }
                    }
                }
            }
        }

        // Only used when the user has not downloaded/loaded the Gemma 4 model yet
        val directAnswer = grounded.directDataAnswer
        val words = directAnswer.split(" ")
        val builder = StringBuilder()
        val startNs = System.nanoTime()
        for (i in words.indices) {
            builder.append(words[i]).append(if (i < words.lastIndex) " " else "")
            if (i % 6 == 0 || i == words.lastIndex) {
                delay(10)
                val elapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.04)
                emit(
                    StreamingInferenceChunk(
                        accumulatedText = builder.toString(),
                        tokensPerSec = (i + 1) / elapsedSec,
                        backendLabel = "On-Device Biometric Engine",
                        toolBadgeLabel = if (includeHealthContext) grounded.badgeLabel else "",
                        toolCalculationsContext = if (includeHealthContext) grounded.compactPromptContext else "",
                        isCompleted = i == words.lastIndex
                    )
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    fun closeEngine() {
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
        conversation = null
        engine = null
        turnsInCurrentConversation = 0
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        }.getOrNull()
    }

    private fun queryFileSize(uri: Uri): Long? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getLong(sizeIndex)
                } else null
            }
        }.getOrNull()
    }
}
