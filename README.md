# Cadence

Your health data is already on your phone. Cadence lets you see it without gifting it to an ad network or a venture-backed "wellness platform" that will eventually email you about mindfulness. If you *want* it on a web dashboard or in front of an AI assistant, there is an optional cloud sync you have to sign in to first.

Cadence is a private, **read-only** Android viewer for [Health Connect](https://developer.android.com/health-connect) and, if you insist on Samsung's ecosystem, Samsung Health. It reads the data already on your device and renders it properly. It sends none of it anywhere unless you sign in to the optional cloud sync.

> **Origin.** Cadence began as a fork of [GuyOnWifi/heartwood](https://github.com/GuyOnWifi/heartwood) (GPL-3.0). The GPL obligations and the upstream copyright notices stay.
>
> **Naming note.** The project was renamed from Heartwood to Cadence. The repository URLs still carry the old name until the repos are renamed. The backend now lives at `cadence-api.dazaike.workers.dev` (same database; existing accounts and tokens carry over).

> No ads. No trackers. No analytics. Nothing leaves your device until you choose to sign in to cloud sync. Revolutionary, apparently.

## What it looks like

Cadence 2.0 is the web dashboard, rebuilt natively. Same flat neutral surfaces, same 24 dp cards with a hairline border and a small tinted category icon, same page titles, same numbers. It follows your system dark or light setting, with a System / Light / Dark choice in Settings, set in Inter, with an accent colour you pick (blue, violet, rose, orange, emerald, amber or cyan, or Material You system colours if you must). No gradients, no glows, no "wellness" pastels.

On a phone you get a floating bottom bar (Overview, Activity, Heart, Sleep, Insights, and a More menu for Body, Calendar, Data Sources and Settings). On a tablet or foldable (840 dp and up) the bar becomes a left rail with the logo, the section list, a status card and a theme toggle. Charts are drawn natively: touch or drag to read a value, tap "Open day" to jump to it in the Calendar.

## What it does

- **Overview**, a customizable widget grid: daily activity rings, steps for 14 days, heart, sleep, recovery (a labelled *estimate*), body, goals this week, insights and a consistency heatmap. Named layouts, hide and show, S / M / L sizes (on a phone S is half width), reorder in Edit mode.
- **Activity, Heart, Sleep, Body**, the web's pages: range bar (week, month, 90 days, year, all, custom) with day / week / month granularity, trends with a dotted average and goal line, weekday patterns, heart-rate zones, sleep schedule and stages, BMI, and a relationship explorer. Missing days are gaps, never zeros.
- **Insights**, computed on-device from your own data with the same formulas as the web dashboard: readiness estimate, week-over-week changes, sleep consistency, correlations (labelled as associations), personal records. Nothing here is a medical diagnosis.
- **Calendar**, a month grid shaded by goal progress and a day view with a heart-rate timeline and a scrubber.
- **Data Sources**, switch between Health Connect and Samsung Health, see which data types Cadence can read, how fresh each one is, and the cloud sync status with a Sync now button.
- **Settings**, appearance, units (Imperial or Metric), accent colour, goals, assumed max heart rate, dashboard widget visibility, widget refresh interval, the local MCP server, your Cadence cloud account, export and about.
- **Home-screen widgets**, Glance widgets in the same neutral palette for the metrics worth seeing before you unlock your phone. They follow the system day / night setting.
- **Export & AI Share**, write CSV or JSON through Android's file picker, or share formatted Markdown directly with your AI assistant through the system share sheet. This works fully offline; nothing is relayed through a server.
- **Samsung Health** *(optional)*, read directly from the Samsung Health app on-device instead of Health Connect.
- **Local AI server (MCP)** *(optional)*, an opt-in loopback Model Context Protocol server for local LLMs and tools. `localhost` only. Unchanged by cloud sync: it never leaves the device and does not use an account.
- **Cloud sync** *(optional, off until you sign in)*, create an account or sign in from Settings > Account and Cadence uploads your readings (and goals) to a Cadence cloud server so you can see them on the web dashboard and let ChatGPT, Claude or another AI assistant read them through the cloud's remote MCP server. Details below.

## The boringly competent stack

- Kotlin, Jetpack Compose, a small Material 3 base for dialogs and fields; the design system itself (colour tokens, type scale, cards, charts) is native Compose ported from the web dashboard
- Charts are drawn with Compose `Canvas`: no chart library
- The analytics (readiness, sleep consistency, insights, correlations, goal stats) are a Kotlin port of the web dashboard's `analytics.ts` / `view.ts` with JUnit tests
- [Inter](https://rsms.me/inter/) (variable), bundled, under the SIL Open Font License 1.1
- `androidx.health.connect:connect-client` and Samsung Health Data SDK for on-device reads
- `androidx.glance` widgets and WorkManager refreshes (widgets, and cloud sync when enabled)
- DataStore for goals, dashboard layouts, appearance, data source, unit and cloud-sync preferences; the cloud session token is encrypted with an Android Keystore key
- NanoHTTPD for the optional loopback MCP server; plain `HttpURLConnection` for cloud sync (no extra networking dependency)
- `minSdk 30`, `targetSdk 36`

No "AI-native health intelligence layer." Just native Android code reading local data like it was designed to, plus an opt-in uploader if you ask for one.

## Build it

Clone the repository. Build the APK. Avoid downloading a random prebuilt binary from a forum run by a man named `APKMaster69`.

```sh
git clone https://github.com/Dazaike/heartwood
cd heartwood
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Or use Nix, which pins the toolchain so your build remains reproducible instead of becoming an archaeological dig six months from now:

```sh
nix develop
./gradlew :app:assembleRelease
```

You need JDK 17 and the Android SDK (`platforms;android-36`, `build-tools;36.0.0`). Gradle pins the rest because manually reconstructing dependency versions is how people lose weekends. See [docs/REPRODUCIBLE.md](docs/REPRODUCIBLE.md) for the full reproducible-build setup and Nix notes.

### Sign a release, if you must

Create `keystore.properties` (already gitignored) or provide the equivalent `CADENCE_*` environment variables:

```properties
storeFile=/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Without signing credentials, `assembleRelease` emits an unsigned APK. That is fine for F-Droid; it signs its own builds because supply-chain hygiene is not optional.

## CI, because hope is not a test strategy

- **CI** (`.github/workflows/ci.yml`) builds and lints the debug APK on every push and pull request.
- **Release** (`.github/workflows/release.yml`) builds the release APK for every `v*` tag, signs it when secrets exist, and attaches it to the GitHub Release.

## F-Droid

Store descriptions and changelogs in `fastlane/metadata/android/en-US/`. The `fdroiddata` recipe template lives at [docs/fdroid-metadata-template.yml](docs/fdroid-metadata-template.yml). Follow it. F-Droid is not impressed by improvisation.

## Privacy: the entire point

Cadence requests only **read** permissions on your health data. You decide which types it can read and can revoke them whenever you like.

**Out of the box, nothing is transmitted.** There are no accounts, no analytics and no trackers. The app declares the `INTERNET` permission, but it is only used by the two opt-in features below.

- **Local AI server (MCP)**, opt-in, listens strictly on loopback (`127.0.0.1`). No account, no cloud.
- **Cloud sync**, opt-in, and does nothing until you sign in (Settings > Account). After you sign in, Cadence uploads your health readings (steps, distance, energy, floors, exercise, hydration, hourly average heart rate, resting heart rate, HRV, sleep sessions with stages, SpO₂, respiratory rate, VO₂ max, weight, height, blood pressure, blood glucose) and your goals over HTTPS to the server address shown in Settings (default `https://cadence-api.dazaike.workers.dev`, editable; plain HTTP is accepted only for `localhost`). Signing out stops syncing and forgets the session on the phone; data already uploaded stays in your cloud account until you delete it from the dashboard. Cadence stays read-only against Health Connect, and cloud sync never writes to it.

### Cloud sync details

- **Account:** email and password, handled by the server (PBKDF2-hashed). The phone stores only a session token, sealed with a non-exportable Android Keystore AES-256-GCM key.
- **When it syncs:** right after sign-in (first sync backfills up to 365 days, or 30 days if Health Connect's history permission is not granted), about every 15 minutes while online if auto-sync is on, when you open the app (at most once per 5 minutes), and when you tap "Sync now". Later syncs re-send the last 3 days; uploads are idempotent.
- **Units:** always metric/SI on the wire (km, kg, cm, L, mg/dL...), whatever your on-screen unit setting is. Goals are sent in the dashboard's units (miles, fl oz).
- **Remote MCP:** create a read-only API token on the web dashboard and point your AI assistant at the cloud's `/mcp` endpoint. That endpoint is separate from the on-device loopback server above. How the app, cloud and dashboard fit together: [docs/CLOUD.md](docs/CLOUD.md).
- **Samsung Health:** cloud sync uses whichever data source is active in Settings. Samsung Health has no resting HR, HRV, respiratory rate or VO₂ max, so those are never synced from it.

The optional Samsung Health integration reads directly from Samsung Health through its official on-device SDK. Your personal health record stays on your device until you choose to sync it. The robots have standards.

## License

[GPL-3.0-or-later](LICENSE) © Eason Huang

The bundled [Inter](https://rsms.me/inter/) typeface is © The Inter Project Authors, licensed under the [SIL Open Font License 1.1](https://openfontlicense.org).
