# Android Test-Driven Development (TDD) Workflow

## Step 1: Red (Failing Test)
1. `@Tester` writes a new unit test in `app/src/test/java/com/fractanomics/crosstraining/` defining the expected behavior, edge cases, or state transitions.
2. Run `.\gradlew.bat testDebugUnitTest --no-daemon` to confirm the test fails as expected.

## Step 2: Green (Implementation)
1. `@Developer` writes the minimal clean code in `app/src/main/` to satisfy the failing test.
2. `@Tester` re-runs `.\gradlew.bat testDebugUnitTest --no-daemon` to confirm all tests pass.

## Step 3: Refactor & Clean Code
1. `@Developer` refactors for readability, performance, and architecture without altering test assertions.
2. Verify that `.\gradlew.bat testDebugUnitTest --no-daemon` remains 100% green.
