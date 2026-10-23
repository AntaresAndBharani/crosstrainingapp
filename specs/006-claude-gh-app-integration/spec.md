# Feature Specification: Claude GitHub App Integration (Interactive `@claude` Mentions & Automated PR Review, Auto-Fix & Squash-Merge Pipeline)

**Feature Branch**: `feat/006-claude-gh-app-integration`

**Created**: 2026-10-23

**Status**: Approved

**Input**: User description: "Integrate the Claude GitHub App into CrossTraining following AntaresAndBharani/darwin-trader, including both interactive @claude collaboration on Issues/PRs and automated PR Review, in-place auto-fix, self-healing label governance, exact-commit verification gating, squash-merge, and parent-story closure."

## Clarifications

### Session 2026-10-23

- Q: Which workflows from the reference project should be integrated into CrossTraining? → A: Integrate both the interactive `@claude` mention workflow and the automated Pull Request review, in-place auto-fix, and squash-merge workflow.
- Q: Should the automated review workflow pre-install heavy mobile build toolchains to run full mobile builds inside the reviewer step? → A: Keep both Claude workflows lightweight without pre-installed mobile build toolchains; let the reviewer inspect diffs and repository files directly (plus lightweight script verification when scripts are modified) while delegating full mobile unit-test and cross-platform script verification to the existing continuous integration workflow via exact-commit polling.
- Q: How should Pull Request and Issue labels be managed across the review lifecycle? → A: Maintain full parity with the reference review labels (`review:approved`, `review:changes-requested`, `status:needs-po-input`, `status:ready`, `status:done`, `tech-debt`) using idempotent auto-creation before applying any label, while also synchronizing CrossTraining orchestrator labels (`needs-refactor` on changes-requested or verification failure, and `dev-implemented` on parent story completion).
- Q: Which continuous integration workflow name should the automated review pipeline poll before merging? → A: Preserve the existing workflow name (`PR CI & Test Verification`) and poll it strictly for the exact target commit SHA before squash-merging.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Interactive `@claude` Collaboration on Issues and Pull Requests (Priority: P1)

As a project maintainer or contributor, when I mention `@claude` in a newly opened or assigned Issue, an Issue Comment, a Pull Request Review, or a Pull Request Review Comment, I want the automated assistant to respond interactively with full repository context and write capabilities so that I can request analysis, explanations, or targeted code changes directly from the issue or pull request thread.

**Why this priority**: Provides immediate on-demand collaboration across Issues and Pull Requests with minimal configuration and zero overhead on commits that do not invoke `@claude`.

**Independent Test**: Can be fully tested by validating that the interactive workflow triggers exclusively on `@claude` mentions across the four supported event types (`issue_comment`, `pull_request_review_comment`, `pull_request_review`, and `issues`), grants the required repository permissions, and invokes the assistant with high-effort reasoning.

**Acceptance Scenarios**:

1. **Given** an open Issue or Pull Request in the repository, **When** a comment, review, or review comment is created containing `@claude`, **Then** the interactive workflow executes and responds in the thread with read/write repository access and actions-read visibility.
2. **Given** a newly opened or assigned Issue whose title or body contains `@claude`, **When** the issue event fires, **Then** the interactive workflow executes for that issue.
3. **Given** an Issue or Pull Request comment that does not contain `@claude`, **When** the comment event fires, **Then** the interactive workflow job is skipped without consuming runner minutes or tokens.

---

### User Story 2 - End-to-End Automated Pull Request Review, In-Place Auto-Fix & Squash-Merge (Priority: P1)

As a project maintainer, when I open or update a non-draft Pull Request, I want an automated reviewer to inspect the Pull Request metadata, diff, and repository files, automatically fix resolvable defects in place, commit and push those fixes without cancelling the active review run, idempotently provision and apply review/orchestrator labels, file non-blocking follow-up backlog issues, await continuous integration verification on the exact target commit, squash-merge the Pull Request, and close the parent story once all sibling subtasks are complete.

**Why this priority**: Eliminates manual review bottlenecks and ensures every Pull Request adheres to architectural, data-integrity, accessibility, and changelog standards while preventing self-cancellation races and missing-label crashes.

**Independent Test**: Can be fully tested by verifying the workflow contract, inline verdict/label processor, exact-SHA verification polling loop, and parent-story closure logic against simulated review outputs and repository states.

**Acceptance Scenarios**:

