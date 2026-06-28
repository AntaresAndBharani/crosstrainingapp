# CrossTraining

An offline Android app to track CrossFit-style strength progression: you run a
**complex/routine** (e.g. *Clean + Hang Clean + Push Jerk*, or *Snatch 3-2-1
waves @ E3MOM*) to improve a **main lift**, and the app tracks both the working
weights used in the routine **and** any new rep-maxes you hit on the main lift —
all grouped into training **cycles** whose end date you can extend or shorten.

It also tracks monostructural machines (Air Bike, Rower, SkiErg, …) scored by
calories / distance / time.

Everything is stored locally on the device (Room/SQLite). No account, no server,
no cost.

## Features

- **Cycles** — training blocks with a start date and an extendable/optional end
  date; one cycle is active at a time.
- **Library** — your tracked exercises (seeded with common lifts + machines) and
  your routines/complexes. Logging an exercise that doesn't exist creates it.
- **Log** — a session is an ordered list of **blocks** (warm-up, strength,
  accessory, WOD…). Each block has its own format (EMOM/E3MOM/AMRAP/…), rep
  scheme (`3-2-1-3-2-1…`), optional target lift, and per-set entry with
  **warm-up** and **failed-rep** toggles and an optional **wave/round** tag. A
  WOD block takes free-text movements + a single score (e.g. "2 rounds + 15
  reps"). Optional per-block "new rep-max" capture.
- **History** — every logged session with its blocks and sets (warm-ups and
  failed reps marked).
- **Progress** — per-exercise rep-max bests (1/2/3/5RM…) with history, plus
  working-weight progression across sessions, shown as **line charts** (pick the
  rep count to chart) and tables.
- **Backup** — export the whole database to a CSV file and re-import it later
  (Library tab → ⋮ menu). The CSV preserves IDs/relationships so a restore is
  exact; it's also human-readable/spreadsheet-friendly.

## Tech

- Kotlin · Jetpack Compose (Material 3) · Navigation Compose
- Room (KSP) for local persistence
- minSdk 26 · targetSdk/compileSdk 35

## Build it yourself (no Android Studio required)

The build runs in **GitHub Actions** — you don't need a local Android SDK.

### Option A — download the APK from a Release (recommended)

1. Push a version tag:
   ```sh
   git tag v1.0.0
   git push origin v1.0.0
   ```
2. The **Release APK** workflow builds the app and publishes a GitHub Release
   with `crosstraining-v1.0.0.apk` attached.
3. On your Android phone, open the Release page, download the `.apk`, and tap it.
   When prompted, allow *Install unknown apps* for your browser/Files app, then
   tap **Install**.

### Option B — download the APK from a workflow run

Every push to `main` runs the **Build APK** workflow and uploads
`crosstraining-debug-apk` as an artifact (Actions tab → run → Artifacts). Unzip
and sideload the APK the same way.

### Local build (optional)

With JDK 17 and the Android SDK installed:
```sh
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

> The published APK is **debug-signed** — perfect for personal testing and free
> to distribute to yourself. To ship to the Play Store later you'd add a release
> keystore and signing config.

## Installing on your phone, step by step

1. On the phone, open the GitHub Release (or artifact) link and download the APK.
2. Tap the downloaded file. Android asks to allow installs from this source —
   enable it for your browser/Files app.
3. Tap **Install**, then **Open**.
4. (If "app not installed" appears because an older copy exists, uninstall the
   previous version first — debug builds use the `.debug` application id.)

## License

See [LICENSE](LICENSE).
