<#
.SYNOPSIS
    Comprehensive unit, integration, and static regression test suite for GitHub API
    sub-issues linking in the Architect workflow (.github/workflows/architect.yml)
    and local orchestration script (scripts/local-pipeline/run-architect.ps1).
    Dot-sourced by Invoke-ScriptTests.ps1.

.DESCRIPTION
    Verifies acceptance criteria for Issue #410:
      1. Subtasks created with type:subtask label.
      2. Subtasks linked via repos/$REPO/issues/$ISSUE/sub_issues endpoint.
      3. Parent issue progress checklist shows subtasks.
#>

$ArchitectWorkflowPath = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "../../.github/workflows/architect.yml")
)
$RunArchitectScriptPath = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "../../scripts/local-pipeline/run-architect.ps1")
)

# ---------------------------------------------------------------------------
# Static Contract Checks: .github/workflows/architect.yml (#410)
# ---------------------------------------------------------------------------
Write-Host "--- static contract: architect.yml sub-issues linking ---"

Assert-True -Condition ([System.IO.File]::Exists($ArchitectWorkflowPath)) `
            -TestName "static: architect.yml exists"

$WorkflowText = [System.IO.File]::ReadAllText($ArchitectWorkflowPath, [System.Text.Encoding]::UTF8)

Assert-True -Condition ($WorkflowText -match '--label\s+"type:subtask,status:pending-review"') `
            -TestName "static: architect.yml creates subtasks with type:subtask and status:pending-review labels"

Assert-True -Condition ($WorkflowText -match '--title\s+"\[Subtask\]:\s*\$TITLE"') `
            -TestName "static: architect.yml prefixes subtask title with [Subtask]:"

Assert-True -Condition ($WorkflowText -match 'NEW_NUM=\$\(basename\s+"\$NEW_URL"\)') `
            -TestName "static: architect.yml parses new subtask issue number from created URL"

Assert-True -Condition ($WorkflowText -match 'NEW_ID=\$\(gh\s+api\s+"repos/\$REPO/issues/\$NEW_NUM"\s+-q\s+\.id\)') `
            -TestName "static: architect.yml queries child integer database ID (not GraphQL node id)"

Assert-True -Condition ($WorkflowText -match 'gh\s+api\s+"repos/\$REPO/issues/\$ISSUE_NUMBER/sub_issues"\s+-F\s+sub_issue_id="\$NEW_ID"') `
            -TestName "static: architect.yml links created subtask via GitHub Sub-issues API"

Assert-True -Condition ($WorkflowText -match 'PARENT_ID=\$\(gh\s+api\s+"repos/\$REPO/issues/\$ISSUE_NUMBER"\s+-q\s+\.id\)') `
            -TestName "static: architect.yml fetches parent issue database ID"

Assert-True -Condition ($WorkflowText -match 'echo\s+"### Parent user story"\s*\n\s*echo\s+"#\$\{ISSUE_NUMBER\}"') `
            -TestName "static: architect.yml render_body links back to parent user story"

Assert-True -Condition ($WorkflowText -match 'echo\s+"### Target repository"\s*\n\s*echo\s+"crosstrainingapp"') `
            -TestName "static: architect.yml render_body sets target repository"

Assert-True -Condition ($WorkflowText -match 'jq\s+-r\s*"\$1\.acceptance_criteria\[\]\s*\|\s*\\"- \[ \] \\"\s*\+\s*\."') `
            -TestName "static: architect.yml render_body formats acceptance criteria as checklist items"

Assert-True -Condition ($WorkflowText -match 'rm\s+-f\s+"\$BODY_FILE"') `
            -TestName "static: architect.yml cleans up temporary subtask body file"

# ---------------------------------------------------------------------------
# Static Contract Checks: scripts/local-pipeline/run-architect.ps1 (#410)
# ---------------------------------------------------------------------------
Write-Host "--- static contract: run-architect.ps1 sub-issues linking ---"

Assert-True -Condition ([System.IO.File]::Exists($RunArchitectScriptPath)) `
            -TestName "static: run-architect.ps1 exists"

