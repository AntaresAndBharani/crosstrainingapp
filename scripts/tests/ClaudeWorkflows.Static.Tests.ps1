<#
.SYNOPSIS
    Static source-text and contract regression assertions for Claude GitHub App workflows:
    - .github/workflows/claude.yml
    - .github/workflows/pr-review.yml
    - .github/workflows/prompts/pr-review.md
    Dot-sourced by Invoke-ScriptTests.ps1.
#>

$ClaudeWorkflowPath = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "../../.github/workflows/claude.yml")
)
$PrReviewWorkflowPath = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "../../.github/workflows/pr-review.yml")
)
$PrReviewPromptPath = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "../../.github/workflows/prompts/pr-review.md")
)

Write-Host "--- static hygiene: claude.yml, pr-review.yml & prompts/pr-review.md ---"

# ---------------------------------------------------------------------------
# 1. Interactive @claude Workflow (.github/workflows/claude.yml)
# ---------------------------------------------------------------------------
Assert-True -Condition ([System.IO.File]::Exists($ClaudeWorkflowPath)) `
            -TestName "static: claude.yml exists"

$ClaudeWorkflowText = [System.IO.File]::ReadAllText($ClaudeWorkflowPath, [System.Text.Encoding]::UTF8)

Assert-True -Condition ($ClaudeWorkflowText -match 'name:\s*Claude Code') `
            -TestName "static: claude.yml defines 'Claude Code' workflow name"

Assert-True -Condition ($ClaudeWorkflowText -match 'issue_comment:' -and $ClaudeWorkflowText -match 'pull_request_review_comment:' -and $ClaudeWorkflowText -match 'issues:' -and $ClaudeWorkflowText -match 'pull_request_review:') `
            -TestName "static: claude.yml triggers on issue_comment, pull_request_review_comment, issues, and pull_request_review"

Assert-True -Condition ($ClaudeWorkflowText -match "contains\(github\.event\.comment\.body,\s*'@claude'\)" -and $ClaudeWorkflowText -match "contains\(github\.event\.review\.body,\s*'@claude'\)" -and $ClaudeWorkflowText -match "contains\(github\.event\.issue\.body,\s*'@claude'\)") `
            -TestName "static: claude.yml filters all triggers on @claude mention"

Assert-True -Condition ($ClaudeWorkflowText -match 'uses:\s*anthropics/claude-code-action@v1') `
            -TestName "static: claude.yml uses anthropics/claude-code-action@v1"

Assert-True -Condition ($ClaudeWorkflowText -match 'claude_code_oauth_token:\s*\$\{\{\s*secrets\.CLAUDE_CODE_OAUTH_TOKEN\s*\}\}') `
            -TestName "static: claude.yml passes secrets.CLAUDE_CODE_OAUTH_TOKEN"

Assert-True -Condition ($ClaudeWorkflowText -match '--model claude-opus-5-5 --effort high') `
            -TestName "static: claude.yml configures --model claude-opus-5-5 --effort high"

Assert-True -Condition (-not ($ClaudeWorkflowText -match 'setup-java|setup-gradle')) `
            -TestName "static: claude.yml stays lightweight without pre-installed JDK/Gradle steps"

# ---------------------------------------------------------------------------
# 2. Automated PR Review & Auto-Merge Workflow (.github/workflows/pr-review.yml)
# ---------------------------------------------------------------------------
Assert-True -Condition ([System.IO.File]::Exists($PrReviewWorkflowPath)) `
            -TestName "static: pr-review.yml exists"

$PrReviewText = [System.IO.File]::ReadAllText($PrReviewWorkflowPath, [System.Text.Encoding]::UTF8)

Assert-True -Condition ($PrReviewText -match 'group:\s*pr-review-\$\{\{\s*github\.event\.pull_request\.number\s*\|\|\s*inputs\.pr_number\s*\}\}') `
            -TestName "static: pr-review.yml scopes concurrency group per PR number"

Assert-True -Condition ($PrReviewText -match 'cancel-in-progress:\s*false') `
            -TestName "static: pr-review.yml disables self-cancellation (cancel-in-progress: false)"

Assert-True -Condition ($PrReviewText -match '\[skip pr-review\]' -and $PrReviewText -match 'merge_only=true' -and $PrReviewText -match 'merge_only=false') `
            -TestName "static: pr-review.yml implements [skip pr-review] merge_only fast-path in steps.meta"

