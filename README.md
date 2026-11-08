# Prayer App

Native Android prayer times app (Kotlin, Jetpack Compose, Glance widgets). Package `com.kamalahmed.prayerapp`, minSdk 26, targetSdk 36.
Defaults: Sharjah, UAE, using the Authority of Dubai calculation method.

## Features
- Prayer times calculated offline. Supports several calculation methods, Asr madhab, high-latitude rules, per-prayer offsets and iqama times.
- Shows the Hijri date, a Qibla compass, a monthly times table, and auto location with a geofence. You can also search ~70,000 cities worldwide offline (GeoNames) or enter coordinates manually.
- Adhan alarms use exact alarms and a foreground playback service. Each prayer can use the bundled adhan, a system ringtone or a custom sound file, and the full length of the sound plays.
- Full-screen alarm notifications are optional and turned on in Settings.
- **Streaks tab** (bottom navigation: Prayers · Times · Streaks · Qibla · Settings): current/best streak, today's tracker, and a Week / Month / Year statistics report with prev/next navigation. It shows prayed vs missed and on time vs late with completion %, a stacked bar chart with a completion trend line, a most-missed ranking with per-prayer on-time rate, the change vs the previous period, a calendar heatmap (tap any past day to backfill or edit it), streak history and insights. Tracking is backed by Room; mark prayers from the notification ("Prayed ✓"), a widget or the app. Unmarked prayers count as missed only once their time has passed, and only from the first tracked day.
- **Qada (make-up prayers)** — Streaks tab › Qada: set your debt per prayer (optional Witr) or calculate it from years/months or a date range (with an optional days-per-month exclusion), log with +1 / +N / "+1 full day", undo, delete entries from the history, and see progress ring, per-prayer progress, days covered/remaining (1 day = 5 prayers), 30-day pace, estimated finish date, qada streak and weekly/monthly charts. Optional daily goal with a reminder (off by default).
- **Nafl (optional worship)** — Streaks tab › Nafl: daily checkboxes for the 12 Sunnah rakaat (2 before Fajr, 4 before + 2 after Dhuhr, 2 after Maghrib, 2 after Isha), configurable optional Asr/Witr slots, Quran reading with optional pages/minutes, Tahajjud, tasbih/dhikr, ruqyah, Duha and up to 50 custom/renamable items. Backfill past days; browse week/month/year counts, per-slot completion rates, current streaks, Sunnah charts and a calendar heatmap. Hiding or renaming an item keeps its history. Up to 12 daily Quran reminders (off until enabled), notification-style or using the default alarm tone once; boot, update and time-zone changes re-arm them. These reminders are not continuous/full-screen adhan alarms.
- **Qibla compass** works on phones without a gyroscope: rotation vector → geomagnetic rotation vector → accelerometer + magnetometer, with clear calibration / no-data / no-sensor states and a static bearing fallback.
- **Tablets and foldables:** a navigation rail on wide screens, two-pane Prayers, Times and Qibla, a multi-column Streaks dashboard and Settings list-detail. Fold, unfold and rotation keep your state.
- **Tasbih counter** — Nafl items can be checkboxes or counters with optional daily targets. Presets include Istighfar, Durud/Salawat, SubhanAllah, Alhamdulillah, Allahu Akbar and La ilaha illallah; create/rename custom counters in Customize worship. Tap Count to open the full-screen counter: large tap surface, optional volume-key counting, haptics, target vibration, −1 and a confirmed session reset that preserves earlier/widget counts. Daily totals persist; view day totals plus week/month/year totals and charts in Nafl. Quran reminder controls and displays always use 12-hour AM/PM.
- **Iqamah** — Settings › Iqamah: choose offset minutes or fixed clock times per prayer and an optional Friday Jumu'ah override. Independently enable each notification and its lead time (0 = at iqamah); all are off by default and do not depend on adhan sound modes. Boot/app-update/time changes re-arm them. Fixed-before-adhan times are skipped except Isha, which can roll to the next day.
- Eight home-screen widgets: Next, Wide, Streak, Tracker, Large, Minimal, **Tasbih** and **Adhan + Iqamah**. Tasbih instances each select a counter and increment/decrement directly, without opening the app. Prayer widgets can show Start, Iqamah or Both; the new larger table always shows both columns. Each widget has its own style settings for theme, transparency (0 % = no background at all), vertical padding, horizontal padding, spacing between items, position in the cell (top/center/bottom), text/icon/box size and accent. The background panel wraps the content, so a tall cell no longer shows an empty tinted area. Row widgets can be 1 cell tall. "Always show full layout" keeps every prayer visible at small sizes. The countdown ticks live, and Widget Studio shows a live preview.
- Onboarding walks through location, notifications, exact alarm, battery and full-screen permissions.

## Diagnostics
Updater diagnosis and fixes: `docs/REVIEW_beta9.md`.
If the app crashes, a local crash log is saved on the device. It is never uploaded. Open **Settings > App > Diagnostics** to view it or copy it. See `docs/REVIEW_beta5.md` for the hardening review and `docs/REVIEW_beta7.md` for the Nafl/reminder self-review, plus `docs/REVIEW_beta8.md` for counters and iqamah.

