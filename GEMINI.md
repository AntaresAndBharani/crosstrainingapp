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
- **Lint Check:** `.\gradlew.bat lintDebug --no-daemon`

## Core Development Guidelines
1. **Architecture:** MVVM with Unidirectional Data Flow (UDF). Composable -> ViewModel -> Repository -> Room/Firebase.
2. **State Management:** Expose immutable `StateFlow<UiState>` from ViewModels; collect with `collectAsStateWithLifecycle()` in Composables.
3. **Testing & CI Parity:** Never break unit test suite or CI builds. Prior to pushing or opening a PR, run the **Full Pre-PR Verification Suite** to ensure 100% green CI on first push.
4. **Environment Isolation:** Never commit machine-specific paths (such as Windows `org.gradle.java.home`) into repository `gradle.properties`. User-specific JVM paths belong in `~/.gradle/gradle.properties`.
5. **Local APK Sync:** When building APKs, automatically copy the output APK to `local_test\latest.apk`.
6. **GitHub Permissions:** Always run `C:\Users\rogal\workspaces\Set-GhToken-Antares.ps1` for Git push and `gh` operations under the `AntaresAndBharani` organization.
7. **CI/CD Lifecycle & Definition of Done:**
   - **PR Workflow:** Opening/updating a PR builds the snapshot APK and updates the rolling `snapshot` pre-release on GitHub.
   - **Merge to Main:** Merging into `main` automatically tags the release and publishes the official GitHub Release with the APK.
   - **Agent Completion Gate:** Development is only complete when the PR is opened AND all remote GitHub Actions CI checks pass (Green).
