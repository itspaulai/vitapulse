# VitalPulse — On-Device Health Connect & Gemma 4 E2B Companion

VitalPulse is a 100% private, offline-capable Android health intelligence app that bridges **Android Health Connect** with an on-device **Gemma 4 E2B** language model powered by **LiteRT-LM**. Users can explore their real-time vitals, sleep, and activity records, ask nuanced natural-language questions with automatic biometric context injection, and save conversations or pinned insights locally on their device.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following product and architectural decisions reflect your confirmed preferences from our planning interview and govern how the app will be built:

- **Confirmed Decision 1 — Health Connect Categories**: The app integrates with Android Health Connect (`androidx.health.connect:connect-client`) across three core domains:
  - **Heart & Vitals**: Heart Rate (`HeartRateRecord`), Heart Rate Variability (`HeartRateVariabilityRmssdRecord`), Blood Pressure (`BloodPressureRecord`), and Oxygen Saturation (`OxygenSaturationRecord`).
  - **Sleep & Recovery**: Sleep Sessions & Stages (`SleepSessionRecord`) and Resting Heart Rate (`RestingHeartRateRecord`).
  - **Activity & Steps**: Steps (`StepsRecord`), Distance (`DistanceRecord`), Total/Active Calories (`TotalCaloriesBurnedRecord`, `ActiveCaloriesBurnedRecord`), and Workouts (`ExerciseSessionRecord`).
- **Confirmed Decision 2 — Gemma 4 E2B Model Provisioning**: Dual-mode model manager supporting an **in-app HTTP downloader** (with real-time byte progress, speed, pause/cancel, and Hugging Face token support if required for gated weights) alongside a **local `.litertlm` / `.bin` file picker fallback** (`ActivityResultContracts.OpenDocument`) so pre-downloaded models can be linked immediately.
- **Confirmed Decision 3 — Material Design 3 Expressive Aesthetic**: Styled in the **Vitality Mint & Deep Forest** palette with expressive M3 tokens, spring-driven shape morphing (`RoundedCornerShape` transitions on active cards/buttons), pill and squircle surface hierarchy, expressive `ToggleButton` / `ButtonGroup` selectors, and custom Google Fonts (`Plus Jakarta Sans` display headers, `DM Sans` body copy, and `JetBrains Mono` for telemetry readouts).
- **Confirmed Decision 4 — Assistant & Persistence Capabilities**:
  - **Dynamic One-Tap Contextual Prompt Chips** synthesized directly from the user's latest Health Connect metrics (e.g., *"Analyze my 7h 15m sleep & 56 bpm resting HR"*).
  - **Local Room Database** storing multi-turn chat sessions, message history with attached health snapshots, and user-pinned insights for instant offline retrieval.
  - **Health Connect Record Writer Utility**: Includes an optional in-app ability to write realistic sample wellness records directly into Health Connect (`insertRecords`) if the device or emulator currently has an empty Health Connect store, ensuring immediate end-to-end testing.

---

### 1. Overview & Core Concept

- **What It Does**: Connects directly to the Android Health Connect platform, aggregates multi-day biometric trends into structured context snapshots, and runs inference locally using Google AI Edge's **LiteRT-LM (`com.google.ai.edge.litertlm`)** runtime with **Gemma 4 E2B** (`gemma-4-E2B-it.litertlm`).
- **Target Audience / Persona**: Privacy-conscious Android users, quantified-self enthusiasts, and wellness trackers who want deep, conversational insights into their heart, sleep, and activity metrics without uploading sensitive personal health records to cloud servers.
- **Key Value**: Complete data sovereignty (zero cloud telemetry for health data or LLM prompts), expressive tactile visualizations of daily readiness, and grounded conversational answers tied to actual biometric measurements.

---

### 2. User Experience & Visual Design

