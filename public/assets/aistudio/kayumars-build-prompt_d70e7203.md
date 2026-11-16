# PROJECT BUILD PROMPT — "KAYUMARS" (کیومرث)

Copy everything below this line into your AI coding agent (or hand it to a developer).

---

## ROLE

You are a senior Android + Rust systems engineer. Your task is to build **Kayumars (کیومرث)** — the fastest possible Android launcher, using a **hybrid architecture: a thin Kotlin shell + a Rust core**. Prioritize speed over features. This is a complete, production-quality project, not a prototype.

## PROJECT OVERVIEW

- **Name:** Kayumars (English/package name), کیومرث (display name, shown in the launcher UI and app label)
- **Type:** Android home-screen launcher (replaces the system launcher)
- **Languages:** Rust (core logic, ~90% of code) + Kotlin (Android shell, ~10%)
- **Core philosophy:** "Fast by doing less, fast by Rust." Minimal UI, zero-GC search path, in-memory index, no network access at all.
- **Reference points:** KISS Launcher's minimalism and ranking algorithms; Kvaesitso's built-from-scratch attitude; Rust-side fuzzy search powered by `nucleo` (the engine used by the Helix editor).

## HARD PERFORMANCE BUDGETS (non-negotiable)

| Metric | Target | How to measure |
|---|---|---|
| Cold start (to first frame) | < 200 ms | Macrobenchmark + `reportFullyDrawn()` |
| Search latency (500 apps, typed query) | < 5 ms | Rust unit benchmarks with `criterion` |
| Janky frames while scrolling | < 1% | Macrobenchmark `frameDurationCpuMs` / `dumpsys gfxinfo` |
| Resident memory while idle | < 50 MB | `adb shell dumpsys meminfo` |
| APK size | < 4 MB | `./gradlew :app:assembleRelease` output |
| Network permissions | ZERO | Manifest lint check |

If a change regresses any budget, the build must fail (see Perf Gate below).

## COMPLETE DIRECTORY STRUCTURE

Create exactly this structure:

```
kayumars/
├── Cargo.toml                        # Rust workspace root
├── Cargo.lock
├── rust-toolchain.toml               # pinned stable toolchain
├── .cargo/config.toml                # Android linker config per target
│
├── settings.gradle.kts
├── build.gradle.kts                  # root Gradle config
├── gradle.properties
├── gradle/libs.versions.toml         # version catalog
│
├── core/                             # 🦀 RUST CORE — all logic lives here
│   ├── Cargo.toml
│   ├── uniffi.toml
│   └── src/
│       ├── lib.rs                    # module declarations, pub use
│       ├── model.rs                  # AppEntry, SearchResult, HistoryEvent
│       ├── index.rs                  # in-memory app index (Vec + HashMap)
│       ├── search.rs                 # fuzzy search via nucleo
│       ├── rank.rs                   # scoring: fuzzy × frequency × recency
│       ├── history.rs                # append-only launch log (ring of N events)
│       ├── store.rs                  # bincode persistence, load/save < 10 ms
│       └── ffi.rs                    # UniFFI exported API
│
├── app/                              # 🤖 KOTLIN SHELL — glue only, zero business logic
│   ├── build.gradle.kts              # rust-android-gradle plugin wired into build
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml       # CATEGORY_HOME, QUERY_ALL_PACKAGES, receivers
│       ├── java/com/kayumars/launcher/
│       │   ├── LauncherApp.kt        # EMPTY Application class (cold-start speed)
│       │   ├── MainActivity.kt       # ~300 lines max: bind UI ↔ Rust core
│       │   ├── pkg/
│       │   │   ├── PackageChangeReceiver.kt   # PACKAGE_ADDED/REMOVED/REPLACED
│       │   │   └── AppIndexer.kt      # PackageManager scan → calls core.replace_index()
│       │   ├── bridge/
│       │   │   └── Core.kt            # wrapper over UniFFI-generated bindings
│       │   ├── ui/
│       │   │   ├── SearchBar.kt
│       │   │   ├── ResultsAdapter.kt  # RecyclerView + DiffUtil, fixed size
│       │   │   └── IconCache.kt       # two-level: LruCache (mem) + disk cache
│       │   └── history/
│       │       └── LaunchLogger.kt    # on app launch → core.log_launch()
│       └── res/
│           ├── layout/activity_main.xml   # one search bar + one RecyclerView. NOTHING else.
│           ├── values/themes.xml
│           ├── values/strings.xml          # app_name = کیومرث
│           └── drawable/
│
├── benchmark/                        # 📏 Macrobenchmark module
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/kayumars/bench/
│           ├── StartupBenchmark.kt    # cold start scenario
│           └── ScrollBenchmark.kt     # jank scenario
│
├── scripts/
│   ├── build-rust.sh                 # cargo ndk → jniLibs, then gradle assemble
│   ├── bench.sh                      # cargo bench + connected Macrobenchmark
│   └── perf-gate.sh                  # FAIL the build if any budget is violated
│
├── .github/workflows/ci.yml          # CI: fmt, clippy, test, bench, assembleDebug
└── README.md                         # setup, build, install, performance results table
```

