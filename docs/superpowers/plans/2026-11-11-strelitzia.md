# Strelitzia Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship public Anemonastrum/Strelitzia repository with root CPU-cap controls and verified GitHub Actions APK builds.

**Architecture:** One Android application module; pure Kotlin policy validation and apply logic separated from bounded su subprocess I/O. Compose state exposes three Glasmio-backed destinations. No backend, boot service or automatic writes.

**Tech Stack:** Kotlin, Compose, Glasmio, Gradle wrapper, Android SDK, GitHub Actions. Resolve and pin compatible published versions before build setup; upstream currently documents Glasmio 0.1.2, JDK 21 and SDK 37. Inspect published artifacts rather than assuming main branch and published versions match.

**Spec:** ../specs/2026-11-11-strelitzia-design.md

## Global Constraints
- Android 13+ (API 33); package `io.github.anemonastrum.strelitzia`.
- Public repository `Anemonastrum/Strelitzia`; profile `https://github.com/Anemonastrum`.
- Three pages: Home/CPU settings, Device information, Profile/developer.
- Allowed writes: validated integer kHz to `/sys/devices/system/cpu/cpufreq/policyN/scaling_max_freq` only.
- Preset: policy0=1286000, policy2=1920000, policy5=1920000, policy7=2515000.
- PAT stays outside repository, APK and Actions. Use command-scoped credential helper reading `/home/hermes/vault/pat.txt` internally.
- No apply-on-boot, thermal bypass, copied MeiloX source/assets, Apple fonts, or production signing infrastructure.

## Review Focus
- Policy list or minimum changes between UI selection and Apply: refresh and reject stale invalid targets.
- Readback differs from request: report actual cap, not false success.
- Root process stalls or floods output: bound time/output and clean up resources.
- Preset fails after some writes: stop and report guarded restoration per affected policy.
- Activity recreation, large text, missing root/data: usable navigation and no implicit writes.

