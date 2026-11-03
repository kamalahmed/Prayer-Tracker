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

## Phase 11: Feedback (v0.1.0-beta.3, versionCode 4), tag `phase-11-feedback`
- **Masjid locator removed** completely: code, route, settings fields, Overpass test and README mentions.
- **Offline world cities.** `assets/cities.tsv.gz` (about 3.3 MB, 69,999 cities) is built by `tools/build_cities.py` from GeoNames `cities5000` (CC BY 4.0). Search folds accents, supports a "city, country" qualifier and ranks in tiers, with population breaking ties. Manual lat/lon entry and Automatic location are kept. Tested by `CitySearchTest` (sylhet, dhaka, sharjah, dubai, london, new york, tokyo, mecca, istanbul, karachi, jakarta, lagos, plus BD cities).
- **Widget collapse root cause:** in beta.2, R8 merged all `GlanceAppWidget` subclasses into one class (mapping.txt showed every widget mapped to `a8.a`). Glance matches receivers to widgets by class name, so `updateAll()` for one kind re-rendered *every* widget with that kind's layout. As a result, "All prayers" became the one-prayer NextSmall layout on the boundary, hourly and screen-on updates. Resizing re-rendered through the correct receiver, which is why the widget looked fine again afterwards. It only happened in release builds.
  - **Fix:** a keep rule for the widget classes, updates go per appWidgetId through each kind's receiver, and the kind is resolved from the appWidgetId. The beta.3 mapping shows all 7 classes as distinct and unmerged.
  - New `WidgetLayout.compact()` with an "Always show full layout" option (`WidgetLayoutTest`).
  - The provider XML now declares heights that match the content.
- **Adaptive layouts:**
  - `ui/adaptive`: size classes, rail vs bottom bar, two-pane, list-detail, dashboard columns, and a dial diameter capped at min(w, h).
  - Two-pane Prayers, Times and Qibla, a multi-column Streaks dashboard, and Settings list-detail with the selection saved. Content width is capped at 840dp.
  - MainActivity declares `resizeableActivity` and handles size/orientation `configChanges`, so the activity is not recreated on fold, unfold or rotation (`AdaptiveTest`).
- **Results:** assembleDebug OK, 79 unit tests passing, lintDebug OK, signed assembleRelease. The signer cert SHA-256 matches `022ddd…481d`.
- **Not verified on a real device:** Fold inner/outer screen switching, launcher widget frame sizes on Samsung One UI, lock-screen widget behaviour, and the adaptive layouts on real tablet hardware. Decisions were checked with unit tests only, and no emulator UI run was done this phase.

## Phase 12: Feedback (v0.1.0-beta.4, versionCode 5), tag `phase-12-feedback`
- **Location crash root cause:** AGP/aapt gunzips `*.gz` assets when packaging, so the APK contains `assets/cities.tsv` and not `cities.tsv.gz`. `CityDatabase.get()` opened `cities.tsv.gz` and threw `FileNotFoundException` inside the CitySearch `produceState` coroutine, which crashed the app every time on every build type. Unit tests had read the source file directly, so they missed it.
  - **Fix:** try `cities.tsv` and then `cities.tsv.gz`, and sniff the gzip magic bytes so either form loads. CitySearch and the manual-coordinates path now catch load failures and show a message.
  - **Tests:** `CityAssetTest` covers the plain and gzip forms, the asset-name list, and checks that the built release APK contains an asset the code opens. The Robolectric `SettingsUiTest.citySearchLoadsPackagedAssetWithoutCrashing` uses the merged assets; it was confirmed to fail with the old asset name.
- **Settings list-detail:** `ListRow(selected=)` adds a tinted rounded background, accent semibold title and `Selected` semantics. NOTIFICATIONS is selected by default. Tested by `SettingsUiTest`, using Robolectric with Compose ui-test.
- **Audit:** `cities.tsv.gz` is the only asset, and no other page loads assets or uses reflection. The other Settings pages were not opened in tests, because they need Hilt ViewModels.
- **Results:** 86 unit tests passing, lintDebug 0 errors, signed assembleRelease with signer `022ddd…481d`.
- **Not verified on a device:** the emulator was offline, so nothing was run on hardware or the emulator this phase.
