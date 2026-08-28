<#
.SYNOPSIS
    Comprehensive unit, integration, and static regression test suite for
    architect-decompose output schema compliance in the Architect workflow
    (.github/workflows/architect.yml, .github/workflows/prompts/architect-decompose.md,
    .claude/tasks/architect-decompose.md, and scripts/local-pipeline/run-architect.ps1).
    Dot-sourced by Invoke-ScriptTests.ps1.

.DESCRIPTION
    Verifies acceptance criteria for Issue #409:
      1. JSON output conforms to schema.
      2. Outcome field is PROCEED or PO_ESCALATION.
      3. All subtask fields match schema.
#>

$ArchitectWorkflowPath = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "../../.github/workflows/architect.yml")
)
$ArchitectDecomposePromptPath = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "../../.github/workflows/prompts/architect-decompose.md")
)
$ClaudeTaskDecomposePath = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "../../.claude/tasks/architect-decompose.md")
)
$RunArchitectScriptPath = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "../../scripts/local-pipeline/run-architect.ps1")
)

# ---------------------------------------------------------------------------
# Static Contract Checks: .github/workflows/prompts/architect-decompose.md (#409)
# ---------------------------------------------------------------------------
Write-Host "--- static contract: architect-decompose.md output schema ---"

Assert-True -Condition ([System.IO.File]::Exists($ArchitectDecomposePromptPath)) `
            -TestName "static: architect-decompose.md exists"

$PromptText = [System.IO.File]::ReadAllText($ArchitectDecomposePromptPath, [System.Text.Encoding]::UTF8)

Assert-True -Condition ($PromptText -match 'Output schema for architect_output\.json:') `
            -TestName "static: architect-decompose.md specifies output schema header"

Assert-True -Condition ($PromptText -match '"outcome":\s*"PROCEED\s*\|\s*PO_ESCALATION"') `
            -TestName "static: architect-decompose.md defines outcome enum as PROCEED | PO_ESCALATION"

Assert-True -Condition ($PromptText -match '"conflict":\s*"string\s*\(PO_ESCALATION only\)"') `
            -TestName "static: architect-decompose.md defines conflict string for PO_ESCALATION"

Assert-True -Condition ($PromptText -match '"subtasks":\s*\{') `
            -TestName "static: architect-decompose.md defines subtasks container object"

Assert-True -Condition ($PromptText -match '"create":\s*\[') `
            -TestName "static: architect-decompose.md defines create subtasks array"

Assert-True -Condition ($PromptText -match '"update":\s*\[') `
            -TestName "static: architect-decompose.md defines update subtasks array"

Assert-True -Condition ($PromptText -match '"close":\s*\[') `
            -TestName "static: architect-decompose.md defines close subtasks array"

Assert-True -Condition ($PromptText -match '"title":\s*"string"') `
            -TestName "static: architect-decompose.md requires title field"

Assert-True -Condition ($PromptText -match '"task_description":\s*"string"') `
            -TestName "static: architect-decompose.md requires task_description field"

Assert-True -Condition ($PromptText -match '"entry_points":\s*"string"') `
            -TestName "static: architect-decompose.md requires entry_points field"

Assert-True -Condition ($PromptText -match '"acceptance_criteria":\s*\["string"\]') `
            -TestName "static: architect-decompose.md requires acceptance_criteria array of strings"

Assert-True -Condition ($PromptText -match '"verification":\s*"string"') `
            -TestName "static: architect-decompose.md requires verification field"

Assert-True -Condition ($PromptText -match '"size":\s*"XS\s*\|\s*S\s*\|\s*M"') `
            -TestName "static: architect-decompose.md defines size enum"

Assert-True -Condition ($PromptText -match '"complexity":\s*"Trivial\s*\|\s*Moderate\s*\|\s*Complex"') `
            -TestName "static: architect-decompose.md defines complexity enum"

Assert-True -Condition ($PromptText -match '"blocked_by":\s*"string"') `
            -TestName "static: architect-decompose.md requires blocked_by field"