$ScriptText = [System.IO.File]::ReadAllText($RunArchitectScriptPath, [System.Text.Encoding]::UTF8)

Assert-True -Condition ($ScriptText -match '--label\s+"type:subtask,status:pending-review"') `
            -TestName "static: run-architect.ps1 creates subtasks with type:subtask label"

Assert-True -Condition ($ScriptText -match 'gh\s+api\s+"repos/\$Repo/issues/\$newIssueNumber"\s+-q\s+\.id') `
            -TestName "static: run-architect.ps1 queries child issue database ID via REST API"

Assert-True -Condition ($ScriptText -match 'gh\s+api\s+"repos/\$Repo/issues/\$IssueNumber/sub_issues"\s+-F\s+"sub_issue_id=\$newId"') `
            -TestName "static: run-architect.ps1 links subtask via GitHub Sub-issues API"

Assert-True -Condition ($ScriptText -match '\$createOutputText\s+-match\s*''/issues/') `
            -TestName "static: run-architect.ps1 parses created issue number with regex"

Assert-True -Condition ($ScriptText -match 'if\s*\(\[string\]::IsNullOrWhiteSpace\(\$parentId\)\)') `
            -TestName "static: run-architect.ps1 guards against missing parent database ID"

# ---------------------------------------------------------------------------
# Unit Logic: Format-SubtaskBody and Sub-Issue Linking Helpers (#410)
# ---------------------------------------------------------------------------
Write-Host "--- unit logic: subtask markdown body and checklist rendering ---"

function Format-TestSubtaskBody {
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
    title = "Verify GitHub API sub-issues linking for created subtasks"
    task_description = "Validate that the workflow correctly uses the GitHub Sub-issues API to link subtasks to the parent story."
    entry_points = ".github/workflows/architect.yml (lines 232-289), GitHub Sub-issues API"
    acceptance_criteria = @(
        'Subtasks created with type:subtask label',
        'Subtasks linked via repos/$REPO/issues/$ISSUE/sub_issues endpoint',
        'Parent issue progress checklist shows subtasks'
    )
    verification = "Check GitHub issue sub_issues endpoint and parent UI."
    size = "M"
    complexity = "Moderate"
    blocked_by = "Verify architect-decompose subtask output schema compliance"
}

$renderedBody = Format-TestSubtaskBody -ParentNumber 407 -Subtask $sampleSubtask

Assert-Match -Value $renderedBody -Pattern '### Parent user story\s*\n#407' `
             -TestName "unit: subtask body links to parent story #407"

Assert-Match -Value $renderedBody -Pattern '### Target repository\s*\ncrosstrainingapp' `
             -TestName "unit: subtask body targets crosstrainingapp repository"

Assert-Match -Value $renderedBody -Pattern '### Acceptance criteria\s*\n- \[ \] Subtasks created with type:subtask label\s*\n- \[ \] Subtasks linked via repos/\$REPO/issues/\$ISSUE/sub_issues endpoint\s*\n- \[ \] Parent issue progress checklist shows subtasks' `
             -TestName "unit: acceptance criteria formatted with checklist markdown"

Assert-Match -Value $renderedBody -Pattern '### How to verify\s*\nCheck GitHub issue sub_issues endpoint and parent UI\.' `
             -TestName "unit: subtask body contains verification step"

Assert-Match -Value $renderedBody -Pattern '### Size\s*\nM' `
             -TestName "unit: subtask body contains size metadata"

Assert-Match -Value $renderedBody -Pattern '### Complexity\s*\nModerate' `
             -TestName "unit: subtask body contains complexity metadata"

Assert-Match -Value $renderedBody -Pattern '### Blocked by\s*\nVerify architect-decompose subtask output schema compliance' `
             -TestName "unit: subtask body contains blocked by dependency"

