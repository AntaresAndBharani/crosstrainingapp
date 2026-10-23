# Phase 0 Research: Claude GitHub App Integration (`006-claude-gh-app-integration`)

## Decision 1: Dual Workflow Architecture (`claude.yml` + `pr-review.yml`)
- **Decision**: Integrate both `.github/workflows/claude.yml` (interactive `@claude` collaboration across Issues and Pull Requests) and `.github/workflows/pr-review.yml` + `.github/workflows/prompts/pr-review.md` (automated Pull Request review, in-place auto-fix, self-healing label governance, exact-SHA CI gating, squash-merge, and parent-story closure).
- **Rationale**: Mirrors the proven `AntaresAndBharani/darwin-trader` reference architecture (`4ab1ca6`, `343aa6a`, `f6c083a`), providing both interactive on-demand developer collaboration and autonomous Pull Request quality gating.
- **Alternatives considered**:
  - *Interactive `claude.yml` only*: Rejected because it requires manual `@claude` comments on every Pull Request and lacks automated auto-fix, label governance, and squash-merge.
  - *Separate `merge.yml` workflow*: Rejected because a separate label-triggered merge workflow races with `pr-review.yml` when `ORCHESTRATION_PAT` applies `review:approved`.

## Decision 2: Lightweight Runner Environment Without Duplicate JDK/Gradle Setup
- **Decision**: Execute `claude.yml` and `pr-review.yml` on `ubuntu-latest` without pre-installing JDK 17, Gradle, or Android SDK steps inside the Claude workflows. In `pr-review.yml`, Claude Opus 5.5 High inspects `pr_meta.json`, `pr_diff.txt`, and repository source files directly (`Read,Write,Edit,MultiEdit,Grep,Glob,Bash`), applies any code/test/script/doc fixes in place, and delegates full Android JVM unit test (`./gradlew testDebugUnitTest`) and cross-platform script (`./scripts/tests/Invoke-ScriptTests.ps1`) verification to the existing `PR CI & Test Verification` (`build.yml`) workflow via `--commit "$CURRENT_SHA"` polling.
- **Rationale**: Cold Android/Gradle builds on GitHub Actions runners take several minutes; running Gradle inside `pr-review.yml` would duplicate the parallel `build.yml` run. Furthermore, if Claude pushes an `[skip pr-review]` auto-fix commit, `build.yml` automatically runs on the new commit SHA and `pr-review.yml`'s `merge_step` waits for that exact commit's `build.yml` run to pass (`completed:success`) before merging.
- **Alternatives considered**:
  - *Pre-warming JDK 17 + Gradle inside `pr-review.yml`*: Rejected by the Product Owner to keep the Claude workflows fast and avoid redundant Gradle compilation.

## Decision 3: Self-Healing Label Parity + CrossTraining Orchestrator Compatibility
- **Decision**: Maintain all `darwin-trader` review labels (`review:approved`, `review:changes-requested`, `status:needs-po-input`, `status:ready`, `status:done`, `tech-debt`) with idempotent `gh label create <name> -R "$REPO" --force` (`ensure_label`) before any label operation, while also applying CrossTraining's orchestrator labels (`needs-refactor` alongside `review:changes-requested` on changes-requested or CI failure, and `dev-implemented` alongside `status:done` on parent-story closure).
- **Rationale**: Prevents `gh pr edit --add-label` from crashing when a label does not yet exist in the repository (`f6c083a` root cause #1) and ensures seamless interoperability with CrossTraining's external Graph Orchestrator (`GEMINI.md`).
- **Alternatives considered**:
  - *Assuming labels pre-exist*: Rejected because missing labels caused `CalledProcessError` crashes in `darwin-trader` PR #138.

## Decision 4: Concurrency & Zero-Token `merge_only` Recovery Fast-Path
- **Decision**: Set `cancel-in-progress: false` on `concurrency.group: pr-review-${{ github.event.pull_request.number || inputs.pr_number }}` and add a `merge_only=true` fast-path in `steps.meta` when the head commit contains `[skip pr-review]` and the latest `<!-- pr-review-verdict -->` comment contains `APPROVED`.
- **Rationale**: Pushing an `[skip pr-review]` commit with `ORCHESTRATION_PAT` triggers a `pull_request: [synchronize]` run within seconds. With `cancel-in-progress: false`, the `synchronize` run queues behind the active run instead of killing it mid-merge; if a PR is ever re-dispatched after an approved auto-fix push, `merge_only=true` merges it with 0 LLM tokens.
- **Alternatives considered**:
  - *`cancel-in-progress: true`*: Rejected because it self-cancels the active workflow run right after pushing an auto-fix commit.
