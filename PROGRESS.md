# Prayer App – Build Progress

Each phase ends with a green `./gradlew assembleDebug` + unit tests, a commit and an annotated tag (break point).
To resume: `git tag -l 'phase-*'`, check out the latest, and continue with the next phase below.

| Phase | Tag | Status |
|---|---|---|
| 1 – Project scaffold (Gradle, Hilt, Compose theme, bottom nav, DataStore settings) | `phase-1-scaffold` | ✅ Done |
| 2 – Core engine (prayer times, hijri, location) | `phase-2-core-engine` | ✅ Done |
| 3 – Main UI (Prayers, Times, Qibla) | `phase-3-main-ui` | ✅ Done |
| 4 – Alarms & notifications (exact alarms, adhan service, full-screen) | `phase-4-alarms` | ✅ Done |
| 5 – Prayer tracker / streaks (Room) | `phase-5-tracker` | ⏳ Next |
| 6 – Widgets (Glance, 6 types, config screen) | `phase-6-widgets` | Pending |
| 7 – Settings completion & polish, onboarding | `phase-7-settings-polish` | Pending |
| 8 – Hardening, lint, release config, README | `v0.1.0` | Pending |

## Toolchain used
- JDK 17 (Temurin), Gradle 8.14.3 (wrapper), AGP 8.13, Kotlin 2.2.20, compileSdk/targetSdk 36, minSdk 26.

## What's next
Phase 5: Room prayer tracker, streak logic + tests, Streaks screen.
