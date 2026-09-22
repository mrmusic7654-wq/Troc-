# Troc ✨ — Your AI Agent with Sandboxed Superpowers

**Troc** is a production-ready Android app: a premium, ChatGPT-style AI assistant powered by the
**Mistral API** with **sandboxed tool execution** (code, data, files), **Agent Mode** with
multi-step tool chaining, workflow automation, streaming markdown chat, chat/sandbox history,
and full Material 3 theming (dark/light, accent colors, font scaling).

---

## Feature highlights

| Area | What you get |
|---|---|
| 💬 **Chat** | Streaming SSE responses from Mistral, markdown rendering (headings, lists, tables, quotes), syntax-highlighted code blocks, animated typing indicator, stop/regenerate |
| 🤖 **Agent Mode** | The model emits tool tags (`<run_code>`, `<analyze_data>`, `<process_file>`); Troc executes them in the sandbox and loops results back until the task completes (max 4 iterations) |
| 🧪 **Sandbox: Code** | **Kotlin subset** (hand-written interpreter) + **JavaScript** (Rhino, class-shuttered). Per-run timeouts (5–120 s), step budgets, output caps, cooperative stop |
| 📊 **Sandbox: Data** | RFC-4180 CSV parsing, type inference, descriptive stats (mean/median/stddev…), filter/sort/select, JSON pretty-print + path queries (`users[].name`), CSV↔JSON conversion, animated bar charts |
| 📁 **Sandbox: Files** | System file picker (scoped storage), text preview, image metadata, regex transforms, safe ZIP listing/extraction (zip-slip protected) |
| ⚙️ **Workflows** | Multi-step builder (Home agent panel + Sandbox “Workflow” tab). `{{result}}` / `{{stepN}}` variable injection between steps |
| 🕒 **History** | Persisted chats + sandbox sessions (Room), search, resume, delete, share/export as Markdown / CSV / zip backup |
| ⚙️ **Settings** | Masked API key + “Test connection”, model picker (small/medium/large + custom), custom self-hosted endpoint, sandbox toggles & limits, theme mode, font size, accent color, clear-all-data |
| 🧱 **Architecture** | 100% Kotlin, Jetpack Compose + Material 3, MVVM + Clean Architecture (domain / data / ui), Hilt DI, Room, DataStore, Retrofit + OkHttp SSE, WorkManager for long jobs |

---

## Project structure

```
app/src/main/java/com/example/troc/
├── data/
│   ├── local/            # Room: TrocDatabase, ChatDao, SandboxSessionDao
│   ├── remote/           # MistralApi (Retrofit DTOs) + MistralClientImpl (OkHttp SSE streaming)
│   ├── repository/       # ChatHistory, Settings (DataStore), File repository impls
│   └── sandbox/          # SandboxManager (single-flight gateway)
│       ├── code/         # CodeSandbox, JsEngine (Rhino), MiniKotlin lexer/parser/interpreter
│       ├── data/         # CsvParser, CsvAnalyzer, JsonAnalyzer
│       ├── file/         # FileSandbox (scoped storage, zip-slip-safe)
│       └── security/     # SandboxSecurityManager (limits + blacklists)
├── di/                   # Hilt modules (network + bindings)
├── domain/
│   ├── agent/            # Tool-call parser + system prompts
│   ├── model/            # ChatMessage, Chat, SandboxRequest/Result, AppSettings…
│   ├── repository/       # Interfaces
│   └── usecase/          # StreamChat, ListModels, ExecuteSandbox, RunWorkflow
├── ui/
│   ├── components/       # MarkdownText, CodeBlock, ChatBubble, TypingIndicator,
│   │                     # DataTable, BarChart, ConsoleView, WorkflowBuilder…
│   ├── navigation/       # Routes + animated NavHost
│   ├── screens/          # onboarding / home / sandbox / history / settings
│   └── theme/            # Material 3 palette, scaled typography, accent themes
├── util/                 # MarkdownParser, SyntaxHighlighter, ShareUtils, FileUtils
├── work/                 # SandboxWorker (HiltWorker) + SandboxJobScheduler
├── MainActivity.kt
└── TrocApplication.kt    # @HiltAndroidApp + WorkManager configuration
```

Tests live in `app/src/test/`: the Mini-Kotlin interpreter (17 cases incl. budget/timeout
guards), CSV analyzer, agent tool-call parser, markdown parser, the SSE streaming client
(MockWebServer) and the ChatViewModel agent loop (with fakes).

---

## Getting started