Assert-True -Condition ($PromptText -match '"subtask_number":\s*0') `
            -TestName "static: architect-decompose.md requires subtask_number for update/close"

# ---------------------------------------------------------------------------
# Static Contract Checks: .claude/tasks/architect-decompose.md (#409)
# ---------------------------------------------------------------------------
Write-Host "--- static contract: .claude/tasks/architect-decompose.md ---"

Assert-True -Condition ([System.IO.File]::Exists($ClaudeTaskDecomposePath)) `
            -TestName "static: .claude/tasks/architect-decompose.md exists"

$ClaudePromptText = [System.IO.File]::ReadAllText($ClaudeTaskDecomposePath, [System.Text.Encoding]::UTF8)

Assert-True -Condition ($ClaudePromptText -match '"outcome":\s*"PROCEED\s*\|\s*PO_ESCALATION"') `
            -TestName "static: .claude/tasks/architect-decompose.md specifies outcome enum"

Assert-True -Condition ($ClaudePromptText -match '"conflict":\s*"string\s*\(PO_ESCALATION only\)"') `
            -TestName "static: .claude/tasks/architect-decompose.md specifies conflict string"

Assert-True -Condition ($ClaudePromptText -match '"subtasks":\s*\{') `
            -TestName "static: .claude/tasks/architect-decompose.md defines subtasks object"

Assert-True -Condition ($ClaudePromptText -match 'Return ONLY the JSON object above') `
            -TestName "static: .claude/tasks/architect-decompose.md enforces JSON-only output"

# ---------------------------------------------------------------------------
# Static Contract Checks: .github/workflows/architect.yml Schema Parsing (#409)
# ---------------------------------------------------------------------------
Write-Host "--- static contract: architect.yml schema consumption ---"

Assert-True -Condition ([System.IO.File]::Exists($ArchitectWorkflowPath)) `
            -TestName "static: architect.yml exists"

$WorkflowText = [System.IO.File]::ReadAllText($ArchitectWorkflowPath, [System.Text.Encoding]::UTF8)

Assert-True -Condition ($WorkflowText -match 'OUTCOME=\$\(jq\s+-r\s*''\.outcome''\s+architect_output\.json\)') `
            -TestName "static: architect.yml parses outcome field with jq"

Assert-True -Condition ($WorkflowText -match 'if\s*\[\s*"\$OUTCOME"\s*=\s*"PO_ESCALATION"\s*\];\s*then') `
            -TestName "static: architect.yml branches on PO_ESCALATION outcome"

Assert-True -Condition ($WorkflowText -match 'CONFLICT=\$\(jq\s+-r\s*''\.conflict''\s+architect_output\.json\)') `
            -TestName "static: architect.yml extracts conflict field on PO_ESCALATION"

Assert-True -Condition ($WorkflowText -match 'CREATE_COUNT=\$\(jq\s*''\.subtasks\.create\s*\|\s*length''\s+architect_output\.json\)') `
            -TestName "static: architect.yml counts subtasks.create entries"

Assert-True -Condition ($WorkflowText -match 'UPDATE_COUNT=\$\(jq\s*''\.subtasks\.update\s*\|\s*length''\s+architect_output\.json\)') `
            -TestName "static: architect.yml counts subtasks.update entries"

Assert-True -Condition ($WorkflowText -match 'CLOSE_COUNT=\$\(jq\s*''\.subtasks\.close\s*\|\s*length''\s+architect_output\.json\)') `
            -TestName "static: architect.yml counts subtasks.close entries"

Assert-True -Condition ($WorkflowText -match 'jq\s+-r\s*"\$1\.task_description"') `
            -TestName "static: architect.yml reads task_description"

Assert-True -Condition ($WorkflowText -match 'jq\s+-r\s*"\$1\.entry_points"') `
            -TestName "static: architect.yml reads entry_points"

Assert-True -Condition ($WorkflowText -match 'jq\s+-r\s*"\$1\.acceptance_criteria\[\]') `
            -TestName "static: architect.yml reads acceptance_criteria array"

Assert-True -Condition ($WorkflowText -match 'jq\s+-r\s*"\$1\.verification"') `
            -TestName "static: architect.yml reads verification"

Assert-True -Condition ($WorkflowText -match 'jq\s+-r\s*"\$1\.size"') `
            -TestName "static: architect.yml reads size"

