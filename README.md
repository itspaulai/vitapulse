# VitalPulse

**VitalPulse** is a 100% local, privacy-first Android wellness companion that pairs **Android Health Connect** telemetry with on-device **Gemma 4 E2B (`.litertlm`)** inference powered by the **Google AI Edge LiteRT-LM SDK** and **Material Design 3 Expressive** UI.

All biometric synchronization, multi-window statistical aggregation (24H / 7D / 30D), and natural language wellness queries execute strictly on-device—your health records never leave your phone.

---

## Key Features

### 1. Live Health Connect Synchronization (`Pulse` Tab)
- **Readiness & Recovery Score**: Dynamically computed from Resting Heart Rate (RHR), Heart Rate Variability (`HeartRateVariabilityRmssdRecord`), Sleep duration/stages, and physical activity balance.
- **Heart & Vitals**:
  - Average, minimum, and maximum Heart Rate (BPM) with live fallback to the latest sensor sample.
  - Heart Rate Variability (HRV RMSSD in ms), Resting Heart Rate (RHR), Blood Pressure (Systolic/Diastolic mmHg), and Blood Oxygen saturation ($\text{SpO}_2$ %).
- **Sleep & Recovery**:
  - Total sleep duration along with Deep, REM, Light, and Awake stage breakdowns (`SleepSessionRecord`).
  - Multi-day sleep consistency and weekly/monthly averages.
- **Activity & Workouts**:
  - Deduplicated daily and window step counts, total distance (km), active/total kcal burned, and chronological `ExerciseSessionRecord` tracking (sorted newest-first with session-matched duration, distance, calories, and heart rate).
- **Interactive Time Windows**: Instant switching between **Last 24 Hours (24H)**, **Last 7 Days (7D)**, and **Last 30 Days (30D)** using official Health Connect `aggregateGroupByPeriod` and paginated reverse-chronological record streams.

---

### 2. On-Device Gemma 4 E2B Assistant (`Discuss` Tab)
- **Zero-Cloud Inference**: Runs `gemma-4-E2B-it.litertlm` locally on Android using `com.google.ai.edge.litertlm:litertlm-android`.
- **Automatic Hardware Acceleration & Fallback**:
  - Probes OpenCL / GPU availability before initialization and automatically falls back to multi-threaded CPU execution if native OpenCL libraries are unavailable on the device.
- **Resumable Model Provisioning**:
  - Built-in model manager supporting resumable HTTP `Range` downloads from Hugging Face, custom mirror URLs, or zero-copy import of an existing `.litertlm` / `.bin` file via the Android system file picker (`ActivityResultContracts.OpenDocument`).
  - Automatic file discovery across internal storage and `Downloads` so you never have to re-download the model after app updates.
- **Intent-Routed Biometric Grounding (`HealthAnalyticsToolbox`)**:
  - Automatically parses natural-language questions (e.g., *"What was my average sleep last week?"*, *"Tell me about my last workout"*, *"How many rest days did I take in the last 30 days?"*) and pre-calculates exact statistical aggregates from your 30-day Health Connect ledger before injecting a compact, focused context block into Gemma 4.

---

### 3. Local Persistence (`Saved` Tab)
- **Room Database (`VitalPulseDatabase`)**:
  - Stores conversation history and pinned insights locally on the device.
  - One-tap pinning of assistant answers to build a personal, searchable health log.

---

## Architecture & Tech Stack

| Layer | Technology |
| :--- | :--- |
| **UI Framework** | Jetpack Compose + Material Design 3 Expressive (*Vitality Mint & Deep Forest* palette) |
| **Typography** | Bundled Google Fonts (`Plus Jakarta Sans`, `Inter`, `JetBrains Mono`) |
| **Health Data** | AndroidX Health Connect Client (`androidx.health.connect:connect-client:1.1.0`) |
| **On-Device LLM** | Google AI Edge LiteRT-LM (`com.google.ai.edge.litertlm:litertlm-android:0.10.0`) |
| **Local Database** | AndroidX Room (`2.8.4`) with Kotlin Coroutines & `Flow` |
| **Networking (Model Download)** | OkHttp (`5.3.2`) with HTTP `Range` resume & Hugging Face Bearer token support |

