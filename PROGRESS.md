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

## Phase 13: Hardening (v0.1.0-beta.5, versionCode 6), tag `phase-13-hardening`
- **Critical review:** `docs/REVIEW_beta5.md` lists 23 findings (1 critical, 9 high, 7 medium, 6 low). All critical, high and medium findings are fixed, plus 5 of the 6 low ones.
- **Critical fix:** at high latitudes, NaN prayer times crashed the app. Times now fall back to a safe-latitude recompute (`PolarTest`).
- **Safety net:** a local crash log (uncaught-exception handler), plus coroutine handlers in all receivers, services, workers and ViewModels. Errors show as a snackbar instead of a crash. Settings > App has a Diagnostics section.
- **Updater:**
  - The SHA-256 is mandatory.
  - Only https GitHub URLs are accepted, including after redirects.
  - Downloads are capped at 100 MB.
  - The update must have the same package, a higher versionCode, and the same signer.
- **Tests:**
  - `AllScreensPhoneTest` and `AllScreensFoldTest` open every tab and all 9 Settings pages.
  - `SafetyTest` and `UpdateSecurityTest` are failure-injection tests.
  - `CityAssetTest` checks that the asset names match what the code opens.
- **Results:**
  - 104 unit tests passing, lintDebug 0 errors.
  - Signed assembleRelease, signer `022ddd…481d`.
  - R8 mapping checked: the manifest classes, workers and Room implementation keep their names.
- **Not verified on a device:** no hardware or emulator run this phase.

## Phase 14: Feedback (v0.1.0-beta.6, versionCode 7), tag `phase-14-feedback`
- **Compass root cause:** the Qibla screen only used `TYPE_ROTATION_VECTOR`. That sensor is a gyroscope fusion sensor; budget phones without a gyroscope (Galaxy A15) don't expose it, so the app said "No compass sensor" although a magnetometer exists. **Fix:** `CompassSelector` chain ROTATION_VECTOR → GEOMAGNETIC_ROTATION_VECTOR → ACCELEROMETER+MAGNETIC_FIELD (pure-Kotlin rotation matrix, low-pass filtered, display-rotation aware), automatic fallback if a sensor fails to register or sends nothing for 3 s. States: starting / calibration needed (magnetometer accuracy low/unreliable) / no data / no magnetometer (static bearing shown). Location note with "Change" (opens Location settings) and "Allow location" when auto location is on without permission. `CompassTest`.
- **Widget root cause:** `provideGlance` loaded data once *outside* `provideContent`. While a Glance session is alive (it stays up after every tap), `update()` only recomposes the running session and never re-runs `provideGlance`, so taps and app-side marks rendered the stale snapshot. **Fix:** the composition now collects a flow combining the tracker DB, settings and a refresh signal (`WidgetLive`), so any DB change (app, widget, notification) re-renders all live widgets immediately; `updateAll` bumps the signal and still updates per appWidgetId for ended sessions. Boundary alarm (prayer times, midnight, date) and time/zone receivers unchanged. `WidgetLiveTest`.
- **Stats redesign:** KPI cards, overview ring with on-time/late/missed bars, stacked bars with y-axis and non-overlapping labels, dashed "no data" bars for periods before tracking started or in the future, completion + on-time rate line chart, per-prayer breakdown, year contribution grid with month/weekday labels that scales to width or scrolls, heat legend. Multi-column on Fold/tablet.
- **Qada:** Room v2 (`qada_debt`, `qada_entry`, `qada_config`) with `MIGRATION_1_2` (prayer_log untouched, `QadaMigrationTest` builds a real v1 DB from 1.json and upgrades it). Streaks tab has Stats | Qada segmented sub-tabs. Daily reminder via WorkManager (off by default).
- **Tests:** 133 unit/Robolectric tests passing (Streaks Week/Month/Year + Qada flows at phone, Fold and tablet widths), lintDebug 0 errors.
- **Not verified on a device:** A15 compass behaviour, widget liveness on Samsung One UI, reminder delivery, migration on a real beta.5 install.

