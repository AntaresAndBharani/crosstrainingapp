# Changelog

All notable changes to the **CrossTraining** mobile application will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- **Voice Ingestion Integration with Atomic Session Persistence in Repository**: Implemented end-to-end voice ingestion orchestration in `Repository` (`createSessionFromVoiceInput`, `parseVoiceInput`, and `appendBlockSetFromVoice`) coordinating `VoiceInputController`, `AiCoreManager`, `ExerciseEntityGrounder`, and `FitnessSpeechLexicon` with Room SQLite atomic transaction execution (`TransactionRunner` / `withDatabaseTransaction`) ensuring all `Session`, `SessionBlock`, and `BlockSet` entities commit atomically or rollback without orphan records on failure; added automatic exercise disambiguation resolution when phonetic/natural language queries match multiple catalog exercises; added live set logging (`appendBlockSetFromVoice`) for active workouts with automatic block set appending, weight extraction, and RPE note recording; updated `BlockDao` with `insertSet` and single-shot block/set retrieval queries; integrated reactive StateFlow state machine (`VoiceIngestionState`, `voiceWorkoutUiState`) in `AppViewModel` managing the complete lifecycle (`Idle`, `Listening`, `Parsing`, `Disambiguating`, `Saving`, `Complete`, `Error`), backed by comprehensive unit and database integration test suites in `VoiceRepositoryIntegrationTest` and `AppViewModelVoiceIngestionTest` (#473).
- **VoiceWorkoutIngestionSheet with Waveform Visualizer & Movement Disambiguation**: Implemented stateless Compose UI `VoiceWorkoutIngestionSheet` (and `VoiceWorkoutIngestionModalSheet`) under `com.fractanomics.crosstraining.ui.voice` providing real-time voice-driven workout structuring and ingestion, featuring an animated multi-bar microphone waveform visualizer responsive to voice volume (`rmsDb`), live speech transcript display with automatic scroll-to-bottom on new text, parsed workout cards with inline spreadsheet-style editing for sets, reps, weights (with decimal support), and warmup flags, clickable disambiguation chips for ambiguous exercise names, color-coded visual feedback for listening (green), processing (amber), error (red), and idle states, confirm & save and cancel intent dispatching, and support for both modal bottom sheet and full-screen layouts, backed by a comprehensive unit test suite in `VoiceWorkoutIngestionSheetTest` (#472).
- **VoiceInputController with SpeechRecognizer & Acoustic Noise Suppression**: Implemented `VoiceInputController` under `com.fractanomics.crosstraining.data.voice` wrapping Android `SpeechRecognizer` with acoustic noise suppression (`EXTRA_PREFER_OFFLINE`, noise suppression, dictation mode, and acoustic echo cancellation intent flags) and lifecycle-safe coroutine StateFlow streams (`isListening`, `transcript`, `confidence`, `rmsDb`, `error`, `state`), providing start/stop/cancel/reset/destroy operations, real-time partial transcript streaming, normalized confidence scores (0.0-1.0), robust error mapping (`VoiceInputError`), runtime microphone permission verification, and deterministic mockable interface abstractions (`SpeechRecognizerClient`, `RecognitionResultExtractor`, `MicrophonePermissionChecker`, `SpeechRecognitionAvailabilityChecker`), backed by a comprehensive unit test suite in `VoiceInputControllerTest` (#471).
- **AiCoreManager for On-Device Gemini Nano Structured JSON Extraction**: Implemented `AiCoreManager` under `com.fractanomics.crosstraining.data.ai` orchestrating on-device Gemini Nano AI inference for structured JSON workout extraction from natural language speech transcripts, featuring prompt engineering strictly commanding JSON schemas (`BlockKind`, `format`, `repScheme`, `sets`, `weights`, `movements`, `rpe`), resilient JSON parser recovering from malformed syntax (markdown code fences, trailing commas, single quotes, unquoted keys, string numbers, truncated braces, and regex emergency recovery), graceful fallback handling when Gemini Nano is unavailable or uninitialized, and deterministic mockable interface abstraction (`GeminiNanoClient`) achieving < 2000ms response performance, backed by a comprehensive unit test suite in `AiCoreManagerTest` covering 12+ diverse workout categories (complexes, EMOM, AMRAP, strength waves, benchmarks, intervals, supersets, warmups) and malformed JSON recovery (#470).
- **ExerciseEntityGrounder for Voice-to-Entity Mapping & Movement Disambiguation**: Implemented `ExerciseEntityGrounder` under `com.fractanomics.crosstraining.data.ai` providing voice-to-entity movement grounding with exact matching, standard fitness alias/acronym mappings (`C&J`, `OHS`, `T2B`, `HSPU`, `DU`, `BJO`, `KBS`, `WB`), root movement disambiguation ranking (e.g., "cleans" -> Clean, Power Clean, Clean & Jerk), token-level overlap, noise and metadata stripping (reps, sets, loads, time domains), and Levenshtein fuzzy distance tolerance (<= 2) for acoustic errors and typos, returning normalized confidence scores (0.0 to 1.0) and auto-selecting singular exact matches within sub-5ms resolution time, backed by comprehensive unit and `SeedData` sample database integration test suites in `ExerciseEntityGrounderTest` (#469).
- **FitnessSpeechLexicon Phonetic Correction & STT Artifact Mapping**: Implemented pure Kotlin on-device speech lexicon module `FitnessSpeechLexicon` under `com.fractanomics.crosstraining.data.ai` with zero platform dependencies, providing immutable dictionary mappings for fitness acronyms and acoustic misrecognitions (EMOM, AMRAP, RPE, WOD, METCON, Olympic/gymnastic acronyms, barbell complexes, units, and common artifacts like "wreps"/"squad"/"K"), standalone and minute-prefixed EMOM/AMRAP structure normalizers, EXMOM interval mapping, RPE score parsing, custom lexicon override capabilities, Levenshtein-distance-based phonetic candidate ranking (`rankCandidates`/`bestMatch`) against an offline ~500-term fitness vocabulary (`CANONICAL_FITNESS_TERMS`), backed by comprehensive JUnit test suite in `FitnessSpeechLexiconTest` (#468).
- **Password Reset Automated Unit & Compose UI Test Suite**: Implemented comprehensive unit test suite in `AppViewModelPasswordResetTest.kt` verifying email sanitization (`trim().lowercase()`), domain shorthand resolution, exception-to-UI message mapping via `PasswordResetErrorMapper`, and success snackbar dispatch, and added Compose UI instrumentation test suite in `ResetPasswordDialogComposeTest.kt` validating dialog UI state transitions, button enabled/disabled states, IME Done action submission, inline error message rendering, loading progress lock, and initial email pre-filling (#458).
- **Cross-Auth Sign-In Compatibility and Profile Integrity Verification**: Verified cross-auth sign-in binding allowing users originally registered via Google Sign-In to authenticate with email/password once set without creating duplicate user profile documents or overwriting training history in Firestore, wired rep maxes upload and sessions & PR history download with deduplication in `UserCloudSyncManager`, and added comprehensive unit and integration tests in `CrossAuthSignInTest` covering UID binding, role preservation, and duplicate prevention (#457).
- **Password Reset Email Sanitization, Error Mapping, and Snackbar Feedback**: Connected `ResetPasswordDialog` to `AppViewModel.sendPasswordReset` and `UserCloudSyncManager.sendPasswordReset` with input sanitization (`email.trim().lowercase()`), added `PasswordResetErrorMapper` to translate Firebase and network exceptions into user-friendly inline messages ("Network error. Please check your connection.", "Too many attempts. Try again later."), and wired dialog dismissal, keyboard hiding, and confirmation Snackbar ("If an account exists, a setup link has been sent to your inbox.") on successful dispatch, backed by comprehensive unit tests in `PasswordResetDispatchTest` (#456).
- **Modular ResetPasswordDialog with Email Validation and Loading Lock**: Extracted standalone stateless `ResetPasswordDialog` composable with `ResetPasswordValidator` regex email validation (`^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$`), autofocus `FocusRequester`, IME Done submission, loading dismissal lock, and updated "Forgot or Set Password?" action link in `LoginWelcomeScreen`, backed by comprehensive unit tests in `ResetPasswordDialogTest` (#455).
- **AppNumericTextField Automated Unit & Compose UI Test Suite**: Added comprehensive unit test suite in `AppNumericTextFieldTest.kt` and Compose UI instrumentation test suite in `AppNumericTextFieldComposeTest.kt` verifying no digit concatenation on replace, decimal parsing accuracy, and clamp on commit on focus loss or IME Done (#430).
- **CrossTrainingApp CLI & Helper Test Suite Integration**: Added `scripts/tests/CrossTrainingAppCli.Tests.ps1` with comprehensive test coverage for AVD discovery and form-factor filtering (`Pixel|Phone` regex), SHA-based artifact caching, 3-stage boot sequencing lock verification, signature conflict auto-healing (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), `--clear-data` execution, and CLI command routing, fully integrated into `Invoke-ScriptTests.ps1` (#441, #445).
- **CrossTrainingApp CLI Usage Documentation & Agent Testing Rules**: Added comprehensive documentation in `docs/local-testing.md` and updated `.agents/rules/local_test_apk.md` mandating `./scripts/crosstrainingapp.ps1 emulator --latest-main` for agent-driven APK verification, documenting supported flags (`--latest-main`, `--local`, `--headless`, `--clear-data`), SHA-based artifact caching behavior (`app/build/outputs/apk/ci-main/`), AVD discovery with `Pixel|Phone` regex filtering, 3-stage boot synchronization lock, signature conflict auto-healing (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), ANSI status badges, and logcat diagnostic commands, backed by static regression test assertions in `scripts/tests/LocalTestingDocumentation.Static.Tests.ps1` (#441, #446).
- **AdbEmulatorHelper Module with 3-Stage Boot Lock & Signature Auto-Healing**: Implemented `scripts/lib/AdbEmulatorHelper.ps1` and comprehensive test suite in `scripts/tests/AdbEmulatorHelper.Tests.ps1` supporting phone-first AVD discovery (`Pixel|Phone` filtering and `sdkmanager`/`avdmanager` actionable errors), bulletproof 3-stage boot sequencing (`sys.boot_completed == 1`, `init.svc.bootanim == stopped`, `adb shell pm path android` check), detached emulator launching, and signature-conflict auto-healing (`INSTALL_FAILED_UPDATE_INCOMPATIBLE` uninstall/reinstall flow and `--clear-data` support) (#443).
- **CrossTraining App CLI Router and Emulator Provisioning**: Implemented `scripts/crosstrainingapp.ps1` router with `emulator` subcommand, supporting `--latest-main` SHA-cached CI build retrieval with graceful local fallback, `--local` direct Gradle compilation mode (`:app:assembleDebug`), `--headless` mode, and `--clear-data` flags, wired with `scripts/lib/AdbEmulatorHelper.ps1` for 3-stage boot sequencing and signature conflict auto-healing, `scripts/lib/GitHubArtifactHelper.ps1` for artifact caching, and unit/integration test coverage in `scripts/tests/CrossTrainingAppCli.Tests.ps1` (#444).
- **Notification and Foreground-Service Permissions with Android 13+ Runtime Gating**: Configured `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` in `AndroidManifest.xml`, implemented `NotificationPermissionHelper` for Android 13+ (API 33+) runtime permission checks and request gating, wired `rememberLauncherForActivityResult` in `TimerScreen.kt`, and ensured timer runs smoothly in-app even if permission is denied (#417).
- **GitHubArtifactHelper SHA-Cached Main-Branch APK Retrieval**: Implemented `Get-LatestMainBuildApk` and `Get-LatestMainCommitSha` in `scripts/lib/GitHubArtifactHelper.ps1` to resolve main branch HEAD SHA, check local artifact cache (`app/build/outputs/apk/ci-main`), and download/cache build artifacts with fallback from GitHub Actions CI runs to GitHub Releases, accompanied by comprehensive Pester test coverage in `scripts/tests/GitHubArtifactHelper.Tests.ps1` (#442).
- **Workout Timer Notification Action Buttons**: Wired interactive Play/Pause (`ACTION_START`/`ACTION_PAUSE`) and Next (`ACTION_NEXT`) notification action buttons to `TimerEngine` via `TimerNotificationActionDispatcher` and `TimerNotificationSpec`, with MediaStyle compact view support, full round advancement across all timer modes (EMOM, Tabata, Death By, AMRAP/Time Cap), and comprehensive unit and integration test coverage (#420).
- **AppNumericTextField Component with Select-All and Deferred Commit**: Implemented `AppNumericTextField` in `CommonUi.kt` featuring select-all on focus (`TextRange(0, text.length)`), immediate single-digit replacement without digit concatenation bugs, intermediate blank states while focused, deferred commit and clamping to `minValue`..`maxValue` on focus loss or IME Done/Next, non-numeric character filtering, integer and decimal leading zero sanitization, trailing decimal point normalization on commit, and decimal weight input support (#429).
- **TimerService Foreground Service with MediaStyle Notification**: Implemented `TimerService` foreground service with `NotificationCompat.MediaStyle` backed by `MediaSessionCompat`, dynamic phase and round formatting via `TimerNotificationFormatter`, real-time countdown updates, and `setOnlyAlertOnce(true)` to prevent re-alerting during active workout timer execution (#419).
- **App-Scoped Shared Timer StateFlow**: Promoted `TimerEngine` to application scope via thread-safe `TimerEngineProvider` exposing shared `StateFlow<TimerSnapshot>`, ensuring seamless timer state continuity across Compose recomposition, navigation, backgrounding, and multi-component observation between `TimerScreen` and `TimerService` (#418).
- **Notification Tap Navigation into Active TimerScreen**: Implemented `MainActivity` launch and resume intent handling with `singleTop` launch mode, `NavigationIntentHandler`, and `AppNavigation` deep-link routing to navigate directly into `TimerScreen` with preserved active timer state when tapping the `TimerService` foreground notification (#421).
- **Workout Timer Foreground Service Graceful Teardown**: Implemented `TimerTeardownController` and `TimerService` lifecycle management to terminate the foreground service, dismiss notifications, and release `MediaSessionCompat` resources cleanly when the workout timer is stopped (`ACTION_STOP`), reset (`TimerEngine.reset()`), or finished (`TimerPhase.FINISHED`) (#422).
- **Architect Decompose Subtask Output Schema Verification**: Added comprehensive unit, integration, and static regression test suite in `scripts/tests/ArchitectWorkflow.Schema.Tests.ps1` verifying schema compliance for Claude agent output (`PROCEED` and `PO_ESCALATION` outcomes, required subtask fields, enums, and live parent story #407 sub-issue structure) (#409).

### Removed
- **Post E2E Evidence Redundant Inline TargetRepo Resets**: Removed redundant inline `$script:TargetRepo = $null` resets from individual `It` blocks in the `Repo fallback behavior` context within `scripts/tests/PostE2EEvidence.Tests.ps1`, relying solely on the existing `BeforeEach` hook (#381, #387, #394).
- **PR Snapshot Build & Rolling Pre-Release**: Removed snapshot APK generation (`assembleSnapshot`), APK artifact uploads, and rolling snapshot pre-release publishing from `.github/workflows/build.yml` in favor of focused PR unit and script test verification, dropping `contents: write` workflow permissions.

### Changed
- **QuickAddWorkoutDialog Numeric Input Refactor to AppNumericTextField**: Refactored `setsScheme` and `weightInput` numeric input fields in `QuickAddWorkoutDialog.kt` to use the reusable `AppNumericTextField` component with select-all on focus, intermediate blank state handling, leading-zero sanitization, decimal support, and domain clamping (`[1.0, 999.0]` for schemes, `[0.0, 999.9]` for weights), accompanied by unit test suite in `QuickAddWorkoutDialogNumericMigrationTest` and `AppNumericTextFieldTest` (#433).
- **TimerScreen Numeric Field Refactoring**: Refactored `TimerScreen.kt` numeric configuration inputs (rounds, interval seconds, work/rest seconds, and target minutes) to use `AppNumericTextField`, enforcing domain-specific minValue/maxValue bounds (intervals/work/rest up to 3600s, target minutes up to 1440m, rounds up to 999) and resilient single-digit replacement behavior without regression (#431).
- **SessionEditor Numeric and Decimal Field Refactoring**: Refactored `SessionEditor.kt` sets, reps, and weight inputs (including spreadsheet set table rows and PR/rep-max fields) to use `AppNumericTextField`, enforcing integer sanitization (`allowDecimals = false`) for sets/reps and decimal support (`allowDecimals = true`, `KeyboardType.Decimal`) for weights without regression (#432).
- **PSScriptAnalyzer Compliance in PrComment**: Verified clean PSScriptAnalyzer compliance on `scripts/lib/PrComment.ps1` with 0 Warning or Error findings and added `[OutputType([bool])]` attribute declaration to `Publish-PrComment` (#385, #386, #391).
- **PR Comment Collection Performance Optimization**: Replaced O(n^2) array accumulation (`$comments += $item`) with `[System.Collections.Generic.List[object]]` and `.Add()` in `Publish-PrComment` (`scripts/lib/PrComment.ps1`), eliminating intermediate array reallocations during PR comment pagination flattening (#383, #386, #390).
- **Post E2E Evidence Sticky Comment Negative POST Endpoint Assertion**: Tightened the negative `POST` `Should -Invoke` ParameterFilter in `scripts/tests/PostE2EEvidence.Tests.ps1` to match against the specific comment-creation endpoint path (`issues/123/comments`) alongside the `POST` HTTP method, mirroring the sibling `PATCH` assertion filter pattern (#379, #387, #393).
- **Cross-Platform Environment Variable Restoration Assertion**: Used `[string]::IsNullOrEmpty` in `scripts/tests/SummarizeUnitTests.Cli.Tests.ps1` for environment variable restoration verification across Windows and POSIX runners where .NET CoreCLR returns empty string or null for unset environment variables (#371, #393).
- **Post E2E Evidence Static Hygiene AST Lexical Scoping**: Scoped variable-assignment matching in `Find-RawGhApiCommentCalls` within `scripts/tests/PostE2EEvidence.Tests.ps1` to enclosing `ScriptBlockAst` and function boundaries rather than whole-file AST offsets, preventing false positives/negatives across sibling functions, and added AST sibling scoping regression test coverage (#377, #387, #392).
- **Summarizer CLI Test Strict Null Assertions**: Reverted loose `[string]::IsNullOrEmpty` workaround assertions to strict `Assert-Equal -Expected $null` checks in `scripts/tests/SummarizeUnitTests.Cli.Tests.ps1` for environment variable restoration verification (#371, #389, #396).
- **Post E2E Evidence Repo Fallback Test Scope Harmonization**: Aligned `$script:TargetRepo` scoping and `BeforeEach`/`AfterEach` variable resets in `scripts/tests/PostE2EEvidence.Tests.ps1` with the reference pattern in `scripts/tests/PrComment.Tests.ps1`, preventing state leakage across sequential test executions (#360, #365).
- **Post E2E Evidence Sticky Comment PATCH Endpoint Assertion**: Tightened the `Should -Invoke` ParameterFilter in `scripts/tests/PostE2EEvidence.Tests.ps1` to assert both the PATCH method and the exact comment endpoint path (`issues/comments/456`), preventing false passes on incorrect comment targets (#360, #364).
- **Release Workflow Regex Flag Simplification**: Removed redundant `(?ms)` inline options from all six `-match` error annotation assertions in `scripts/tests/ReleaseWorkflow.Static.Tests.ps1`, relying solely on default .NET regex semantics and newline whitespace patterns (#361, #367).
- **Branch Protection Script-Tests Matrix Required Checks**: Configured repository branch protection rules on `main` to require both `script-tests (ubuntu-latest)` and `script-tests (windows-latest)` matrix status checks (replacing the deprecated singular `script-tests` check), with empirical verification and documentation captured in `docs/local-testing.md` (#250, #254, #329, #359, #362, #368, #369).

### Fixed
- **Test Helper Null Preservation in Line Ending Normalization**: Updated `ConvertTo-LfLineEnding` and `Assert-Equal` in `scripts/tests/TestHelpers.ps1` to allow and preserve `$null` input without coercing to empty string, fixing false test failures on strict null equality assertions under PowerShell Core (#371, #393).
- **PR Comment Safe Author Fallback and Cross-Author Matching Prevention**: Fixed `Publish-PrComment` in `scripts/lib/PrComment.ps1` to safely handle current-user resolution failures (`gh api user` exception, non-zero exit code, or blank/whitespace return) by skipping author matching, emitting a `Write-Warning`, and creating a new comment instead of matching and patching comments from other authors, and added comprehensive Pester test coverage in `scripts/tests/PrComment.Tests.ps1` (#207, #327).
- **PR Comment Multi-Page Slurp and Pagination Handling**: Configured `Publish-PrComment` in `scripts/lib/PrComment.ps1` to query PR comments with `--paginate --slurp` and flatten multi-page JSON arrays, ensuring marker comments on subsequent pages (>30 comments) are discovered and updated rather than failing JSON parsing, and added multi-page regression test coverage in `scripts/tests/PrComment.Tests.ps1` (#207, #326).
- **Release Keystore Decode Error Annotation Bounding**: Truncated captured `$DECODE_STDERR` to at most 200 characters (`${DECODE_STDERR:0:200}`) before interpolating into the `::error::` annotation in `.github/workflows/release.yml`, preventing oversized or garbled GitHub Actions annotations on malformed secret decode failures, and updated static regression assertions in `scripts/tests/ReleaseWorkflow.Static.Tests.ps1` (#361, #366).
- **Release Workflow ANDROID_HOME and build-tools Precondition Diagnostics**: Split compound precondition guard into distinct sequential checks for unset `ANDROID_HOME` and missing `$ANDROID_HOME/build-tools` directory in `.github/workflows/release.yml`, each with dedicated `::error::` annotations followed by `exit 1`, and updated static regression assertions in `scripts/tests/ReleaseWorkflow.Static.Tests.ps1` (#311, #318, #325).
- **Release Keystore Decode Error Diagnostic Detail**: Captured `base64 -d` stderr into a variable in the `Decode release keystore` step of `.github/workflows/release.yml` and surfaced it within the `::error::` annotation on decode failure, and added static regression assertions in `scripts/tests/ReleaseWorkflow.Static.Tests.ps1` (#309, #318, #324).
- **Release Workflow Error Annotation Trailing Exit 1 Assertions**: Extended static regex assertions in `scripts/tests/ReleaseWorkflow.Static.Tests.ps1` to require each hardened guard's `::error::` annotation in `.github/workflows/release.yml` to be immediately followed by `exit 1` (#313, #319, #334).
- **Branch Name Sanitization Regex Bracket Expression**: Removed redundant backslash escape before forward slash in the `BRANCH_NAME` sanitization `sed` bracket expression within `.github/workflows/build.yml` to strictly match POSIX regex allowlist intent (`[^a-zA-Z0-9._/-]`) (#302, #320, #335).
- **Unconditional Environment Variable Restoration in Test Helper**: Modified `Invoke-SummarizerScript` in `scripts/tests/TestHelpers.ps1` to unconditionally restore environment variables in its `finally` block, ensuring originally-unset variables injected via `-Environment` are cleared ($null) and do not leak across sequential test executions (#316, #321, #336).
- **README CI Workflow References and Release Signing Documentation**: Updated README.md workflow references to accurately describe `Release on Merge to Main` on merge to `main`, `PR Snapshot Build & Pre-Release` on pull requests with `crosstraining-snapshot-<sha>` artifacts, and production release signing verification (#271, #292, #300).
- **PR Comment Caller Repo Normalization Consolidation**: Removed redundant caller-side `-Repo` fallback guards and parameter defaults in `scripts/post-e2e-evidence.ps1` and `scripts/summarize-unit-tests.ps1`, consolidating normalization logic into `Publish-PrComment` as the single source of truth (#280, #291, #296).
- **apksigner Path Resolution and Directory Existence Guards**: Added precondition guards for `$ANDROID_HOME`, `$ANDROID_HOME/build-tools` directory existence, and build-tools version discovery before resolving `apksigner` in `.github/workflows/release.yml`, emitting explicit `::error::` annotations instead of failing on unguarded shell globs (#261, #269, #273).
- **Release Keystore Decode Error Annotation Guard**: Added explicit base64 decoding check and non-empty file validation with `::error::` annotations to the `Decode release keystore` step in `.github/workflows/release.yml`, preventing silent or opaque failures prior to release APK compilation (#259, #269, #272).
- **Snapshot Release Body Branch Name Sanitization & Security Boundary**: Sanitized `BRANCH_NAME` in the `Compute build metadata` step of `.github/workflows/build.yml` to an allowlist of characters (`[a-zA-Z0-9._/-]`), preventing Markdown formatting breakout or link spoofing in the public snapshot pre-release body, and added inline comments documenting parameter injection safety and the Markdown sanitization boundary (#244, #245, #247).
- **CI Branch Ref Injection Hardening**: Passed `github.head_ref` via step-level `env: HEAD_REF: ${{ github.head_ref }}` in `.github/workflows/build.yml` and referenced `$HEAD_REF` within the `Compute build metadata` script to prevent branch name script injection (#152, #167).
- **Unit Test Summary Artifact Name Guard**: Added fallback guard in `scripts/summarize-unit-tests.ps1` defaulting `-ArtifactName` to `"unit test report"` when null, empty, or whitespace, preventing double-spaced generic truncation notes when `SHORT_SHA` is unset in CI workflows (#161).
- **Unit Test Summary Variable Hygiene & Static Analysis**: Renamed local regex match variables from `$matches` to `$backtickMatches` in `Get-BacktickFence` and `Format-FailureMessage` to resolve `PSAvoidAssignmentToAutomaticVariable`, added `PSAvoidUsingWriteHost` suppression attribute for CI console output, and cleaned up trailing whitespace in `scripts/summarize-unit-tests.ps1` (#160).
- **Unit Test Failure Message Capping & Path Sanitization**: Extracted shared `Limit-TextLines` helper to cap multi-line failure messages at 40 lines matching stack trace truncation in `scripts/summarize-unit-tests.ps1`, preventing GitHub PR comment size exhaustion on large assertion diffs, and eliminated redundant extraction-site `ConvertTo-RelativePath` call (#159).
- **Guest Mode Demo Data**: Clicking "Continue as Guest" now automatically populates and enables Demo Mode. This intentional product decision ensures new guest users have immediately populated History, Library, and Cycles screens to explore, rather than landing in a completely empty app state.
- **E2E Test Suites**: Addressed genuine UI regressions in E2E flows (03, 04, 05, 06) and fixed flakiness across the entire test suite. Flows now properly reflect updated data models and UI state.

### Added
- **Architect Workflow & Claude Sonnet 5 Medium Thinking Decomposition Verification**: Added comprehensive static regression and unit logic test suite in `scripts/tests/ArchitectWorkflow.Static.Tests.ps1` verifying end-to-end user story decomposition, mode determination (`decompose`, `restructure`, `answer_clarifications`), model selection routing (`origin:backlog-triage` to `claude-sonnet-5` with medium thinking effort vs `claude-opus-5` default), prompt template selection, Claude agent headless execution arguments, and GitHub API sub-issues linking (#407, #408, #409, #410, #411).
- **Architect Workflow Sub-Issues Linking Test Suite**: Added comprehensive static contract, unit formatting, behavioral mock, and live API regression test coverage in `scripts/tests/ArchitectWorkflow.SubIssues.Tests.ps1` verifying GitHub Sub-issues API linking (`repos/$REPO/issues/$ISSUE/sub_issues`), integer database ID resolution, `type:subtask` label application, and checklist progress integration (#407, #410).
- **Post E2E Evidence AST Hygiene Indirect Invocation Detection**: Extended `Find-RawGhApiCommentCalls` in `scripts/tests/PostE2EEvidence.Tests.ps1` to detect indirect `gh` invocations via the call operator `&` with variable command names using same-scope constant propagation on preceding assignments, and added indirect invocation variants to test coverage (#360, #363).
- **Script-Tests Matrix Check Names Documentation**: Added Section 6 in `docs/local-testing.md` documenting canonical GitHub Actions status-check context names (`script-tests (ubuntu-latest)` and `script-tests (windows-latest)`) emitted by `.github/workflows/build.yml` for configuring branch protection rules on `main` (#250, #254, #328).
- **Post E2E Evidence Static Hygiene AST-Based Detection**: Replaced source-text regex static hygiene checks in `scripts/tests/PostE2EEvidence.Tests.ps1` with PowerShell AST parsing via `Parser::ParseFile` and `Parser::ParseInput` to detect raw `gh api` comment invocations across variable formatting and interpolation variants (#285, #290, #333).
- **Post E2E Evidence Direct Publish-PrComment Mock Assertion**: Added direct unit mock test in `scripts/tests/PostE2EEvidence.Tests.ps1` mocking `Publish-PrComment` directly and asserting single invocation with `-Marker '<!-- e2e-evidence -->'` and non-empty `-Body` (#284, #290, #332).
- **Post E2E Evidence Guard-Clause Non-Posting Assertions**: Added explicit `Should -Invoke` non-invocation assertions and throwing `gh` mocks across all guard clause tests in `scripts/tests/PostE2EEvidence.Tests.ps1`, proving comment-posting APIs are never called on early exits (#283, #290, #331).
- **Post E2E Evidence Sticky Comment PATCH Invocations Test Coverage**: Added explicit Pester v5 `Should -Invoke` assertions with parameter filtering to `scripts/tests/PostE2EEvidence.Tests.ps1` verifying that sticky comment updates invoke `gh api` PATCH exactly once on the existing comment ID and execute zero POST invocations against comment creation (#282, #290, #330).
- **Invoke-SummarizerScript Environment Variable Restoration Regression Test**: Added regression test coverage in `scripts/tests/SummarizeUnitTests.Cli.Tests.ps1` verifying that originally-unset and pre-set environment variables passed in `-Environment` are restored to `$null` and their initial values respectively after `Invoke-SummarizerScript` returns (#321, #337).
- **Release Workflow Canonical Step Names Documentation**: Added canonical step name reference table in `docs/local-testing.md` documenting `Build distributable APK (release variant)` and `Package release-signed APK as release distribution artifact` in `.github/workflows/release.yml` to prevent backlog drift (#271, #292, #299).
- **PR Comment Repo Fallback Test Coverage**: Added comprehensive test coverage across `scripts/tests/PrComment.Tests.ps1`, `scripts/tests/PostE2EEvidence.Tests.ps1`, and `scripts/tests/SummarizeUnitTests.Cli.Tests.ps1` asserting repository parameter fallback to `$env:GITHUB_REPOSITORY` and default repository across omitted, blank, and explicit arguments (#291, #297).
- **PR Comment Mock Helper Path Tests**: Added direct Pester test coverage in `scripts/tests/PrComment.Tests.ps1` verifying `New-GhApiMock` exit-code propagation and exception throwing behavior (#289, #295).
- **Release Workflow Static Regression Test Suite**: Added dedicated static regression tests in `scripts/tests/ReleaseWorkflow.Static.Tests.ps1` verifying presence of keystore-decode failure checks, `$ANDROID_HOME/build-tools` existence guards, build-tools glob-match discovery, and Android Debug certificate rejection annotations in `.github/workflows/release.yml` (#269, #274).
- **Self-Test Scenario Consolidation & Process Spawn Optimization**: Consolidated self-test failure scenarios in `scripts/tests/InvokeScriptTests.Selftest.Tests.ps1` into a single multi-fixture child process exercising passing assertions, failing assertions, and unhandled exceptions together, reducing child-process spawn overhead by 50% across multi-platform CI matrix runs (#255, #257).
- **Script Test Runner Self-Test Scenario Helper**: Extracted `Invoke-SelftestScenario` helper function in `scripts/tests/InvokeScriptTests.Selftest.Tests.ps1` managing temporary directory lifecycle, multi-fixture file generation, process execution, and assertion validation, eliminating duplicated try-finally boilerplate across test scenarios (#255, #256).
- **PR Comment Test Mock Setup Helper**: Added shared `Set-GhAvailable` and `New-GhApiMock` mock helpers to `scripts/tests/PrComment.Tests.ps1` and refactored all context blocks to eliminate duplicated inline mock dispatch logic (#229, #233, #238, #239).
- **Post E2E Evidence Automated Pester Test Suite**: Added dedicated Pester test suite in `scripts/tests/PostE2EEvidence.Tests.ps1` covering PR number validation, release verification guards, flow parsing, markdown formatting, static hygiene, and delegation to `Publish-PrComment` (#232, #237).
- **ArtifactName Fallback-Equivalence Regression Test**: Added test coverage in `scripts/tests/SummarizeUnitTests.Cli.Tests.ps1` asserting that explicitly passing `-ArtifactName "unit test report"` renders a backticked code span rather than unquoted generic fallback text (#225, #231, #235).
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
- **Script Test Runner Self-Test Docstring Update**: Updated the header comment block in `scripts/tests/InvokeScriptTests.Selftest.Tests.ps1` to accurately describe the consolidated 2-scenario layout (Scenario 1: single passing fixture exiting 0; Scenario 2: consolidated passing, failing, and exception fixtures exiting 1) and document the child-process spawn optimization rationale (#306, #322, #338).
- **Post E2E Evidence Repo Parameter Normalization**: Added defensive normalization guard for `-Repo` parameter in `scripts/post-e2e-evidence.ps1` to ensure consistent fallback to `$env:GITHUB_REPOSITORY` or repository default when called with whitespace/empty string (#227, #232, #236).
- **ArtifactName Fallback Tracking & Single-Sourced Default**: Consolidated `-ArtifactName` parameter default to `""` and introduced explicit boolean `$artifactNameFallbackApplied` in `scripts/summarize-unit-tests.ps1`, eliminating magic-string sentinel comparisons when formatting truncation notes (#224, #225, #231, #234).
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