## RUST CORE — DETAILED REQUIREMENTS

**`model.rs`**
- `AppEntry { package: String, label: String, user_serial: u32, flags: u32 }`
- `SearchResult { package, label, score, kind }` where `kind` ∈ { App, Contact, Setting } (only App is required in v1)
- All types derive `Serialize, Deserialize, Clone, Debug`

**`index.rs`**
- In-memory index: `Vec<AppEntry>` plus a normalized-label map for prefix matching
- `replace_index(apps)` swaps the whole index atomically (ArcSwap pattern or double-buffer)
- Normalization: lowercase + Unicode NFC; store a pre-normalized copy of each label
- Target: replacing a 500-app index < 1 ms

**`search.rs`**
- Use `nucleo = "0.2"` (fuzzy matcher from Helix editor)
- Case-smart, normalization-smart matching; `AtomKind::Fuzzy`
- Zero allocation in the hot path: reuse a preallocated `Vec` buffer per query
- Return top-N (default 12) sorted by combined score, descending
- Target: < 5 ms for 500 apps including sort

**`rank.rs`**
- Final score = `fuzzy_score × (launches_for_app / total_launches) × (1 / recent_position)`
  (the KISS Launcher "frequency × recency" formula)
- Also expose `top_apps(n)` for the empty-query state
- Ranking math must be unit-tested with fixed fixtures

**`history.rs`**
- Append-only event log: `{ package, timestamp_ms }`
- In-memory bounded buffer (e.g., last 10,000 events)
- Auto-compaction: fold into per-app frequency table when the buffer is full

**`store.rs`**
- Persist index + history as a single bincode file in the app's private dir
- Load on background thread at startup; save debounced (≥ 2 s) after changes
- Corrupt file → rebuild from scratch, never crash

**`ffi.rs` (UniFFI — proc-macro style, `uniffi = "0.28"`)**
```rust
#[derive(uniffi::Object)]
pub struct Kayumars { ... }

#[uniffi::export]
impl Kayumars {
    fn new(config_dir: String) -> Arc<Kayumars>;
    fn replace_index(&self, apps: Vec<AppEntry>);
    fn search(&self, query: String, max_results: u8) -> Vec<SearchResult>;
    fn log_launch(&self, package: String, timestamp_ms: u64);
    fn top_apps(&self, max_results: u8) -> Vec<SearchResult>;
    fn save_now(&self);
}
```

**`core/Cargo.toml` release profile (also set at workspace root):**
```toml
[profile.release]
opt-level = 3
lto = "fat"
codegen-units = 1
strip = true
panic = "abort"
```

**Rust tests:** unit tests for normalization, scoring, history compaction, store round-trip; `criterion` benchmarks for `search` and `replace_index` with a 500-app fixture.

## KOTLIN SHELL — DETAILED REQUIREMENTS

**`AndroidManifest.xml`** must include:
```xml
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>
<uses-permission android:name="android.permission.EXPAND_STATUS_BAR"/>
```
- `<queries>` block for ACTION_MAIN/LAUNCHER intents
- MainActivity with `launchMode="singleTask"` and intent filter: `ACTION_MAIN` + categories `HOME`, `DEFAULT`, `LAUNCHER`
- `PackageChangeReceiver` (exported=false) for PACKAGE_ADDED / PACKAGE_REMOVED / PACKAGE_REPLACED with `<data android:scheme="package"/>`
- NO other permissions. No INTERNET. No ACCESS_NETWORK_STATE.

**Startup rules (critical):**
- `LauncherApp` must be EMPTY — no SDK init, no logging frameworks, no DI graph
- MainActivity: `onCreate` → bind views → read index from Rust core (already loaded on a background thread, in-memory) → first frame. No PackageManager calls on the UI thread, ever.
- The only PackageManager scan happens in `AppIndexer` on a background dispatcher, results pushed into `core.replace_index()`
- Use `reportFullyDrawn()` after first results render