## Phase 15: Nafl (v0.1.0-beta.7, versionCode 8), tag `phase-15-nafl`
- **Third Streaks tab:** Stats | Qada | Nafl. Daily Sunnah slots total the fixed 12 rakaat (2 before Fajr; 4 before + 2 after Dhuhr; 2 after Maghrib; 2 after Isha). Optional Asr and Witr slots are off by default. Hide/rename items without losing history; optional slots never inflate the baseline.
- **Optional worship:** Quran done flag plus optional pages/minutes; Tahajjud, tasbih/dhikr, ruqyah, Duha and up to 50 custom items, with checkbox/count logging and past-day editing. Full-page, scrollable, keyboard-safe editors; accessible checkbox and edit labels.
- **Dashboard:** ring/KPIs, week/month/year navigation, per-item counts and per-slot completion rates, current item/overall streaks, Sunnah daily/monthly bar chart, completed-item calendar heatmap, and monthly Quran reading days. Existing dashboard/chart components reused; adaptive phone/wide layout. Aggregation runs off Main. Untracked/future days are blank; optional-worship zeroes are neutral, not missed-prayer debt.
- **Quran reminders:** up to 12 persisted local times, off until enabled, normal notification or default alarm-tone channel. Explicit immutable PendingIntents, exact/idle-allowed fallback, cancellation on disable/delete, local-time/DST recalculation and rearming on receive/startup/boot/package replacement/time/timezone/date changes. Notification permission refusal keeps a reminder off. Alarm-tone mode is a notification sound, not continuous/full-screen playback.
- **Room v3:** additive MIGRATION_2_3; fixture upgrade preserves prayer_log and all three Qada tables/config fields. Existing v1 migration chain also remains green. Defaults use INSERT IGNORE, stable IDs and immutable baseline metadata.
- **Safety/review:** validated dates/counts/names/times, serialized edits, safe coroutine paths, non-destructive error + explicit Retry on a failed read (failure-injection tested). Self-review in docs/REVIEW_beta7.md; caught/fixed API 31-only AlarmManager usage with an API 28 regression. No new network endpoint or permissions; signing secrets remain outside git.
- **Results:** assembleDebug, 157 unit/Robolectric tests (0 failures/errors/skipped), lintDebug (0 errors; 84 warnings, 2 hints), signed R8 assembleRelease. APK package/version verified as com.kamalahmed.prayerapp / 0.1.0-beta.7 / 8, signer SHA-256 022dddfbdf9c07eef5f47e4cdf141782fbbeb2559ff7a1691ea381333dce481d. Release receiver, Room implementation and widget class names preserved by R8. APK and notes staged in /workspace/prayer-app-release/; no push or publication.
- **Not verified on a device:** beta.6 upgrade, real reboot/package-replacement delivery, Samsung/DND/battery behavior and alarm-channel sound. The optional Today-screen shortcut was not added; quick checkboxes are in Streaks → Nafl.

## Phase 16: Tasbih + Iqamah (v0.1.0-beta.8, versionCode 9), tag `phase-16-tasbih-iqamah`
- **Typed Nafl items / Tasbih:** checkbox or counter, six named presets plus custom counters, optional daily targets. Dedicated counter screen with large tap area, haptics, distinct target vibration, optional volume-key counting, −1, item selection, confirmed current-session reset and persistent daily counts. Session ownership is per item/day and intentionally resets with process death; daily totals do not. SavedStateHandle keeps item selection across recreation.
- **Stats:** selected-day counts, week/month/year per-item totals, reused bar charts and combined counter totals in Nafl. Larger counts are no longer truncated at the old 10,000 threshold; target/type/name changes preserve history.
- **Tasbih widget:** per-instance selection, direct +1/−1 ActionCallback, count/target and open-selected-counter action. Atomic Room transactions serialize app/widget taps. Live sessions observe Nafl item/log flows; first-emission reload closes the initial subscription race; app-side refresh also handles ended sessions.
- **12-hour Quran reminders:** validated 1–12 hour input with AM/PM and 12-hour displays; midnight/noon and form behavior tested.
- **Iqamah:** offset/fixed times per prayer, optional Friday Jumu'ah fixed/offset override, independent per-prayer/Friday notification toggles and lead minutes (0 = at iqamah), off by default. Distinct PendingIntent type/IDs reuse existing exact/inexact AlarmManager, beep notification channel, boot/package-replaced/time/date rescheduling and crash-safe receivers. Today/Times/countdowns share the fixed/Friday-aware timing helper. Invalid fixed-before-adhan times skip; Isha may roll to tomorrow.
- **Widget timing:** Start / Iqamah / Both per prayer-widget instance, plus new larger prayer | adhan | iqamah table. Iqamah-aware next target, ring progress, row highlighting and Isha rollover; device-midnight refresh supports counter dates when the prayer location uses another zone. Eight widget providers total, preserved distinctly by R8.
- **Room v4 / safety:** additive MIGRATION_3_4 preserves all seven tables' history; real v3 fixture tests renamed/hidden/custom items, Quran pages/minutes and reminders. Existing v1/v2 migration-chain tests remain green. Transaction concurrency, bounds/targets, reopen persistence, settings roundtrip, actual widget callbacks, AlarmManager cancellation and phone counter/iqamah/reminder/custom-item forms are tested. Risky I/O, widgets, permissions, clock dialogs and haptic paths are guarded. Self-review: docs/REVIEW_beta8.md.
- **Results:** assembleDebug; 180 unit/Robolectric tests (0 failures/errors/skipped); lintDebug 0 errors, 96 warnings, 2 hints; signed R8 assembleRelease. Package/version verified as com.kamalahmed.prayerapp / 0.1.0-beta.8 / 9; v2 signature verified with unchanged signer SHA-256 022dddfbdf9c07eef5f47e4cdf141782fbbeb2559ff7a1691ea381333dce481d. APK, checksum and RELEASE_NOTES_beta8.md staged in /workspace/prayer-app-release/. No push or publication.
- **Not verified on a device:** installed beta.7 upgrade, launcher/widget latency and resizing, volume-key/haptic feel, actual boot/package-replacement delivery, DND and vendor battery behavior. No core feature omitted; session reset ownership is deliberately in-memory while daily counts persist.