#### Key User Flows
1. **Onboarding, Permissions & Model Readiness Bar**:
   - At launch, a unified **Expressive Status Header** shows two live pills: **Health Connect Status** (Connected / Permissions Needed / SDK Unavailable) and **Gemma 4 E2B Engine Status** (Ready on GPU/CPU / Loading / Model Required).
   - Tapping the Health Connect pill launches the native Android Health Connect permission sheet (`PermissionController.createRequestPermissionResultContract()`). If the device has zero recorded metrics, users can tap **"Populate Sample Records to Health Connect"** to insert real `StepsRecord`, `HeartRateRecord`, `SleepSessionRecord`, and `BloodPressureRecord` entries into Health Connect.
2. **Gemma 4 E2B Model Hub (Bottom Sheet / Dedicated Drawer)**:
   - Users can download the `gemma-4-E2B-it.litertlm` bundle via an in-app resumable downloader showing live MB/GB progress, transfer rate, and hardware backend selection (`GPU` accelerated default with `CPU` fallback), or tap **"Select Local `.litertlm` File"** to load a model from device storage.
3. **Expressive Vitals & Activity Pulse Dashboard**:
   - Displays asymmetric, high-contrast cards for **Heart & Vitals**, **Sleep & Recovery**, and **Activity & Workouts** over a selectable time window (24 Hours, 7 Days, 14 Days).
   - Tapping any metric card morphs its corner radius with a bouncy spring animation and offers a **"Discuss with Gemma"** quick action that pre-loads that metric's detailed time-series into the chat composer.
4. **100% Local Gemma 4 Chat & Contextual Prompt Chips**:
   - Above the chat input bar, a horizontal carousel of **One-Tap Contextual Prompt Chips** adapts to the user's real data (e.g., *"Why was my HRV lower after yesterday's workout?"*, *"Compare my 7-day sleep stages against my step count"*, *"Evaluate my resting heart rate & SpO2 stability"*).
   - Each message displays a collapsible **"Included Health Context"** badge showing the exact local metrics injected into the Gemma 4 E2B prompt, followed by real-time streaming tokens (`sendMessageAsync` Flow) and tokens-per-second telemetry.
5. **Pinned Insights & Saved Sessions Vault**:
   - Any assistant response can be bookmarked with a single tap into the **Pinned Insights Vault** (backed by Room), tagged by category (`Heart`, `Sleep`, `Activity`, `General`), and searched or revisited alongside past conversation threads.

#### Visual Identity & Theme (Material Design 3 Expressive)
- **Aesthetic Direction**: Organic Bio-Expressive — combining botanical deep forest surfaces with kinetic mint accents, oversized typographic numerals, and tactile shape morphing inspired by Material 3 Expressive guidelines.
- **Color Palette & Mood (Vitality Mint & Deep Forest)**:
  - **Dark Posture (Default Expressive Mode)**:
    - Background / Surface: Deep Forest Obsidian (`#0A1410`) & Elevated Moss Surface (`#12211B`)
    - Surface Containers: Rich Pine (`#182C24`) to High Container (`#213B31`)
    - Primary Accent: Vitality Mint (`#52F2A8`) with On-Primary Deep Forest (`#003822`)
    - Secondary Accent: Eucalyptus Sage (`#A6D4BE`) & Container (`#234738`)
    - Tertiary / Vitals Highlight: Coral Pulse (`#FF8A75`) for heart rate peaks and Amber Dawn (`#FFD166`) for sleep recovery stages
  - **Light Posture**:
    - Background / Surface: Mist Alabaster (`#F3FAF6`) & Crisp Mint-Tinted Surface (`#E6F4EC`)
    - Primary Accent: Deep Emerald (`#006C45`) with Vitality Mint Containers (`#8DF7C2`)
