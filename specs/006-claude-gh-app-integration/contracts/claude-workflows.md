# Phase 1 Interface Contracts: Claude GitHub App Workflows (`006-claude-gh-app-integration`)

## 1. `.github/workflows/claude.yml` Contract

- **Workflow Name**: `Claude Code`
- **Triggers**:
  - `issue_comment: types: [created]`
  - `pull_request_review_comment: types: [created]`
  - `issues: types: [opened, assigned]`
  - `pull_request_review: types: [submitted]`
- **Job `claude` Condition (`if`)**:
  ```yaml
  (github.event_name == 'issue_comment' && contains(github.event.comment.body, '@claude')) ||
  (github.event_name == 'pull_request_review_comment' && contains(github.event.comment.body, '@claude')) ||
  (github.event_name == 'pull_request_review' && contains(github.event.review.body, '@claude')) ||
  (github.event_name == 'issues' && (contains(github.event.issue.body, '@claude') || contains(github.event.issue.title, '@claude')))
  ```
- **Permissions**: `contents: write`, `pull-requests: write`, `issues: write`, `id-token: write`, `actions: read`
- **Action Invocation**:
  - `uses: anthropics/claude-code-action@v1`
  - `claude_code_oauth_token: ${{ secrets.CLAUDE_CODE_OAUTH_TOKEN }}`
  - `claude_args: '--model claude-opus-5-5 --effort high'`
  - `additional_permissions: actions: read`

## 2. `.github/workflows/pr-review.yml` Contract

- **Workflow Name**: `PR Review`
- **Triggers**:
  - `pull_request: types: [opened, synchronize, ready_for_review, reopened]`
  - `workflow_dispatch: inputs: { pr_number: { required: true, type: string } }`
- **Concurrency**:
  ```yaml
  concurrency:
    group: pr-review-${{ github.event.pull_request.number || inputs.pr_number }}
    cancel-in-progress: false
  ```
- **Step IDs & Contracts**:
  1. `id: meta`: Evaluates PR state, `ROUND_CAP` (`3`), and `[skip pr-review]` fast-path (`skip` and `merge_only` outputs).
  2. `Checkout PR branch` & `Fetch PR context`: Guarded by `steps.meta.outputs.skip != 'true' && steps.meta.outputs.merge_only != 'true'`.
  3. `id: claude`: Invokes `anthropics/claude-code-action@v1` with `--model claude-opus-5-5 --effort high --allowedTools "Read,Write,Edit,MultiEdit,Grep,Glob,Bash"` reading `.github/workflows/prompts/pr-review.md`.
  4. `id: verdict_step`: Commits & pushes any working-tree changes with `"fix(pr-review): apply Claude Opus 5.5 auto-fixes [skip pr-review]"`, posts `<!-- pr-review-verdict -->` comment, idempotently creates labels via `ensure_label()`, files `followup_backlog_issues`, applies `review:approved` or `review:changes-requested` + `needs-refactor`, and outputs `verdict` and `current_sha`.
  5. `id: merge_step`: Polls `gh run list -R "$REPO" --commit "$CURRENT_SHA" -w "PR CI & Test Verification" -L 1` up to 60 times (10s interval). Requires `CI_PASSED="true"` before `gh pr merge "$PR_NUMBER" -R "$REPO" --squash --delete-branch`; on failure/timeout, transitions labels to `review:changes-requested` + `needs-refactor` and exits `1`.
  6. `id: close_story_step`: Checks linked subtask's parent story; if `OPEN_SIBLINGS == 0`, ensures `status:done` and `dev-implemented`, comments on parent story, updates labels, and closes parent story.