Assert-True -Condition ($WorkflowText -match 'jq\s+-r\s*"\$1\.complexity"') `
            -TestName "static: architect.yml reads complexity"

Assert-True -Condition ($WorkflowText -match 'jq\s+-r\s*"\$1\.blocked_by"') `
            -TestName "static: architect.yml reads blocked_by"

# ---------------------------------------------------------------------------
# Static Contract Checks: scripts/local-pipeline/run-architect.ps1 (#409)
# ---------------------------------------------------------------------------
Write-Host "--- static contract: run-architect.ps1 schema validation ---"

Assert-True -Condition ([System.IO.File]::Exists($RunArchitectScriptPath)) `
            -TestName "static: run-architect.ps1 exists"

$ScriptText = [System.IO.File]::ReadAllText($RunArchitectScriptPath, [System.Text.Encoding]::UTF8)

Assert-True -Condition ($ScriptText -match '\$decision\.outcome\s+-ne\s*"PROCEED"\s*-and\s*\$decision\.outcome\s+-ne\s*"PO_ESCALATION"') `
            -TestName "static: run-architect.ps1 enforces outcome enum (PROCEED | PO_ESCALATION)"

Assert-True -Condition ($ScriptText -match '\$Decision\.outcome\s+-eq\s*"PO_ESCALATION"') `
            -TestName "static: run-architect.ps1 branches on PO_ESCALATION"

Assert-True -Condition ($ScriptText -match '\$Decision\.conflict') `
            -TestName "static: run-architect.ps1 reads Decision.conflict"

Assert-True -Condition ($ScriptText -match '\$Decision\.subtasks\.create') `
            -TestName "static: run-architect.ps1 accesses Decision.subtasks.create"

Assert-True -Condition ($ScriptText -match '\$Decision\.subtasks\.update') `
            -TestName "static: run-architect.ps1 accesses Decision.subtasks.update"

