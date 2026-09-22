# Troc — Your AI Agent with Sandboxed Superpowers

Premium AI assistant Android app built with 100% Kotlin + Jetpack Compose (Material 3), Clean Architecture, MVVM.

## 📦 APK Build

### GitHub Actions (Automatic)
- Push to `main` or `arena/**` triggers `.github/workflows/android-apk.yml`
- Builds debug + release APKs, uploads as artifacts (14-day retention)
- Requires `MISTRAL_API_KEY` and `GROQ_API_KEY` in repo Secrets (optional fallback)
- Download APK from Actions tab → Artifacts → `troc-debug-apk`

### Local Build
```bash
chmod +x build-apk.sh
./build-apk.sh
# APK at app/build/outputs/apk/debug/app-debug.apk
# Install: adb install app/build/outputs/apk/debug/app-debug.apk
```
Or manually:
```bash
./gradlew :app:assembleDebug
```

## Features

### 💬 Chat
- Streaming chat with Mistral AI (SSE via OkHttp)
- Markdown rendering (bold, italics, code blocks with copy, tables, lists) via compose-richtext
- User bubbles: purple gradient (#8B5CF6 → #6366F1), Assistant: transparent ChatGPT-style
- Auto-scroll, typing indicator (3 pulsing dots)

### 🤖 Agent Mode
- Multi-step tool-chained workflows
- System prompt instructs Mistral to emit JSON tool calls: `{"tool":"code|data|file","language":"python","input":"..."}`
- App parses, executes in sandbox, feeds results back, loops max 6 iterations
- Workflow builder UI above input bar: tool dropdown, input field, output var, add step, execute sequentially with variable passing

### 🧪 Sandboxed Execution
- **Code**: Chaquopy (Python 3.10 with numpy/pandas), Rhino (JS), Kotlin mock
  - Strict security: blocklist ProcessBuilder, Runtime.exec, network classes
  - Hard timeout via `withTimeoutOrNull`, temp files in `cacheDir` only
  - Heavy jobs offloaded to WorkManager with foreground notification
- **Data**: OpenCSV, stats (mean/median/sum), filter/sort, chart generation via MPAndroidChart (bar/pie/line)
- **Files**: Scoped storage, regex replace, case transform, ZIP extract/compress, metadata viewer
- **Custom**: Saved workflows persisted in Room

### 🎤 Voice — STT via Groq Whisper, TTS via Groq PlayAI
- AudioRecord 16kHz mono PCM16 → WAV in cacheDir
- VAD auto-stop after 1s silence, barge-in detection (amplitude > threshold during TTS cancels playback)
- State machine: Idle → Listening → Transcribing → Thinking (Mistral streaming) → Speaking (TTS queue) → Listening
- ExoPlayer gapless queue, AUDIOFOCUS_GAIN_TRANSIENT, speakerphone in Voice Mode
- VoiceOrb: Canvas-drawn, idle breathe, listening ripples reacting to amplitude, thinking rotation, speaking synced ripples
- Mic: hold-to-talk, slide-left cancel, amplitude ring, "Transcribing…" pill

### 🆕 New Features (v1.1)
- **Model Selector on Input Bar**: Dropdown chip showing current model (e.g., `mistral-small`), tap to switch between `small/medium/large/codestral/nemo/ministral-3b/8b`, live refresh from API, persists via DataStore
- **Live API Usage Tracking**: Real-time token & cost dashboard
  - Parses `usage` from Mistral streaming/non-streaming responses (`prompt_tokens`, `completion_tokens`)
  - Fallback estimation (1 token ≈ 4 chars) when usage not returned
  - Room DB `usage_stats` + `usage_history` tracks total, today, per-request, cost via `MistralModelPricing` (per-model $/1M tokens)
  - UI: top bar badge `12k/1M • 1.2%` + progress bar, input bar `3 today • $0.0021`, Settings → Live API Usage card with total/today/requests/cost, history, near-limit warnings, clear/refresh
  - Free plan limits: 1B tokens, 1000 req (configurable), shows remaining, % used, over-limit error
- **Web Search Mode**: 
  - Toggle chip `🌐 Web` on input bar, or `Web Search` suggestion
  - `WebSearchApi` uses DuckDuckGo Instant Answer (free, no key) + Wikipedia fallback, no API key needed
  - Search → formats results for LLM → injects into system prompt with citations
  - Agent Mode supports `{"tool":"web_search","input":"query"}` tool
  - Tool card shows search results, mini preview in input bar
  - Works offline? No, requires internet, graceful error handling

### Screens
- **Onboarding**: Logo animation, API key setup (Mistral + optional Groq) with Test buttons (Mistral GET /v1/models, Groq short TTS), feature tour (3 slides), RECORD_AUDIO rationale
- **Home**: Top bar logo + wordmark, Agent toggle (segmented), voice orb, new chat, history, settings, sandbox. Empty state greeting + 2x2 suggestion cards + Web Search. **Input bar** now: model selector chip + Web/Agent toggles + usage summary + attachment + web results preview + mic + TextField + attach + send/stop. Top usage badge shows live tokens % + progress. Web search indicator.
- **Sandbox**: Tabs Code/Data/Files/Custom, dark code editor with line numbers, language selector, timeout slider, console (stdout cyan, stderr red)
- **VoiceMode**: Immersive dark (#0F172A radial gradient), large orb center, mini-transcript, mute, keyboard, close
- **History**: Searchable, swipe-to-delete, tool badges, export Markdown/JSON, expandable sandbox sessions
- **Settings**: Grouped premium cards — **Live API Usage** (total/today/requests/cost/history/refresh/clear + limit warnings), API Keys (masked •••• + last 4, reveal, Test, EncryptedSharedPreferences), Model (dropdown, temp slider, custom endpoint), Voice (enable, auto-send/play, STT/TTS dropdowns with preview, speed, language), Sandbox (enable, timeout, tools), Appearance (System/Light/Dark, font size S/M/L, accent picker), Privacy (clear chats/sessions, export ZIP, wipe), About (version, docs links, Premium badge)

## Tech Stack
- Kotlin, Compose Material3, Navigation, MVVM + Clean Architecture
- Retrofit + OkHttp + Coroutines/Flow (streaming SSE)
- Hilt DI, Room, WorkManager, DataStore, EncryptedSharedPreferences (Android Keystore)
- Chaquopy (Python), Rhino (JS), OpenCSV, kotlinx.serialization, MPAndroidChart
- Media3 ExoPlayer, AudioRecord, compose-richtext, PermissionX/Accompanist
- Secrets Gradle Plugin (default key in local.properties, overridable in-app)

## Architecture
```
com.troc/
├── app/ MainActivity, TrocApplication
├── navigation/ NavGraph
├── data/
│   ├── api/ MistralApi (streaming+usage), GroqApi, WebSearchApi (DuckDuckGo+Wiki), AuthInterceptor
│   ├── audio/ AudioRecorder (Flow<Amplitude>), VadDetector, VoicePlayer
│   ├── sandbox/ CodeSandbox, DataSandbox, FileSandbox, SandboxSecurity
│   ├── repository/ ChatRepository, VoiceRepository, SandboxRepository, ApiKeyRepository, UsageRepository (live tracking), WebSearchRepository
│   ├── db/ Room: Chat, Message, SandboxSession, SavedWorkflow, UsageEntity, UsageHistory, UsageDao
│   ├── prefs/ EncryptedPrefs, SettingsDataStore
│   └── worker/ SandboxWorker
├── domain/
│   ├── model/ ChatMessage, ToolCall (web_search), SandboxResult, WorkflowStep, VoiceState, ApiKeyState, ApiUsage, UsageHistory, MistralModelPricing, ChatModeExtended
│   └── usecase/ SendMessage (web search + usage), RunAgentLoop, ExecuteSandbox, RunWorkflow, TranscribeAudio, SynthesizeSpeech, WebSearch
├── di/ AppModule, NetworkModule, SandboxModule, DatabaseModule
├── ui/
│   ├── screens/ Onboarding, Home (model selector + usage badge + web search), Sandbox, VoiceMode, History, Settings (Live Usage)
│   ├── components/ ChatBubble, MarkdownMessage, ToolCard, TypingIndicator, MicButton, VoiceOrb, WaveformVisualizer, ApiKeyField, UsageCard, WebSearchToggle
│   └── theme/ Theme, Color, Type, Shapes
├── viewmodel/ HomeViewModel (model selector, usage, web search), VoiceViewModel, SandboxViewModel, SettingsViewModel (usage dashboard), HistoryViewModel, OnboardingViewModel
└── util/ MarkdownRenderer, FileUtils, WavWriter, BackoffPolicy, Constants
```

## API Key System
- Single source: ApiKeyRepository backed by EncryptedSharedPreferences, exposing Flow<ApiKeyState>
- Precedence: user-pasted > local.properties fallback > locked
- Retrofit interceptors inject at request time, never hardcoded
- On 401: snackbar "Invalid API key — update it in Settings" with deep-link to Settings; graceful degradation (Mistral 401 → text tools disabled, Groq 401 → voice locked)
- Masking: •••• + last 4 chars, excluded from logs, backups, Room

## Mistral API
- Base https://api.mistral.ai, POST /v1/chat/completions streaming SSE, GET /v1/models validation
- Agent Mode JSON tool blocks parsed, executed, fed back as {"role":"tool","name":"code","content":"<output>"}
- Streaming emits chunks real-time, Stop cancels OkHttp call
- Errors: 401 → key flow, 429 → exponential backoff (2 retries), network → offline snackbar

## Groq Voice API
- STT: POST https://api.groq.com/openai/v1/audio/transcriptions, model whisper-large-v3/turbo, multipart WAV, temp 0
- TTS: POST /audio/speech, model playai-tts, voice from settings, response_format wav, limit 10,240 chars — chunk at sentence boundaries, sequential synth, gapless ExoPlayer queue
- Errors: empty transcription → "Couldn't hear that", TTS 401/403 → paid tier message, mic denied → locked state

## Theme
- Dark-first like ChatGPT: Primary #8B5CF6, Secondary #06B6D4, Tertiary #10B981, Dark bg #0F172A surface #1E293B variant #334155 text #E2E8F0 muted #94A3B8, Light bg #F8FAFC surface #FFFFFF text #1E293B
- Shapes: 24dp bubbles, 16dp cards, 12dp buttons, 16dp screen padding
- Motion: 300ms spring (stiffness 300 damping 30) panel expand/collapse, message fade + 200dp slide, typing dots staggered 150ms, sandbox pulse glow + shimmer
- Adaptive icon: stylized "T" chat-bubble + spark orb, gradient #8B5CF6 → #06B6D4, monochrome layer, mipmap-anydpi-v26 + legacy

## Setup

1. Clone, open in Android Studio Hedgehog+
2. Add keys to `local.properties` (optional fallback):
   ```
   MISTRAL_API_KEY=your_mistral_key
   GROQ_API_KEY=your_groq_key
   ```
   Or paste in-app via Onboarding/Settings — in-app wins.
3. Sync Gradle, Run. App opens even without keys, showing locked-state UI for affected features.

## Permissions
- INTERNET, RECORD_AUDIO, FOREGROUND_SERVICE, POST_NOTIFICATIONS

## Quality
- Idiomatic Kotlin: sealed classes, Flow, no !!, structured concurrency
- Compose best practices: remember/derivedStateOf/LaunchedEffect, stable hoisting, LazyColumn keys
- Performance: lazy keys, debounced input 300ms, WorkManager heavy jobs, sentence-level TTS
- No deprecated APIs, full error handling, deep-links from errors to Settings
- Unit tests: JUnit + MockK + Turbine — ChatViewModel, RunAgentLoop, ApiKeyRepository precedence, VoiceViewModel state machine

## License
Private — All rights reserved.
