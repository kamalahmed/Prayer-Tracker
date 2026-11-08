# Phase 19 / beta.11 implementation plan

**Goal:** Implement the approved local beta11 HTML concepts, trustworthy backup receipts and lap-aware counter rings.
**Architecture:** Pure summary/ring models over existing Room v4 data; Compose grouped overview/detail and summary pages; opt-in DataStore notification preferences plus guarded periodic WorkManager delivery; independent local/folder backup receipts; shared Android Canvas ring painter for Compose and Glance. No destructive migrations or schema changes.
**Constraints:** Local only; no CloudAgent; version 0.1.0-beta.11/code 12; retain signing key and all history; author/committer Kamal Ahmed <kamalacca@gmail.com>; tag phase-19-summaries; do not push.

- [x] Model/TDD: `summary/WorshipSummary.kt`, `nafl/CounterProgress.kt`; test daily/week boundaries, no-data/future data, long totals, disabled-history inclusion, distinct units and lap boundaries. Commands: `./gradlew testDebugUnitTest --tests '*phase19*'` (red then green).
- [x] Backup/TDD: extend `BackupRepository`, `BackupSnapshots`, `BackupViewModel`, `BackupScreen`; test provider success/failure/read-back and per-destination timestamps. Keep existing File-returning dailySnapshot API; expose a receipt API to UI and preserve safety exports. Inline progress/result plus SnackbarHost; system backup settings link and unknown Android completion status.
- [x] Counter/TDD: shared transparent `CounterRingPainter`, in-app canvas and Glance bitmap; keep callback-only logging and square/wide/transparent/light layouts, cap bitmap dimensions; haptics respect preference, every completed lap gives target feedback. Native pixel/Glance tests render beta11 variants into release directory.
- [x] Overview/TDD: `ui/nafl/NaflOverview.kt` groups stable IDs/types, excludes baseline slots from duplicate groups, includes hidden history separately, uses show-more and item detail charts. Keep day logging/editors/calendar/reminders via a separate Log / Overview switch so no functionality is deleted.
- [x] Summaries/TDD: add `summary/SummaryViewModel.kt`, `SummaryScreen.kt`, `SummaryNotifications.kt`; add a dedicated route linked from Streaks and Today. Daily/weekly date navigation, completed-period-only comparisons, transparent best-day rule and streak labels. Notification settings off by default; dedup after actual delivery, permission/channel guard, date/time/DST safe periodic check and resume/boot scheduling; deep-link into selected summary.
- [x] Integration: version/code, AppSettings typed backup coverage for summary settings, start/rearm worker on app/system changes and after restore. Production DB stays v4; no deletes/reset/migration.
- [x] Verification: all existing/new tests, lint and debug/androidTest compilation, signed/minified release; verify certificate/package/code and R8 class identities; critical self-review `docs/REVIEW_beta11.md`, progress, actual widget PNGs/contact sheet.
- [x] Package APK/sha256/RELEASE_NOTES_beta11.md (ending SHA-256 + versionCode:12), commit/tag locally with required identity; verify clean tree and no push.

## Verification record
- Fresh full serial gate passed: 268 tests (0 failures/errors/skips), lint 0 errors/115 warnings/4 hints, debug + instrumentation APK compilation + signed/minified release.
- Native counter renders: 34 PNGs plus contact sheet under `/workspace/prayer-app-release/widget-renders/beta11/`.
- Existing Log default retained; Overview is the redesigned stats mode. Reports are derived over Room v4; no migration needed.
- Reproduced and fixed future detail aggregation, off-screen ring with many items, unreadable resize-minimum text and Sunday Doze catch-up. Repeated notification navigation is integration-tested. Real device/SAF/cloud delivery remains unverified.
- APK SHA-256: `0ea52cf579a028877c7aa3bb609c1ee8793c74f53e5742ce864475ca99fb4e66`.