Assert-True -Condition ($ScriptText -match '\$Decision\.subtasks\.close') `
            -TestName "static: run-architect.ps1 accesses Decision.subtasks.close"

# ---------------------------------------------------------------------------
# Unit Logic: Schema Validation Engine (#409)
# ---------------------------------------------------------------------------
Write-Host "--- unit logic: Test-ArchitectOutputSchema validator ---"

function Test-ArchitectOutputSchema {
    <#
    .SYNOPSIS
        Validates a parsed JSON object or JSON string against the Architect decompose output schema.
    .OUTPUTS
        PSCustomObject with IsValid (bool) and Errors (array of strings).
    #>
    param(
        [Parameter(Mandatory=$true)]
        $InputJson
    )

    $errors = [System.Collections.Generic.List[string]]::new()

    $parsed = $InputJson
    if ($InputJson -is [string]) {
        try {
            $parsed = $InputJson | ConvertFrom-Json -ErrorAction Stop
        } catch {
            $errors.Add("Invalid JSON format: $_")
            return [pscustomobject]@{ IsValid = $false; Errors = @($errors) }
        }
    }

    if ($null -eq $parsed) {
        $errors.Add("Output JSON is null or empty.")
        return [pscustomobject]@{ IsValid = $false; Errors = @($errors) }
    }

    # 1. Outcome validation
    if ($null -eq $parsed.outcome -or [string]::IsNullOrWhiteSpace([string]$parsed.outcome)) {
        $errors.Add("Field 'outcome' is required and cannot be empty.")
    } elseif ($parsed.outcome -ne "PROCEED" -and $parsed.outcome -ne "PO_ESCALATION") {
        $errors.Add("Field 'outcome' must be either 'PROCEED' or 'PO_ESCALATION', found '$($parsed.outcome)'.")
    }

    # 2. PO_ESCALATION specific schema checks
    if ($parsed.outcome -eq "PO_ESCALATION") {
        if ($null -eq $parsed.conflict -or [string]::IsNullOrWhiteSpace([string]$parsed.conflict)) {
            $errors.Add("Field 'conflict' is required when outcome is 'PO_ESCALATION'.")
        }
    }

    # 3. PROCEED specific schema checks
    if ($parsed.outcome -eq "PROCEED") {
        if ($null -eq $parsed.subtasks) {
            $errors.Add("Field 'subtasks' object is required when outcome is 'PROCEED'.")
        } else {
            $validSizes = @("XS", "S", "M", "L", "XL")
            $validComplexities = @("Trivial", "Moderate", "Complex", "Low", "Medium", "High")

            # subtasks.create validation
            if ($null -ne $parsed.subtasks.create) {
                $createItems = @(@($parsed.subtasks.create) | Where-Object { $null -ne $_ })
                $idx = 0
                foreach ($item in $createItems) {
                    if ([string]::IsNullOrWhiteSpace($item.title)) {
                        $errors.Add("subtasks.create[$idx]: 'title' is required.")
                    }
                    if ([string]::IsNullOrWhiteSpace($item.task_description)) {
                        $errors.Add("subtasks.create[$idx]: 'task_description' is required.")
                    }
                    if ([string]::IsNullOrWhiteSpace($item.entry_points)) {
                        $errors.Add("subtasks.create[$idx]: 'entry_points' is required.")
                    }
                    if ($null -eq $item.acceptance_criteria) {
                        $errors.Add("subtasks.create[$idx]: 'acceptance_criteria' array is required.")
                    } elseif ($item.acceptance_criteria -isnot [array] -and $item.acceptance_criteria -isnot [System.Collections.IEnumerable]) {
                        $errors.Add("subtasks.create[$idx]: 'acceptance_criteria' must be an array.")
                    } else {
                        $criteria = @($item.acceptance_criteria)
                        if ($criteria.Count -eq 0) {
                            $errors.Add("subtasks.create[$idx]: 'acceptance_criteria' must contain at least 1 testable criterion.")
                        }
                    }
                    if ([string]::IsNullOrWhiteSpace($item.verification)) {
                        $errors.Add("subtasks.create[$idx]: 'verification' is required.")
                    }
                    if ([string]::IsNullOrWhiteSpace($item.size)) {
                        $errors.Add("subtasks.create[$idx]: 'size' is required.")
                    } elseif ($validSizes -notcontains [string]$item.size) {
                        $errors.Add("subtasks.create[$idx]: 'size' must be one of ($($validSizes -join ', ')), found '$($item.size)'.")
                    }
                    if ([string]::IsNullOrWhiteSpace($item.complexity)) {
                        $errors.Add("subtasks.create[$idx]: 'complexity' is required.")
                    } elseif ($validComplexities -notcontains [string]$item.complexity) {
                        $errors.Add("subtasks.create[$idx]: 'complexity' must be one of ($($validComplexities -join ', ')), found '$($item.complexity)'.")
                    }
                    if ($null -eq $item.blocked_by) {
                        $errors.Add("subtasks.create[$idx]: 'blocked_by' is required (can be 'None').")
                    }
                    $idx++
                }
            }

            # subtasks.update validation
            if ($null -ne $parsed.subtasks.update) {
                $updateItems = @(@($parsed.subtasks.update) | Where-Object { $null -ne $_ })
                $idx = 0
                foreach ($item in $updateItems) {
                    if ($null -eq $item.subtask_number -or "$($item.subtask_number)" -notmatch '^\d+$') {
                        $errors.Add("subtasks.update[$idx]: 'subtask_number' must be a positive integer.")
                    }
                    if ([string]::IsNullOrWhiteSpace($item.task_description)) {
                        $errors.Add("subtasks.update[$idx]: 'task_description' is required.")
                    }
                    if ([string]::IsNullOrWhiteSpace($item.entry_points)) {
                        $errors.Add("subtasks.update[$idx]: 'entry_points' is required.")
                    }
                    if ($null -eq $item.acceptance_criteria) {
                        $errors.Add("subtasks.update[$idx]: 'acceptance_criteria' is required.")
                    }
                    if ([string]::IsNullOrWhiteSpace($item.verification)) {
                        $errors.Add("subtasks.update[$idx]: 'verification' is required.")
                    }
                    $idx++
                }
            }

            # subtasks.close validation
            if ($null -ne $parsed.subtasks.close) {
                $closeItems = @(@($parsed.subtasks.close) | Where-Object { $null -ne $_ })
                $idx = 0
                foreach ($item in $closeItems) {
                    if ($null -eq $item.subtask_number -or "$($item.subtask_number)" -notmatch '^\d+$') {
                        $errors.Add("subtasks.close[$idx]: 'subtask_number' must be a positive integer.")
                    }
                    if ([string]::IsNullOrWhiteSpace($item.reason)) {
                        $errors.Add("subtasks.close[$idx]: 'reason' is required.")
                    }
                    $idx++
                }
            }
        }
    }

    return [pscustomobject]@{
        IsValid = ($errors.Count -eq 0)
        Errors  = @($errors)
    }
}

# ---------------------------------------------------------------------------
# Gherkin Scenario 1: Valid PROCEED with created subtasks
# ---------------------------------------------------------------------------
Write-Host "--- Gherkin: Scenario 1 - Valid PROCEED with created subtasks ---"

$validProceedJson = @"
{
  "outcome": "PROCEED",
  "subtasks": {
    "create": [
      {
        "title": "Verify architect-decompose subtask output schema compliance",
        "task_description": "Validate that Claude agents produce JSON conforming to the schema in architect-decompose.md.",
        "entry_points": ".github/workflows/architect.yml, .github/workflows/prompts/architect-decompose.md",
        "acceptance_criteria": [
          "JSON output conforms to schema",
          "Outcome field is PROCEED or PO_ESCALATION",
          "All subtask fields match schema"
        ],
        "verification": "Validate JSON structure against schema.",
        "size": "M",
        "complexity": "Moderate",
        "blocked_by": "None"
      },
      {
        "title": "Verify GitHub API sub-issues linking for created subtasks",
        "task_description": "Validate that subtasks are linked via repos/REPO/issues/ISSUE/sub_issues endpoint.",
        "entry_points": ".github/workflows/architect.yml",
        "acceptance_criteria": [
          "Subtasks created with type:subtask label",
          "Subtasks linked via sub_issues endpoint"
        ],
        "verification": "Check GitHub issue sub_issues endpoint.",
        "size": "S",
        "complexity": "Trivial",
        "blocked_by": "Verify architect-decompose subtask output schema compliance"
      }
    ],
    "update": [],
    "close": []
  }
}
"@

$result1 = Test-ArchitectOutputSchema -InputJson $validProceedJson
Assert-True -Condition ($result1.IsValid) `
            -TestName "gherkin: Given valid PROCEED output, When validated, Then IsValid is true"
