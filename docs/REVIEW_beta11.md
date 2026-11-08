# Critical self-review — beta.11 / Phase 19

## Scope and assessment
Reviewed the local changes against the approved `beta11.html`, Phase 19 scope and baseline `970afe1706efa4385a872c8d88437974a5dacedf`. Version is **0.1.0-beta.11 / 12**. This is a local self-review, not a hardware or third-party audit.

**Assessment:** ready for local device testing. No unresolved critical or important data-safety finding was identified after the fixes below. Room remains v4: this release has no schema change, destructive migration, reset or logging-history rewrite.

## Strengths
- Derived reports are separated from persistence. `SummarySource` reads fard, item definitions, optional logs and Qada in one Room transaction per invalidation; it cannot mix old/new tables during an import.
- Fard prayers, Sunnah rakaat, other prayer sessions, Quran days/pages/minutes, counters and Qada remain distinct units. Hidden definitions retain their historical totals. Canonical Sunnah IDs keep the baseline at 12.
- Log/backfill/edit/customization/reminder/calendar flows are retained, while Overview progressively exposes group rows, slots, charts and daily histories. Existing Log remains the default for fast logging; Overview is the explicit redesigned statistics mode.
- Counter writes still use the existing atomic Room transaction. Ring state is derived, not stored, and cannot reset counts. Compose and Glance share the same transparent native painter.
- Backup receipts distinguish verified phone/folder copies from Android-managed cloud backup. A folder failure preserves and reports the phone success.

## Findings and actions
| ID | Severity | Finding / reference | Action and evidence |
|---|---|---|---|
| B11-01 | Important | beta.10 had no snackbar, placed feedback above a long scroll, said only “Local snapshot saved”, and advanced a generic timestamp before the SAF copy. | `backup/BackupScreen.kt:33–75`, `BackupViewModel.kt:90–102`, `BackupRepository.kt:60–83`: persistent near-action feedback/progress plus snackbar, Saved in N locations, separate successful-write timestamps, partial-error styling. Real fake-provider stream tests and UI tests pass. |
| B11-02 | Important | A closed SAF stream alone does not prove an intact copy; provider failures must not make a phone success disappear. | `BackupSnapshots.kt:31–46` reads the file back with size/SHA-256 verification before a folder receipt. Failure and silent-truncation tests keep the local file and old folder timestamp; worker API can retry. |
| B11-03 | Important | Combining independently emitted DB tables can briefly report pre-import fard with post-import optional worship. | `summary/SummarySource.kt`: one transactional history snapshot after invalidation; no summary write paths. Existing transactional-import rollback and navigation-cancellation tests remain green. |
| B11-04 | Medium | Detail series initially aggregated a future log into the current monthly bar. | `ui/nafl/NaflOverview.kt:171`: future values are zeroed before aggregation; untracked/future bars remain distinct. A new regression failed before the fix, then passed. |
| B11-05 | Medium | Exact target multiples must show a full ring, not an empty modulo ring; later laps must keep counting. | `nafl/CounterProgress.kt`, `CounterRingPainter.kt`: tested 0, 100, 101, 300, 350, free mode and bounded extremes. Overlapping lap colour and completed-lap label are derived from the unchanged daily total. |
| B11-06 | Medium | Fifty custom counters pushed the in-app tap ring off screen. | `ui/nafl/TasbihActivity.kt`: selected item/ring precede the scrollable item choices. Phone/Fold visibility regression and existing undo/reset/selection tests pass. |
| B11-07 | Medium | At 110×48, scaling the full wide layout produced microscopic counts. | `widget/WidgetUi.kt`: compact count/ring/name/Open fallback; a native rendered-text test failed before the fix and now requires a count of at least 10 px. Normal square/wide layouts retain count, target, lap and both actions. |
| B11-08 | Medium | Sunday-only delivery could miss a weekly summary when Doze deferred work past midnight. | `summary/SummaryTiming.kt:12–27`: a bounded 24-hour catch-up retains the original week; dedup keys prevent repeating it. Sunday/Monday/DST/boundary tests pass. Completed catch-up reports do not say “so far”. |
| B11-09 | Medium | Partial-current periods must not be compared with complete previous periods, and zero/untracked data must not become a spiritual score. | `SummaryViewModel.kt:39`: comparisons only for completed periods with previous data; unequal fard coverage is labelled. Best day means most recorded fard, earliest tied day; runs are explicitly within the selected week. |
| B11-10 | Medium | Denied notification permission/channel must not consume delivery deduplication. | `SummaryNotifications.kt`: private opt-in channel, explicit checks, key only after posting, stable IDs, validated period deep links, guarded worker retry. Permission, blocked-channel, independent-opt-in and repeated-navigation tests pass. |
| B11-11 | Low | The old activity target branch vibrated even with tap haptics disabled, and only the first target crossing signalled a goal. | Shared guarded preference-aware feedback and each completed-lap crossing; undo does not signal a completed lap. Existing count/session transactions are unchanged. |

