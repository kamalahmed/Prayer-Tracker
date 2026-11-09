# Critical self-review — beta.13 / Phase 21

## Scope
Baseline `e6c2d6c` (beta.12). Version **0.1.0-beta.13 / 14**. Built from two approved mockups (`qada-logging.html`, `insights.html`):
1. **Qada logging redesign:** clear feedback on every tap, a "done today" state, batch logging, a goal-reached state, and a compact history that stays short however many prayers are logged.
2. **Insights:** a new Streaks sub-tab (Stats / Insights / Qada / Nafl) with checks, an on-device coach, fard combined/per-prayer, changes, trends and scorecards.

This is a local self-review (JVM/Robolectric), not a hardware or third-party audit. Room stays **v4**. No schema change and no migration.

## What changed
### Qada (numbered sections 1–6)
1. **Overview:** animated progress ring, remaining count, "X prayers made up of Y".
2. **Log today:**
   - Each `+1` press:
     - scales the button and flashes the row;
     - floats a "+N" and pops the "×N" count;
     - plays a haptic tick;
     - shows a snackbar ("Fajr +1 · ×3 today", "N left", **UNDO**; auto-hides after 4.5 s).
   - Prayers done today stay highlighted with ✓ and ×N. TalkBack reads them as "Fajr done today, 3 times".
   - **+N** opens a bottom sheet with chips 2/5/10/20/50/100, ±1/±10 and "Add N Fajr".
   - "+1 full day (5)" logs all five fard; one Undo removes all five.
   - With a daily goal: a goal bar, and a gold "Goal reached" pill plus a distinct haptic when the goal is crossed.
3. **History:**
   - One row per day for the last 14 days, as "Today / Yesterday / date" with per-prayer chips.
   - Before that, one row per month (weeks inside) for the last 12 months, then one row per year (months inside).
   - At most ~37 rows, regardless of how many entries there are.
   - **Edit day** opens ± steppers per prayer that set that day's count exactly. **Undo last** is also here.
   - This replaces the per-entry log list.
4. **Progress by prayer**
5. **Pace & finish:** the weekly/monthly chart is now behind a toggle.
6. **Goal, reminder & debt:** collapsible. Adds a "Tap vibration" switch.

Haptics use the existing shared `tasbih/haptics` preference, so the Tasbih and Qada vibration settings are the same switch. They use predefined click/tick effects on API 29+ and a short waveform otherwise. All haptic calls are guarded.

### Insights (on-device, rule-based; no network, no AI)
- **Windows:**
  - "This week" is the last 7 complete days and is compared with the 7 days before.
  - Trends use 5 weekly windows or 5 calendar months.
  - "vs yesterday" compares only prayers already due today against yesterday's same prayers.
  - Dhikr and Quran have no time of day, so they compare after Isha.
  - Qada compares against entries logged before the same clock time yesterday (`at <= now−24h`).
- **At a glance:** fard, Sunnah, dhikr, Quran and qada are each rated:
  - **Good**;
  - **Needs attention**: below 80% of the 4-week average, a 3+ day zero run, or (fard) 3+ missed;
  - **Not tracked yet**.
  - "Steady" means within ±5%.
- **Coach:**
  - It opens with praise, then gives at most 3 suggestions, then the consistency hadith.
  - Suggestion buttons deep-link:
    - weak on-time prayer → **Settings › Iqamah** (beta.12 before-iqamah alarm);
    - missed prayers → Qada tab;
    - Quran/Sunnah/dhikr → Nafl tab;
    - new user → Stats.
  - **Not helpful** mutes the shown suggestions for 28 days.
  - **Hide coach** collapses the card to "Coach hidden · Show coach". Both persist in SharedPreferences `insights`, and `coach_hidden` is included in backups.
- **Fard:**
  - Combined: totals, on-time/late/missed and a daily bar.
  - Per prayer: on-time dots, STRONGEST / NEEDS CARE.
- **What changed, Trends (Week/Month), Scorecards** with sparklines.
- **Empty/new user:** "Not tracked yet" badges, a start-tracking suggestion and an empty fard hint. Nothing divides by zero; covered by a test.