1. **Given** an open non-draft Pull Request where the automated reviewer applies in-place fixes and outputs an `APPROVED` verdict, **When** the post-review step commits and pushes the `[skip pr-review]` fix commit, **Then** the resulting synchronization event queues behind the active run (`cancel-in-progress: false`), all required labels are idempotently created and applied (`review:approved` added, `review:changes-requested` and `needs-refactor` removed), follow-up backlog issues are created, `PR CI & Test Verification` is polled strictly for the new commit SHA until passing, and the Pull Request is squash-merged with its branch deleted.
2. **Given** an open non-draft Pull Request where the automated reviewer identifies a blocking architectural or product ambiguity and outputs `CHANGES_REQUESTED`, **When** the verdict step executes, **Then** the workflow posts the detailed review verdict comment, idempotently creates and applies both `review:changes-requested` and `needs-refactor` (removing `review:approved`), and does not merge the Pull Request.
3. **Given** a Pull Request linked to a subtask issue whose parent story has zero remaining open subtasks after the Pull Request merges, **When** the post-merge story-closure step executes, **Then** the parent story receives a completion comment, has `status:done` and `dev-implemented` ensured and applied (with `status:ready` and `ready-for-dev` removed), and is closed automatically.

---

### User Story 3 - Zero-Token Fast-Path Recovery & Strict Commit-Scoped Verification Gating (Priority: P2)

As a project maintainer, when an open Pull Request's latest commit is already an automated review fix commit (`[skip pr-review]`) accompanied by an `APPROVED` verdict comment, I want subsequent synchronization or manual dispatch runs to bypass redundant AI review (consuming zero AI tokens) and proceed directly to label assurance, commit-scoped verification polling, and squash-merging—and if verification fails or times out after 3 review rounds or 10 minutes of polling, I want the Pull Request cleanly transitioned to `review:changes-requested` / `needs-refactor` or `status:needs-po-input`.

**Why this priority**: Guarantees idempotent zero-cost recovery for queued or re-dispatched runs after an auto-fix push and prevents untested or failing commits from ever being auto-merged.

**Independent Test**: Can be tested independently by verifying the pre-flight routing logic (`skip` vs. `merge_only` vs. full review), the 3-round escalation cap, and the 60-iteration commit-scoped verification polling failure transitions.

**Acceptance Scenarios**:

1. **Given** an open Pull Request whose `HEAD` commit message contains `[skip pr-review]` and whose latest `<!-- pr-review-verdict -->` comment contains `APPROVED`, **When** the review workflow runs via `synchronize` or `workflow_dispatch`, **Then** pre-flight routing activates `merge_only=true`, skips branch checkout and AI review (consuming zero AI tokens), ensures `review:approved` is applied, verifies `PR CI & Test Verification` passes for `HEAD_SHA`, and squash-merges the Pull Request.
2. **Given** an approved Pull Request at target commit `CURRENT_SHA`, **When** the `PR CI & Test Verification` run for `CURRENT_SHA` concludes with failure, cancellation, or does not succeed within 10 minutes (60 attempts at 10-second intervals), **Then** the workflow aborts the merge, removes `review:approved`, ensures and applies `review:changes-requested` and `needs-refactor`, posts a diagnostic comment referencing `CURRENT_SHA`, and exits with a non-zero status.
3. **Given** an open Pull Request that has already accumulated 3 `<!-- pr-review-verdict -->` review rounds without merging, **When** the review workflow is triggered again, **Then** pre-flight routing escalates the Pull Request by ensuring and applying `status:needs-po-input`, posting an escalation comment, and exiting cleanly without invoking another AI review round.

### Edge Cases

