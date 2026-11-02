# Prayer App – Build Progress

Each phase ends with a green `./gradlew assembleDebug` + unit tests, a commit and an annotated tag (break point).
To resume: `git tag -l 'phase-*'`, check out the latest, and continue with the next phase below.

| Phase | Tag | Status |
|---|---|---|
| 1 – Project scaffold (Gradle, Hilt, Compose theme, bottom nav, DataStore settings) | `phase-1-scaffold` | ✅ Done |
| 2 – Core engine (prayer times, hijri, location) | `phase-2-core-engine` | ✅ Done |
| 3 – Main UI (Prayers, Times, Qibla) | `phase-3-main-ui` | ✅ Done |
| 4 – Alarms & notifications (exact alarms, adhan service, full-screen) | `phase-4-alarms` | ✅ Done |
| 5 – Prayer tracker / streaks (Room) | `phase-5-tracker` | ✅ Done |
| 6 – Widgets (Glance, 6 types, config screen) | `phase-6-widgets` | ✅ Done |
| 7 – Settings completion & polish, onboarding | `phase-7-settings` | ✅ Done |
| 8 – Hardening, lint, release config, README | `v0.1.0` | ✅ Done |
| 9 – In-app updater (GitHub Releases), release signing, versioning | `phase-9-updater` | ✅ Done |

## Toolchain used
- JDK 17 (Temurin), Gradle 8.14.3 (wrapper), AGP 8.13, Kotlin 2.2.20, compileSdk/targetSdk 36, minSdk 26.

## Phase 8 results (2026-11-02)
- `assembleDebug`, `testDebugUnitTest` (35 tests, 0 failures), `lintDebug` (0 errors, 71 warnings: dependency versions, UnusedAttribute, UseKtx – non-blocking), `assembleRelease` (R8 + resource shrinking, unsigned, ~4.3 MB) all pass.
- Review: boot/package-replaced/time/timezone/date/exact-alarm-permission rescheduling; setAlarmClock (Doze-exempt) with inexact fallback when exact alarms are denied; media-playback foreground service plays the full sound with configurable max duration, Stop/Snooze; full-screen intent only when the setting is on and the OS allows it; playback fallback reset per alarm.
- Widgets: 6 kinds, per-widget (appWidgetId) opacity, padding, spacing, text/icon/box scale; tracker supports tap-to-mark.
- Emulator smoke (API 34, software GPU): debug APK installs, launches to onboarding, no app crash. Not tested on a real device; adhan playback, alarms in Doze and widgets on a launcher are unverified on hardware.
- Note: USE_EXACT_ALARM is restricted by Google Play to alarm/clock-type apps; be ready to justify it or drop it and rely on SCHEDULE_EXACT_ALARM.

## Phase 9 results (2026-11-02)
- `update/` package: SemVer + VersionCompare, ReleaseParser (org.json), UpdateManager (ETag-cached GitHub API check, rate-limit/offline handling, resumable cancelable download with size + SHA-256 verification, PackageInstaller session install), InstallReceiver (pending-user-action and status callbacks), UpdateWorker (daily, Wi-Fi-only option, notification), UpdatePrefs, Settings > App "Updates" section and an update banner on Settings and More.
- Manifest: REQUEST_INSTALL_PACKAGES, ACCESS_NETWORK_STATE. versionName `0.1.0-beta.1`, versionCode 2, both overridable with `-PversionName` / `-PversionCode`.
- Release signing is read from `~/secrets/prayer-app-signing.properties` or env vars, with an unsigned fallback. The keystore lives outside the repo.
- `assembleDebug`, `testDebugUnitTest` (45 tests, 0 failures), `lintDebug` (0 errors, 74 warnings) and a signed `assembleRelease` (R8, APK Signature Scheme v2) all pass.
- Not yet verified on a device: the end-to-end update install. It needs a second, higher release published on GitHub.

## What's next
Next: push to https://github.com/kamalahmed/Prayer-Tracker, publish the `v0.1.0-beta.1` pre-release with the APK and its .sha256, then test on a real device.

## Phase 10 — Feedback (v0.1.0-beta.2, versionCode 3) — tag `phase-10-feedback`
- Masjid finder rebuilt on OSM Overpass: 2 endpoints, timeouts, User-Agent, 24h cache per ~1km cell, offline and error states, list sorted by distance, geo: intent.
- Streaks bottom-nav tab with a Week/Month/Year dashboard and day backfill/edit (StatsTest).
- Widget opacity is linear, 0 = fully transparent (WidgetTest, WidgetRenderTest).
- Separate padV/padH/spacing (0..), top/center/bottom alignment, transparent root with a wrap-content panel, 1-cell row widgets (WidgetDimsTest; tall 520dp render test).
- Results: assembleDebug OK, 60 unit tests passing, lint 0 errors, connectedDebugAndroidTest 2/2 passing on the API 34 emulator.