## Performance (18,000+ qada entries)
- Every Qada, Insights and Summary read is a SQL aggregate:
  - `GROUP BY prayer` totals;
  - `GROUP BY date` day totals;
  - a 15-day `GROUP BY date, prayer` breakdown;
  - `countOnUntil`.
- Nothing loads all rows. `SummarySource` also switched from all entries to day totals.
- Test: 18,250 entries load, report and build history in under 5 s on the JVM. The history stays at ≤37 rows.
- Writes (log, batch, undo, edit day) run under a `Mutex`, so a fast Undo cannot race the insert it reverses. Undo deletes exactly the ids of the last action.
- `setDayCount` runs in a Room `@Transaction`. It inserts the difference, or removes that day's newest entries first. Future dates are rejected and the value is clamped to 0–10,000.

## Migration / data safety
- No Room change, so no migration is needed. A test builds a beta.12 v4 database with rows in every table, opens it with the new code and checks every row is kept.
- New preferences only: `insights/coach_hidden`, `insights/coach_muted`.
- Backup:
  - Export adds an `"insights"` object.
  - Validation accepts it with booleans only.
  - Older backups without it still restore.
  - Merge keeps the existing value.
- Risky paths are guarded: haptics, deep-link navigation, and VM flows (`reportErrors`) and writes (`safeHandler`).

## Findings during review
| ID | Severity | Finding | Action |
|---|---|---|---|
| B13-01 | Important | The old Qada screen and `SummarySource` loaded every entry, which grows linearly with 18k+ rows. | Aggregate DAO queries everywhere, plus an 18,250-row test. |
| B13-02 | Medium | The snackbar Undo could have removed the newest entry instead of the action's entries (e.g. after a batch). | `QadaAction(ids)` with exact-id delete; test. |
| B13-03 | Medium | After an Edit day, Undo could reach into the edited day. | Editing clears the pending action. |
| B13-04 | Low | SharedPreferences listener updates arrive on the main looper; the first prefs test missed them. | Test idles the main looper; the prefs flow is driven by the listener (restores update live). |
| B13-05 | Low | Under Robolectric the virtual clock runs past the 4.5 s snackbar while waiting on the background insert, so asserting the full-day title in the UI test was unreliable. | Title logic extracted (`snackTitle`) and unit-tested. The UI test asserts the Fajr snackbar, Undo, batch, full day and Edit day. |
| B13-06 | Low | The old smoke test relied on the removed "Fajr × 1" list and the always-visible chart. | Updated to the new history, "Undo last", chart toggle and Insights tab. |

## Verification
Fresh clean serial gate (build cache disabled):
```
./gradlew --no-daemon --no-build-cache --no-parallel --max-workers=1 clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
./gradlew --no-daemon assembleRelease   # after commit (embeds the git revision)
```
- **305 unit/Robolectric tests: 0 failures, 0 errors, 0 skipped.** That is 287 existing plus 18 new in `phase21/`:
  - `QadaHistoryTest` 4
  - `QadaAggregateDbTest` 4
  - `InsightsEngineTest` 6
  - `InsightsPrefsTest` 2
  - `Phase21UiTest` 2
  
  `StreaksScreensSmoke` was updated for the new Qada UI.
- `lintDebug`: **0 errors**, 125 warnings (non-blocking, pre-existing kinds).
- Debug APK, instrumentation APK and the signed/minified release all build.

## Known limits
- Dhikr and Quran have no time of day, so "vs yesterday" for them only counts after Isha.
- The coach's alarm link opens Settings › Iqamah. The user still turns on the before-iqamah alarm for that prayer there; the app does not toggle it silently.
- Haptic feel is not verified on hardware: Robolectric has no vibrator, and OEM "touch vibration" settings may suppress the effects.
- The insight rules are deliberately simple thresholds (80% of the 4-week average, 3-day gaps). They are guidance, not judgement.
- Not tested on a real device: animation smoothness with very large histories, and TalkBack wording on device.

No push or publication is part of this phase.
