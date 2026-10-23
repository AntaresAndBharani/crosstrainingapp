---
description: "Task list for Claude GitHub App Integration (006-claude-gh-app-integration)"
---

# Tasks: Claude GitHub App Integration (`claude.yml` & Automated `pr-review.yml` Auto-Fix & Squash-Merge Pipeline)

**Input**: Design documents from `/specs/006-claude-gh-app-integration/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/claude-workflows.md

**Tests**: Deterministic static regression tests in `scripts/tests/ClaudeWorkflows.Static.Tests.ps1` are explicitly requested in the feature specification (FR-009, SC-004).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Ensure prompt directory and constitution hygiene

- [X] T001 Ensure `.github/workflows/prompts/` directory exists and remove the temporary Sync Impact Report HTML comment from `.specify/memory/constitution.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Create the CrossTraining PR Review & Auto-Fix prompt required by the automated review pipeline

- [X] T002 Create `.github/workflows/prompts/pr-review.md` defining the CrossTraining PR Review & Auto-Fix Engineer (Claude Opus 5.5, High Effort) instructions covering MVVM/UDF (`StateFlow<UiState>`, `collectAsStateWithLifecycle()`), Room migration & schema export parity, Cloud Sync tombstone safety, display-unit rounding consistency (`kg`/`lbs`), responsive 360dp Compose UI & TalkBack accessibility, `gradle.properties` environment isolation, `CHANGELOG.md` completeness, in-place working-tree edits, and the required `pr_review_output.json` schema (`verdict` enum `"APPROVED"` or `"CHANGES_REQUESTED"`, `fixes_applied`, `summary`, `pr_comment_markdown`, `blocking_issues`, `followup_backlog_issues`)

**Checkpoint**: Foundational prompt ready — user story workflow implementation can now proceed.

---

## Phase 3: User Story 1 - Interactive `@claude` Collaboration on Issues and Pull Requests (Priority: P1) 🎯 MVP

**Goal**: Enable interactive `@claude` mentions across Issues, Issue Comments, Pull Request Reviews, and PR Review Comments using `anthropics/claude-code-action@v1` (`--model claude-opus-5-5 --effort high`).

**Independent Test**: Verify `.github/workflows/claude.yml` triggers exclusively on `@claude` across all 4 event types with required permissions and model arguments via `scripts/tests/ClaudeWorkflows.Static.Tests.ps1`.

### Implementation for User Story 1

- [X] T003 [P] [US1] Create `.github/workflows/claude.yml` triggering on `issue_comment` (`created`), `pull_request_review_comment` (`created`), `issues` (`opened`, `assigned`), and `pull_request_review` (`submitted`) with `@claude` filter guards, write permissions (`contents: write`, `pull-requests: write`, `issues: write`, `id-token: write`, `actions: read`), `actions/checkout@v4` (`fetch-depth: 1`), and `anthropics/claude-code-action@v1` (`claude_code_oauth_token: ${{ secrets.CLAUDE_CODE_OAUTH_TOKEN }}`, `claude_args: '--model claude-opus-5-5 --effort high'`)

**Checkpoint**: User Story 1 (`.github/workflows/claude.yml`) is complete and independently verifiable.

---

## Phase 4: User Story 2 & User Story 3 - Automated PR Review, In-Place Auto-Fix, Zero-Token Fast-Path & Exact-SHA Squash-Merge (Priority: P1 / P2)

**Goal**: Implement `.github/workflows/pr-review.yml` owning the full pre-flight routing (`skip` / `merge_only` / `ROUND_CAP=3`), Claude Opus 5.5 High review & in-place auto-fix, `[skip pr-review]` commit/push without self-cancellation (`cancel-in-progress: false`), idempotent self-healing label governance (`ensure_label` for `review:*`, `status:*`, `needs-refactor`, `dev-implemented`, `tech-debt`), exact-SHA polling of `PR CI & Test Verification`, squash-merge, and parent-story closure.

**Independent Test**: Verify all workflow steps, concurrency settings, `merge_only` step guards, `ensure_label` metadata, `--commit "$CURRENT_SHA"` polling of `PR CI & Test Verification`, and parent-story closure in `scripts/tests/ClaudeWorkflows.Static.Tests.ps1`.

### Implementation for User Story 2 & User Story 3

- [X] T004 [US2] [US3] Create `.github/workflows/pr-review.yml` implementing `concurrency` (`cancel-in-progress: false`), `steps.meta` (`[skip pr-review]` `merge_only=true` fast-path and `ROUND_CAP: 3` escalation to `status:needs-po-input`), PR branch checkout & `pr_meta.json`/`pr_diff.txt` extraction, `anthropics/claude-code-action@v1` (`--model claude-opus-5-5 --effort high`), `steps.verdict_step` (in-place `[skip pr-review]` auto-fix commit/push, `ensure_label` for `review:approved`, `review:changes-requested`, `needs-refactor`, `status:needs-po-input`, `status:ready`, `status:done`, `dev-implemented`, `tech-debt`, and `followup_backlog_issues` creation), `steps.merge_step` (`--commit "$CURRENT_SHA"` polling against `"PR CI & Test Verification"` for 60 attempts with `CI_PASSED="true"` gate and `review:changes-requested` + `needs-refactor` failure fallback), and `steps.close_story_step` (parent story `status:done` + `dev-implemented` closure when `OPEN_SIBLINGS == 0`)

---

## Phase 5: Polish, Regression Test Suite & Cross-Cutting Concerns

**Purpose**: Add static regression test suite, update `CHANGELOG.md`, and run full verification gates.

- [X] T005 [P] Create `scripts/tests/ClaudeWorkflows.Static.Tests.ps1` asserting all structural and behavioral contracts of `.github/workflows/claude.yml`, `.github/workflows/pr-review.yml`, and `.github/workflows/prompts/pr-review.md`
- [X] T006 Update `CHANGELOG.md` under `## [Unreleased]` documenting the Claude GitHub App integration (`claude.yml`, `pr-review.yml`, `prompts/pr-review.md`, and `ClaudeWorkflows.Static.Tests.ps1`)
- [X] T007 Execute `pwsh -NoProfile -File ./scripts/tests/Invoke-ScriptTests.ps1` and `.\gradlew.bat testDebugUnitTest --no-daemon` to verify 100% green test suite

---

## Dependencies & Execution Order

- **Phase 1 (T001)** → **Phase 2 (T002)** → **Phase 3 (T003) & Phase 4 (T004)** → **Phase 5 (T005, T006, T007)**.
- **Parallel Opportunities**: `T002`, `T003`, `T004`, and `T005` operate on distinct files and can be authored in parallel before running `T007`.