- **Missing repository labels on first run**: Any label referenced by the workflow (`review:approved`, `review:changes-requested`, `needs-refactor`, `status:needs-po-input`, `status:ready`, `status:done`, `dev-implemented`, `tech-debt`, or custom follow-up issue labels) is idempotently created before application so missing labels never abort execution.
- **Draft or closed Pull Requests**: Pre-flight metadata inspection detects `STATE != "OPEN"` or `IS_DRAFT == "true"` and exits `0` immediately (`skip=true`) without invoking AI review.
- **Verification run registration delay after auto-fix push**: Polling strictly by `--commit "$CURRENT_SHA"` against `PR CI & Test Verification` treats an empty run list as pending (`not_registered_yet`) rather than falsely matching an earlier commit's completed run.
- **Pull Request without a linked subtask or parent story**: Post-merge parent-story closure checks whether the Pull Request body references a closing issue (`Closes #N`, `Fixes #N`, `Resolves #N`) and whether that issue has a parent story, exiting `0` cleanly when neither is present.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The repository MUST provide an interactive assistant workflow (`.github/workflows/claude.yml`) that triggers on `issue_comment` (`created`), `pull_request_review_comment` (`created`), `pull_request_review` (`submitted`), and `issues` (`opened`, `assigned`) exclusively when `@claude` is present in the respective comment, review, or issue title/body.
- **FR-002**: Both the interactive workflow and the automated Pull Request review workflow MUST execute on lightweight runners without pre-installing heavy mobile build toolchains inside the assistant workflow steps, delegating full build and unit-test verification to `PR CI & Test Verification` (`.github/workflows/build.yml`).
- **FR-003**: The automated Pull Request review workflow (`.github/workflows/pr-review.yml`) MUST set `cancel-in-progress: false` on its per-Pull-Request concurrency group (`pr-review-<PR>`) so pushing an `[skip pr-review]` auto-fix commit never cancels the active workflow run.
- **FR-004**: Pre-flight metadata evaluation (`steps.meta`) MUST skip closed or draft Pull Requests (`skip=true`), escalate Pull Requests that have reached `ROUND_CAP` (`3` prior verdict comments) to `status:needs-po-input`, and activate a zero-token `merge_only=true` fast-path when the latest commit contains `[skip pr-review]` and the latest `<!-- pr-review-verdict -->` comment contains `APPROVED`.
- **FR-005**: The automated review prompt (`.github/workflows/prompts/pr-review.md`) MUST instruct the reviewer to audit CrossTraining architectural invariants (MVVM + Unidirectional Data Flow, Room migration & schema export parity, Cloud Sync tombstone safety, display-unit rounding consistency, responsive 360dp Compose UI & TalkBack accessibility, `gradle.properties` environment isolation, and `CHANGELOG.md` completeness), apply in-place fixes to the working tree when resolvable, and write a structured `pr_review_output.json` file.
- **FR-006**: Before applying any label to a Pull Request, parent story, or follow-up backlog issue, the workflow MUST idempotently ensure the label exists in the repository (`gh label create ... --force`) covering both reference review labels (`review:approved`, `review:changes-requested`, `status:needs-po-input`, `status:ready`, `status:done`, `tech-debt`) and CrossTraining orchestrator labels (`needs-refactor`, `dev-implemented`).
- **FR-007**: When the review verdict is `APPROVED`, the workflow MUST poll `PR CI & Test Verification` filtered strictly by `--commit "$CURRENT_SHA"` for up to 60 iterations at 10-second intervals (10 minutes max) and gate `gh pr merge --squash --delete-branch` on an explicit `CI_PASSED="true"` confirmation; if verification fails or times out, it MUST remove `review:approved`, apply `review:changes-requested` and `needs-refactor`, post a diagnostic comment, and exit non-zero.
- **FR-008**: After squash-merging an approved Pull Request, the workflow MUST inspect whether a linked subtask issue's parent story has zero remaining open subtasks and automatically comment on, relabel (`status:done` and `dev-implemented`), and close the parent story.
- **FR-009**: The repository MUST include deterministic static regression tests (`scripts/tests/ClaudeWorkflows.Static.Tests.ps1`) executed by `scripts/tests/Invoke-ScriptTests.ps1` verifying all workflow and prompt contracts.

### Key Entities

- **Interactive Mention Trigger**: Event filter matching `@claude` across issue comments, pull request review comments, pull request reviews, and newly opened/assigned issues.
- **Pipeline Execution Mode (`steps.meta`)**: Pre-flight routing state (`skip=true` for closed/draft/unapproved-skip/escalated PRs, `merge_only=true` for open `[skip pr-review]` PRs with an `APPROVED` verdict comment, or full review mode when `skip=false` and `merge_only=false`).
- **Review Verdict Record (`pr_review_output.json`)**: Structured JSON output emitted by the reviewer containing `verdict` (`APPROVED` or `CHANGES_REQUESTED`), `fixes_applied`, `summary`, `pr_comment_markdown`, `blocking_issues`, and `followup_backlog_issues`.
- **Commit-Scoped Verification Gate**: Polling check bound strictly to `--commit "$CURRENT_SHA"` and workflow `"PR CI & Test Verification"` requiring `completed:success` within 60 attempts before squash-merging.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of `@claude` mentions in Issues, Issue Comments, Pull Request Reviews, and PR Review Comments trigger the interactive assistant workflow, while 0 non-mention comments trigger the workflow.
- **SC-002**: 100% of open non-draft Pull Requests receiving an `APPROVED` automated review verdict (with or without pushed in-place auto-fixes) complete label synchronization, follow-up backlog issue creation, exact-SHA verification polling against `PR CI & Test Verification`, and squash-merging without self-cancellation or missing-label errors.
- **SC-003**: 100% of open Pull Requests whose head commit carries `[skip pr-review]` and an existing `APPROVED` verdict comment execute via the `merge_only` fast-path consuming 0 AI review tokens.
- **SC-004**: 100% of static workflow contract assertions in `scripts/tests/Invoke-ScriptTests.ps1` pass on both Windows and Linux runners.

## Assumptions

- The repository has `CLAUDE_CODE_OAUTH_TOKEN` and `ORCHESTRATION_PAT` (with fallback to `GITHUB_TOKEN`) configured in GitHub Actions secrets.
- The primary Pull Request verification workflow in `.github/workflows/build.yml` retains its display name `PR CI & Test Verification`.
- Squash-merging (`--squash --delete-branch`) is enabled on `AntaresAndBharani/crosstrainingapp`.