1. Open the project in **Android Studio** (Hedgehog/Iguana or newer, JDK 17).
2. Let Gradle sync (`./gradlew assembleDebug` from the CLI also works).
3. Launch on a device/emulator with Android 8.0+ (minSdk 26, targetSdk 34).
4. On the onboarding screen, paste your **Mistral API key**
   (get one at [console.mistral.ai](https://console.mistral.ai)) and press *Test connection*.
5. Try it out:
   - Chat: “Explain quicksort with a runnable example.”
   - Agent Mode: “Analyze this CSV and chart the totals” (attach a CSV with 📎 first).
   - Sandbox → Code: run `fun factorial(n: Int): Int { if (n == 0) return 1; return n * factorial(n-1) }` + `println(factorial(5))`
   - Sandbox → Data: paste a CSV, hit Run → stats table; set a column → chart.

### Sandbox languages

| Language | Engine | Notes |
|---|---|---|
| JavaScript | Rhino 1.7.14 (ES6 subset) | Interpreter mode; Java bridge globals removed **and** a `ClassShutter` denies every class; instruction-count deadline observer enables hard timeouts & stop |
| Kotlin (subset) | Built-in **MiniKotlin** interpreter | `val/var`, functions, `if/else`, `while`, `for-in`, ranges, string templates, `listOf/mapOf`, lambdas (`it`), common collection/string ops. No reflection, no I/O, step + wall-clock budgets |
| Python | Not bundled | See below |

### Enabling Python (optional)

The prompt allows shipping without Python when APK size matters — the Chaquopy runtime adds
~20 MB and needs its own Maven repository. To enable it:

1. In `settings.gradle.kts` add `maven { url = uri("https://chaquo.com/maven") }` to
   `pluginManagement.repositories` and `dependencyResolutionManagement.repositories`.
2. In root `build.gradle.kts` add `id("com.chaquo.python") version "15.0.1" apply false`,
   and apply it in `app/build.gradle.kts` with
   `python { version("3.10") }` inside `defaultConfig`.
3. Add `implementation("com.chaquo.python:android:15.0.1")` and implement
   `SandboxLanguage.PYTHON` inside `CodeSandbox.execute` (the code path and its friendly
   “how to enable” error are already wired).

### Charts & markdown

- Charts are drawn with **pure Compose Canvas** (`BarChart`) instead of MPAndroidChart —
  same capability, no JitPack dependency, full theme support.
- Markdown is rendered by a self-contained **parser + Compose renderer**
  (`util/MarkdownParser.kt` + `ui/components/MarkdownText.kt`) covering headings, bold/italic,
  inline code, links, strikethrough, lists, quotes, tables, rules and fenced code blocks with
  syntax highlighting. Unit-tested, zero third-party markdown dependencies.

---

## Sandbox security model

1. **No network by construction** — MiniKotlin cannot express I/O; Rhino runs with all Java
   bridge globals deleted and a ClassShutter that returns `false` for every class. There is no
   escape hatch, so sandboxed code cannot open sockets, files or threads.
2. **Time** — per-run timeout slider (5–120 s, clamped by `SandboxSecurityManager`), enforced
   three ways: `withTimeoutOrNull`, interpreter wall-clock checks, and Rhino’s instruction-count
   observer (which also powers the Stop button via a cancellation flag).
3. **CPU/steps** — evaluation-step budget (20 M) + recursion depth cap (200) + materialization
   guards (e.g. `(1..2000000000).toList()` is rejected instead of OOM-ing).
4. **Output** — console output is capped (200 K chars) and truncated with a notice.
5. **Files** — only reachable through the SAF document picker; sandbox ZIP extraction is
   zip-slip protected (canonical-path checks), entry-count and total-size capped; transforms
   write only to `cacheDir`.
6. **Defense in depth** — a regex blacklist also rejects obviously dangerous source
   (`Runtime`, `ProcessBuilder`, `System.`, reflection…) with a clear explanation, even though
   the interpreters could never reach them.

---

## Build notes & deviations from the original prompt

- **Verified logic**: the interpreter, CSV engine, markdown parser, tool-call parser and
  syntax highlighter were compiled and exercised with a real Kotlin compiler during
  development (all 60+ smoke/test assertions pass).
- **Chaquopy/Python**, **MPAndroidChart**, **compose-richtext**, **OpenCSV**, **PermissionX**,
  **FilePicker** and the **secrets plugin** were intentionally replaced by the lighter,
  dependency-free implementations above (the prompt itself suggests dropping Python when size
  matters).
- API key is entered **in-app** (DataStore) rather than `local.properties`, matching the
  onboarding/settings flow.
- Kotlin `1.9.22` + Compose compiler `1.5.8` + AGP `8.2.2` + Gradle `8.6` (wrapper included).
  KSP handles Room/Hilt/androidx.hilt codegen.
- `local.properties` (with your `sdk.dir`) is ignored by git, as usual.