### Project Structure

```text
app/src/main/java/com/example/
├── MainActivity.kt                  # Edge-to-edge Activity & Health Connect permission launcher
├── data/
│   ├── ai/
│   │   ├── GemmaLiteRtManager.kt    # LiteRT-LM Engine lifecycle, OpenCL probe, downloader & streaming
│   │   └── HealthAnalyticsToolbox.kt# Deterministic 24H/7D/30D calculator & intent router for chat queries
│   ├── health/
│   │   └── HealthConnectManager.kt  # Paginated Health Connect reads, aggregateGroupByPeriod & deduplication
│   ├── local/
│   │   ├── VitalPulseDao.kt         # Room DAO for chat messages and pinned insights
│   │   ├── VitalPulseDatabase.kt    # Local SQLite database configuration
│   │   └── VitalPulseEntities.kt    # Room entities (ChatMessageEntity, PinnedInsightEntity)
│   └── model/
│       └── HealthModels.kt          # Immutable domain models for vitals, sleep, workouts & daily ledgers
└── ui/
    ├── VitalPulseApp.kt             # Adaptive M3 Navigation Bar / Navigation Rail shell
    ├── VitalPulseViewModel.kt       # Unidirectional state holder coordinating Health Connect, Room & Gemma
    ├── components/
    │   └── ModelSetupSheet.kt       # Modal bottom sheet for downloading or importing Gemma 4 E2B
    ├── screens/
    │   ├── PulseDashboardScreen.kt  # Readiness hero banner, 24H/7D/30D selector & biometric cards
    │   ├── GemmaChatScreen.kt       # Streaming on-device chat with contextual prompt chips
    │   └── SavedInsightsScreen.kt   # Pinned insights & local management
    └── theme/
        ├── Color.kt                 # Vitality Mint & Deep Forest M3 color tokens
        ├── Theme.kt                 # Material 3 Expressive theme configuration
        └── Type.kt                  # Responsive typography scale
```

---

## Getting Started

### Prerequisites
- **Android Studio** (Ladybug or newer) / Android SDK 36 (`minSdk = 26`).
- An Android device with **Health Connect** installed (built into Android 14+, or available via Google Play on Android 9–13).
- ~2.5 GB of free storage on the device for the quantized `gemma-4-E2B-it.litertlm` model file.

### Building the App

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd vitalpulse
   ```

2. **Optional — Configure a Hugging Face Token**:
   If downloading gated model weights directly from Hugging Face inside the app, you can either paste your `hf_...` token into the in-app **Model Setup Sheet** or add it to `.env`:
   ```properties
   HF_TOKEN=hf_your_token_here
   ```

3. **Build and install the debug APK**:
   ```bash
   gradle :app:assembleDebug
   ```

### Loading the Gemma 4 E2B Model on Device
1. Open **VitalPulse** and tap the **Model Chip** in the top-right bar (or go to the **Discuss** tab).
2. Choose one of two options:
   - **Download in-app**: Tap **Download Gemma 4 E2B** (supports pause/resume).
   - **Select local file**: If you already downloaded `gemma-4-E2B-it.litertlm` to your phone's `Downloads` folder (or via `adb push`), tap **Select Local `.litertlm` File** to link and initialize it immediately.

---

## Privacy & Security

- **100% Local Processing**: Health Connect records are read directly into memory and analyzed on-device.
- **Zero External Telemetry**: No analytics SDKs, cloud LLM endpoints, or external database syncs are used.
- **Least-Privilege Health Permissions**: Requests read-only access strictly for Heart Rate, HRV, Resting Heart Rate, Blood Pressure, Oxygen Saturation, Sleep, Steps, Distance, Calories, and Exercise Sessions.
