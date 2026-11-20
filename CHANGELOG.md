# Changelog

## [Unreleased]

### Changed
- Renamed the app from Heartwood to **Cadence**. New application id `dev.dazaike.cadence`, so this installs as a separate app (no data migration; set up and sign in again). Export files are now `cadence-export.*` with a `Cadence Health Data Export` header. The default cloud server address is unchanged.

## [v1.1.0] - 2026-11-18

### Added
- Optional **Cloud sync** (Settings > Cloud sync). Sign in or create an account on a Heartwood cloud server (default `https://heartwood-api.dazaike.workers.dev`, editable; HTTPS required except `localhost`) and Heartwood uploads your readings so the web dashboard and the cloud's remote MCP server can read them. Nothing is sent until you sign in.
- Sync uses the active data source (Health Connect or Samsung Health), always in metric/SI units: daily totals (steps, distance, active/total energy, floors, exercise, hydration), hourly average heart rate, resting heart rate, HRV, sleep sessions with REM/deep/light/awake stages, SpO₂, respiratory rate, VO₂ max, weight, height, blood pressure (systolic and diastolic) and blood glucose.
- First sync backfills 365 days (30 without Health Connect's history permission); later syncs re-send the last 3 days. Batched at 5,000 points per request. Metrics without granted access are skipped and a failing metric never stops the others.
- Auto-sync with WorkManager (about every 15 minutes, network required) plus an expedited sync right after sign-in, on "Sync now", and when the app is opened (throttled to once per 5 minutes). Retries with exponential backoff on network errors; an expired session signs the app out locally.
- Goals are pushed to the cloud (distance in mi, hydration in fl oz) when they change or at sync time. Goals are never pulled.
- The session token is stored only as ciphertext sealed by a non-exportable Android Keystore AES-256-GCM key.
- `HealthDataSource` gained `readHourlyHeartRate`, `readBloodPressure` and `readSleepSessions` for the Health Connect and Samsung Health managers.
- Network security config: cleartext HTTP is permitted only for `localhost`, `127.0.0.1` and `10.0.2.2` (development).

### Changed
- README, Settings footer and F-Droid description no longer claim the app never uses the network or has no accounts; the on-device loopback MCP server is unchanged.
- Version 1.1.0 (versionCode 3).

## [v1.0.1] - 2026-11-05

### Added
- Samsung Health: pulling to refresh (or tapping refresh) on Today and detail screens first wakes the Samsung Health app so data is current (`SamsungHealthWake`).
- Export: a "Preparing export…" progress dialog while CSV/JSON/Markdown is generated.
- Export: per-metric timing log (`HeartwoodExport` logcat tag).

### Changed
- README rewritten with a new intro and tone.
- Samsung Health heart-rate export reads one day at a time and reduces samples to 5-minute averages (all export formats); Markdown export reduces Health Connect heart rate the same way when there are 2000+ samples.

### Fixed
- Export no longer hangs on large heart-rate data from Samsung Health (previously a single 30-day read of per-second watch samples). Not yet confirmed on device at release time.
- Health Connect reads now page through all results instead of stopping at the first page of 1000 records.
- Refresh spinner no longer sticks if a refresh throws.

### Included commits since v1.0.0
- `d600323` feat: Samsung Health direct integration, Markdown AI share export, and unit system support
- `3b9fe94` docs: update README with AI share, unit system, dashboard customization, and fork repo

## [v1.0.0]
- Initial release.