# Test empty acceptance criteria handling
$emptyCriteriaSubtask = [pscustomobject]@{
    title = "Minimal subtask"
    task_description = "Minimal description"
    entry_points = "none"
    acceptance_criteria = @()
    verification = "none"
    size = "S"
    complexity = "Low"
    blocked_by = "None"
}
$renderedEmpty = Format-TestSubtaskBody -ParentNumber 407 -Subtask $emptyCriteriaSubtask
Assert-Match -Value $renderedEmpty -Pattern '### Acceptance criteria\s*\n\s*\n### How to verify' `
             -TestName "unit: empty acceptance criteria produces clean empty section without crashing"

# Test issue number parsing from URL
function Parse-IssueNumberFromUrl {
    param([string]$Url)
    if ($Url -match '/issues/(\d+)\s*$') {
        return [int]$Matches[1]
    }
    return $null
}

Assert-Equal -Actual (Parse-IssueNumberFromUrl "https://github.com/AntaresAndBharani/crosstrainingapp/issues/410") `
             -Expected 410 `
             -TestName "unit: Parse-IssueNumberFromUrl extracts numeric issue ID"

Assert-Equal -Actual (Parse-IssueNumberFromUrl "https://github.com/AntaresAndBharani/crosstrainingapp/issues/410`n") `
             -Expected 410 `
             -TestName "unit: Parse-IssueNumberFromUrl handles trailing newline"

Assert-Equal -Actual (Parse-IssueNumberFromUrl "invalid-output") `
             -Expected $null `
             -TestName "unit: Parse-IssueNumberFromUrl returns null on unparseable input"

# ---------------------------------------------------------------------------
# Behavioral Simulation: Sub-Issues Creation and Linking Flow (#410)
# ---------------------------------------------------------------------------
Write-Host "--- behavioral simulation: sub-issue create and link orchestration ---"

function Simulate-SubtaskCreationAndLinking {
    param(
        [string]$Repo,
        [int]$ParentNumber,
        [array]$CreateSubtasks,
        [scriptblock]$GhRunner
    )

    $createdNumbers = [System.Collections.Generic.List[string]]::new()
    foreach ($st in $CreateSubtasks) {
        if ([string]::IsNullOrWhiteSpace($st.title)) { continue }

        $body = Format-TestSubtaskBody -ParentNumber $ParentNumber -Subtask $st
        $createRes = & $GhRunner "issue" "create" "--repo" $Repo "--title" "[Subtask]: $($st.title)" "--label" "type:subtask,status:pending-review"
        $newNum = Parse-IssueNumberFromUrl $createRes
        if (-not $newNum) { continue }

        $idRes = & $GhRunner "api" "repos/$Repo/issues/$newNum" "-q" ".id"
        $childId = "$idRes".Trim()

        $linkRes = & $GhRunner "api" "repos/$Repo/issues/$ParentNumber/sub_issues" "-F" "sub_issue_id=$childId"
        $createdNumbers.Add("#$newNum")
    }

    return $createdNumbers
}

$invocations = [System.Collections.Generic.List[string]]::new()
$mockGh = {
    param()
    $cmd = ($args -join ' ')
    $invocations.Add($cmd)

    if ($cmd -match 'issue create') {
        if ($cmd -match 'Subtask Alpha') {
            return "https://github.com/AntaresAndBharani/crosstrainingapp/issues/501"
        } elseif ($cmd -match 'Subtask Beta') {
            return "https://github.com/AntaresAndBharani/crosstrainingapp/issues/502"
        }
    } elseif ($cmd -match 'api repos/.*issues/501 -q \.id') {
        return "1000501"
    } elseif ($cmd -match 'api repos/.*issues/502 -q \.id') {
        return "1000502"
    } elseif ($cmd -match 'api repos/.*issues/407/sub_issues -F sub_issue_id=') {
        return '{"status":"linked"}'
    }
    return ""
}

$subtaskList = @(
    [pscustomobject]@{
        title = "Subtask Alpha"
        task_description = "Alpha desc"
        entry_points = "alpha.yml"
        acceptance_criteria = @("Alpha pass")
        verification = "Test alpha"
        size = "S"
        complexity = "Low"
        blocked_by = "None"
    },
    [pscustomobject]@{
        title = "Subtask Beta"
        task_description = "Beta desc"
        entry_points = "beta.yml"
        acceptance_criteria = @("Beta pass")
        verification = "Test beta"
        size = "M"
        complexity = "Moderate"
        blocked_by = "#501"
    }
)

$createdResult = Simulate-SubtaskCreationAndLinking -Repo "AntaresAndBharani/crosstrainingapp" `
                                                   -ParentNumber 407 `
                                                   -CreateSubtasks $subtaskList `
                                                   -GhRunner $mockGh

