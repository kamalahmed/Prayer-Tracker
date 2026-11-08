# Phase 16 implementation plan

Approved scope: implement locally, commit as Kamal Ahmed <kamalacca@gmail.com>, no push/publication.

Architecture: additive Room v4 item type/target fields; transactional day counters shared by app/widget; dedicated counter Activity with optional key handling and current-session bookkeeping. Existing Nafl reports retain historical counts and gain counter charts. Existing AlarmManager flow gains independent per-prayer iqamah notices, offset/fixed timing and Friday Dhuhr override. DataStore stores timing/notification settings. Existing per-instance widget styles gain timing mode and selected counter ID; two new Glance providers reuse flow observation and refresh hooks.

- [x] Tests first: counter atomicity/bounds/targets, schema 3→4 preservation, twelve-hour conversion, iqamah planning/Friday/overnight/default-off, styles/flows and UI.
- [x] Room types/targets and presets; safe transactional increment/decrement; full-screen counter, haptics/volume, undo/session reset and stats.
- [x] Quran reminders always use 12-hour AM/PM controls and display.
- [x] Independent iqamah configuration, persisted settings, offset/fixed/Jumuah scheduling and safe delivery on existing boot/update chain.
- [x] Per-instance Tasbih widget and prayer/adhan/iqamah table widget, timing mode on existing prayer widgets, configuration and observed live data.
- [x] Run all tests, lint, self-review and signed release. Update progress, notes/checksum, commit/tag locally.

Semantics: counter totals are daily; resetting a session subtracts only increments made by that Activity session, never unrelated widget increments. Sessions restart on process death; daily totals persist. Counts cap at 1,000,000/day/item. Targets are optional; reaching/crossing a target causes a vibration once per crossing. Volume keys are captured only on the counter screen and only when explicitly enabled. Friday Jumuah replaces the Dhuhr iqamah configuration, not the obligatory-prayer tracker. Invalid fixed times before adhan are skipped; Isha may cross midnight. Iqamah notices are off until separately enabled and independent of adhan sound mode.
