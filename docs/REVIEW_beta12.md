# Critical self-review — beta.12 / Phase 20

## Scope
Baseline `5101f246c92734fcdec5a91fa4031f13fd20e888` (beta.11). Version **0.1.0-beta.12 / 13**. Two requests from user feedback:
1. Prayer start-time adjustment was capped at ±30 min; Bangladesh masjids pray Dhuhr ~2 h after the calculated start.
2. A friend reported the iqamah alert is very quiet; there should be at least two alarms per prayer (start + before iqamah).

This is a local self-review (JVM/Robolectric), not a hardware or third-party audit. Room stays **v4**; only typed DataStore keys were added.

## Root cause: why the iqamah alert was quiet
The beta.8–beta.11 `IQAMA_NOTICE` path never used the alarm engine. `AlarmReceiver` posted a plain notification on the shared `prayer_beep_v1` channel:
- The channel sound is the default **notification** tone with `USAGE_NOTIFICATION_EVENT`, so Android plays it on the **notification stream**: it follows notification volume (often low, separate on Samsung), is muted in vibrate/silent/DND, and is a 1–2 s clip played once.
- Android freezes a channel's sound/usage once created, so it could not be made louder in code without a new channel.
- It was scheduled with `setExactAndAllowWhileIdle`, not `setAlarmClock`, so it also lacked the Doze/background-start exemptions the adhan alarm uses; there was no loop, time limit, Stop button or full-screen option.

The adhan itself is played by `AdhanService` via `MediaPlayer` on `USAGE_ALARM` — which is why the prayer-start alarm was loud and the iqamah alert was not.