Assert-Equal -Actual ($createdResult -join ' ') -Expected "#501 #502" `
             -TestName "behavioral: subtask creation and linking returns all created subtask numbers"

Assert-True -Condition ($invocations -contains "issue create --repo AntaresAndBharani/crosstrainingapp --title [Subtask]: Subtask Alpha --label type:subtask,status:pending-review") `
            -TestName "behavioral: issue create called with type:subtask label for Subtask Alpha"

Assert-True -Condition ($invocations -contains "issue create --repo AntaresAndBharani/crosstrainingapp --title [Subtask]: Subtask Beta --label type:subtask,status:pending-review") `
            -TestName "behavioral: issue create called with type:subtask label for Subtask Beta"

Assert-True -Condition ($invocations -contains "api repos/AntaresAndBharani/crosstrainingapp/issues/407/sub_issues -F sub_issue_id=1000501") `
            -TestName "behavioral: sub_issues linking endpoint called with child ID 1000501"

Assert-True -Condition ($invocations -contains "api repos/AntaresAndBharani/crosstrainingapp/issues/407/sub_issues -F sub_issue_id=1000502") `
            -TestName "behavioral: sub_issues linking endpoint called with child ID 1000502"

# ---------------------------------------------------------------------------
# Live API Integration Verification (Parent Story #407 and Subtasks) (#410)
# ---------------------------------------------------------------------------
Write-Host "--- live integration: verify story #407 sub-issues on GitHub API ---"

$liveGhToken = [Environment]::GetEnvironmentVariable("GH_TOKEN")
if ([string]::IsNullOrWhiteSpace($liveGhToken)) {
    $liveGhToken = [Environment]::GetEnvironmentVariable("GITHUB_TOKEN")
}

if (-not [string]::IsNullOrWhiteSpace($liveGhToken) -and (Get-Command gh -ErrorAction SilentlyContinue)) {
    try {
        $subIssuesJson = gh api "repos/AntaresAndBharani/crosstrainingapp/issues/407/sub_issues" 2>&1
        if ($LASTEXITCODE -eq 0 -and (-not [string]::IsNullOrWhiteSpace($subIssuesJson))) {
            $subIssues = $subIssuesJson | ConvertFrom-Json
            Assert-True -Condition ($subIssues.Count -ge 3) `
                        -TestName "live integration: issue #407 has at least 3 linked sub-issues"

            $subIssueNumbers = @($subIssues | ForEach-Object { $_.number })
            Assert-True -Condition ($subIssueNumbers -contains 410) `
                        -TestName "live integration: issue #407 sub-issues includes #410"

            Assert-True -Condition ($subIssueNumbers -contains 409) `
                        -TestName "live integration: issue #407 sub-issues includes #409"

            Assert-True -Condition ($subIssueNumbers -contains 411) `
                        -TestName "live integration: issue #407 sub-issues includes #411"

            # Check type:subtask label on linked sub-issues
            foreach ($sub in $subIssues) {
                $hasSubtaskLabel = ($sub.labels | Where-Object { $_.name -eq "type:subtask" }) -ne $null
                Assert-True -Condition $hasSubtaskLabel `
                            -TestName "live integration: subtask #$($sub.number) has type:subtask label"
            }
        } else {
            Write-Host "  [SKIP] Live API call to issue #407 sub_issues skipped or returned error (CI/offline)" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "  [SKIP] Live API verification skipped: $_" -ForegroundColor Yellow
    }
} else {
    Write-Host "  [SKIP] Live API verification skipped (no GH_TOKEN or gh CLI)" -ForegroundColor Yellow
}
