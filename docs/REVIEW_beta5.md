# Code review: v0.1.0-beta.5 (Phase 13 hardening)

This review read every source file under `app/src/main`, looking for crash paths, security problems and poor code quality. Line numbers are from before the fix (tag `phase-12-feedback`). File paths are relative to `app/src/main/java/com/kamalahmed/prayerapp/`.

Totals: **1 critical, 9 high, 7 medium, 6 low** (23 in all). All critical, high and medium findings are fixed, and 5 of the 6 low ones. Open items are listed at the end.

| # | Sev | Where | Finding | Status |
|---|-----|-------|---------|--------|
| C1 | critical | core/prayer/PrayerTimesCalculator.kt `rawTimes` | At high latitudes the hour angle is NaN, and `roundToLong()` throws. Examples: London Fajr/Isha in June with rule NONE, and polar sunrise. This crashed the Prayers tab, the alarms and the widgets. | **Fixed.** Missing times are recomputed at a safe latitude of ±48.5°, with offsets from Dhuhr as a last resort, and times are kept in order. `PolarTest` covers every method and rule at 51.5°, 64°, 69° and 78°. |
| H1 | high | AlarmReceiver, BootReceiver, MarkPrayedReceiver, WidgetTickReceiver, Widgets.onDeleted | `goAsync()` + `launch` had no catch, so any exception killed the process and `finish()` could be skipped. | **Fixed.** `launchAsync` catches everything, logs it and always finishes. |
| H2 | high | alarm/AlarmReceiver | If anything threw before `rescheduleAll`, no later alarm was armed, so the app stayed silent until reboot. | **Fixed.** Re-arming now runs in `finally`. |
| H3 | high | AlarmReceiver → AdhanService | If the foreground service failed to start, that prayer had no alert. | **Fixed.** Falls back to a beep notification. |
| H4 | high | PrayerApp.onCreate collectors | The first error ended the collector for good, or crashed the app at startup. | **Fixed.** `safeScope`, with each step and each iteration guarded. |
| H5 | high | SettingsRepository, WidgetStyleStore | A DataStore `IOException` reached every collector. | **Fixed.** Falls back to `emptyPreferences()`, logged. |
| H6 | high | update/UpdateManager | If a release had no `.sha256` asset, the APK was installed with no integrity check. | **Fixed.** The SHA-256 is mandatory and must be 64 hex characters. |
| H7 | high | update/UpdateManager | There was no check on URL scheme or host, before or after redirects. | **Fixed.** Only https on GitHub hosts is accepted, with no userinfo, and the final URL is checked too. |
| H8 | high | update/UpdateManager | The downloaded APK was not checked for package, version or signer. | **Fixed.** `UpdateSecurity.checkApk` requires the same package, a higher versionCode and the same signer set. Checked after download and again before install. |
| H9 | high | ui/* ViewModels | `viewModelScope.launch` and `stateIn` had no handler. | **Fixed.** `safeHandler` on every launch, `reportErrors` before `stateIn`, and a snackbar in the Scaffold. |
| M1 | medium | CityDatabase.load | One malformed row aborted the whole city list. | **Fixed.** Bad rows are skipped, and an empty result is an `IOException`. |
| M2 | medium | MarkPrayedReceiver | Used the device time zone instead of the zone in settings. | **Fixed.** |
| M3 | medium | UpdateManager | No limit on download size. | **Fixed.** Capped at 100 MB while streaming. |
| M4 | medium | AdhanService | `startForeground`, `requestFocus` and `release` were unguarded. | **Fixed.** Guarded, with `stopSelf`/`stopAll` on failure. |
| M5 | medium | Widgets, WidgetConfigActivity, WidgetStudioScreen | A style that couldn't be decoded, or a config load that failed, crashed the screen. | **Fixed.** Falls back to the default style and shows an error state. |
| M6 | medium | AndroidManifest | Backups included the updater cache. | **Fixed.** Backup rules exclude `diagnostics/`, `sounds/` and `updater.xml`. |
| M7 | medium | update/ReleaseParser `CODE_RX` | A versionCode of 10 or more digits was cut to its first 9 digits. | **Fixed.** Found by the new malformed-JSON test. |
| L1 | low | Sounds.importCustom | The file extension came from the provider unsanitized. | **Fixed.** Letters and digits only, up to 5. |
| L2 | low | Sounds.importCustom | `openInputStream(...)!!`. | **Fixed.** Throws an `IOException` and is logged. |
| L3 | low | Stats.insights | `first()` on a list that could be empty. | **Fixed.** Uses `firstOrNull`. |
| L4 | low | AdhanService logs | Logged the full sound URI. | **Fixed.** Logs the scheme only. |
| L5 | low | AndroidManifest | Cleartext traffic was not disabled. | **Fixed.** `usesCleartextTraffic=false`. |
| L6 | low | Widgets `provideGlance` | If `loadWidgetData` fails, Glance shows its generic error layout. | Not fixed. It doesn't crash and the next tick recovers; a custom layout would be cosmetic. |

## Open items, not fixed
- **No crash-loop guard.** After an uncaught exception the crash is logged and the process dies normally.
- **Exception messages in crash logs.** Stack traces may include exception messages, such as a city or file name. The log stays on the device and is shared only when you tap "Copy diagnostics".
- **Broad permissions.** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and both location permissions are still declared. They are deliberate, but Play review may question them.
- **Accessibility** was not audited in depth.
- **`CorruptSettingsTest` is weak.** It passes even with the fix removed, so the H5 fix was verified by review only.
- **Nothing tested on hardware.** The alarm, foreground-service fallback and install paths are covered by unit tests only.

## Safety net
- `core/safety/Safety.kt`:
  - `CrashLog` writes `filesDir/diagnostics/last_crash.txt` (version, device, stack trace; no network, no location), then hands off to the system handler. Also keeps a capped `recent_errors.txt`.
  - Also provides `safeHandler`/`safeScope`, `launchAsync`, `guard`, and `AppErrors` + `reportErrors`, which turn failures into a snackbar.
- **Settings > App > Diagnostics:** view the last crash, Copy diagnostics, Clear.

## R8
Checked against the beta.5 `mapping.txt`:
- Every manifest class keeps its name, including the 7 widget receivers.
- The workers and `TrackerDatabase_Impl` are kept.
- The widget classes are distinct.
