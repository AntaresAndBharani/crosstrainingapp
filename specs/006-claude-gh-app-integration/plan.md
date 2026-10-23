# Implementation Plan: Claude GitHub App Integration (`claude.yml` & Automated `pr-review.yml` Auto-Fix & Squash-Merge Pipeline)

**Branch**: `feat/006-claude-gh-app-integration` | **Date**: 2026-10-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/006-claude-gh-app-integration/spec.md`

## Summary

Integrate the Claude GitHub App (`anthropics/claude-code-action@v1` with `--model claude-opus-5-5 --effort high`) into `AntaresAndBharani/crosstrainingapp` following the reference architecture in `AntaresAndBharani/darwin-trader`:
1. Create `.github/workflows/claude.yml` for interactive `@claude` mentions on Issues, Issue Comments, Pull Request Reviews, and PR Review Comments.
2. Create `.github/workflows/pr-review.yml` and `.github/workflows/prompts/pr-review.md` for automated Pull Request review, in-place auto-fixing (`[skip pr-review]`), non-cancelling concurrency (`cancel-in-progress: false`), zero-token `merge_only` recovery fast-path, idempotent self-healing label governance (`review:*`, `status:*`, `needs-refactor`, `dev-implemented`, `tech-debt`), exact-SHA CI polling against `PR CI & Test Verification` (`build.yml`), squash-merge, and post-merge parent-story closure.
3. Add deterministic static regression tests in `scripts/tests/ClaudeWorkflows.Static.Tests.ps1` executed automatically by `scripts/tests/Invoke-ScriptTests.ps1`.

## Technical Context

**Language/Version**: YAML (GitHub Actions Workflows), Bash & Python 3 (inline workflow steps on `ubuntu-latest`), PowerShell (`pwsh` 7+ / 5.1 for static regression tests), Markdown (`.github/workflows/prompts/pr-review.md`).

**Primary Dependencies**: `actions/checkout@v4`, `anthropics/claude-code-action@v1`, GitHub CLI (`gh`), `jq`, `python3`.

**Storage**: N/A (ephemeral runner files `pr_meta.json`, `pr_diff.txt`, `pr_review_output.json` cleaned before git status / commit).

**Testing**: `scripts/tests/Invoke-ScriptTests.ps1` (`scripts/tests/ClaudeWorkflows.Static.Tests.ps1`) and `./gradlew testDebugUnitTest --no-daemon`.

**Target Platform**: GitHub Actions (`ubuntu-latest` for workflows; `ubuntu-latest` and `windows-latest` for `Invoke-ScriptTests.ps1`).

**Project Type**: Mobile application (`com.fractanomics.crosstraining`) CI/CD & Agentic Governance infrastructure.

**Performance Goals**: Zero redundant JDK/Gradle build overhead inside Claude workflows; `merge_only` fast-path consumes `0` LLM tokens and completes pre-flight routing in `< 10s`.

**Constraints**: Must preserve `.github/workflows/build.yml` workflow name (`PR CI & Test Verification`), never self-cancel active review runs on `[skip pr-review]` pushes (`cancel-in-progress: false`), and idempotently create any label before applying it.

**Scale/Scope**: 5 files created/modified (`.github/workflows/claude.yml`, `.github/workflows/pr-review.yml`, `.github/workflows/prompts/pr-review.md`, `scripts/tests/ClaudeWorkflows.Static.Tests.ps1`, `CHANGELOG.md`) plus Spec-Kit artifacts.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (MVVM & UDF)**: PASS — `.github/workflows/prompts/pr-review.md` explicitly enforces MVVM + Unidirectional Data Flow and immutable `StateFlow<UiState>` collection during automated PR reviews.
- **Principle II (Pure Deterministic Analytics & Display-Unit Consistency)**: PASS — `prompts/pr-review.md` instructs the reviewer to audit display-unit rounding consistency (`kg`/`lbs`) and pure date-injected domain functions.
- **Principle III (Test-First & 100% CI Parity)**: PASS — All new workflows and prompts are covered by `scripts/tests/ClaudeWorkflows.Static.Tests.ps1` and gated on `PR CI & Test Verification` (`--commit "$CURRENT_SHA"`).
- **Principle IV (Responsive Mobile UX & Accessibility)**: PASS — `prompts/pr-review.md` enforces 360dp anti-truncation and TalkBack semantics checks.
- **Principle V (Offline-First & Environment Isolation)**: PASS — `prompts/pr-review.md` checks against machine-specific paths in `gradle.properties` and verifies Room migration/schema parity.
- **Principle VI (Agentic Collaboration, Automated PR Review & Self-Healing Governance)**: PASS — Directly implements Principle VI (`claude.yml`, `pr-review.yml`, `cancel-in-progress: false`, `merge_only` fast-path, `ensure_label`, and exact-SHA polling).

## Project Structure

### Documentation (this feature)

```text
specs/006-claude-gh-app-integration/
├── plan.md                          # This file (/speckit-plan output)
├── research.md                      # Phase 0 output (/speckit-plan)
├── data-model.md                    # Phase 1 output (/speckit-plan)
├── quickstart.md                    # Phase 1 output (/speckit-plan)
├── contracts/
│   └── claude-workflows.md          # Phase 1 workflow contracts (/speckit-plan)
├── checklists/
│   └── requirements.md              # Spec quality checklist (/speckit-specify)
└── tasks.md                         # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
.github/
└── workflows/
    ├── build.yml                    # Existing PR CI & Test Verification workflow (preserved)
    ├── release.yml                  # Existing Release on Merge to Main workflow (preserved)
    ├── claude.yml                   # NEW: Interactive @claude GitHub App workflow
    ├── pr-review.yml                # NEW: Automated PR Review, Auto-Fix & Squash-Merge workflow
    └── prompts/
        └── pr-review.md             # NEW: CrossTraining PR review & auto-fix instructions

scripts/
└── tests/
    ├── Invoke-ScriptTests.ps1       # Existing cross-platform PowerShell test runner
    ├── ReleaseWorkflow.Static.Tests.ps1
    └── ClaudeWorkflows.Static.Tests.ps1 # NEW: Static regression assertions for claude.yml & pr-review.yml

CHANGELOG.md                         # MODIFIED: Document integration under ## [Unreleased]
```

**Structure Decision**: Extends the existing `.github/workflows/` and `scripts/tests/` layout, following the exact pattern of `ReleaseWorkflow.Static.Tests.ps1` for cross-platform static verification.

## Complexity Tracking

No Constitution violations — table omitted.