Assert-True -Condition ($PrReviewText -match "steps\.meta\.outputs\.skip != 'true' && steps\.meta\.outputs\.merge_only != 'true'") `
            -TestName "static: pr-review.yml guards checkout, context fetch, and Claude review with merge_only != 'true'"

Assert-True -Condition (-not ($PrReviewText -match 'setup-java|setup-gradle')) `
            -TestName "static: pr-review.yml stays lightweight without pre-installed JDK/Gradle steps"

Assert-True -Condition ($PrReviewText -match 'uses:\s*anthropics/claude-code-action@v1' -and $PrReviewText -match '--model claude-opus-5-5 --effort high') `
            -TestName "static: pr-review.yml runs Claude Opus 5.5 High via anthropics/claude-code-action@v1"

Assert-True -Condition ($PrReviewText -match 'def ensure_label\(' -and $PrReviewText -match "'gh',\s*'label',\s*'create'" -and $PrReviewText -match "'--force'") `
            -TestName "static: pr-review.yml defines idempotent ensure_label helper with --force"

Assert-True -Condition ($PrReviewText -match '"review:approved"' -and $PrReviewText -match '"review:changes-requested"' -and $PrReviewText -match '"needs-refactor"' -and $PrReviewText -match '"status:needs-po-input"' -and $PrReviewText -match '"status:done"' -and $PrReviewText -match '"dev-implemented"' -and $PrReviewText -match '"tech-debt"') `
            -TestName "static: pr-review.yml provisions both darwin-trader review labels and CrossTraining orchestrator labels"

Assert-True -Condition ($PrReviewText -match 'CI_WORKFLOW_NAME:\s*"PR CI & Test Verification"') `
            -TestName "static: pr-review.yml targets 'PR CI & Test Verification' workflow"

Assert-True -Condition ($PrReviewText -match '--commit "\$CURRENT_SHA" -w "\$CI_WORKFLOW_NAME"' -and $PrReviewText -match 'seq 1 60' -and $PrReviewText -match 'CI_PASSED="true"') `
            -TestName "static: pr-review.yml polls CI strictly by --commit CURRENT_SHA with explicit CI_PASSED gate"

Assert-True -Condition ($PrReviewText -match 'gh pr merge "\$PR_NUMBER" -R "\$REPO" --squash --delete-branch') `
            -TestName "static: pr-review.yml squash-merges and deletes branch upon passing CI"

Assert-True -Condition ($PrReviewText -match 'id:\s*close_story_step' -and $PrReviewText -match 'OPEN_SIBLINGS' -and $PrReviewText -match 'gh issue close "\$STORY"') `
            -TestName "static: pr-review.yml closes parent story when OPEN_SIBLINGS is 0"

# ---------------------------------------------------------------------------
# 3. PR Review Prompt (.github/workflows/prompts/pr-review.md)
# ---------------------------------------------------------------------------
Assert-True -Condition ([System.IO.File]::Exists($PrReviewPromptPath)) `
            -TestName "static: prompts/pr-review.md exists"

$PrReviewPromptText = [System.IO.File]::ReadAllText($PrReviewPromptPath, [System.Text.Encoding]::UTF8)

Assert-True -Condition ($PrReviewPromptText -match 'pr_meta\.json' -and $PrReviewPromptText -match 'pr_diff\.txt' -and $PrReviewPromptText -match 'pr_review_output\.json') `
            -TestName "static: prompts/pr-review.md documents pr_meta.json, pr_diff.txt, and pr_review_output.json"

Assert-True -Condition ($PrReviewPromptText -match '"verdict":\s*"APPROVED"' -and $PrReviewPromptText -match 'CHANGES_REQUESTED' -and $PrReviewPromptText -match '"followup_backlog_issues"') `
            -TestName "static: prompts/pr-review.md defines required JSON schema fields"

Assert-True -Condition ($PrReviewPromptText -match 'collectAsStateWithLifecycle' -and $PrReviewPromptText -match 'gradle\.properties') `
            -TestName "static: prompts/pr-review.md enforces CrossTraining MVVM/UDF and gradle.properties hygiene rules"
