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

1. Merging changes into `main` automatically triggers the **Release on Merge to Main** workflow.
2. The workflow verifies production signing credentials, builds the release APK, verifies the signature, and publishes an official GitHub Release with `crosstraining-<tag>.apk` attached (e.g. `crosstraining-v1.0.0.apk`).
3. On your Android phone, open the Release page, download the `.apk`, and tap it.
   When prompted, allow *Install unknown apps* for your browser/Files app, then
   tap **Install**.

### Option B — download the snapshot APK from a PR workflow run

Every pull request runs the **PR Snapshot Build & Pre-Release** workflow, uploading a
`crosstraining-snapshot-<sha>` artifact to the workflow run and updating the rolling **Snapshot**
pre-release with `crosstraining-snapshot.apk`. Download the snapshot APK from the PR run artifacts
or the Snapshot pre-release page to test in-flight changes.

### Local build & testing

With JDK 17 and the Android SDK installed:
```sh
# Fast unit & lint suite
.\gradlew.bat testDebugUnitTest assembleSnapshot -PsnapshotLabel=localtest --no-daemon

# Run local End-to-End (E2E) UI flows on Emulator or Device with Maestro:
.\scripts\run-e2e-tests.ps1
```

> **Release Signing:** Published official GitHub releases are **release-signed**. The `Release on Merge to Main` workflow enforces preflight checks for release keystore secrets and runs `apksigner` verification to ensure the published release artifact is signed with the production release keystore rather than debug credentials.

## Installing on your phone, step by step

1. On the phone, open the GitHub Release (or snapshot pre-release / artifact) link and download the APK.
2. Tap the downloaded file. Android asks to allow installs from this source —
   enable it for your browser/Files app.
3. Tap **Install**, then **Open**.
4. (If "app not installed" appears because an older copy exists with a different signing key or package variant, uninstall the previous version first.)

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for detailed release notes, version history, and migration details.

## License

See [LICENSE](LICENSE).
