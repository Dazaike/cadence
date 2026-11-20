# Changelog

## [Unreleased]

### Added
- Samsung Health: opening Cadence (and refreshing, at most once a minute) now makes Samsung Health sync from the watch before Cadence reads. Without Shizuku it is brought to the front for 5 seconds and Cadence returns; this only runs while Cadence is on screen, because Android blocks one app from opening another from the background. The old wake only connected to the SDK and never opened the app.
- Optional Shizuku support (Data Sources > Silent Samsung Health sync): with Shizuku running and Cadence allowed, Samsung Health is opened on a hidden virtual display instead, so nothing appears on screen and the wait ends as soon as the watch sync finishes (about 3 seconds). It also runs before each cloud sync, including background ones. Falls back to the visible method when Shizuku isn't available.
- A status line at the top of the app while Cadence is working: opening Samsung Health, syncing the watch, reading health data (with counts), loading heart-rate history, and each stage of a cloud sync.

### Changed
- Overview widgets no longer have a full-screen button.
- Overview: the layout picker, Edit, New layout, Reset and Delete controls (and edit mode with its size and move buttons) are gone. The subtitle now shows when the data was last updated and the last cloud sync result instead of "Data through".
- The launcher icon's heart is optically centred (it sat high).

## [v2.0.0] - 2026-11-19

### Changed
- **Full UI redesign: the app is now the web dashboard, rebuilt natively.** Flat neutral surfaces (24 dp cards, hairline borders, no gradients), the web's dark and light palettes, Inter (variable) typography, and the web's page set: Overview, Activity, Heart, Sleep, Body, Insights, Calendar, Data Sources and Settings, with the same titles, subtitles, range bar (week, month, 90 days, year, all, custom) and day / week / month granularity.
- Navigation: a floating rounded bottom bar (Overview, Activity, Heart, Sleep, Insights, More) on phones; a left rail with logo, section list, status card and theme toggle at 840 dp and wider. Page transitions fade and slide, the selection highlight springs, and the system "remove animations" setting is respected.
- Appearance: System / Light / Dark, and a selectable accent (blue, violet, rose, orange, emerald, amber, cyan, or Material You system colours on Android 12+). Material You is no longer the default.
- Overview is a customizable widget grid with named layouts, hide and show, S / M / L sizes (S is half width on a phone) and reordering in Edit mode: daily activity rings, steps for 14 days, heart, sleep, recovery, body, goals this week, insights, activity consistency. The old metric-tile Today screen, per-metric detail screens and weekly Summary are gone; dashboard layout settings start fresh.
- Charts are native Compose Canvas (trend, bars, range band, stages, schedule, heatmap, scatter, gauge, day timeline). Touch or drag to read values; "Open day" jumps to the Calendar.
- Status card (rail, and Data Sources on phones): "Your data", cloud sync status with the last sync as a relative and an exact time, and a Sync now button. Home-screen widgets are restyled to the same neutral palette (they follow the system day / night setting).
- The pixel-art Cadence mark (nearest-neighbour scaled) appears in the rail, onboarding and About. The launcher icon is unchanged.
- Version 2.0.0 (versionCode 4).
- Renamed the app from Heartwood to **Cadence**. New application id `dev.dazaike.cadence`, so this installs as a separate app (no data migration; set up and sign in again). Export files are now `cadence-export.*` with a `Cadence Health Data Export` header.
- The default cloud server is now `cadence-api.dazaike.workers.dev` (same database, accounts and tokens carry over). Existing installs may need to update the server address in Settings > Account.

### Added
- On-device analytics ported from the web dashboard (readiness estimate, resting-HR proxy, sleep consistency, insights, correlations, goal hit rate and streaks, week and month comparisons, BMI, activity heatmap), with JUnit tests. Where the data source cannot supply something, the web's empty state is shown instead of an invented number. Days without a recording are gaps, never zeros.
- Settings: appearance, accent, goals in your display units (including a sleep goal, kept on the phone), assumed max heart rate, dashboard widget visibility, and a reset for preferences.

### Removed
- Dynamic-colour-only theme, the Today / Summary screens and the Navigation Compose and Glance Material 3 dependencies.

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