- **Typography & Hierarchy**:
  - **Display & Headlines (`Plus Jakarta Sans`)**: Bold, expressive numerals (`36sp`–`48sp`, tight `-0.5sp` tracking) for daily step counts, BPM, and sleep duration headers.
  - **Body & Chat (`DM Sans`)**: Highly legible, warm geometric sans (`15sp`–`16sp`, `24sp` line height) for multi-paragraph Gemma 4 explanations.
  - **Biometric Telemetry & Model Stats (`JetBrains Mono`)**: Monospace precision (`12sp`–`13sp`) for HRV ms, SpO2 %, mmHg blood pressure, and LiteRT inference tok/s badges.
- **Component Styling & Layout**:
  - **Expressive Shape Morphing**: Cards and toggle buttons transition smoothly between `32.dp` pill shapes (unselected) and `16.dp` squircle shapes (selected/expanded) using `animateDpAsState` with `Spring.DampingRatioLowBouncy`.
  - **Segmented Expressive Button Groups**: Custom M3 Expressive connected button groups for switching time windows (`Today`, `7D`, `30D`) and hardware delegates (`GPU`, `CPU`).

#### Interactive Feedback & Motion
- **Spring Physics**: All card expansions, prompt chip selections, and navigation transitions use M3 expressive spring specs (`stiffness = Spring.StiffnessMediumLow`, `dampingRatio = Spring.DampingRatioLowBouncy`).
- **Live Streaming Pulse**: While Gemma 4 E2B generates tokens on-device, an animated organic waveform border glows around the active assistant bubble.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: On-Device LiteRT-LM Runtime (`com.google.ai.edge.litertlm`) with Tool/Context Augmentation**
  - *Chosen Approach*: Use Google AI Edge's official `litertlm-android` SDK (`Engine`, `EngineConfig`, `Conversation`) to load the `.litertlm` Gemma 4 E2B model locally, paired with an automatic **Health Context Serializer** that formats the user's latest Health Connect aggregations into a structured system/context block before streaming inference.
  - *Why*: Gives Gemma 4 E2B immediate, accurate grounding in the user's real Health Connect numbers without requiring fragile multi-round tool calls on small parameter models, while also registering a local LiteRT tool definition so Gemma can query specific date ranges on demand.
  - *Alternatives Considered*: Cloud LLM APIs were rejected because the user explicitly requires 100% local on-device health discussion.
- **Decision 2: Dual Model Loader (Resumable Downloader + Local File Picker + Diagnostic Mode)**
  - *Chosen Approach*: Provide both an in-app HTTP stream downloader (with configurable URL pre-filled for Gemma 4 E2B `.litertlm` and optional Hugging Face bearer token input) and a zero-permission SAF file picker (`OpenDocument`) that copies or maps a local `.litertlm` file into app storage. If the user wants to test the UI flow before the 2.58 GB download finishes, clearly labeled local model state diagnostics guide them through setup.
  - *Why*: A 2.58 GB model download can take several minutes depending on network speed, or a user may already have `gemma-4-E2B-it.litertlm` pushed to `/sdcard/Download/`. Supporting both makes onboarding frictionless.
- **Decision 3: Strictly Local Room Persistence (`AppDatabase`)**
  - *Chosen Approach*: Store all chat threads, messages, health context snapshots, and pinned insights in a local SQLite database via Jetpack Room with KSP and reactive `Flow` queries.
  - *Why*: Aligns with the 100% local privacy guarantee and confirmed user selection, ensuring zero health data ever leaves the device.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

#### Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                   Jetpack Compose UI (M3 Expressive)                       │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────┐  │
│  │  Pulse Dashboard     │  │  Gemma 4 Local Chat  │  │  Insights Vault  │  │
│  │  • Vitals/Sleep/Act  │  │  • Contextual Chips  │  │  • Pinned Cards  │  │
│  │  • Expressive Cards  │  │  • Streaming Bubble  │  │  • Chat History  │  │
│  │  • Time Window Group │  │  • Context Inspector │  │  • Category Tags │  │
│  └──────────┬───────────┘  └──────────┬───────────┘  └────────┬─────────┘  │
│             └─────────────────────────┼───────────────────────┘            │
│                                       ▼                                    │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                VitalPulseViewModel (StateFlow / MVVM)                │  │
│  └────────────┬───────────────────────┼─────────────────────┬───────────┘  │
└───────────────┼───────────────────────┼─────────────────────┼──────────────┘
                ▼                       ▼                     ▼