## Files and boundaries
- Root `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, Gradle wrapper, `.gitignore`: reproducible build.
- `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/drawable/ic_launcher.xml`: application configuration/resources.
- Under `app/src/main/java/io/github/anemonastrum/strelitzia/`: `CpuPolicy.kt` (parsing/validation), `RootShell.kt` (bounded process execution), `CpuController.kt` (discovery/read/apply/restore), `MainActivity.kt` (state lifecycle), `Screens.kt` (three destinations and Glasmio composition).
- Matching `app/src/test/java/io/github/anemonastrum/strelitzia/*Test.kt`: unit tests with fake I/O, no live sysfs writes.
- `.github/workflows/android.yml`, `README.md`, `LICENSE`, `NOTICE`: CI and handoff.

### Task 1: Tested policy domain and build baseline
**Interfaces:** `CpuPolicy(id: Int, relatedCpus: String, currentKhz: Long?, minKhz: Long?, maxKhz: Long?, hardwareMinKhz: Long?, hardwareMaxKhz: Long?, availableKhz: List<Long>, governor: String?)`; `parseFrequencies(raw: String): List<Long>`; `validateTarget(policy: CpuPolicy, khz: Long): String?` returns null if valid, otherwise reason. `PRESET: Map<Int, Long>` contains exact four values.
- [ ] Inspect Glasmio published POM/AAR and current integration documentation; select compatible pinned AGP/Kotlin/Compose/Gradle and SDK. Record versions in build files, checksum wrapper distribution, ignore credentials/build output.
- [ ] Write `CpuPolicyTest`: assert parsed `"1920000 1286000 1920000"` becomes `[1286000L,1920000L]`; malformed/zero/negative/overflow tokens never become selectable; unsupported target, missing bounds/list, target below minimum or beyond hardware maximum rejects; exact supported boundary accepts; preset equals specified map.
- [ ] Run `./gradlew :app:testDebugUnitTest` and record expected missing-symbol failure before implementing domain.
- [ ] Implement named domain functions and fixed numeric policy path validation; do not silently round frequency requests.
- [ ] Run `./gradlew :app:testDebugUnitTest`; require all domain assertions pass. Commit explicit build/domain/test paths.

### Task 2: Root access and guarded transactions
**Interfaces:** `ShellResult(exitCode: Int, stdout: String, stderr: String)`; `RootShell.run(command: String): ShellResult` runs bounded `su -c`; constructor accepts process launch function for tests. `CpuController` accepts a shell command executor and read-only file reader; `connect(): Boolean`, `refresh(): List<CpuPolicy>`, `apply(targets: Map<Int, Long>): ApplyReport`, `restoreSession(): ApplyReport`. `ApplyReport` contains per-policy requested/observed values, failure reason, and rollback outcomes; never collapse partial failure into success.
- [ ] Write `RootShellTest` for missing executable, nonzero exit, 15-second timeout, output limit (64 KiB per stream), drain/close cleanup and a process with no output; failing tests must run before implementation.
- [ ] Write `CpuControllerTest` fake-I/O cases: no writes before complete preset validation; missing policy5 rejects all; changed minimum rejects stale target; shell injection policy/value rejects; readback mismatch fails; second write failure stops remaining writes and restores prior changed policy only when revalidated; denied restore remains visible; repeated Apply serialized; session restore does not claim factory defaults.
- [ ] Run unit tests and confirm expected missing implementation failures.
- [ ] Implement bounded process I/O, strict numeric paths, unprivileged reads with explicit root-connected fallback, numeric policy discovery, session baseline snapshots, validated single-policy/preset writes, immediate readback and guarded rollback. Root connection is explicit; no prompts from startup/background reads. Keep all blocking work off main thread.
- [ ] Run all unit tests; inspect generated commands against fixed sysfs base and numeric-only interpolation. Commit root/controller/test changes.

### Task 3: Three-page Glasmio application
**Interfaces:** `MainActivity` owns lifecycle-safe state and serialized background operations; `StrelitziaApp` in `Screens.kt` receives state plus connect/refresh/apply/restore callbacks. No UI callback runs raw commands. Use actual Glasmio theme/backdrop and controls with supported API, not a glass-colored Material substitute.
- [ ] Add state tests for missing root/data, refresh failure retaining actionable error, recreation not triggering writes, busy state preventing duplicate Apply, and unsupported preset explanation. Run and confirm expected failures before adding state behavior.
- [ ] Implement Home cards, supported-frequency selectors, cap confirmation dialog, exact preset and session-restore actions; show unavailable values honestly. MHz display must preserve integer-kHz source. Refresh on foreground/resume and explicit request only.
- [ ] Implement Device page from Android APIs/kernel data and Profile page with Anemonastrum identity, repository links, version and credits. External links open browser safely. No Internet permission needed for browser links.
- [ ] Implement shared Glasmio-backed light/dark UI, three labeled bottom destinations, scrollable large-text layouts, 48dp minimum controls and accessibility labels. Use original vector icon and system fonts.
- [ ] Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`; fix all build/lint errors. If emulator available, test navigation, denied/missing root, rotation and large text without root writes; otherwise explicitly record visual/runtime verification gap.
- [ ] Commit UI/resources/test changes.

### Task 4: Public GitHub delivery and CI proof
**Interfaces:** `.github/workflows/android.yml` builds APK on push, pull_request and workflow_dispatch; read-only contents permission; JDK/SDK versions match checked build configuration. Upload `app/build/outputs/apk/debug/*.apk` as `Strelitzia-debug` artifact; missing file must fail upload.
- [ ] Write workflow using pinned action versions/SHAs; run unit tests, lint and assembleDebug. No privileged pull_request_target, PAT or production signing key.
- [ ] Write README: build/install, root safety, exact preset, unavailable frequency behavior, partial rollback, debug signing/update limitations, artifact download and hardware-test gap. Add original-source license plus Glasmio attribution; MeiloX visual reference credit without copying its code/assets.
- [ ] Run full build/test/lint command again. Review diff for root correctness, lifecycle/resource leaks, accessibility and supply-chain risks; resolve findings with regression tests.
- [ ] Check staged paths/blobs for PAT bytes without printing secret; fetch origin and inspect divergence. Commit explicit deliverables; push with scoped helper, compare local SHA to remote main ref.
- [ ] Check actual GitHub Actions run for pushed SHA, inspect failures/logs and fix until tests/lint/build succeed. Verify APK artifact exists and download it if available. Report verified repo/run/artifact URLs; never claim hardware cap writes tested without rooted phone.

## Self-review
Spec coverage maps policy validation/preset safety to tasks 1–2, UI/device/profile to task 3, public repo/CI/docs to task 4. Five review-focus risks have owning tests/checks above. No production app or build scaffolding starts until user reviews plan and selects execution approach.
