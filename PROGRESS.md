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

## Toolchain used
- JDK 17 (Temurin), Gradle 8.14.3 (wrapper), AGP 8.13, Kotlin 2.2.20, compileSdk/targetSdk 36, minSdk 26.

## Phase 8 results (2026-11-02)
- `assembleDebug`, `testDebugUnitTest` (35 tests, 0 failures), `lintDebug` (0 errors, 71 warnings: dependency versions, UnusedAttribute, UseKtx – non-blocking), `assembleRelease` (R8 + resource shrinking, unsigned, ~4.3 MB) all pass.
- Review: boot/package-replaced/time/timezone/date/exact-alarm-permission rescheduling; setAlarmClock (Doze-exempt) with inexact fallback when exact alarms are denied; media-playback foreground service plays the full sound with configurable max duration, Stop/Snooze; full-screen intent only when the setting is on and the OS allows it; playback fallback reset per alarm.
- Widgets: 6 kinds, per-widget (appWidgetId) opacity, padding, spacing, text/icon/box scale; tracker supports tap-to-mark.
- Emulator smoke (API 34, software GPU): debug APK installs, launches to onboarding, no app crash. Not tested on a real device; adhan playback, alarms in Doze and widgets on a launcher are unverified on hardware.
- Note: USE_EXACT_ALARM is restricted by Google Play to alarm/clock-type apps; be ready to justify it or drop it and rely on SCHEDULE_EXACT_ALARM.

## What's next
v0.1.0 complete. Next: real-device testing, signing config with your own keystore, push to the private GitHub repo.
