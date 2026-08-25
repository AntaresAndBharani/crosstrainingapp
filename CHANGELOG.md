# Changelog

All notable changes to the **CrossTraining** mobile application will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Fixed
- **CI Branch Ref Injection Hardening**: Passed `github.head_ref` via step-level `env: HEAD_REF: ${{ github.head_ref }}` in `.github/workflows/build.yml` and referenced `$HEAD_REF` within the `Compute build metadata` script to prevent branch name script injection (#152, #167).
- **Unit Test Summary Artifact Name Guard**: Added fallback guard in `scripts/summarize-unit-tests.ps1` defaulting `-ArtifactName` to `"unit test report"` when null, empty, or whitespace, preventing double-spaced generic truncation notes when `SHORT_SHA` is unset in CI workflows (#161).
- **Unit Test Summary Variable Hygiene & Static Analysis**: Renamed local regex match variables from `$matches` to `$backtickMatches` in `Get-BacktickFence` and `Format-FailureMessage` to resolve `PSAvoidAssignmentToAutomaticVariable`, added `PSAvoidUsingWriteHost` suppression attribute for CI console output, and cleaned up trailing whitespace in `scripts/summarize-unit-tests.ps1` (#160).
- **Unit Test Failure Message Capping & Path Sanitization**: Extracted shared `Limit-TextLines` helper to cap multi-line failure messages at 40 lines matching stack trace truncation in `scripts/summarize-unit-tests.ps1`, preventing GitHub PR comment size exhaustion on large assertion diffs, and eliminated redundant extraction-site `ConvertTo-RelativePath` call (#159).
- **Guest Mode Demo Data**: Clicking "Continue as Guest" now automatically populates and enables Demo Mode. This intentional product decision ensures new guest users have immediately populated History, Library, and Cycles screens to explore, rather than landing in a completely empty app state.
- **E2E Test Suites**: Addressed genuine UI regressions in E2E flows (03, 04, 05, 06) and fixed flakiness across the entire test suite. Flows now properly reflect updated data models and UI state.

### Added
- **Summarizer Static Hygiene Regression Guard for Throwaway Comments**: Added a static hygiene assertion to `scripts/tests/SummarizeUnitTests.Static.Tests.ps1` ensuring `scripts/summarize-unit-tests.ps1` contains no throwaway `# TEST:` comments (#178, #209, #218).
- **Unit Test Summary 40-Line Truncation Assertion**: Added an exact boundary regression assertion to `scripts/tests/SummarizeUnitTests.Markdown.Tests.ps1` verifying that failure message capping extracts exactly 40 lines before the truncation marker (#208, #221).
- **Unit Test Summary Status Line Regression Assertions**: Extended `scripts/tests/SummarizeUnitTests.Markdown.Tests.ps1` with status line assertions covering checkmark pass formatting, singular/plural failure wording, and relative positioning before failure detail sections (#208, #219).
- **Script Test Harness Line-Ending Helper**: Added `ConvertTo-LfLineEnding` function in `scripts/tests/TestHelpers.ps1` and refactored child-process output capture, assertion equality comparisons, and baseline-file reads to standardize CRLF-to-LF normalization (#181, #182, #183, #184, #208, #215).
- **Single-Sourced Unit Test Artifact Name in CI**: Exported `UNIT_TEST_ARTIFACT` once from `Compute build metadata` in `.github/workflows/build.yml` and consumed it across `Publish unit test summary` and `Upload unit test report` steps, eliminating duplicate literal string definitions (#150, #157, #168).
- **Release Workflow Signing Verification & Preflight Guards**: Added preflight secret validation and post-build signature verification using `apksigner` in `.github/workflows/release.yml` to assert release signing secrets are present and ensure tagged distribution artifacts are signed with the release certificate rather than the debug certificate before git tag creation or release publication (#68, #76).
- **Release Workflow Release-Signed APK Packaging**: Updated `.github/workflows/release.yml` to decode `RELEASE_KEYSTORE_BASE64` into a temporary runner keystore, execute `assembleRelease` with step-level signing environment variables, clean up decoded keystores upon completion, package `app-release.apk` as the tagged distribution artifact, and include a one-time upgrade notice in official GitHub release notes (#68, #75).
- **Release Signing Configuration & Fallback**: Configured `release` signing config in `app/build.gradle.kts` reading credentials from Gradle properties or environment variables (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) with automated fallback to the debug keystore when unconfigured, enabling local release builds while supporting production signing in CI (#68, #74).
- **Script Test Runner Failure Exit Code Self-Test**: Added optional `-TestDir` parameter to `scripts/tests/Invoke-ScriptTests.ps1` and created `scripts/tests/InvokeScriptTests.Selftest.Tests.ps1` proving non-zero exit code propagation on failing fixtures/exceptions and exit code 0 on passing suites (#220).
- **Multi-Platform CI Script Tests Matrix**: Expanded the `script-tests` job in `.github/workflows/build.yml` to a matrix across `[ubuntu-latest, windows-latest]`, ensuring PowerShell regression test suites run on both Linux and Windows on every pull request (#217).
- **PR Comment Helper Test Suite**: Added `scripts/tests/PrComment.Tests.ps1` with isolated Pester tests validating `Publish-PrComment` CLI guards, parameter validation, pagination, literal marker matching, update vs create branch selection, error resilience, and temporary file lifecycle cleanup (#163).
- **Shared PR Comment Publishing Helper**: Created `scripts/lib/PrComment.ps1` exposing dot-sourceable, exception-safe `Publish-PrComment` function with CLI presence guards, `--paginate` comment lookup, ordinal marker matching, BOM-free UTF-8 temp file creation under `GetTempPath()`, guaranteed `finally` cleanup, and non-fatal warning logging (#162).
- **PR Snapshot Build Concurrency & Cancellation**: Configured top-level concurrency group `build-${{ github.workflow }}-${{ github.head_ref || github.run_id }}` with `cancel-in-progress: true` on `.github/workflows/build.yml`, automatically cancelling superseded snapshot builds on rapid pushes to the same branch while safely updating the shared rolling pre-release in place (#137).
- **Unit Test Summary Status Line**: Added an at-a-glance `**Status:**` verdict line with Unicode pass/fail indicators (`0x2705` and `0x274C`) to `scripts/summarize-unit-tests.ps1` right after the summary table, providing instant status visibility before failure details (#80).
- **Unit Test Summary Artifact Name Parameter**: Added `-ArtifactName` parameter to `scripts/summarize-unit-tests.ps1` and wired it in `.github/workflows/build.yml` (`unit-test-report-pr-${{ env.SHORT_SHA }}`) so failure truncation notes reference the exact artifact holding the full test report (#84).
- **CI Script Regression Tests Workflow Job**: Added a standalone `script-tests` job to `.github/workflows/build.yml` running `scripts/test-summarize-unit-tests.ps1` under `pwsh` on `ubuntu-latest` on pull requests to validate test summarizer regression scenarios in parallel without Android SDK overhead.
- **Dependency-Free Script Test Harness**: Replaced the monolithic `scripts/test-summarize-unit-tests.ps1` runner with a structured harness under `scripts/tests/` — `Invoke-ScriptTests.ps1` (runner), `TestHelpers.ps1` (shared assertion helpers and child-process summarizer invoker), and three test modules: `SummarizeUnitTests.Markdown.Tests.ps1` (fixture output assertions including exact fail-baseline comparison and balanced-delimiter checking), `SummarizeUnitTests.Cli.Tests.ps1` (CLI parameter edge cases), and `SummarizeUnitTests.Static.Tests.ps1` (source hygiene assertions). Updated the `script-tests` CI job to invoke the new runner. Committed `scripts/testdata/unit-tests/expected/fail-summary.md` as a stable baseline; pinned it to LF in `.gitattributes` (#169, #170, #171).
- **Summarizer Test Harness Scenario Assertions**: Extended `scripts/test-summarize-unit-tests.ps1` with pass, fail, and runner path sanitization regression scenarios against committed JUnit XML fixtures, validating exit codes, table counts, elapsed time formatting, stack trace tab indentation, and absence of runner workspace path leaks.
- **CI Unit Test Summary Workflow Step**: Integrated `scripts/summarize-unit-tests.ps1` into `.github/workflows/build.yml` to publish JUnit test results to the GitHub Actions Job Summary and sticky PR comments across PR and manual workflow runs.
- **Sticky PR Comment for Unit Test Results**: Extended `scripts/summarize-unit-tests.ps1` to publish and update in-place a sticky PR comment with unit test summaries and failure stack traces using the `<!-- unit-test-evidence -->` marker.
- **Unit Test Summary Generator**: Added `scripts/summarize-unit-tests.ps1` to parse Gradle JUnit XML test results into markdown summaries with failure and stack trace details, along with committed test fixtures (`scripts/testdata/unit-tests/`).
- **Release Unit Test Report Artifacts**: Added step in `.github/workflows/release.yml` to preserve unit test HTML reports and JUnit XML results as a 14-day artifact (`unit-test-report-${{ env.RELEASE_TAG }}`) on merge to main, even on test failures.
- **Delta E2E Testing**: Added `-Delta` mode to `run-e2e-tests.ps1` powered by declarative `e2e/flow-mapping.json` rules and Maestro flow tags (`auth`, `logging`, `history`, `library`, `settings`, `theme`, `coach`, `core`). Enables sub-minute local feedback during active development while preserving the full 6-flow suite safety net for pre-PR verification.
- **Auto-capture E2E Failure Screenshots**: Enhanced the E2E test runner to automatically extract Maestro failure screenshots via `--debug-output`, archive them to release artifacts, and embed direct screenshot links in PR evidence summaries.
- **E2E Evidence on PRs**: Added automated scripts to generate per-flow pass/fail summaries and post E2E execution evidence directly as comments on pull requests.
- **Stable QA Releases**: Synced QA artifacts now use PR-specific tags on `virgymia-qa` GitHub Releases for stable test report URLs.
- **CI PR Unit Test Report Artifacts**: Upload Gradle unit test HTML reports and JUnit XML results in `.github/workflows/build.yml` with a 7-day retention period (`unit-test-report-pr-<short-sha>`) and `overwrite: true` for collision-free re-runs, using `if: ${{ !cancelled() }}` to retain diagnostics across non-cancelled test runs.

### Removed
- **Orphaned Script Test Runner**: Deleted superseded legacy script `scripts/test-summarize-unit-tests.ps1` in favor of modular test harness under `scripts/tests/` (#208, #221).

### Changed
- **Snapshot Pre-Release Publish Permissions & Token Explicitness**: Configured explicit `env: GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}` on the snapshot pre-release publish step in `.github/workflows/build.yml` and documented workflow permission requirements and fork-run token limitations (#199, #210, #213).
- **Release Workflow Packaging Documentation Polish**: Added an explicit comment above the release APK packaging step in `.github/workflows/release.yml` cross-referencing the earlier 'Ensure debug keystore' step and clarifying variant distinctions (#192, #210, #212).
- **ArtifactName Backtick Escaping in Truncation Note**: Hardened failure truncation note rendering in `scripts/summarize-unit-tests.ps1` to sanitize `-ArtifactName` using `Get-BacktickFence -MinLength 1` and leading/trailing whitespace padding, ensuring artifact names containing backticks render as valid inline code spans (#196, #209, #216).
- **Failure Message Backtick Fence Consolidation**: Refactored `Format-FailureMessage` in `scripts/summarize-unit-tests.ps1` to delegate multi-line and single-line fence length computation to `Get-BacktickFence` with a new `-MinLength` parameter, eliminating duplicated regex backtick scanning (#206, #209, #214).
- **Shared PR Comment Helper Adoption & Workspace Cleanup**: Refactored both `scripts/summarize-unit-tests.ps1` and `scripts/post-e2e-evidence.ps1` to dot-source `scripts/lib/PrComment.ps1` and delegate sticky PR comment publishing to `Publish-PrComment`. Hardened PR evidence comment publishing with isolated BOM-free UTF-8 temporary files under `[System.IO.Path]::GetTempPath()`, guaranteed cleanup in `finally` blocks, `--paginate` comment lookup, ordinal marker matching, `gh` CLI presence checks, and non-fatal warning logging on publishing failures. Retired workspace-relative `body.txt` generation and removed the obsolete `body.txt` entry from `.gitignore` (#129, #130, #131, #140, #141, #153, #154, #156, #164, #165, #166).
- **Unit Test Summary PowerShell Conventions & Refactoring**: Renamed `Sanitize-Paths` to approved-verb `ConvertTo-RelativePath`, replaced `[PSObject]` usages with idiomatic `[PSCustomObject]`, and verified PSScriptAnalyzer cleanliness.
- **E2E Evidence Script Hardening & Temp Isolation**: Hardened `scripts/post-e2e-evidence.ps1` with isolated temp comment body generation under the OS temp directory, non-fatal comment publishing using `Write-Warning` and `--silent`, numeric validation for `-PrNumber`, and explicit error exits without relying on `$ErrorActionPreference = "Stop"`.
- **Unit Test Summary Markdown & Backtick Safety**: Enhanced `scripts/summarize-unit-tests.ps1` with `Format-FailureMessage` and `Get-BacktickFence` helpers to safely format assertion messages as inline spans or fenced code blocks with dynamic backtick delimiter sizing, ensuring characters like `<`, `>`, `&`, `*`, `_` and embedded backtick runs render verbatim without breaking markdown or leaking into HTML sanitizers. Added committed test fixture `scripts/testdata/unit-tests/messy/`.
- **Unit Test Summary Temp Isolation & Gitignore**: Isolated PR comment temporary markdown generation in `scripts/summarize-unit-tests.ps1` under the OS temp directory with unique GUID-based paths and guaranteed deletion in a `finally` block, and added `unit-test-summary.md` and `body.txt` to `.gitignore` to prevent generated test evidence from polluting workspace status.
- **Unit Test Summary Script Hardening**: Hardened `scripts/summarize-unit-tests.ps1` with non-fatal PR comment publishing via `Write-Warning`, added regex validation for `-PrNumber`, suppressed multi-KB response dumps with `--silent` on `gh api` calls, and consolidated the `<!-- unit-test-evidence -->` marker variable.
- **CI Build Workflow Hardening**: Configured `overwrite: true` on `Upload unit test report` and `Upload APK artifact` steps in `.github/workflows/build.yml` to prevent 409 conflict errors when re-running failed jobs on the same commit SHA. Updated unit test step conditions to `if: ${{ !cancelled() }}` to skip running summary and artifact steps on cancelled runs.
- **CI Release Pipeline Hardening**: Added top-level concurrency group `release-${{ github.ref }}` with `cancel-in-progress: false` to `.github/workflows/release.yml` along with inline documentation on pending-run coalescing behavior to prevent tag collisions on rapid merges to `main`. Added fail-fast assertion step `Assert release tag resolved` to prevent publishing invalid empty-tag artifacts.
- **CI Release Tag Resolution**: Hoisted release tag resolution in `release.yml` before the unit test execution step to ensure prospective release tag metadata is always available to subsequent failure-handling and reporting steps.

---

## [3.0.8] - 2026-08-17

### Fixed
- **Clean Production Releases**: Prevented snapshot and dummy training cycles/routines (e.g. *Olympic Lifting & Strength Block*, *Fran*) from being seeded into production release builds on first install, ensuring clean production data separation while keeping the baseline exercise library intact.

---

## [3.0.7] - 2026-08-17

### Added
- **Remember Me Session Persistence**: Persistent authentication state across app restarts, updates, and device reboots via `SharedPreferences`.
- **Environment Isolation**: Firestore data isolated by environment (`environments/production` vs `environments/snapshot`).
- **User Role Cloud Mapping**: Automatic role resolution for coaches (`pv.joseangel@gmail.com`) and athletes (`jangelpv`).

---

## [3.0.6] - 2026-08-17

### Added
- **Coach Mode vs Athlete Mode**: Dynamic UI adaptation based on the active user role.
  - **Athlete Mode**: Focused daily logging, personal history, and progression tracking.
  - **Coach Mode**: Full cycle planning, exercise library management, routine creation, and athlete progression monitoring.
- **Dynamic Role Navigation**: Role-specific bottom navigation bars and quick role switcher toggles in the drawer and profile screens.

---

## [3.0.5] - 2026-08-16

### Added
- **Maestro UI E2E Automated Test Suite**: 5 local end-to-end user flows in `e2e/flows/` covering authentication, session logging, workout timer, library creation, and cycle goal tracking.
- **Automated Test Runner Script**: PowerShell runner (`scripts/run-e2e-tests.ps1`) for launching and reporting E2E test runs on emulators and physical devices.

---

## [3.0.4] - 2026-08-16

### Added
- **Compact Spreadsheet Set Tables**: Overhauled bulky set input boxes into clean, compact tabular layouts (`SET # | REPS | WEIGHT | FLAGS | ✕`), reducing vertical scrolling by ~70%.
- **Search & Category Filters**: Real-time search and filter bars in History and Library tabs.
- **Expandable Session Summary Cards**: Collapsible cards in History for quick session overviews.

---

## [3.0.3] - 2026-08-16

### Added
- **Vector Google Logo**: Crisp vector Google branding icon on the login screen (`ic_google_logo.xml`).

### Fixed
- **Duplicate Google Account Chooser**: Streamlined Google Sign-In by directly invoking `AccountManager.newChooseAccountIntent` for a single-pass login flow without redundant system dialogs.

---

## [3.0.2] - 2026-08-16

### Added
- **4 Theme Modes**:
  - Light (Default)
  - Dark
  - Light High Contrast
  - Dark High Contrast (Carbon & Volt Green)
- **Live Theme Switching**: Instant theme application and persistence in app settings and profile drawer.

---

## [3.0.1] - 2026-08-15

### Changed
- **CI Versioning**: Standardized semantic version tag generation (strict `vX.Y.Z` 3-part SemVer) across GitHub Actions release pipelines.

---

## [3.0.0] - 2026-08-14

### Added
- **Carbon & Volt Green High Contrast Design**: High-visibility neon volt green accents on deep carbon surfaces for gym environments.
- **App Launcher Icons**: Modernized monochrome and adaptive app icons.

---

## [2.5.0] - 2026-08-14

### Added
- **Modernized Mobile Navigation**: 3-tab bottom bar (`Log`, `History`, `Progress`) coupled with a slide-out navigation drawer for secondary tools (`Cycles`, `Library`, `Timer`, `Cloud Sync`).
- **Quick Workout Timer Floating Sheet**: Accessible floating timer overlay throughout the app.

---

## [2.4.2] - 2026-08-12

### Fixed
- **Cloud Sync Timeout & Re-auth**: Resolved Firestore sync timeouts with 20-second thresholds and automatic re-authentication guards.

---

## [2.4.1] - 2026-08-12

### Fixed
- **Google Sign-In Error 10**: Handled Google Play Services Developer Error 10 with graceful system account chooser fallback.

---

## [2.4.0] - 2026-08-12

### Added
- **Native Google System Account Chooser**: Replaced custom password dialogs with standard Android Google Account Picker.

---

## [2.3.0] - 2026-08-11

### Added
- **Google Account Authentication**: Support for signing in via Google account.
- **Scrollable Navigation**: Enhanced tab navigation for smaller screens.

---

## [2.2.0] - 2026-08-10

### Added
- **Routine Sequence RM Logging**: Automatic rep-max calculation and progression logging for complex routine blocks in the Session Logger.

---

## [2.1.0] - 2026-08-07

### Changed
- **Dynamic Baseline Progression**: Replaced static start weight fields in Cycle Goals with dynamically computed baselines derived from historical routine logs.

---

## [2.0.0] - 2026-08-06

### Added
- **Per-Block Routine Progress Analytics**: Dynamic evolution charts and KPI cards filtered by individual routine block movements.
- **Skipped Block Tracking**: Visual indicators and metrics for completed vs skipped blocks within daily routines.

---

## [1.9.0] - 2026-08-06

### Added
- **Metabolic & Interval Blocks**: Dedicated routine blocks for monostructural machines (Air Bike, Rower, SkiErg, Run) scored by calories, distance, or time intervals.

---

## [1.7.1] - 2026-08-05

### Fixed
- **Routine Deduplication**: Prevented duplicate routine insertions during local migrations and Firestore cloud synchronization.

---

## [1.7.0] - 2026-07-31

### Added
- **User Authentication & Cloud Backup**: Firebase Authentication (Email/Password, Anonymous) with cloud backup and restore.
- **Lost Routine Cloud Search**: Recovery tool to search and restore routines from cloud snapshots.

---

## [1.6.0] - 2026-07-31

### Added
- **Firebase SDK Integration**: Core cloud foundation for workout and routine sharing.
- **Curated Starter Routines**: Expanded starter library including Olympic lifting waves, benchmark metcons, and strength blocks.

---

## [1.5.1] - 2026-07-31

### Fixed
- **Room Migration Version 4**: Robust database migration preserving historical sessions and linking routine blocks to the Progress tab.

---

## [1.5.0] - 2026-07-31

### Added
- **Rep Schemes for Daily Routines**: Built-in support for Wave 3-2-1, Wave 2-2-1, and Fixed Rep structures with per-set weight tracking in the Log tab.

---

## [1.4.0] - 2026-07-30

### Added
- **Dedicated Workout Timer**: Built-in EMOM, AMRAP, Death By, Time Cap, and Tabata timers with high-volume audio beeps and haptic vibration feedback.

---

## [1.3.0] - 2026-07-30

### Added
- **Daily Multi-Block Routines**: Ability to compose single sessions with multiple custom blocks (warm-up, strength, accessory, WOD).

---

## [1.2.0] - 2026-06-28

### Added
- **Session Detail View, Edit & Duplicate**: Full session review with inline editing and one-tap duplication into the active log.

---

## [1.1.0] - 2026-06-28

### Added
- **Structured Session Blocks**: Ordered session blocks with warmup/failed-rep flags and per-block rep-max logging.

---

## [1.0.0] - 2026-06-28

### Added
- **Initial Release**: Offline CrossFit strength and cycle progression tracking.
- **Local Persistence**: Room SQLite database with zero cloud requirement.
- **Cycles & Goals**: Training cycle management with extendable start/end dates.
- **Exercise Library**: Tracked barbell lifts, gymnastics, and monostructural machines.
- **Progress Charts & CSV Backup**: Rep-max progression line charts and full CSV export/import backup.
