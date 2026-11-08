# Phase 17: reliable update discovery and accepted installation

Local workflow, no push/publication. Base: af97433 (beta.8).

Diagnosis: compare published release JSON with the actual parser; inspect scheduling, launch/resume wiring, notification permission/channel and deduplication. No phone telemetry is available, so distinguish proven code defects from device-dependent causes.

- [x] Actual beta release parsing fixture, versionCode and five-minute throttle tests.
- [x] Persisted one-time migration to auto-check on; resume checks and connected 15-minute WorkManager periodic checks, no initial hour/battery-low gating.
- [x] Today Update/Later banner; safe, permission/channel-aware system notification with direct Update action; only deduplicate a successfully posted notification.
- [x] Accepted update persisted to durable foreground WorkManager download, progress, unchanged hash/package/version/certificate validation; automatic foreground installer continuation with unknown-sources guide and background-ready notification.
- [x] Full tests/lint, self-review and root-cause documentation, signed beta.9 / code 10, local commit/tag.

Android permits periodic work no more often than every 15 minutes and can delay it further. A five-minute foreground throttle is not a five-minute background timer. Installer UI requires foreground user interaction; an update finishing in the background must provide a ready-to-install notification and continue on the next app resume.
