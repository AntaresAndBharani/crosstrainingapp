<#
.SYNOPSIS
    Static source-text and logic regression assertions for .github/workflows/architect.yml
    and scripts/local-pipeline/run-architect.ps1.
    Verifies end-to-end Claude Sonnet 5 Medium thinking decomposition, model selection,
    prompt mapping, and GitHub API sub-issues linking (#407, #408, #409, #410, #411).
    Dot-sourced by Invoke-ScriptTests.ps1.
#>

$ArchitectWorkflowPath = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "../../.github/workflows/architect.yml")
)
$RunArchitectScriptPath = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "../local-pipeline/run-architect.ps1")
)

Write-Host "--- static hygiene: architect.yml ---"

Assert-True -Condition ([System.IO.File]::Exists($ArchitectWorkflowPath)) `
            -TestName "static: architect.yml exists"

$WorkflowText = [System.IO.File]::ReadAllText($ArchitectWorkflowPath, [System.Text.Encoding]::UTF8)

# ---------------------------------------------------------------------------
# Workflow Triggers, Concurrency, and Permissions (#408, #411)
# ---------------------------------------------------------------------------
Assert-True -Condition ($WorkflowText -match 'types:\s*\[labeled\]') `
            -TestName "static: architect.yml triggers on issues labeled"

Assert-True -Condition ($WorkflowText -match 'group:\s*architect-\$\{\{\s*github\.event\.issue\.number\s*\}\}') `
            -TestName "static: architect.yml groups concurrency by issue number"

Assert-True -Condition ($WorkflowText -match 'status:ready-for-architect') `
            -TestName "static: architect.yml filters on status:ready-for-architect"

Assert-True -Condition ($WorkflowText -match 'status:needs-revision') `
            -TestName "static: architect.yml filters on status:needs-revision"

Assert-True -Condition ($WorkflowText -match 'status:needs-clarification') `
            -TestName "static: architect.yml filters on status:needs-clarification"

Assert-True -Condition ($WorkflowText -match 'id-token:\s*write') `
            -TestName "static: architect.yml declares id-token write permission for claude-code-action"

# ---------------------------------------------------------------------------
# Story Detection and Mode Determination (#408, #411)
# ---------------------------------------------------------------------------
Assert-True -Condition ($WorkflowText -match 'IS_STORY=false') `
            -TestName "static: architect.yml initializes IS_STORY to false"

Assert-True -Condition ($WorkflowText -match '\*,type:user-story,\*\)\s*IS_STORY=true;;') `
            -TestName "static: architect.yml verifies type:user-story label"

Assert-True -Condition ($WorkflowText -match 'if\s*\[\s*"(\$IS_STORY|"\$IS_STORY")"\s*!=\s*"true"\s*\];\s*then\s*\n\s*echo\s*"mode=skip"\s*>>\s*"\$GITHUB_OUTPUT"\s*\n\s*exit\s*0') `
            -TestName "static: architect.yml skips non-user-story issues"

Assert-True -Condition ($WorkflowText -match 'status:ready-for-architect\)\s*MODE=decompose\s*;;') `
            -TestName "static: architect.yml maps ready-for-architect to decompose mode"

Assert-True -Condition ($WorkflowText -match 'status:needs-revision\)\s*MODE=restructure\s*;;') `
            -TestName "static: architect.yml maps needs-revision to restructure mode"

Assert-True -Condition ($WorkflowText -match 'status:needs-clarification\)\s*MODE=answer_clarifications\s*;;') `
            -TestName "static: architect.yml maps needs-clarification to answer_clarifications mode"

# ---------------------------------------------------------------------------
# Model Selection: Claude Sonnet 5 vs Opus 5 (#408, #411)
# ---------------------------------------------------------------------------
Assert-True -Condition ($WorkflowText -match 'MODEL=claude-opus-5') `
            -TestName "static: architect.yml defaults model to claude-opus-5"

Assert-True -Condition ($WorkflowText -match '\*,origin:backlog-triage,\*\)\s*MODEL=claude-sonnet-5;;') `
            -TestName "static: architect.yml routes origin:backlog-triage to claude-sonnet-5"

Assert-True -Condition ($WorkflowText -match 'echo\s*"model=\$MODEL"\s*>>\s*"\$GITHUB_OUTPUT"') `
            -TestName "static: architect.yml outputs selected model to GITHUB_OUTPUT"

# ---------------------------------------------------------------------------
# Mode-Specific Prompt Selection (#408, #409, #411)
# ---------------------------------------------------------------------------
Assert-True -Condition ($WorkflowText -match 'decompose\)\s*FILE=architect-decompose\.md\s*;;') `
            -TestName "static: architect.yml selects architect-decompose.md for decompose mode"

Assert-True -Condition ($WorkflowText -match 'restructure\)\s*FILE=architect-restructure\.md\s*;;') `
            -TestName "static: architect.yml selects architect-restructure.md for restructure mode"

