# Strelitzia

Root CPU frequency-cap manager for Android 13+. Kotlin + Jetpack Compose + [Glasmio](https://github.com/NEORUAA/Glasmio), with a [MeiloX](https://github.com/MeiloX/MeiloX)-inspired layout.

## Pages

- **CPU:** discover cpufreq policies, inspect current clocks and limits, select any kernel-supported maximum, apply the preset, restore session caps.
- **Device:** Android/kernel, model, SoC, architecture, memory and policy topology.
- **Profile:** [Anemonastrum](https://github.com/Anemonastrum), source and design credits.

Light/dark appearance, glass navigation and controls, system fonts, no analytics, no account and no network permission. Root connection is explicit. Read-only information remains available without root where the kernel permits it.

## Get an APK

Open [Actions](https://github.com/Anemonastrum/Strelitzia/actions), select a successful **Android APK** run, download **Strelitzia-debug**, unzip it and install `app-debug.apk`. GitHub sign-in may be required to download artifacts. Builds run on every push, pull request and manual dispatch; artifacts expire after 30 days.

These are **debug-signed test APKs**, not Play Store releases. Clean CI machines can generate different debug keys: an update may require uninstalling the previous build, which removes its app data. No production signing key is included.

## CPU controls and safety

**Requires an already-rooted phone and permission from its root manager. This app does not root your device. Changing CPU limits can affect stability and performance. Use at your own risk.**

1. Tap **Connect root** and approve the root manager prompt.
2. Select a policy maximum from the kernel's supported list, tap **Apply**, then confirm.
3. Inspect the read-back result. Thermal/power services can override caps later; refresh to check.

Values are integer **kHz** in sysfs and **MHz** in the UI. `scaling_max_freq` sets an upper cap, not a fixed clock. No governor, minimum, voltage, thermal protection or file permissions are modified. No apply-on-boot or background enforcement.

The preset executes the equivalent of these commands inside `su -c`, after validating all policies and frequencies:

```sh
echo 1286000 > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
echo 1920000 > /sys/devices/system/cpu/cpufreq/policy2/scaling_max_freq
echo 1920000 > /sys/devices/system/cpu/cpufreq/policy5/scaling_max_freq
echo 2515000 > /sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq
```

Policies with missing supported lists or bounds are read-only. Unsupported preset values disable the preset; no silent rounding or policy substitution. Targets below the current minimum or outside hardware bounds are rejected. Root subprocesses have a 15-second timeout and a 64 KiB output limit per stream.

Preset writes are **not atomic**. On failure, remaining writes stop and attempted policies are restored to their prior caps when current validation permits. Every observed value and restore failure is reported. If a root command times out, exceeds its output limit, or loses its I/O channel, its completion is **indeterminate**: killing the `su` launcher cannot prove a daemon-owned command stopped. Strelitzia blocks all further root commands for that app session and skips rollback rather than racing unfinished work. **Reboot the phone before retrying; restarting the app alone does not establish safety.** Inspect current caps after reboot. Other restore errors are reported per policy. External power services can race with any userspace app.

**Restore session caps** uses the first readable cap per policy after connecting root in the current app process. It is not a factory reset. Restarting the app process forgets this baseline. Reboot behavior belongs to your kernel/root manager, not Strelitzia.

## Build

JDK 21, Android SDK 37 / Build Tools 36.0.0. Gradle 9.6.0 wrapper and checksum included; AGP 9.4.1, Compose compiler 2.4.21, Glasmio 0.1.2.

```sh
export ANDROID_HOME=/path/to/android-sdk
sdkmanager 'platforms;android-37.0' 'build-tools;36.0.0'
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions needs no PAT or repository secrets. Developer PATs must never be put in this repository, workflow, APK or remote URL.

## Verification limits

Unit tests use controlled sysfs I/O and real local subprocesses; they never change the build machine's CPU caps. Android compilation/lint and CI results are visible in Actions. A successful build does not prove root behavior or rendering on your phone. Rooted-device frequency changes, OEM thermal-service interactions and visual behavior require physical-device testing.

## Credits and license

Original Strelitzia code: Apache-2.0. Glasmio: Apache-2.0 with upstream third-party notices retained in its artifacts. MeiloX is a visual reference only; no MeiloX source, music features, Apple fonts or artwork are copied. See [NOTICE](NOTICE).
