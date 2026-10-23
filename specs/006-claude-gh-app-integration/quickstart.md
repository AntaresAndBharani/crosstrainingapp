# Phase 1 Quickstart & Validation Guide: Claude GitHub App Integration (`006-claude-gh-app-integration`)

## Prerequisites
- PowerShell 7 (`pwsh`) or Windows PowerShell
- JDK 17 (configured in `~/.gradle/gradle.properties` for Android unit tests)
- Repository secrets configured on `AntaresAndBharani/crosstrainingapp`:
  - `CLAUDE_CODE_OAUTH_TOKEN`
  - `ORCHESTRATION_PAT` (optional, falls back to `GITHUB_TOKEN`)

## Local Validation Scenarios

### 1. Run Cross-Platform Static Workflow Regression Tests
Verify `.github/workflows/claude.yml`, `.github/workflows/pr-review.yml`, and `.github/workflows/prompts/pr-review.md` against all contract assertions in [claude-workflows.md](contracts/claude-workflows.md):

```powershell
pwsh -NoProfile -File ./scripts/tests/Invoke-ScriptTests.ps1
```
**Expected Outcome**: `ClaudeWorkflows.Static.Tests.ps1` and all existing `*.Tests.ps1` suites pass with `0 failed`.

### 2. Run Full Pre-PR Verification Suite
Verify Android JVM unit tests and PowerShell script tests:

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
.\scripts\tests\Invoke-ScriptTests.ps1
```
**Expected Outcome**: `BUILD SUCCESSFUL` and `0 failed` across all test files.

## Remote GitHub Validation Scenarios

1. **Interactive `@claude` Mention**:
   - Comment `@claude summarize the architecture of this PR` on any open Issue or Pull Request.
   - Confirm the `Claude Code` workflow (`.github/workflows/claude.yml`) triggers and posts a response.
2. **Automated PR Review & Auto-Merge**:
   - Open a non-draft Pull Request targeting `main`.
   - Confirm the `PR Review` workflow (`.github/workflows/pr-review.yml`) executes Claude Opus 5.5 High, posts a `<!-- pr-review-verdict -->` comment, applies `review:approved`, waits for `PR CI & Test Verification` on `CURRENT_SHA`, and squash-merges once green.