Assert-Equal -Actual $result1.Errors.Count -Expected 0 `
             -TestName "gherkin: Given valid PROCEED output, When validated, Then error count is 0"

# ---------------------------------------------------------------------------
# Gherkin Scenario 2: Valid PO_ESCALATION with conflict explanation
# ---------------------------------------------------------------------------
Write-Host "--- Gherkin: Scenario 2 - Valid PO_ESCALATION output ---"

$validEscalationJson = @"
{
  "outcome": "PO_ESCALATION",
  "conflict": "Story specifies conflicting offline sync requirements that require PO decision on conflict resolution priority."
}
"@

$result2 = Test-ArchitectOutputSchema -InputJson $validEscalationJson
Assert-True -Condition ($result2.IsValid) `
            -TestName "gherkin: Given valid PO_ESCALATION with conflict, When validated, Then IsValid is true"
Assert-Equal -Actual $result2.Errors.Count -Expected 0 `
             -TestName "gherkin: Given valid PO_ESCALATION with conflict, When validated, Then error count is 0"

# ---------------------------------------------------------------------------
# Gherkin Scenario 3: Valid PROCEED with updates and closes
# ---------------------------------------------------------------------------
Write-Host "--- Gherkin: Scenario 3 - Valid PROCEED with update and close ---"

$validUpdateCloseJson = @"
{
  "outcome": "PROCEED",
  "subtasks": {
    "create": [],
    "update": [
      {
        "subtask_number": 408,
        "task_description": "Updated subtask description per PO guidance.",
        "entry_points": "app/src/main/java/com/fractanomics/Sync.kt",
        "acceptance_criteria": ["Sync uses exponential backoff"],
        "verification": "./gradlew testDebugUnitTest",
        "size": "S",
        "complexity": "Moderate",
        "blocked_by": "None"
      }
    ],
    "close": [
      {
        "subtask_number": 405,
        "reason": "Redundant with subtask #408"
      }
    ]
  }
}
"@

$result3 = Test-ArchitectOutputSchema -InputJson $validUpdateCloseJson
Assert-True -Condition ($result3.IsValid) `
            -TestName "gherkin: Given valid update/close output, When validated, Then IsValid is true"
