# Phase 1 Data Model: Claude GitHub App Integration (`006-claude-gh-app-integration`)

## 1. Pipeline Execution Mode (`steps.meta` Outputs)

Computed at the start of `.github/workflows/pr-review.yml` from `gh pr view` and `gh api`:

| Output Key | Type / Values | Description & Routing Invariant |
| :--- | :--- | :--- |
| `head_ref` | `string` | Branch name of the Pull Request head (`headRefName`). |
| `head_sha` | `string` (40-hex SHA) | Commit SHA of the Pull Request head (`headRefOid`) at job start. |
| `skip` | `'true'` \| `'false'` | `'true'` when `STATE != "OPEN"`, `IS_DRAFT == "true"`, `ROUNDS >= 3`, or `LAST_MSG` contains `[skip pr-review]` without an `APPROVED` verdict comment. |
| `merge_only` | `'true'` \| `'false'` | `'true'` when `STATE == "OPEN"`, `IS_DRAFT == "false"`, `LAST_MSG` contains `[skip pr-review]`, and the latest `<!-- pr-review-verdict -->` comment contains `APPROVED`. |

## 2. Review Verdict Record (`pr_review_output.json`)

Written to the workspace root by Claude Opus 5.5 High during the `Run PR Review & Auto-Fix` step and consumed by `steps.verdict_step`:

| Field | Type | Constraints & Validation Rules |
| :--- | :--- | :--- |
| `verdict` | `string` | Enum: `"APPROVED"` or `"CHANGES_REQUESTED"`. Any unrecognized value defaults to `"CHANGES_REQUESTED"`. |
| `fixes_applied` | `boolean` | `true` if Claude modified files in the working tree; `false` otherwise. |
| `summary` | `string` | Concise 1–2 sentence summary of the review outcome and any auto-fixes applied. |
| `pr_comment_markdown` | `string` | Detailed Markdown summary posted under `<!-- pr-review-verdict -->`. Falls back to `summary` if omitted. |
| `blocking_issues` | `array<BlockingIssue>` | List of `{ "file": string, "issue": string, "suggested_fix": string }`. Empty array `[]` when `verdict == "APPROVED"`. |
| `followup_backlog_issues` | `array<BacklogIssue>` | List of `{ "title": string, "body": string, "labels": array<string> }` created as non-blocking GitHub issues. Defaults `labels` to `["tech-debt"]`. |

## 3. Repository Label Registry (`LABEL_META`)

Idempotently provisioned via `gh label create <name> -R "$REPO" --color <hex> --description <desc> --force`:

| Label Name | Color Hex | Description | Lifecycle Trigger |
| :--- | :--- | :--- | :--- |
| `review:approved` | `0E8A16` | PR approved by automated Claude Opus review | Added when `verdict == "APPROVED"` or `merge_only == "true"`; removed on `CHANGES_REQUESTED` or CI failure. |
| `review:changes-requested` | `D93F0B` | PR requires changes or CI remediation | Added when `verdict == "CHANGES_REQUESTED"` or when `PR CI & Test Verification` fails/times out on `CURRENT_SHA`. |
| `needs-refactor` | `D93F0B` | PR requires remediation or CI fixes, returned to DevTest | Synchronized alongside `review:changes-requested` for CrossTraining orchestrator compatibility. |
| `status:needs-po-input` | `B60205` | Escalated to Product Owner after max review rounds | Added when `ROUNDS >= 3` (`ROUND_CAP`). |
| `status:ready` | `0E8A16` | Story or subtask ready for Dev & Test implementation | Removed from parent story upon completion. |
| `status:done` | `0E8A16` | All subtasks merged and parent story completed | Added to parent story when `OPEN_SIBLINGS == 0` after merge. |
| `dev-implemented` | `C2E0C6` | Implementation completed by DevTest node | Synchronized alongside `status:done` on parent story closure. |
| `tech-debt` | `FBCA04` | Technical debt or non-blocking improvement | Default label for `followup_backlog_issues`. |
