# Beta 7 self-review — Phase 15 Nafl

Scope: Room v3 migration and new optional-worship data, statistics, Compose dashboard/editors, Quran reminder scheduling/notification delivery and integration with existing startup/system-change receivers. This is a self-review, not an independent review or hardware certification.

## Findings and mitigations

- **High — older Android compatibility:** AlarmManager.canScheduleExactAlarms exists only from API 31. Guarded by API level, scheduling directly on API 26–30; exercised by an API 28 Robolectric regression alongside the API 34 permission-denied fallback test.
- **High — history integrity:** seed definitions with INSERT IGNORE, never replace customized definitions on reopening. Stable IDs tie logs to slots through renames and hiding. Validate that editing cannot alter baseline metadata. New tables are additive only; no destructive fallback. v2 fixture preserves prayer_log, qada_debt, qada_entry and every qada_config field; the existing v1 upgrade test also runs through both migrations.
- **High — persistence failure:** coroutine writes use a safe handler and serialized edits; invalid input is user-visible. Read failure emits a non-destructive error state and explicit Retry restarts observation. Failure-injection test exercises recovery. There is no automatic reset or deletion of worship history.
- **Medium — reminder identity/cancellation:** immutable, explicit receiver PendingIntents have stable, unique data URIs, isolated from adhan/widget IDs. Cancel before rescheduling or deleting. Bound to 12 reminders, with duplicate times rejected. Off until enabled, including on fresh installs. Delivery rearms a local-time one-shot alarm; boot, package replacement, time/timezone/date changes and process startup rearm persisted reminders.
- **Medium — permission/ringtone behavior:** request notification permission only when enabling; refusal keeps the reminder off. Separate Android channels prevent alarm-sound selection from changing default reminder behavior. Exact-alarm denial falls back to allow-while-idle scheduling. The alarm option plays the Android default alarm tone as a notification sound; it is not continuous playback or full-screen presentation. Android/OEM policies can delay delivery.
- **Medium — misleading statistics:** the 12-rakaat baseline is immutable and optional Asr/Witr never inflate it. A count greater than zero marks a completed day; Quran pages/minutes do not silently mark completion. Calendar gaps before first tracking and future dates are blank. Rates use elapsed tracked days in the selected period; streaks are current (today or yesterday), not limited by the selected period. Renamed/hidden items retain history in reports.
- **Medium — responsiveness:** perform statistics aggregation off the main thread. Reuse the existing KPI/ring/bar/calendar components. Stack the three-tab selector beneath its heading on compact screens. Full-page, scrollable, keyboard-safe editors replace separate dialog windows after Robolectric reproduced a non-idling dialog-window layout loop. Phone and wide form tests exercise edit/customization/reminder navigation.
- **Low — optional-worship tone:** neutral zero cells and explicit wording distinguish optional worship from missed obligatory prayers. Witr is optional/configurable in this tracker; practices vary. This is a personal log, not a religious ruling.
- **Low — source hygiene:** keep signing files outside the repository; ignore local Kotlin compiler cache/session files. No new network endpoint, account, permission or third-party dependency. No GITHUB_TOKEN use.

## Verification boundary

Unit/Robolectric tests, migration fixtures, lint and signed APK inspection are the release gates. Not verified on a real device: beta.6 upgrade, Samsung launcher interactions, notification channels/sound under DND, force-stop restrictions, reboot/package-replacement delivery and OEM battery-management behavior. No device/emulator or screenshot claim is made. The optional Today-screen shortcut is deliberately omitted; logging is available in Streaks → Nafl.

## Final local results

157 tests passed (0 failures/errors/skipped), including API 28 and API 34 reminder tests, Room upgrades, load-failure recovery, pure phone/wide forms and Hilt-backed tab persistence on phone/Fold/tablet. lintDebug: 0 errors, 84 warnings, 2 hints. Debug and signed R8 release builds succeeded. Package/versionCode/versionName, APK signature, packaged city asset and preserved receiver/Room/widget class names were inspected. The signer matches prior betas.
