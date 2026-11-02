# Prayer App

Native Android prayer times app (Kotlin, Jetpack Compose, Glance widgets). Package `com.kamalahmed.prayerapp`, minSdk 26, targetSdk 36.
Defaults: Sharjah, UAE, using the Authority of Dubai calculation method.

## Features
- Prayer times calculated offline. Supports several calculation methods, Asr madhab, high-latitude rules, per-prayer offsets and iqama times.
- Shows the Hijri date, a Qibla compass, a monthly times table, and auto location with a geofence. You can also search for a city manually.
- Adhan alarms use exact alarms and a foreground playback service. Each prayer can use the bundled adhan, a system ringtone or a custom sound file, and the full length of the sound plays.
- Full-screen alarm notifications are optional and turned on in Settings.
- Prayer tracker with streaks, backed by Room. You can mark a prayer from the notification ("Prayed ✓") or from a widget.
- Six home-screen widgets: Next, Wide, Streak, Tracker, Large and Minimal. Each widget has its own style settings for theme, transparency, spacing, text size and accent. The countdown ticks live, and Widget Studio shows a live preview.
- Onboarding walks through location, notifications, exact alarm, battery and full-screen permissions.

## Build
Requirements: JDK 17 and the Android SDK (platform 36). Put the SDK path in `local.properties` (`sdk.dir=...`) or set `ANDROID_HOME`.

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug
./gradlew assembleRelease      # R8-minified, unsigned (no keystore committed)
```
To sign a release build, provide your own keystore. Never commit it; `*.jks`, `*.keystore` and `keystore.properties` are gitignored.

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
| INTERNET | City search (geocoding) |

## Attribution
The bundled adhan recordings are public domain (CC0) and come from Wikimedia Commons:
- "Beautiful adhan.ogg" by Adam-synagda
- "Adhan.ogg" by Aishatu98

## Development
Work was built in phases, and each phase has an annotated tag (`phase-1-scaffold` … `phase-8-hardening`, then `v0.1.0`). See PROGRESS.md.
