# Prayer App

Native Android prayer times app (Kotlin, Jetpack Compose, Glance widgets). Package `com.kamalahmed.prayerapp`, minSdk 26, targetSdk 36.
Defaults: Sharjah, UAE, using the Authority of Dubai calculation method.

## Features
- Prayer times calculated offline. Supports several calculation methods, Asr madhab, high-latitude rules, per-prayer offsets and iqama times.
- Shows the Hijri date, a Qibla compass, a monthly times table, and auto location with a geofence. You can also search for a city manually.
- Adhan alarms use exact alarms and a foreground playback service. Each prayer can use the bundled adhan, a system ringtone or a custom sound file, and the full length of the sound plays.
- Full-screen alarm notifications are optional and turned on in Settings.
- **Streaks tab** (bottom navigation: Prayers · Times · Streaks · Qibla · Settings): current/best streak, today's tracker, and a Week / Month / Year statistics report with prev/next navigation. It shows prayed vs missed and on time vs late with completion %, a stacked bar chart with a completion trend line, a most-missed ranking with per-prayer on-time rate, the change vs the previous period, a calendar heatmap (tap any past day to backfill or edit it), streak history and insights. Tracking is backed by Room; mark prayers from the notification ("Prayed ✓"), a widget or the app. Unmarked prayers count as missed only once their time has passed, and only from the first tracked day.
- **Nearby masjids** (Times tab): found automatically around your saved/GPS location from OpenStreetMap via the free Overpass API (no key, no Google Maps), sorted by distance, cached 24 h per ~1 km cell, with two fallback servers. Tap one to open it in your maps app.
- Six home-screen widgets: Next, Wide, Streak, Tracker, Large and Minimal. Each widget has its own style settings for theme, transparency (0 % = no background at all), vertical padding, horizontal padding, spacing between items, position in the cell (top/center/bottom), text/icon/box size and accent. The background panel wraps the content, so a tall cell no longer shows an empty tinted area. Row widgets can be 1 cell tall. The countdown ticks live, and Widget Studio shows a live preview.
- Onboarding walks through location, notifications, exact alarm, battery and full-screen permissions.

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
- **Auto-check daily** (default on) runs a WorkManager job and posts a notification when a new version appears. A **Wi-Fi only** option is available.
- **Include beta releases** is on by default while the installed version is a beta.
- Version comparison is semver (`0.1.0-beta.1 < 0.1.0-beta.2 < 0.1.0`). If names are equal, a `versionCode: N` line in the release notes breaks the tie.

## Cutting a release
```bash
./gradlew assembleRelease -PversionName=0.2.0-beta.1 -PversionCode=3   # versionCode must always increase
cp app/build/outputs/apk/release/app-release.apk PrayerApp-v0.2.0-beta.1.apk
sha256sum PrayerApp-v0.2.0-beta.1.apk > PrayerApp-v0.2.0-beta.1.apk.sha256
```
Create a GitHub release tagged `v0.2.0-beta.1` (tick *pre-release* for betas) and attach the `.apk` and `.apk.sha256` files. Optionally add `versionCode: 3` to the notes. The tag name must be the versionName with a `v` prefix. Defaults when the properties are omitted: `0.1.0-beta.1` / `2`.

## Permissions
| Permission | Why |
|---|---|
| ACCESS_COARSE/FINE_LOCATION | Optional auto location for prayer times |
| POST_NOTIFICATIONS | Prayer alerts |
| SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM | Adhan fires on time |
| USE_FULL_SCREEN_INTENT | Optional full-screen alarm (Settings) |
| FOREGROUND_SERVICE(_MEDIA_PLAYBACK) | Plays the full adhan |
| RECEIVE_BOOT_COMPLETED | Reschedule after reboot, app update, time or timezone change |
| WAKE_LOCK, VIBRATE | Alarm delivery |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Optional, asked in onboarding so alarms are reliable |
| INTERNET | City search (geocoding), nearby masjids (OpenStreetMap Overpass), update checks |

## Attribution
The bundled adhan recordings are public domain (CC0) and come from Wikimedia Commons:
- "Beautiful adhan.ogg" by Adam-synagda
- "Adhan.ogg" by Aishatu98

Masjid data © OpenStreetMap contributors (ODbL).

## Development
Work was built in phases, and each phase has an annotated tag (`phase-1-scaffold` … `phase-8-hardening`, then `v0.1.0`, `phase-9-updater`, `phase-10-feedback`). See PROGRESS.md.

Widget rendering tests (real Glance → RemoteViews → bitmap, on an emulator): `./gradlew connectedDebugAndroidTest`.