**UI rules:**
- Exactly one search bar + one results RecyclerView. No widgets, no dock, no folders, no gestures in v1.
- `ResultsAdapter`: `setHasFixedSize(true)`, DiffUtil, no XML inflation per row beyond one minimal layout
- Icons: decode lazily on a background thread; `IconCache` = LruCache (~8 MB) + disk cache; adaptive-icon backed-down version cached too
- Theme: minimalist, no window animations beyond system defaults, support system dark/light
- `app_name` string = کیومرث

**Bridge:**
- `bridge/Core.kt` wraps the UniFFI-generated bindings; all calls from the UI thread are forbidden except `search()` (it is < 5 ms and allocation-free)
- Kotlin must contain ZERO scoring/search/indexing logic — if you need an `if` that isn't UI-glue, it belongs in Rust

**Gradle wiring (`app/build.gradle.kts`):**
```kotlin
plugins {
    id("com.android.application")
    id("org.mozilla.rust-android-gradle.rust-android")
}
rust {
    root = projectDir.parentFile.resolve("core").path
    targets = listOf("arm64", "armv7", "x86_64")
    profile = "release"
}
tasks.matching { it.name.startsWith("merge") || it.name.startsWith("package") }
    .configureEach { dependsOn("cargoBuild") }
```
- `minSdk = 26`, `targetSdk = 35`, Kotlin 2.x, ViewBinding enabled
- Version catalog `libs.versions.toml` pins every dependency version

## BUILD & TOOLING

- `rust-toolchain.toml` pinning current stable
- `.cargo/config.toml` with Android NDK linker targets for `aarch64-linux-android`, `armv7-linux-androideabi`, `x86_64-linux-android` (or rely on `cargo-ndk`)
- `scripts/build-rust.sh`:
  ```bash
  #!/usr/bin/env bash
  set -euo pipefail
  cargo install cargo-ndk 2>/dev/null || true
  (cd core && cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 \
      -o ../app/src/main/jniLibs build --release)
  ./gradlew :app:assembleDebug
  ```
- `scripts/perf-gate.sh`: parses Macrobenchmark + criterion output and fails if ANY budget above is violated
- CI (`.github/workflows/ci.yml`): `cargo fmt --check`, `cargo clippy -- -D warnings`, `cargo test`, `cargo bench` (record-only), Gradle assembleDebug

## BENCHMARK MODULE

- `benchmark/` uses `androidx.benchmark.macro` (Macrobenchmark)
- `StartupBenchmark`: `StartupMode.COLD`, iterations ≥ 10, report `timeToInitialDisplayMs`
- `ScrollBenchmark`: jank metrics while scrolling results
- Include a `compilation-profile` baseline profile module or `BaselineProfileRule` so the app ships with a baseline profile covering: app startup, first search, result rendering

## MILESTONES (implement in this order, fully test each)

1. **M1 — Skeleton builds:** workspace, Gradle, cargo-ndk pipeline, empty Rust `Kayumars` object callable from Kotlin (log a round-trip)
2. **M2 — Index:** AppIndexer scan → `replace_index`, PackageChangeReceiver updates, store round-trip
3. **M3 — Search UI:** search bar + results list live via `core.search()`, launching apps on tap, `log_launch` wired
4. **M4 — Ranking:** history + frequency × recency, `top_apps` on empty query
5. **M5 — Icons & polish:** IconCache two-level, dark/light theme, empty states
6. **M6 — Perf gate:** benchmarks, baseline profile, `perf-gate.sh` wired into CI, README with results table

## QUALITY RULES

- Zero `TODO`/`unwrap()`/`panic!` in Rust production paths; `thiserror` for errors, never let a panic cross FFI
- No logging frameworks in Kotlin; strip logs in release
- All public Rust functions documented; all UI strings in `strings.xml`
- Original code, no GPL-incompatible copying (independent implementation, inspired by documented algorithms only)
- App works fully offline, forever, and stores no data outside its private dir

## DEFINITION OF DONE

- `scripts/build-rust.sh` produces an installable debug APK on arm64 device/emulator
- Setting the app as HOME works; searching "te" on a 500-app fixture returns Chrome/Telegram-class matches in < 5 ms
- All tests pass; `cargo bench` and Macrobenchmark numbers recorded in README and all budgets met
- README documents: prerequisites (Android Studio, NDK, Rust, cargo-ndk), one-command build, architecture diagram, and performance table

Build the complete project now, milestone by milestone, and run everything you can (tests, builds) as you go.
