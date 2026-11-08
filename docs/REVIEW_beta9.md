# Phase 17 / beta.9 updater diagnosis and self-review

## What actually caused discovery/delivery to fail

The beta.8 code checked automatically **only through a daily WorkManager job**. That job had a **one-hour initial delay**, a two-hour daily flex and a battery-not-low constraint. Neither Application nor Activity resume called the checker. The only in-app banners were in Settings/More, and those banners depended on a check having happened. Opening Today therefore did not discover or show an update.

There was a second, deterministic delivery bug: UpdateWorker wrote `notifiedTag` even when `notify()` returned early because Android 13 notification permission was denied (or threw/notifications were blocked). Once marked, that release was never notified again after permission was granted. A regression test reproduces denial → check → grant → notification retry.

Not the root cause:
- Auto-check already defaulted to **true**, not false. Existing explicit false preferences were nevertheless retained forever; beta.9 performs the requested one-time migration to true and respects future opt-outs.
- The app already used **GET /repos/kamalahmed/Prayer-Tracker/releases?per_page=15**, not `/releases/latest`.
- Prereleases were included by default on beta builds. The published release list was read during this investigation: beta.1 through beta.8 have valid `v0.1.0-beta.N` tags, APK/checksum assets and `versionCode: N+1` notes. A checked-in, reduced fixture verifies beta.8 is selected from every earlier beta and confirms the published SHA-256/code. Version parsing was not broken.
- The existing `updates_v1` channel has DEFAULT importance. User-muted channels, denied permission, Doze or vendor background restrictions can still suppress notifications; without this phone's diagnostics, no claim is made that its exact permission/battery state was observed.

## Changes and reviewed behavior

- Every Activity resume triggers an automatic connected check, serialized with periodic/manual checks and throttled by a persisted **last attempt** (including failures) to once per five minutes. Clock rollback recovers; explicit Check for updates bypasses the throttle. Transient failures keep a known update visible.
- Existing daily work is updated in place to connected **15-minute** periodic work, no initial delay/battery-low gating. Unmetered-only remains an explicit option. Android's scheduler may delay work: this is not a five-minute background timer.
- Today has Update / Later, progress and notification-permission guidance. Later snoozes the selected release for an hour, not future releases. System notifications include a direct Update action. Permission/channel failures do not consume notification deduplication; available and downloaded notifications use distinct keys.
- Only explicit acceptance enqueues an independent foreground WorkManager download. The selected release is persisted across process recreation. Progress is shown in-app/system notification; Range resume, size cap, trusted HTTPS URLs/redirects, required SHA-256, package, higher versionCode and matching signer checks remain enforced. Cached APKs have hash/key metadata and are rehashed again immediately before installation.
- A resumed MainActivity continues a verified accepted download into installation. Unknown-sources permission uses an explanatory panel; after settings return, installation continues if granted, otherwise the file stays available. Installer confirmation is launched only while foreground or through its notification PendingIntent, never by relying on an unrestricted background broadcast Activity launch. Android's final consent is not bypassed.
- Background completion posts a ready-to-install notification and continues on the next foreground visit. This is the intentional Android restriction, not a promised background installer popup.
- Self-review caught and fixed cached-install acceptance not emitting a new UI state: consent now has its own StateFlow, so Update works even when Downloaded is unchanged. Accepted releases restore without the network cache; stale already-installed acceptance is cleared. Corrupt cached verification metadata is invalidated before retry, avoiding an endless install-failure loop. Foreground-worker start failures become a visible retryable action rather than leaving a stuck progress state. Notification guidance accounts for a muted updates channel and opens Android notification settings, including when runtime permission has previously been denied.
- No Room schema change. No GitHub write, push or publication. Signing material stays outside git.

## Verification / remaining device checks

Final verification: 196 unit/Robolectric tests, 0 failures/errors/skipped; lintDebug 0 errors (96 warnings, 2 hints); assembleDebug and signed R8 assembleRelease passed. APK v2 signature and package/version confirmed (0.1.0-beta.9 / 10), unchanged release signer; update worker/download worker/receiver and existing widget names preserved in the R8 mapping. Artifacts are recorded in PROGRESS.md and RELEASE_NOTES_beta9.md. Tests include actual prerelease metadata, versionCode tie-breaking, throttle/concurrency/failure paths, preference migration, interval/constraints, blocked permission/channel, notification action, Today card and cached-file tampering.

Not hardware-tested: OEM/Doze timing, notification visibility on this phone, foreground download survival under force-stop, unknown-sources screens and an end-to-end signed installation. Force-stop and Android restrictions can stop WorkManager until the app is opened again. No device telemetry was available to establish which of these affected the owner previously.