## Data-safety audit
- No new DB table, migration, `DELETE`, clear-all or reset is introduced by the summary/ring features. Existing v1→v4 preservation and restore rollback tests all run in the full suite.
- Local snapshots remain verified atomic files, seven rolling daily files plus ten independent safety points. Status is recorded after the actual write/read-back, including pre-import/migration snapshots. A failed status write is reported, not called a cloud success.
- Accepted imports keep their existing non-cancellable transaction and post-commit alarm/widget reconciliation; summary scheduling is also reconciled.
- New summary preferences use the existing typed DataStore, so existing manual and Android backup allowlists include them. Invalid imported summary times are rejected. Older backups missing them use off defaults.
- Risky file, picker, scheduler, notification, haptic and bitmap paths are caught/guarded. Failed report loading presents Retry and never deletes saved history.
- No new network service, account, permission or Drive sign-in. Backup JSON remains personal and unencrypted; UI says so.

## Verification
Fresh serial gate:
```
./gradlew --no-daemon --no-parallel --max-workers=1 testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease
```
- **268 unit/Robolectric tests: 0 failures, 0 errors, 0 skipped** (226 existing + 42 new).
- `lintDebug`: **0 errors, 115 warnings, 4 hints**. Non-blocking style/dependency warnings remain, including KTX suggestions and optional modifier parameter order.
- Debug APK, instrumentation APK compilation and signed/minified release all succeed. Instrumentation tests were compiled, not run on a device.
- APK package/code/name: `com.kamalahmed.prayerapp` / `12` / `0.1.0-beta.11`.
- APK Signature Scheme v2 verifies; unchanged signer SHA-256: `022dddfbdf9c07eef5f47e4cdf141782fbbeb2559ff7a1691ea381333dce481d`.
- R8 mapping: all eight widget implementations remain distinct/named; `TasbihAction`, `SummaryWorker`, local backup worker and Room implementation names remain intact.
- **34 actual native Glance PNGs** plus `/workspace/prayer-app-release/widget-renders/beta11/contact-sheet.jpg`, visually inspected at normal and minimum sizes, light/dark and transparency. Native Compose window PixelCopy screenshots timed out under Robolectric and were not substituted for hardware screenshots; phone/Fold Compose interaction tests and actual Glance native renders are the verification used.

## Explicit limits / device checklist
- No real Galaxy launcher, installed beta.10→beta.11 upgrade, SAF provider permission-revocation, uninstall/restore, cloud upload, vibration-feel or OEM/Doze run was performed. The requested JPG filenames were absent from the attachment directory; the approved mockup/source/native renders were used.
- Android does not report its last successful cloud backup to this app. The screen therefore never fabricates one; system backup settings vary by OEM and have privacy/general-settings fallbacks.
- Daily notifications are same-day reflections after the chosen time; weekly reports have a 24-hour grace window. Android/force-stop policies can still defer or suppress delivery. Both features start off.
- The 110×48 fallback hides the visual target and −1 to keep the number readable; target/lap information remains accessible and Open remains available. Prefer 170×170 or 320×80 for full controls.
- Custom checkbox/Ruqyah items remain Other worship instead of guessing that every custom act is a prayer. Counter classification follows the current item type; historic counts are retained.
- Existing daily count bound (1,000,000), JSON limits and local-snapshot uninstall caveat remain. No real-time cloud-sync or Google Drive feature is claimed.

No push or publication is part of this phase.
