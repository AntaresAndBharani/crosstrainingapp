# Testing & Verification Rules

## 1. Test Command & Execution
- **Command:** `.\gradlew.bat testDebugUnitTest --no-daemon`
- Always use `--no-daemon` on Windows to prevent file locking issues with Gradle daemons.

## 2. Pre-PR CI Parity Verification (Mandatory Gate)
- Before creating a PR or pushing to a feature branch, execute the complete CI build matrix locally:
  `.\gradlew.bat testDebugUnitTest assembleSnapshot -PsnapshotLabel=localtest --no-daemon`
- This guarantees 100% parity with GitHub Actions CI (`build.yml`), catching compilation errors, snapshot packaging failures, and broken tests before remote submission.
- Ensure no machine-specific paths (e.g. Windows paths in `gradle.properties`) are committed to version control.

## 3. Test Authoring Best Practices
- **Location:** Unit tests belong in `app/src/test/java/com/fractanomics/crosstraining/`.
- **Coroutines Testing:** Use `kotlinx-coroutines-test` with `runTest` and `StandardTestDispatcher` / `UnconfinedTestDispatcher`.
- **ViewModel Testing:** Use `Turbine` or collect `StateFlow` values to test state transitions.
- **Repository Fakes:** Prefer lightweight in-memory fake repositories over complex Mockito mocking.

## 4. Strict Boundary Rules
- `@Developer` MUST NEVER modify or delete existing test assertions in `app/src/test/` to make a build pass.
- All fixes must be implemented in `app/src/main/`.