## Build
Requirements: JDK 17 and the Android SDK (platform 36). Put the SDK path in `local.properties` (`sdk.dir=...`) or set `ANDROID_HOME`.

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug
./gradlew assembleRelease      # R8-minified; signed if a release keystore is configured, else unsigned
```
### Release signing
Release signing is read from a properties file **outside the repo** (default `~/secrets/prayer-app-signing.properties`, override with `-PsigningProps=/path` or `PRAYER_SIGNING_PROPS`):

```properties
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=prayerapp
keyPassword=...
```

or from env vars `PRAYER_KEYSTORE`, `PRAYER_KEYSTORE_PASSWORD`, `PRAYER_KEY_ALIAS`, `PRAYER_KEY_PASSWORD`. If neither is present (e.g. a public clone) the release APK is built unsigned. Never commit keystores or passwords; `*.jks`, `*.keystore`, `*.p12` and signing properties are gitignored.
Keep the keystore backed up: every future update must be signed with the same key, or Android will refuse to install it over the existing app.

## Install via GitHub Releases
1. Open [Releases](https://github.com/kamalahmed/Prayer-Tracker/releases) and download the latest `PrayerApp-vX.Y.Z.apk` (betas are marked *Pre-release*).
2. Open the file on your phone and allow "Install unknown apps" for your browser/files app when asked.
3. Optional: verify it with the matching `.apk.sha256` file (`sha256sum -c PrayerApp-vX.Y.Z.apk.sha256`).

> **Debug builds cannot be updated by release builds.** A debug APK (from `assembleDebug`) is signed with a different key. Uninstall it first, then install the release APK once; after that all updates install in place.

## In-app updates
- Settings → App → **Updates** shows the current version and a **Check for updates** button. The app asks the public GitHub Releases API (`kamalahmed/Prayer-Tracker`, unauthenticated, ETag-cached; rate limits and offline are reported gracefully).
- When a newer release is found you see **Update available** with the release notes, plus a banner on the Settings and More screens. One tap downloads the APK (progress, pause/resume, cancel), verifies its size and SHA-256 (from a `<apk>.sha256` asset, or a `SHA-256: <hex>` line in the notes), and installs it with the PackageInstaller session API. The first time, the app guides you to allow "Install unknown apps", and Android shows its own confirmation dialog.
- **Automatic update checks** are on by default (existing installs are migrated on once). Every app/Activity resume checks when connected, with a persisted five-minute attempt throttle; WorkManager also checks about every 15 minutes when connected. Android may delay periodic work in Doze. A **Wi-Fi only** option is available, and future opt-outs are respected.
- **Today** shows an Update / Later banner, and system notifications have an Update action. Later snoozes that release for an hour. Denied notification permission/muted channels never consume a release's notification key; the in-app banner remains available.
- Accepting an update starts a durable foreground WorkManager download with progress. Once verified, a resumed app continues to Android's installer; if finished in the background, a ready notification opens the flow. An Install unknown apps guide handles permission and resumes after returning. Size/SHA-256/package/higher-code/signer checks remain mandatory, including a fresh cached-file hash check before installation.
- **Include beta releases** is on by default while the installed version is a beta.
- Version comparison is semver (`0.1.0-beta.1 < 0.1.0-beta.2 < 0.1.0`). If names are equal, a `versionCode: N` line in the release notes breaks the tie.

## Cutting a release
```bash
./gradlew assembleRelease -PversionName=0.2.0-beta.1 -PversionCode=3   # versionCode must always increase
cp app/build/outputs/apk/release/app-release.apk PrayerApp-v0.2.0-beta.1.apk
sha256sum PrayerApp-v0.2.0-beta.1.apk > PrayerApp-v0.2.0-beta.1.apk.sha256
```
Create a GitHub release tagged `v0.2.0-beta.1` (tick *pre-release* for betas) and attach the `.apk` and `.apk.sha256` files. Optionally add `versionCode: 3` to the notes. The tag name must be the versionName with a `v` prefix. Defaults when the properties are omitted: `0.1.0-beta.9` / `10`.

## Permissions
| Permission | Why |
|---|---|
| ACCESS_COARSE/FINE_LOCATION | Optional auto location for prayer times |
| POST_NOTIFICATIONS | Prayer alerts |
| SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM | Adhan fires on time |
| USE_FULL_SCREEN_INTENT | Optional full-screen alarm (Settings) |
| FOREGROUND_SERVICE(_MEDIA_PLAYBACK, _DATA_SYNC) | Full adhan playback and accepted background APK downloads |
| RECEIVE_BOOT_COMPLETED | Reschedule after reboot, app update, time or timezone change |
| WAKE_LOCK, VIBRATE | Alarm delivery |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Optional, asked in onboarding so alarms are reliable |
| INTERNET | Update checks (city search is fully offline) |

## Attribution
The bundled adhan recordings are public domain (CC0) and come from Wikimedia Commons:
- "Beautiful adhan.ogg" by Adam-synagda
- "Adhan.ogg" by Aishatu98

City search uses a bundled offline copy of the GeoNames `cities5000` dataset (all places with population ≥ 5,000, ~70k cities, with admin regions, time zones and Latin-script alternate names), © GeoNames (https://www.geonames.org/), licensed under CC BY 4.0. The asset `app/src/main/assets/cities.tsv.gz` (~3.3 MB) is regenerated with `tools/build_cities.py` from `cities5000.txt`, `admin1CodesASCII.txt` and `countryInfo.txt` downloaded from https://download.geonames.org/export/dump/.


## Development
Work was built in phases, and each phase has an annotated tag (`phase-1-scaffold` … `phase-8-hardening`, then `v0.1.0`, `phase-9-updater`, `phase-10-feedback`). See PROGRESS.md.

Widget rendering tests (real Glance → RemoteViews → bitmap, on an emulator): `./gradlew connectedDebugAndroidTest`.
