# beta.8 / Phase 16 self-review

Scope: local review of the beta.7 → beta.8 diff; no independent reviewer or device run is claimed.

## Data and counters
- Room v4 is additive: `type` (CHECKBOX default) and `dailyTarget` (0 default) on `nafl_item`; no table recreation/deletion. Existing dhikr becomes a counter without modifying its name, enabled flag or historical counts. New presets use INSERT IGNORE.
- The real v3 schema fixture contains prayer logs, all Qada tables/config, renamed/hidden/custom Nafl items, counts, Quran pages/minutes and enabled reminders; opening it with the migration chain validates the v4 schema and preserved rows. Earlier v1/v2 upgrade tests remain in the full suite.
- Increment/decrement is a Room transaction, not a read/write pair protected only by a repository mutex. Concurrent app/widget repository tests verify no lost taps. Counts clamp at 0…1,000,000 per item/day; target crossing, invalid/hidden items, date boundaries and persistent reopen are tested.
- Session reset subtracts only this screen's net recorded taps, not earlier or widget taps. Per-item/day ledgers prevent switching/day rollover from resetting another day's total. Ledger is intentionally in memory; daily totals are in Room. Absolute manual edits are deliberate corrections, not atomic increments.
- Counter selection uses SavedStateHandle, avoiding resetting selection on rotation/process recreation. Optional volume-key counting consumes only initial presses and only on this Activity. Haptics/preferences/vibration and database coroutine paths are guarded; read failures show a non-destructive error.
- Fixed an old 10,000 count aggregation cap so larger daily counter values are included in period totals. Total-across-counters uses Long; individual day/month/year totals remain within Int bounds at the enforced cap. Changing an item's type does not delete/truncate its stored counts.

## Widgets
- Two distinct Glance subclasses/receivers are registered. Existing R8 keep rules preserve subclass names and callback constructors; release mapping is checked before delivery.
- Widget selection lives in the per-appWidgetId style store. ActionCallback reads that persisted selection and the current device date; no rendered date is trusted. Actual callback tests cover two instances, +1/−1 and no Activity launch. Opening the counter passes the selected item.
- Running sessions observe Nafl items/logs in addition to settings/tracker/refresh flows. A first-emission reload closes the load-before-subscribe race without changing existing generic flow callers. App-side changes refresh ended sessions too.
- Fixed countdown targeting and row highlighting in Iqamah mode; Both labels distinguish A (adhan) and I (iqamah). Keep today's rows through a pending Isha iqamah rather than prematurely showing tomorrow. A separate device-midnight boundary supports Nafl daily reset even when prayer location is in another zone.
- Failed initial widget loads are caught and render an open-app retry message. Small widget resizing can intentionally hide secondary details, as before; the dedicated larger table always shows both columns.

## Reminders and iqamah
- Quran editor uses validated 1…12 input with AM/PM; midnight/noon conversion and form/display behavior are tested. No Quran reminder path formats a 24-hour clock.
- Offset/fixed timings share one calculation helper across planner, Today/Times and widgets. Fixed-before-adhan is rejected; Isha can roll to the next day. Friday Jumu'ah supports fixed or offset mode with its own opt-in/lead time.
- Iqamah notification alarms have a distinct stable request code/type and notification ID; they are independent of adhan sound/display modes and off by default. 0 lead means at iqamah. A nine-day search handles a next-Friday notice after this Friday's lead time has passed.
- Reuse existing exact/inexact AlarmManager fallback and beep channel. Disabling cancels the old PendingIntent. Boot, MY_PACKAGE_REPLACED, clock/zone/date changes and the existing worker call the shared scheduler. Receiver work is safely wrapped and rearms in finally.
- Permission refusal keeps new notices off. Fixed an asynchronous permission-result closure: snapshot the Friday flag before resetting pending UI state, so granting Jumu'ah permission cannot accidentally enable nothing/the wrong prayer.
- Legacy iqamah boundary events remain compatible; audible notices use the separate opt-in type. Existing optional silent adhan-time countdowns now use the fixed/Friday-aware end time.

## Verification and limitations
Final verification: 180 unit/Robolectric tests, 0 failures/errors/skipped; lintDebug 0 errors (96 warnings, 2 hints); assembleDebug and signed R8 assembleRelease passed. APK v2 signature/package/version verified (beta.8 / versionCode 9), signer unchanged from beta.7; new Glance providers/callback and Room implementation stay unmerged/named in the release R8 mapping. Results are also recorded in PROGRESS.md and the staged release notes. No push or publication is performed. No signing material is checked in.

Not verified on hardware/emulator: real launcher resize/liveness and widget tap latency, volume-key/haptic feel, notification sound/DND/battery policy, reboot/app-update delivery, and an installed beta.7 → beta.8 upgrade. Robolectric coverage is not a substitute for these device checks. Session ownership is not persisted across process death; daily counts are. Quran sound remains a once-only notification tone, as in beta.7.