## What changed
### Start time
- `CalcParams.fixedTimes` + `StartTimeLimits` (±360 min). User offsets/fixed times are applied in `PrayerTimesCalculator.applyUser` after the calculation (method offsets unchanged), then ordered: Fajr ≥ local midnight; each prayer ≥ previous + 1 min; a **changed** prayer may not pass the next prayer; Isha (may pass midnight) < next Fajr. The changed prayer is the one limited; unchanged neighbours keep their calculated time. `PrayerDay.limited` drives a "limited to keep prayer order" note.
- Fixed Isha before 12:00 PM is the same night after midnight (e.g. 12:30 AM). `PrayerSchedule.dateOf` logs such an Isha (from the notification's Prayed ✓) on its prayer day.
- Night thirds/Islamic midnight stay astronomical (calculated Maghrib → calculated Fajr).
- No user change ⇒ the code path returns the original map untouched (all old time tests unchanged).
- UI (Settings › Calculation › Prayer start time (adhan)): each row shows calculated → adjusted; editor has offset (±1/±15/±60, "Set from the masjid's clock time…") or fixed clock time (prayers only, not Sunrise), Reset, and text explaining it shifts the **start** (adhan, alarms, widgets, Today, tracking) vs Iqamah for congregation time. Uses the app's 12/24 h setting.
- Everything downstream (alarms, widgets, notifications, Today, iqamah "minutes after adhan") already consumes `toCalcParams()` schedules, so it uses the adjusted time automatically; a test asserts the adhan alarm, before-iqamah alarm and iqamah time follow a fixed 1:30 PM Dhuhr.

### Before-iqamah alarm
- Per prayer: own enable toggle (existing `iq_notify_*`), minutes before (chips At iqamah/5/10/15/20 + stepper 0–180, existing `iq_before_*`), and **Full alarm** (default) vs quiet notification (`iq_quiet_*`). Jumu'ah has the same (`jumuah_quiet`).
- Global iqamah sound, separate from adhan: system alarm tone (default), bundled chime/short/full adhan, or any ringtone; volume, repeat, stop-after (10–600 s), full-screen, Preview/Stop.
- Full alarm: scheduled with `setAlarmClock`; `AlarmReceiver` starts `AdhanService` with `ACTION_IQAMAH`; service always uses `USAGE_ALARM`, own sound/loop/volume/limit, no snooze and no dua; optional full-screen `AlarmActivity` ("Iqamah is coming up", Stop only). If the service start is refused, a notification on the new `iqamah_alarm_v1` channel (alarm tone, `USAGE_ALARM`, high importance) is posted instead.
- If the adhan is still playing when the iqamah alarm fires, the adhan is not cut off; the iqamah info is posted as an alarm-channel notice. If an adhan starts while an iqamah alarm plays (same-minute setup), the adhan wins and the iqamah info is kept as a notice.
- Quiet style keeps the old notification-stream behaviour, now explicitly labelled.
- Rescheduling: unchanged hooks — settings collector (any field change), boot, package replaced, time/timezone/date change, exact-alarm permission change, 6-hourly worker, backup restore.

## Migration / data safety
- No Room change. Existing installs keep `iq_notify_*`, `iq_before_*`, `iqama_*`, `iq_fixed_*`, Jumu'ah and `adj_*` values. Absent `iq_quiet_*` ⇒ full alarm, so existing before-iqamah users now get the loud alarm (the requested fix) with the same prayers and lead minutes. A Robolectric test restores exactly the beta.11 key set and checks every value.
- Backup validation checks raw values (offset ±360, fixed 0–1439, iqamah volume 0–100, play limit 5–1800, known sound ids, ringtone URI scheme). Older backups (±180 offsets) remain valid.
- Risky paths guarded: picker/ringtone/preview launches, sound resolution (falls back to chime), service start (falls back to alarm-channel notification), `dateOf` (falls back to the calendar date), schedule computation in UI (`runCatching`).

## Findings during review
| ID | Severity | Finding | Action |
|---|---|---|---|
| B12-01 | Important | Root cause above: iqamah alert on notification stream. | Alarm engine path + alarm channel fallback; tests assert alarm-clock scheduling, service start with `ACTION_IQAMAH`, and `USAGE_ALARM` channel vs the old `USAGE_NOTIFICATION_EVENT`. |
| B12-02 | Important | Backup validation read clamped values, so out-of-range `iqamah_volume`/offsets could pass. Found by a new test (failed first). | Validate raw preference values. |
| B12-03 | Medium | First ordering pass pushed **neighbours** (e.g. fixed Dhuhr 6 PM moved Asr/Maghrib). | Limit the changed prayer instead; test `changedPrayerIsLimitedNotItsNeighbour`. |
| B12-04 | Medium | Isha moved past midnight would be logged on the next date from the notification action. | `PrayerSchedule.dateOf`; test. |
| B12-05 | Low | Radio labels in the start-time editor were not tappable (only the circle). Found by UI test. | Whole row `selectable` with RadioButton role. |
| B12-06 | Low | A free-text minutes field in the AlertDialog never reached idle under Robolectric; not proven on device either way. | Replaced with ±1/±15/±60 steps and a clock-time shortcut (better for "masjid at 1:30"). |
| B12-07 | Low | Adhan and iqamah alarm in the same minute would silently drop one. | Adhan wins; iqamah info is kept as an alarm-channel notice. |

## Verification
Fresh clean serial gate (build cache disabled):
```
./gradlew --no-daemon --no-build-cache --no-parallel --max-workers=1 testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease
```
- **287 unit/Robolectric tests: 0 failures, 0 errors, 0 skipped** (268 existing + 19 new in `phase20/`: `StartTimeTest` 11, `IqamahAlarmTest` 6, `Phase20UiTest` 2). One existing assertion was updated for the intentional rename "Iqamah notification" → "Before-iqamah alarm".
- `lintDebug`: **0 errors**, 117 warnings, 4 hints (non-blocking, pre-existing kinds).
- Debug APK, instrumentation APK compilation and signed/minified release succeed; R8 keeps all eight widget classes, `AlarmReceiver`, `AdhanService`, `BootReceiver`, `AlarmActivity` and the Room implementation distinct.
- Build note: an interrupted Gradle run left a corrupted content-hashed transform/build-cache entry that made Hilt's release aggregation emit nothing (R8 "Missing class Hilt_PrayerApp"), only for this exact source/version content. Reproduced in a fresh worktree, fixed by deleting the regenerable cache entries from that window; the clean gate above then passed. No source change was needed.

## Known limits
- Not tested on hardware: actual loudness, OEM alarm/DND behaviour, full-screen on the Galaxy lock screen, exact-alarm denial on Android 14+ (falls back to inexact; a refused service start then uses the alarm-channel notification).
- `setAlarmClock` shows the system "next alarm" indicator for before-iqamah alarms — expected for a real alarm.
- Widget list/next highlighting between 00:00 and an after-midnight Isha is approximate (the next-prayer countdown is correct; the row highlight may not be). Only affects users who move Isha past midnight.
- One iqamah sound for all prayers (per-prayer toggle, lead and loud/quiet style are per prayer).
- Free-text minute entry was dropped from the editor (see B12-06).

No push or publication is part of this phase.