## Phase 17: Automatic updates (v0.1.0-beta.9, versionCode 10), tag `phase-17-autoupdate`
- **Proven root causes:** automatic checks ran only in a daily WorkManager task with a one-hour first delay, daily flex and battery-low gate; no app-resume check and no Today banner existed. Notification code marked a release notified even when POST_NOTIFICATIONS was denied or posting failed, suppressing later delivery. Phone-specific permission/battery state was not observed. Auto-check already defaulted true, the API already listed prereleases (not /latest), and parsing worked: an actual published beta.1–8 fixture verifies detection, assets, SHA and versionCode. Details: docs/REVIEW_beta9.md.
- **Scheduling:** every Activity resume checks when connected, using a persisted five-minute attempt throttle, a mutex against concurrent checks and failed-attempt throttling/clock-rollback recovery. Manual checking bypasses the throttle. Connected 15-minute periodic work replaces old daily work in place, without the initial hour or battery gate. Auto-check migrates on once for existing installs; later opt-outs and unmetered-only preference remain available. Android may delay periodic background work.
- **Visibility:** Today Update / Later card, progress and notification-settings guidance. Later snoozes the selected release's banner for an hour. System notification has an Update action; blocked app/channel permissions never consume the deduplication key, and ready-download notices have a separate key. Known updates survive transient network errors.
- **Accepted download → installation:** explicitly selected release persists to a foreground WorkManager download; progress, Range resume and network retry survive leaving/restarting the process. Once verified, a resumed MainActivity proceeds to installation, with an unknown-sources permission panel and automatic return continuation. Background completion posts a ready notification. PackageInstaller confirmation is launched from a resumed Activity or its notification PendingIntent, not an unrestricted background broadcast. Android consent is retained.
- **Safety/review:** no Room change. HTTPS/redirect, size, mandatory hash, package, higher-code and matching-signer checks kept; cached file rehashed immediately before installation and invalidated on verification failure. Consent has a separate StateFlow to avoid cached-Downloaded no-op acceptance; worker-start failures show an actionable error. Risky scheduler, receiver, download, settings/permission and installer paths are guarded. Root-cause/self-review in docs/REVIEW_beta9.md.
- **Results:** 196 unit/Robolectric tests, 0 failures/errors/skipped (actual prerelease/versionCode fixture, throttle/concurrency/failure paths, one-time preference migration, 15-minute constraints, blocked notification retry/channel/action, Today UI, durable consent and cached-file tampering). lintDebug 0 errors, 96 warnings, 2 hints; assembleDebug and signed R8 assembleRelease passed. Package/version confirmed com.kamalahmed.prayerapp / 0.1.0-beta.9 / 10; signer unchanged (022dddfbdf9c07eef5f47e4cdf141782fbbeb2559ff7a1691ea381333dce481d). Update worker/download worker/receiver and existing Glance class names preserved by R8. APK, checksum and RELEASE_NOTES_beta9.md staged in /workspace/prayer-app-release/; no push/publication.
- **Device limitations:** real OEM/Doze scheduling, permission/channel settings on the owner's phone, force-stop recovery and an end-to-end installed beta.8 → beta.9 update were not hardware-tested. Android may require opening the ready notification/app before installer UI can appear. First install of beta.9 may need manual sideload because older versions still have the discovery bug.
