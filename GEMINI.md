# CrossTraining App - Project Instructions & Context

## Project Overview
- **Application:** CrossTraining Mobile App (`com.fractanomics.crosstraining`)
- **Stack:** Android (SDK 35), Kotlin (JVM 17), Jetpack Compose, Material 3, Room, Firebase.
- **Build Tool:** Gradle Kotlin DSL (Use `.\gradlew.bat` on Windows).

## Quick Commands
- **GitHub Token Setup:** `& C:\Users\rogal\workspaces\Set-GhToken-Antares.ps1` (Run before git push / gh commands)
- **Run Unit Tests:** `.\gradlew.bat testDebugUnitTest --no-daemon`
- **Build Debug APK:** `.\gradlew.bat assembleDebug --no-daemon`
- **Build Snapshot APK (CI Parity):** `.\gradlew.bat assembleSnapshot -PsnapshotLabel=localtest --no-daemon`
- **Full Pre-PR Verification Suite:** `.\gradlew.bat testDebugUnitTest assembleSnapshot -PsnapshotLabel=localtest --no-daemon`
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
   - **PR Workflow:** Opening/updating a PR builds the snapshot APK and updates the rolling `snapshot` pre-release on GitHub.
     - **Concurrency / cancellation:** Pushing a new commit to a PR branch automatically cancels the superseded snapshot build for that branch — redundant runner minutes are eliminated.
     - **In-place release update:** The `snapshot` pre-release is always updated in place and **never deleted**, so it remains downloadable even if an in-flight build is cancelled mid-run.
     - **Shared release:** The `snapshot` pre-release is shared across all open PRs — it reflects the most recently *completed* build. Always check the PR/branch/commit line in the release body before installing to confirm which PR the APK belongs to.
   - **Merge to Main:** Merging into `main` automatically tags the release and publishes the official GitHub Release with the APK.
   - **Agent Completion Gate:** Development is only complete when local tests pass, E2E artifacts are captured, the PR is opened, AND all remote GitHub Actions CI checks pass (Green).

## Agentic SDLC Pipeline
Headless Architect (Claude) and Three Amigos (Gemini) run automatically on
issue label changes (`.github/workflows/architect.yml`,
`three-amigos.yml`). Full design and rationale live in the
`AntaresAndBharani/graph-engineering` repo (`docs/definition-node.md`,
`docs/three-amigos-node.md`, `README.md`) — this is the quick-reference for
using it here, not a copy of that design.

- **As PO, draft a User Story** with the `user-story.yml` issue template.
  When ready, relabel it `status:ready-for-architect` to hand off.
- **Label meanings:**
  - `status:definition` — still drafting
  - `status:ready-for-architect` — PO says go (on a story: decompose; on a
    subtask: incorporate my answer to a prior `status:needs-po-input`)
  - `status:needs-po-input` — Architect needs your decision; read the
    comment, answer, then relabel `status:ready-for-architect`
  - `status:review` — Architect handed this subtask to Three Amigos
  - `status:needs-revision` / `status:needs-clarification` — Three Amigos
    bounced it back to Architect; no action needed from you unless it
    escalates to `status:needs-po-input`
  - `status:awaiting-approval` — Three Amigos' own internal marker on a
    subtask it's cleared for pickup; not something you act on
  - `status:ready` — Three Amigos sets this on the story automatically on
    a READY batch verdict (as of 2026-08-25 — no PO relabel step anymore).
    Dev & Test picks it up from here on its own.
  - `status:done` — set automatically once every subtask under a story is
    closed; the story itself is also closed at that point
- **Nothing gets implemented until Three Amigos clears the batch.** Past
  that point the whole loop — Dev & Test's implementation, PR Review,
  fix-up rounds, and Merge — runs without you. You still get pulled in for
  `status:needs-po-input` escalations (Architect conflicts, round-cap
  hits) at any stage.