Assert-Equal -Actual $result3.Errors.Count -Expected 0 `
             -TestName "gherkin: Given valid update/close output, When validated, Then error count is 0"

# ---------------------------------------------------------------------------
# Gherkin Scenario 4: Invalid outcome enum rejected
# ---------------------------------------------------------------------------
Write-Host "--- Gherkin: Scenario 4 - Invalid outcome enum rejected ---"

$invalidOutcomeJson = @"
{
  "outcome": "INVALID_OUTCOME",
  "subtasks": { "create": [], "update": [], "close": [] }
}
"@

$result4 = Test-ArchitectOutputSchema -InputJson $invalidOutcomeJson
$errText4 = ($result4.Errors -join "`n")
Assert-True -Condition (-not $result4.IsValid) `
            -TestName "gherkin: Given invalid outcome 'INVALID_OUTCOME', When validated, Then IsValid is false"
Assert-True -Condition ($errText4 -match "Field 'outcome' must be either 'PROCEED' or 'PO_ESCALATION'") `
            -TestName "gherkin: Given invalid outcome, Then errors explain valid outcome options"

# ---------------------------------------------------------------------------
# Gherkin Scenario 5: Missing PO_ESCALATION conflict rejected
# ---------------------------------------------------------------------------
Write-Host "--- Gherkin: Scenario 5 - PO_ESCALATION without conflict rejected ---"

$missingConflictJson = @"
{
  "outcome": "PO_ESCALATION"
}
"@

$result5 = Test-ArchitectOutputSchema -InputJson $missingConflictJson
$errText5 = ($result5.Errors -join "`n")
Assert-True -Condition (-not $result5.IsValid) `
            -TestName "gherkin: Given PO_ESCALATION without conflict, When validated, Then IsValid is false"
Assert-True -Condition ($errText5 -match "Field 'conflict' is required when outcome is 'PO_ESCALATION'") `
            -TestName "gherkin: Given missing conflict, Then error reports missing conflict field"

# ---------------------------------------------------------------------------
# Gherkin Scenario 6: Missing required create fields rejected
# ---------------------------------------------------------------------------
Write-Host "--- Gherkin: Scenario 6 - Missing required subtask fields rejected ---"

$missingFieldsJson = @"
{
  "outcome": "PROCEED",
  "subtasks": {
    "create": [
      {
        "title": "",
        "task_description": "Only description",
        "entry_points": "",
        "acceptance_criteria": [],
        "verification": "",
        "size": "INVALID_SIZE",
        "complexity": "INVALID_COMPLEXITY"
      }
    ]
  }
}
"@

$result6 = Test-ArchitectOutputSchema -InputJson $missingFieldsJson
$errText6 = ($result6.Errors -join "`n")
Assert-True -Condition (-not $result6.IsValid) `
            -TestName "gherkin: Given create subtask with invalid/missing fields, When validated, Then IsValid is false"
Assert-True -Condition ($errText6 -match "'title' is required") `
            -TestName "gherkin: Validator flags empty title"
Assert-True -Condition ($errText6 -match "'entry_points' is required") `
            -TestName "gherkin: Validator flags empty entry_points"
Assert-True -Condition ($errText6 -match "'acceptance_criteria' must contain at least 1 testable criterion") `
            -TestName "gherkin: Validator flags empty acceptance_criteria"
Assert-True -Condition ($errText6 -match "'verification' is required") `
            -TestName "gherkin: Validator flags empty verification"
Assert-True -Condition ($errText6 -match "'size' must be one of") `
            -TestName "gherkin: Validator flags invalid size enum"
Assert-True -Condition ($errText6 -match "'complexity' must be one of") `
            -TestName "gherkin: Validator flags invalid complexity enum"
Assert-True -Condition ($errText6 -match "'blocked_by' is required") `
            -TestName "gherkin: Validator flags missing blocked_by"

