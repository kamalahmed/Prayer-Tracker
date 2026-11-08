# Phase 18 Widget Layout Implementation Plan

**Goal:** Fit real launcher cells, align prayer timing and provide truthful counter previews.
**Architecture:** Keep SizeMode.Exact and observed live flows. Persist bounded per-instance coverage settings in WidgetStyle; share responsive layout logic between configuration and real RemoteViews rendering.
**Constraints:** Local only. beta.10 / code 11; retain signing identity and data; no push/publication.

- [x] Reproduce provider and style regressions in Phase18 tests; run red.
- [x] Add fill/wrap, outer margin, overall scale and tick time placement, tolerant persisted decoding and live size preview controls.
- [x] Correct provider bounds and tasbih preview; implement adaptive square/wide counter and aligned time columns without separators.
- [x] Render actual Glance RemoteViews at multiple sizes; validate coverage, counter text, timing placement and alignment.
- [x] Run final unit/Robolectric tests, lint, debug and signed release after self-review fixes. Inspect render PNGs.
- [x] Update progress, stage release/checksum/notes, commit as Kamal Ahmed and annotate phase-18-widget-layout. Never push.

## Added backup scope (owner steering)
- [x] Versioned seven-table JSON plus typed app/widget/counter/update preferences and custom audio; SAF CreateDocument/OpenDocument, summary and explicit Merge/Replace.
- [x] Validate entirely before mutation, verified atomic safety snapshot, transactional SQL restore and preference rollback; test corrupt/future/old input, conflict-aware merge and injected mid-transaction failure.
- [x] Daily WorkManager snapshots (seven) and ten separate import/migration points, persistable SAF folder option, local snapshot restore/export list and last-backup date.
- [x] Wrap production migration chain with pre-migration snapshots. Explicit Android cloud/device-transfer include rules for DB, DataStore, audio and snapshots; no updater cache/installer state.
- [x] Test real v3 migration capture, audio round trip and Robolectric Backup screen. Document storage/OS/quotas and launcher limitations honestly.