┌───────────────────────────┐ ┌─────────────────────┐ ┌──────────────────────┐
│  HealthConnectRepository  │ │  GemmaLiteRtEngine  │ │  LocalChatRepository │
│  • HealthConnectClient    │ │  • LiteRT-LM Engine │ │  • Room AppDatabase  │
│  • Permission Controller  │ │  • GPU/CPU Backend  │ │  • ChatSessionDao    │
│  • Read/Aggregate Records │ │  • Conversation API │ │  • ChatMessageDao    │
│  • Sample Data Seeder     │ │  • Model Downloader │ │  • PinnedInsightDao  │
└───────────────────────────┘ └─────────────────────┘ └──────────────────────┘
```

#### Data Model & State
- **Health Connect Domain Snapshot (`HealthSummarySnapshot`)**:
  - **Heart & Vitals**: Latest & 7-day average Heart Rate (bpm, min/max), RMSSD Heart Rate Variability (ms), Systolic/Diastolic Blood Pressure (mmHg), and SpO2 percentage (%).
  - **Sleep & Recovery**: Total sleep duration (hours/minutes), sleep stage breakdown (Deep, REM, Light, Awake minutes), and Resting Heart Rate (bpm).
  - **Activity & Steps**: Daily & 7-day step counts, total distance (km), active & total kilocalories burned, and recent workout sessions (type, duration, calories).
- **Room Database Entities (`AppDatabase`)**:
  - `ChatSessionEntity`: `id`, `title`, `createdAt`, `updatedAt`, `summaryPreview`.
  - `ChatMessageEntity`: `id`, `sessionId`, `role` (`USER` / `ASSISTANT`), `content`, `attachedHealthSnapshotJson`, `inferenceSpeedTokPerSec`, `timestamp`.
  - `PinnedInsightEntity`: `id`, `title`, `content`, `category` (`HEART`, `SLEEP`, `ACTIVITY`, `HOLISTIC`), `metricsBadge`, `createdAt`.
- **LiteRT-LM Model State (`ModelUiState`)**:
  - Sealed state tracking `NotDownloaded`, `Downloading(bytesDownloaded, totalBytes, speedMbps)`, `InitializingEngine(backend)`, `Ready(modelName, modelPath, backend, fileSizeMb)`, and `Error(message)`.

#### Interactive Component & State Mapping
- **Permission & Availability Flow**: Checks `HealthConnectClient.getSdkStatus(context)` on startup and whenever the app resumes (`LifecycleEventObserver`). Grants or revokes update `healthPermissionState` immediately and trigger a fresh coroutine aggregation across the selected time range (`24H`, `7D`, `30D`).
- **Contextual Prompt Chip Synthesis**: Whenever `HealthSummarySnapshot` updates, the ViewModel derives 4–6 tailored prompt chips quoting the user's real numbers (e.g., *"My resting HR is 58 bpm and HRV is 64 ms—how is my recovery?"*). Tapping a chip populates or immediately dispatches the prompt with the current biometric snapshot.
- **LiteRT-LM Streaming Conversation**: Sending a message constructs a grounded prompt containing the serialized `HealthSummarySnapshot` (if context attachment is toggled on), invokes `conversation.sendMessageAsync(Message.of(prompt))` on `Dispatchers.IO`, and collects streaming partial tokens into the active assistant message state while computing real-time token throughput.
- **Model File Management**: Users can start/cancel the background coroutine download (using `OkHttpClient` streaming directly to `context.filesDir/models/gemma-4-e2b.litertlm`), pick a local file via `ActivityResultContracts.OpenDocument`, or switch between `Backend.GPU()` and `Backend.CPU()` with automatic `Engine` re-initialization and cleanup (`engine.close()`).