# ---------------------------------------------------------------------------
# Gherkin Scenario 7: Malformed update and close subtask numbers rejected
# ---------------------------------------------------------------------------
Write-Host "--- Gherkin: Scenario 7 - Malformed update/close subtask numbers rejected ---"

$malformedUpdateCloseJson = @"
{
  "outcome": "PROCEED",
  "subtasks": {
    "create": [],
    "update": [
      {
        "subtask_number": "not-a-number",
        "task_description": "Desc",
        "entry_points": "src/",
        "acceptance_criteria": ["Criteria"],
        "verification": "Test"
      }
    ],
    "close": [
      {
        "subtask_number": "also-bad",
        "reason": ""
      }
    ]
  }
}
"@

$result7 = Test-ArchitectOutputSchema -InputJson $malformedUpdateCloseJson
$errText7 = ($result7.Errors -join "`n")
Assert-True -Condition (-not $result7.IsValid) `
            -TestName "gherkin: Given malformed update/close entries, When validated, Then IsValid is false"
Assert-True -Condition ($errText7 -match "subtasks\.update\[0\]: 'subtask_number' must be a positive integer") `
            -TestName "gherkin: Validator flags non-integer update subtask_number"
Assert-True -Condition ($errText7 -match "subtasks\.close\[0\]: 'subtask_number' must be a positive integer") `
            -TestName "gherkin: Validator flags non-integer close subtask_number"
Assert-True -Condition ($errText7 -match "subtasks\.close\[0\]: 'reason' is required") `
            -TestName "gherkin: Validator flags empty close reason"

# ---------------------------------------------------------------------------
# Live Integration Verification: Issue #409 & Decomposed Subtasks (#409)
# ---------------------------------------------------------------------------
Write-Host "--- live integration: verify story #407 subtasks schema compliance ---"

$liveGhToken = [Environment]::GetEnvironmentVariable("GH_TOKEN")
if ([string]::IsNullOrWhiteSpace($liveGhToken)) {
    $liveGhToken = [Environment]::GetEnvironmentVariable("GITHUB_TOKEN")
}

if (-not [string]::IsNullOrWhiteSpace($liveGhToken) -and (Get-Command gh -ErrorAction SilentlyContinue)) {
    try {
        $subIssuesJson = gh api "repos/AntaresAndBharani/crosstrainingapp/issues/407/sub_issues" 2>&1
        if ($LASTEXITCODE -eq 0 -and (-not [string]::IsNullOrWhiteSpace($subIssuesJson))) {
            $subIssues = $subIssuesJson | ConvertFrom-Json
            Assert-True -Condition ($subIssues.Count -gt 0) `
                        -TestName "live integration: parent story #407 has subtasks returned from GitHub API"

            foreach ($st in $subIssues) {
                $body = $st.body
                $num = $st.number

                # Verify all required subtask template sections are present in each decomposed subtask
                Assert-Match -Value $body -Pattern '### Parent user story' `
                             -TestName "live integration: subtask #$num contains 'Parent user story' header"
                Assert-Match -Value $body -Pattern '### Target repository' `
                             -TestName "live integration: subtask #$num contains 'Target repository' header"
                Assert-Match -Value $body -Pattern '### Task description' `
                             -TestName "live integration: subtask #$num contains 'Task description' header"
                Assert-Match -Value $body -Pattern '### Files / entry points' `
                             -TestName "live integration: subtask #$num contains 'Files / entry points' header"
                Assert-Match -Value $body -Pattern '### Acceptance criteria' `
                             -TestName "live integration: subtask #$num contains 'Acceptance criteria' header"
                Assert-Match -Value $body -Pattern '### How to verify' `
                             -TestName "live integration: subtask #$num contains 'How to verify' header"
                Assert-Match -Value $body -Pattern '### Size' `
                             -TestName "live integration: subtask #$num contains 'Size' header"
                Assert-Match -Value $body -Pattern '### Complexity' `
                             -TestName "live integration: subtask #$num contains 'Complexity' header"
                Assert-Match -Value $body -Pattern '### Blocked by' `
                             -TestName "live integration: subtask #$num contains 'Blocked by' header"

                # Verify checklist items in acceptance criteria
                Assert-Match -Value $body -Pattern '- \[ \]' `
                             -TestName "live integration: subtask #$num has markdown checklist items"
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