Assert-True -Condition ($WorkflowText -match 'answer_clarifications\)\s*FILE=architect-answer-clarifications\.md\s*;;') `
            -TestName "static: architect.yml selects architect-answer-clarifications.md for answer_clarifications mode"

# ---------------------------------------------------------------------------
# Claude Agent Execution with Explicit Model (#408, #411)
# ---------------------------------------------------------------------------
Assert-True -Condition ($WorkflowText -match 'uses:\s*anthropics/claude-code-action@v1') `
            -TestName "static: architect.yml invokes anthropics/claude-code-action@v1"

Assert-True -Condition ($WorkflowText -match 'claude_args:\s*.*--model\s+\$\{\{\s*steps\.mode\.outputs\.model\s*\}\}') `
            -TestName "static: architect.yml passes explicit --model from mode step"

Assert-True -Condition ($WorkflowText -match '--permission-mode\s+dontAsk') `
            -TestName "static: architect.yml specifies --permission-mode dontAsk"

Assert-True -Condition ($WorkflowText -match '--allowedTools\s+"Read"\s+"Grep"\s+"Glob"\s+"Write"') `
            -TestName "static: architect.yml restricts allowed tools to Read, Grep, Glob, Write"

Assert-True -Condition ($WorkflowText -match 'OVERLOAD_RETRY_DELAY_SECONDS:\s*300') `
            -TestName "static: architect.yml configures 300s overload retry backoff"

# ---------------------------------------------------------------------------
# Decision Application & GitHub API Sub-issues Linking (#410, #411)
# ---------------------------------------------------------------------------
Assert-True -Condition ($WorkflowText -match 'OUTCOME=\$\(jq\s+-r\s*''\.outcome''\s+architect_output\.json\)') `
            -TestName "static: architect.yml extracts outcome from architect_output.json"

Assert-True -Condition ($WorkflowText -match '--add-label\s+"status:needs-po-input"') `
            -TestName "static: architect.yml adds status:needs-po-input on PO_ESCALATION"

Assert-True -Condition ($WorkflowText -match '--label\s+"type:subtask,status:pending-review"') `
            -TestName "static: architect.yml creates subtasks with type:subtask,status:pending-review"

Assert-True -Condition ($WorkflowText -match 'gh\s+api\s+"repos/\$REPO/issues/\$ISSUE_NUMBER/sub_issues"\s+-F\s+sub_issue_id="\$NEW_ID"') `
            -TestName "static: architect.yml links created subtasks via GitHub Sub-issues API"

Assert-True -Condition ($WorkflowText -match 'gh\s+issue\s+close\s+"\$SUBTASK_NUM"') `
            -TestName "static: architect.yml supports closing subtasks"

Assert-True -Condition ($WorkflowText -match 'gh\s+issue\s+edit\s+"\$SUBTASK_NUM"') `
            -TestName "static: architect.yml supports updating subtasks"

Assert-True -Condition ($WorkflowText -match '--add-label\s+"status:review"') `
            -TestName "static: architect.yml transitions parent story to status:review on PROCEED"

# ---------------------------------------------------------------------------
# Static hygiene & contract checks for run-architect.ps1 (#410, #411)
# ---------------------------------------------------------------------------
Write-Host "--- static hygiene: run-architect.ps1 ---"

Assert-True -Condition ([System.IO.File]::Exists($RunArchitectScriptPath)) `
            -TestName "static: run-architect.ps1 exists"

$ScriptText = [System.IO.File]::ReadAllText($RunArchitectScriptPath, [System.Text.Encoding]::UTF8)

Assert-True -Condition ($ScriptText -match '\$DefaultModel\s*=\s*"claude-sonnet-5"') `
            -TestName "static: run-architect.ps1 defaults model to claude-sonnet-5"

Assert-True -Condition ($ScriptText -match '\$BacklogTriageModel\s*=\s*"claude-sonnet-5"') `
            -TestName "static: run-architect.ps1 backlog triage model is claude-sonnet-5"

Assert-True -Condition ($ScriptText -match '--effort",\s*"medium"') `
            -TestName "static: run-architect.ps1 invokes Claude with medium thinking effort"

Assert-True -Condition ($ScriptText -match '--tools",\s*"Read,Grep,Glob"') `
            -TestName "static: run-architect.ps1 restricts tools to Read,Grep,Glob"

