# CrossTraining App - Project Instructions & Context

## Project Overview
- **Application:** CrossTraining Mobile App (`com.fractanomics.crosstraining`)
- **Stack:** Android (SDK 35), Kotlin (JVM 17), Jetpack Compose, Material 3, Room, Firebase.
- **Build Tool:** Gradle Kotlin DSL (Use `.\gradlew.bat` on Windows).

## Quick Commands
- **GitHub Token Setup:** `& C:\Users\rogal\workspaces\Set-GhToken-Antares.ps1` (Run before git push / gh commands)
- **Run Unit Tests:** `.\gradlew.bat testDebugUnitTest --no-daemon`
- **Build Debug APK:** `.\gradlew.bat assembleDebug --no-daemon`
- **Full Pre-PR Verification Suite:** `.\gradlew.bat testDebugUnitTest --no-daemon; .\scripts\tests\Invoke-ScriptTests.ps1`
- **Capture E2E Artifacts:** `.\scripts\run-e2e-tests.ps1 -CaptureArtifacts -Version "latest" -PushArtifacts`
- **Lint Check:** `.\gradlew.bat lintDebug --no-daemon`

## Core Development Guidelines
1. **Architecture:** MVVM with Unidirectional Data Flow (UDF). Composable -> ViewModel -> Repository -> Room/Firebase.
2. **State Management:** Expose immutable `StateFlow<UiState>` from ViewModels; collect with `collectAsStateWithLifecycle()` in Composables.
3. **Testing & CI Parity:** Never break unit test suite or CI builds. Prior to pushing or opening a PR, run the **Full Pre-PR Verification Suite** and ensure 100% green CI on first push.
4. **E2E Visual Testing:** Before opening a PR for UI changes, you MUST capture updated E2E screenshots by running the Capture E2E Artifacts script. This syncs the latest screenshots to `docs/screenshots/` and archives them to the `virgymia-qa` repository. Commit the `docs/screenshots/` changes alongside your code.
5. **Environment Isolation:** Never commit machine-specific paths (such as Windows `org.gradle.java.home`) into repository `gradle.properties`. User-specific JVM paths belong in `~/.gradle/gradle.properties`.
6. **Local APK Sync:** When building APKs, automatically copy the output APK to `local_test\latest.apk`.
7. **GitHub Permissions:** Always run `C:\Users\rogal\workspaces\Set-GhToken-Antares.ps1` for Git push and `gh` operations under the `AntaresAndBharani` organization.
8. **CI/CD Lifecycle & Definition of Done:**
   - **PR Workflow:** Opening/updating a PR triggers the CI & Test Verification workflow (`build.yml`), executing cross-platform script regression tests and Android unit tests, uploading unit test reports, and publishing sticky test summaries on the PR.
   - **Merge to Main:** Merging into `main` automatically tags the release and publishes the official GitHub Release with the release-signed APK (`release.yml`).
   - **Agent Completion Gate:** Development is only complete when local tests pass, E2E artifacts are captured, the PR is opened, AND all remote GitHub Actions CI checks pass (Green).

## Agentic SDLC Pipeline (Graph Orchestrator)
The repository is orchestrated externally by the host-based Graph Engineering daemon (`orchestrator`).
Autonomous governance is driven by a clean 2-node topology (Architect and DevTest) using a unified, standardized GitHub label taxonomy.

### Standard Label Taxonomy & Lifecycle
- `needs-triage`: Applied to newly created User Stories (via `user-story.yml`) to signal the Architect node for triage and INVEST decomposition.
- `architect-processed`: Applied to the parent User Story by the Architect once decomposed into child subtasks.
- `queued`: Initial inactive state assigned to newly created child subtasks (via `subtask.yml`).
- `ready-for-dev`: Active trigger for the DevTest node. DevTest deterministically resolves the lowest open subtask in ascending ID/sequence order under the active User Story.
- `dev-implemented`: Applied upon PR implementation and successful CI auto-merge into `main`.
- `needs-refactor`: Flagged when a PR encounters CI failure or structural issues, immediately triggering autonomous remediation by DevTest before any new work is picked up.
- `orchestration-failed`: Applied if an AI harness exhausts its retry budget or encounters an unrecoverable failure.

### Workflow
1. **PO Drafts Story:** Create an issue using the User Story template (`labels: ["needs-triage"]`).
2. **Architect Decomposes:** The Architect node inspects the codebase, creates INVEST subtasks labeled `queued`, links them to the parent, and marks the story `architect-processed`.
3. **DevTest Implements & Auto-Merges:** DevTest unlocks subtasks in ascending order, creates feature branch `feat/issue-<id>`, verifies local tests, opens PR, and squash-and-merges upon 100% passing remote CI.