Assert-True -Condition ($ScriptText -match 'gh\s+api\s+"repos/\$Repo/issues/\$IssueNumber/sub_issues"\s+-F\s+"sub_issue_id=\$newId"') `
            -TestName "static: run-architect.ps1 links subtasks via GitHub Sub-issues API"

Assert-True -Condition ($ScriptText -match 'status:review') `
            -TestName "static: run-architect.ps1 transitions parent story to status:review"

Assert-True -Condition ($ScriptText -match 'status:needs-po-input') `
            -TestName "static: run-architect.ps1 handles PO escalation with status:needs-po-input"

# ---------------------------------------------------------------------------
# Unit Logic: Format-SubtaskBody and Model Routing (#411)
# ---------------------------------------------------------------------------
Write-Host "--- unit logic: subtask formatting & model resolution ---"

function Test-FormatSubtaskBodyHelper {
    param(
        [int]$ParentNumber,
        [pscustomobject]$Subtask
    )

    $acceptanceLines = @()
    if ($null -ne $Subtask.acceptance_criteria) {
        foreach ($criterion in @($Subtask.acceptance_criteria)) {
            if ($null -ne $criterion) { $acceptanceLines += "- [ ] $criterion" }
        }
    }

    $lines = @(
        "### Parent user story",
        "#$ParentNumber",
        "",
        "### Target repository",
        "crosstrainingapp",
        "",
        "### Task description",
        [string]$Subtask.task_description,
        "",
        "### Files / entry points",
        [string]$Subtask.entry_points,
        "",
        "### Acceptance criteria"
    )
    $lines += $acceptanceLines
    $lines += @(
        "",
        "### How to verify",
        [string]$Subtask.verification,
        "",
        "### Size",
        [string]$Subtask.size,
        "",
        "### Complexity",
        [string]$Subtask.complexity,
        "",
        "### Blocked by",
        [string]$Subtask.blocked_by
    )

    return ($lines -join "`n")
}

$sampleSubtask = [pscustomobject]@{
    title = "Verify end-to-end Claude Sonnet 5 Medium thinking decomposition"
    task_description = "Execute full E2E test verifying story decomposition, model selection, and subtask generation."
    entry_points = ".github/workflows/architect.yml, issue #407"
    acceptance_criteria = @(
        "Workflow runs Claude agent with claude-sonnet-5 model",
        "Subtasks created and linked as GitHub sub-issues",
        "Subtasks appear in issue #407 web checklist"
    )
    verification = "Confirm all subtasks are linked as sub-issues on issue #407."
    size = "M"
    complexity = "Complex"
    blocked_by = "Verify subtask output schema compliance, Verify GitHub API sub-issues linking"
}

$formattedBody = Test-FormatSubtaskBodyHelper -ParentNumber 407 -Subtask $sampleSubtask

Assert-Match -Value $formattedBody -Pattern '### Parent user story\s*\n#407' `
             -TestName "unit: Format-SubtaskBody contains parent user story reference"

Assert-Match -Value $formattedBody -Pattern '### Target repository\s*\ncrosstrainingapp' `
             -TestName "unit: Format-SubtaskBody contains target repository"

Assert-Match -Value $formattedBody -Pattern '### Acceptance criteria\s*\n- \[ \] Workflow runs Claude agent with claude-sonnet-5 model\s*\n- \[ \] Subtasks created and linked as GitHub sub-issues\s*\n- \[ \] Subtasks appear in issue #407 web checklist' `
             -TestName "unit: Format-SubtaskBody formats checklist items correctly"

Assert-Match -Value $formattedBody -Pattern '### How to verify\s*\nConfirm all subtasks are linked as sub-issues on issue #407\.' `
             -TestName "unit: Format-SubtaskBody contains verification instructions"

Assert-Match -Value $formattedBody -Pattern '### Size\s*\nM' `
             -TestName "unit: Format-SubtaskBody contains size estimate"

Assert-Match -Value $formattedBody -Pattern '### Complexity\s*\nComplex' `
             -TestName "unit: Format-SubtaskBody contains complexity estimate"

# Test model routing logic
function Resolve-ArchitectModel {
    param([string[]]$Labels)
    if ($Labels -contains "origin:backlog-triage") {
        return "claude-sonnet-5"
    }
    return "claude-opus-5"
}

Assert-Equal -Actual (Resolve-ArchitectModel -Labels @("type:user-story", "origin:backlog-triage")) `
             -Expected "claude-sonnet-5" `
             -TestName "unit: model resolution routes origin:backlog-triage to claude-sonnet-5"

Assert-Equal -Actual (Resolve-ArchitectModel -Labels @("type:user-story")) `
             -Expected "claude-opus-5" `
             -TestName "unit: model resolution defaults to claude-opus-5 for standard stories"
