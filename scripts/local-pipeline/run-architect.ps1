<#
.SYNOPSIS
    Local Windows Task Scheduler replacement for the GitHub Actions
    "Architect" workflow -- Fetch -> Gate -> Judge -> Act, judgment-only
    LLM call.

.DESCRIPTION
    Design: ws-setups/graph-engineering/docs/definition-node.md

    Runs the same batch decomposition procedure as
    `.github/workflows/architect.yml` /
    `.github/workflows/prompts/architect-*.md`, but splits it so the LLM
    is only ever asked to do the one thing that genuinely needs judgment
    (decomposing/restructuring/clarifying a story's subtask set), while
    every deterministic step (listing stories, reading their context and
    existing subtasks, creating/updating/closing subtask issues, posting
    comments, labeling, syncing the checkout) runs as plain PowerShell/gh:

      1. Fetch  - one `gh issue list` call per trigger label (never
                  combined -- `gh issue list --label a,b,c` is AND
                  semantics, not OR, so each of the three status labels
                  needs its own call to find issues matching ANY of
                  them), filtered to only `type:user-story` issues (same
                  IS_STORY check architect.yml's `Determine mode` step
                  does). For each qualifying story, `gh issue view` for
                  full context and `gh api .../sub_issues` (+ per-number
                  `gh issue view`) for its existing subtasks.
      2. Gate   - if no open issue anywhere matches any of the three
                  trigger labels (or none of the matches are actually
                  `type:user-story` issues), exit 0 without ever invoking
                  claude.exe. A poll with nothing to do must cost zero LLM
                  tokens. Otherwise every qualifying story found in this
                  one poll is processed, one at a time in a loop -- unlike
                  Dev & Test there is no shared working-tree state between
                  stories, so processing all of them in one poll is safe.
      3. Judge  - one short `claude.exe --print` call per qualifying
                  story, using the judgment-only prompt template that
                  matches its mode (`.claude/tasks/architect-decompose.md`,
                  `architect-restructure.md`, or
                  `architect-answer-clarifications.md`). No tool/bash
                  access -- the model only ever sees the issue/subtasks
                  text embedded in the prompt and returns a JSON decision.
      4. Act    - apply the decision exactly as architect.yml's "Apply
                  Architect's decision" step does: on PO_ESCALATION, swap
                  the trigger label for `status:needs-po-input` and post
                  the conflict as a comment; on PROCEED, create/update/
                  close subtask issues (linking new ones to the parent via
                  the real GitHub Sub-issues API), post a summary comment,
                  and swap the trigger label for `status:review`.

    Mode/model are re-derived from each story's CURRENT labels on every
    poll (never cached) -- exactly one of the three trigger labels
    determines mode (decompose / restructure / answer_clarifications), and
    the `origin:backlog-triage` label (which persists across re-entries)
    determines whether the judge call uses claude-sonnet-5 instead of the
    claude-opus-5 default.

.EXAMPLE
    .\scripts\local-pipeline\run-architect.ps1
#>
param(
    [string]$Repo = "AntaresAndBharani/crosstrainingapp",
    [string]$ClaudePath = "C:\Users\rogal\.local\bin\claude.exe",
    [string]$DefaultModel = "claude-opus-5",
    [string]$BacklogTriageModel = "claude-sonnet-5",
    [string]$PromptTemplateDir = (Join-Path $PSScriptRoot "..\..\.claude\tasks")
)

$ErrorActionPreference = "Stop"

$TriggerLabels = @("status:ready-for-architect", "status:needs-revision", "status:needs-clarification")
$ModeByTriggerLabel = @{
    "status:ready-for-architect" = "decompose"
    "status:needs-revision"      = "restructure"
    "status:needs-clarification" = "answer_clarifications"
}
$PromptFileByMode = @{
    "decompose"             = "architect-decompose.md"
    "restructure"           = "architect-restructure.md"
    "answer_clarifications" = "architect-answer-clarifications.md"
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$LogDir = Join-Path $RepoRoot "logs\local-pipeline"
if (-not (Test-Path -LiteralPath $LogDir)) {
    New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
}
$LogFile = Join-Path $LogDir ("architect-{0}.log" -f (Get-Date -Format "yyyy-MM-dd"))

function Write-Log {
    param(
        [string]$Message,
        [string]$Level = "INFO"
    )
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $line = "[$timestamp] [$Level] $Message"
    Write-Host $line
    Add-Content -LiteralPath $LogFile -Value $line -Encoding utf8
}

function ConvertTo-EscapedArgument {
    # Standard Win32/CommandLineToArgvW argument-quoting algorithm. Needed
    # because PowerShell 5.1's own native-command argument marshaling
    # (`& $exe --print $largeString`) mangles arguments containing embedded
    # double quotes / backslash sequences -- both routine in real issue
    # body/comment text (paths, inline-code spans). ProcessStartInfo.
    # ArgumentList isn't available on this system's .NET Framework, so
    # build the pre-quoted command line by hand instead of relying on
    # either.
    param([string]$Arg)
    if ($Arg -eq "") { return '""' }
    if ($Arg -notmatch '[\s"]') { return $Arg }
    $result = '"'
    $backslashes = 0
    foreach ($ch in $Arg.ToCharArray()) {
        if ($ch -eq '\') {
            $backslashes++
        } elseif ($ch -eq '"') {
            $result += ('\' * ($backslashes * 2 + 1))
            $result += '"'
            $backslashes = 0
        } else {
            if ($backslashes -gt 0) {
                $result += ('\' * $backslashes)
                $backslashes = 0
            }
            $result += $ch
        }
    }
    if ($backslashes -gt 0) {
        $result += ('\' * ($backslashes * 2))
    }
    $result += '"'
    return $result
}

function Invoke-NativeProcess {
    param(
        [string]$FilePath,
        [string[]]$ArgumentStrings,
        [string]$WorkingDirectory = $null
    )

    $argLine = ($ArgumentStrings | ForEach-Object { ConvertTo-EscapedArgument $_ }) -join ' '

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $FilePath
    $psi.Arguments = $argLine
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    if (-not [string]::IsNullOrWhiteSpace($WorkingDirectory)) {
        # Push-Location/Pop-Location around the git sync step earlier in this
        # script does not affect this: .NET's ProcessStartInfo.WorkingDirectory
        # defaults to the current process's cwd only if left unset, and by the
        # time the Judge step runs, Pop-Location has already restored whatever
        # directory this script was originally invoked from. Explicit here
        # because this Judge call (unlike Backlog Triage/PR Review's) grants
        # Read/Grep/Glob, which need to resolve relative paths against the
        # real checkout, not wherever Task Scheduler happened to launch from.
        $psi.WorkingDirectory = $WorkingDirectory
    }

    $proc = [System.Diagnostics.Process]::Start($psi)
    $stdout = $proc.StandardOutput.ReadToEnd()
    $stderr = $proc.StandardError.ReadToEnd()
    $proc.WaitForExit()

    return [pscustomobject]@{
        ExitCode = $proc.ExitCode
        StdOut   = $stdout
        StdErr   = $stderr
    }
}

function ConvertTo-JsonArray {
    # ConvertTo-Json collapses a 1-element array back to a bare object when
    # piped; force array bracket wrapping so the prompt always embeds a
    # JSON array even for a single comment / single existing subtask.
    param($InputObject)

    $json = ConvertTo-Json -InputObject $InputObject -Depth 10
    if ($null -eq $InputObject) { return "[]" }
    $trimmed = $json.TrimStart()
    if (@($InputObject).Count -le 1 -and -not $trimmed.StartsWith('[')) {
        $json = "[$json]"
    }
    return $json
}

function Get-QualifyingStories {
    # Note: `gh issue list --label a,b,c` is AND semantics (an issue must
    # carry every listed label), not OR -- confirmed live against this
    # repo before relying on it. The three trigger labels are mutually
    # exclusive statuses in the normal case, so a single combined call
    # would (incorrectly) only ever match an issue carrying all three at
    # once, i.e. effectively nothing. Each label therefore needs its own
    # `gh issue list` call, same per-label-loop pattern as
    # run-backlog-triage.ps1.
    $byNumber = @{}

    foreach ($label in $TriggerLabels) {
        Write-Log "Fetching open issues for label '$label'..."
        $raw = $null
        try {
            $raw = gh issue list --repo $Repo --label $label --state open --json number,title,labels 2>&1
        } catch {
            Write-Log "gh issue list threw for label '${label}': $_" "ERROR"
            throw
        }
        if ($LASTEXITCODE -ne 0) {
            Write-Log "gh issue list exited $LASTEXITCODE for label '${label}': $($raw | Out-String)" "ERROR"
            throw "gh issue list failed for label '$label'"
        }

        $rawText = ($raw | Out-String).Trim()
        $items = @()
        if (-not [string]::IsNullOrWhiteSpace($rawText)) {
            try {
                $parsed = $rawText | ConvertFrom-Json -ErrorAction Stop
            } catch {
                Write-Log "Failed to parse gh issue list JSON for label '${label}': $_. Raw: $rawText" "ERROR"
                throw
            }
            if ($null -ne $parsed) {
                foreach ($item in @($parsed)) { if ($null -ne $item) { $items += $item } }
            }
        }
        Write-Log "Fetched $($items.Count) open issue(s) for label '$label'."

        foreach ($item in $items) {
            $labelNames = @($item.labels | ForEach-Object { $_.name })
            if ($labelNames -notcontains "type:user-story") {
                Write-Log "Skipping issue #$($item.number) ('$($item.title)') for label '${label}': not a type:user-story issue."
                continue
            }
            if (-not $byNumber.ContainsKey([int]$item.number)) {
                $byNumber[[int]$item.number] = [pscustomobject]@{
                    Number            = $item.number
                    Title             = $item.title
                    LabelNames        = $labelNames
                    TriggerLabelsSeen = @($label)
                }
            } else {
                $byNumber[[int]$item.number].TriggerLabelsSeen += $label
            }
        }
    }

    $qualifying = @()
    foreach ($number in $byNumber.Keys) {
        $entry = $byNumber[$number]

        if ($entry.TriggerLabelsSeen.Count -gt 1) {
            Write-Log "Issue #$number carries more than one Architect trigger label simultaneously ($($entry.TriggerLabelsSeen -join ', ')); this is unexpected. Picking the first match by trigger-label priority order (ready-for-architect > needs-revision > needs-clarification)." "WARN"
        }

        $chosenLabel = $null
        foreach ($label in $TriggerLabels) {
            if ($entry.TriggerLabelsSeen -contains $label) { $chosenLabel = $label; break }
        }
        $mode = $ModeByTriggerLabel[$chosenLabel]

        $model = $DefaultModel
        if ($entry.LabelNames -contains "origin:backlog-triage") { $model = $BacklogTriageModel }

        $qualifying += [pscustomobject]@{
            Number       = $entry.Number
            Title        = $entry.Title
            TriggerLabel = $chosenLabel
            Mode         = $mode
            Model        = $model
        }
    }

    return , $qualifying
}

function Get-IssueContext {
    param([int]$Number)

    $raw = $null
    try {
        $raw = gh issue view $Number --repo $Repo --json number,title,body,labels,comments 2>&1
    } catch {
        Write-Log "gh issue view threw for issue #${Number}: $_" "ERROR"
        throw
    }
    if ($LASTEXITCODE -ne 0) {
        Write-Log "gh issue view exited $LASTEXITCODE for issue #${Number}: $($raw | Out-String)" "ERROR"
        throw "gh issue view failed for issue #$Number"
    }

    $rawText = ($raw | Out-String).Trim()
    try {
        return $rawText | ConvertFrom-Json -ErrorAction Stop
    } catch {
        Write-Log "Failed to parse gh issue view JSON for issue #${Number}: $_. Raw: $rawText" "ERROR"
        throw
    }
}

function Get-ExistingSubtasks {
    param([int]$ParentNumber)

    $raw = $null
    try {
        $raw = gh api "repos/$Repo/issues/$ParentNumber/sub_issues" -q '.[].number' 2>&1
    } catch {
        Write-Log "gh api sub_issues threw for parent #${ParentNumber}: $_" "ERROR"
        throw
    }
    if ($LASTEXITCODE -ne 0) {
        Write-Log "gh api sub_issues exited $LASTEXITCODE for parent #${ParentNumber}: $($raw | Out-String)" "ERROR"
        throw "gh api sub_issues failed for parent #$ParentNumber"
    }

    $rawText = ($raw | Out-String).Trim()
    $subtaskNumbers = @()
    if (-not [string]::IsNullOrWhiteSpace($rawText)) {
        $subtaskNumbers = @($rawText -split "`r?`n" | Where-Object { $_ -match '^\d+$' })
    }
    Write-Log "Parent #$ParentNumber has $($subtaskNumbers.Count) existing subtask(s)."

    $subtasks = @()
    foreach ($num in $subtaskNumbers) {
        $subRaw = $null
        try {
            $subRaw = gh issue view $num --repo $Repo --json number,title,body,labels 2>&1
        } catch {
            Write-Log "gh issue view threw for subtask #${num}: $_" "ERROR"
            throw
        }
        if ($LASTEXITCODE -ne 0) {
            Write-Log "gh issue view exited $LASTEXITCODE for subtask #${num}: $($subRaw | Out-String)" "ERROR"
            throw "gh issue view failed for subtask #$num"
        }
        $subRawText = ($subRaw | Out-String).Trim()
        try {
            $subtasks += ($subRawText | ConvertFrom-Json -ErrorAction Stop)
        } catch {
            Write-Log "Failed to parse gh issue view JSON for subtask #${num}: $_. Raw: $subRawText" "ERROR"
            throw
        }
    }

    return , $subtasks
}

function Invoke-ArchitectJudge {
    param(
        [string]$Mode,
        [string]$Model,
        [pscustomobject]$IssueContext,
        [array]$ExistingSubtasks,
        [hashtable]$PromptTemplates
    )

    $promptTemplate = $PromptTemplates[$Mode]
    $commentsJson = ConvertTo-JsonArray -InputObject $IssueContext.comments
    $subtasksJson = ConvertTo-JsonArray -InputObject $ExistingSubtasks

    $prompt = $promptTemplate.Replace('{{ISSUE_NUMBER}}', [string]$IssueContext.number).Replace('{{ISSUE_TITLE}}', [string]$IssueContext.title).Replace('{{ISSUE_BODY}}', [string]$IssueContext.body).Replace('{{ISSUE_COMMENTS_JSON}}', $commentsJson).Replace('{{EXISTING_SUBTASKS_JSON}}', $subtasksJson)

    Write-Log "Invoking claude.exe (model=$Model, mode=$Mode) for issue #$($IssueContext.number)..."
    $result = $null
    try {
        # Unlike Backlog Triage/PR Review's pure text-in/text-out judgment
        # calls, Architect's decomposition/restructure/clarification quality
        # depends on real repo knowledge (entry points, existing patterns) --
        # all three original prompts said as much. Grant read-only tool
        # access (no Write, no Bash) rather than pure judgment, and pin the
        # working directory to the real checkout so relative paths resolve.
        $result = Invoke-NativeProcess -FilePath $ClaudePath -ArgumentStrings @("--model", $Model, "--output-format", "json", "--permission-mode", "dontAsk", "--allowedTools", "Read Grep Glob", "--print", $prompt) -WorkingDirectory $RepoRoot
    } catch {
        Write-Log "claude.exe invocation threw for issue #$($IssueContext.number): $_" "ERROR"
        return $null
    }
    if ($result.ExitCode -ne 0) {
        Write-Log "claude.exe exited $($result.ExitCode) for issue #$($IssueContext.number). StdOut: $($result.StdOut) StdErr: $($result.StdErr)" "ERROR"
        return $null
    }

    $claudeRawText = $result.StdOut.Trim()

    $envelope = $null
    try {
        $envelope = $claudeRawText | ConvertFrom-Json -ErrorAction Stop
    } catch {
        Write-Log "Failed to parse claude.exe JSON envelope for issue #$($IssueContext.number): $_. Raw: $claudeRawText" "ERROR"
        return $null
    }

    if ($envelope.is_error -eq $true) {
        Write-Log "claude.exe reported is_error=true for issue #$($IssueContext.number). Envelope: $claudeRawText" "ERROR"
        return $null
    }

    if ([string]::IsNullOrWhiteSpace($envelope.result)) {
        Write-Log "claude.exe envelope for issue #$($IssueContext.number) had an empty 'result' field." "ERROR"
        return $null
    }

    $responseText = $envelope.result.Trim()
    if ($responseText -match '(?s)^```(?:json)?\s*(.*?)\s*```$') {
        $responseText = $Matches[1].Trim()
    }

    Write-Log "Judge response for issue #$($IssueContext.number): $responseText"

    $decision = $null
    try {
        $decision = $responseText | ConvertFrom-Json -ErrorAction Stop
    } catch {
        Write-Log "Failed to parse decision JSON from judge response for issue #$($IssueContext.number): $_. Response: $responseText" "ERROR"
        return $null
    }

    if ($decision.outcome -ne "PROCEED" -and $decision.outcome -ne "PO_ESCALATION") {
        Write-Log "Judge returned an unexpected outcome '$($decision.outcome)' for issue #$($IssueContext.number); leaving it unprocessed for manual triage." "ERROR"
        return $null
    }

    return $decision
}

function Format-SubtaskBody {
    # Same section order as architect.yml's render_body() bash function:
    # Parent user story / Target repository / Task description / Files +
    # entry points / Acceptance criteria (checklist) / How to verify /
    # Size / Complexity / Blocked by.
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

function Publish-ArchitectDecision {
    param(
        [int]$IssueNumber,
        [string]$TriggerLabel,
        [string]$Mode,
        [pscustomobject]$Decision
    )

    if ($Decision.outcome -eq "PO_ESCALATION") {
        Write-Log "Issue #$IssueNumber -> PO_ESCALATION: $($Decision.conflict)"

        $prevEAP = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $editOutput = gh issue edit $IssueNumber --repo $Repo --remove-label $TriggerLabel --add-label "status:needs-po-input" 2>&1
            if ($LASTEXITCODE -ne 0) {
                Write-Log "Failed to relabel issue #${IssueNumber} for PO escalation: $($editOutput | Out-String)" "ERROR"
            }
        } finally {
            $ErrorActionPreference = $prevEAP
        }

        $commentBody = "**Architect escalation -- needs your decision:**`n`n$($Decision.conflict)"
        $bodyFile = Join-Path ([System.IO.Path]::GetTempPath()) "architect-escalation-$([guid]::NewGuid()).md"
        try {
            $utf8NoBom = New-Object System.Text.UTF8Encoding $false
            [System.IO.File]::WriteAllText($bodyFile, $commentBody, $utf8NoBom)

            $prevEAP = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            try {
                $commentOutput = gh issue comment $IssueNumber --repo $Repo --body-file $bodyFile 2>&1
                if ($LASTEXITCODE -ne 0) {
                    Write-Log "Failed to post PO escalation comment on issue #${IssueNumber}: $($commentOutput | Out-String)" "ERROR"
                }
            } finally {
                $ErrorActionPreference = $prevEAP
            }
        } finally {
            Remove-Item -LiteralPath $bodyFile -Force -ErrorAction SilentlyContinue
        }
        return
    }

    # PROCEED: apply create / update / close.
    $created = @()
    $updated = @()
    $closed = @()

    # Outer @(...) around the whole pipeline is required, not just around
    # $Decision.subtasks.create -- PowerShell unwraps a single-object
    # pipeline result back to a bare scalar on assignment, which would
    # silently turn a genuine 1-subtask create/update/close list into a
    # non-array whose .Count is $null (falsy), breaking every -gt 0 / -eq 0
    # gate below for the single-item case.
    $createList = @()
    if ($null -ne $Decision.subtasks.create) { $createList = @(@($Decision.subtasks.create) | Where-Object { $null -ne $_ }) }

    $parentId = $null
    if ($createList.Count -gt 0) {
        $prevEAP = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            # Integer database id, NOT the GraphQL node id `gh issue view
            # --json id` returns -- the sub_issues endpoint 422s on that.
            # Same requirement architect.yml's original bash step already
            # confirmed live.
            $parentIdOutput = gh api "repos/$Repo/issues/$IssueNumber" -q .id 2>&1
            if ($LASTEXITCODE -ne 0) {
                Write-Log "Failed to fetch parent database id for issue #${IssueNumber}: $($parentIdOutput | Out-String)" "ERROR"
            } else {
                $parentId = ($parentIdOutput | Out-String).Trim()
            }
        } finally {
            $ErrorActionPreference = $prevEAP
        }
    }

    foreach ($subtask in $createList) {
        if ([string]::IsNullOrWhiteSpace($subtask.title)) {
            Write-Log "Skipping malformed create-subtask entry for issue #${IssueNumber}: missing title." "WARN"
            continue
        }
        if ([string]::IsNullOrWhiteSpace($parentId)) {
            Write-Log "Skipping create-subtask entry '$($subtask.title)' for issue #${IssueNumber}: parent database id unavailable, cannot link sub-issue." "ERROR"
            continue
        }

        $bodyText = Format-SubtaskBody -ParentNumber $IssueNumber -Subtask $subtask
        $bodyFile = Join-Path ([System.IO.Path]::GetTempPath()) "architect-subtask-$([guid]::NewGuid()).md"
        $createOutput = $null
        try {
            $utf8NoBom = New-Object System.Text.UTF8Encoding $false
            [System.IO.File]::WriteAllText($bodyFile, $bodyText, $utf8NoBom)

            $prevEAP = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            try {
                $createOutput = gh issue create --repo $Repo --title "[Subtask]: $($subtask.title)" --body-file $bodyFile --label "type:subtask,status:pending-review" 2>&1
                if ($LASTEXITCODE -ne 0) {
                    Write-Log "Failed to create subtask '$($subtask.title)' for issue #${IssueNumber}: $($createOutput | Out-String)" "ERROR"
                    continue
                }
            } finally {
                $ErrorActionPreference = $prevEAP
            }
        } finally {
            Remove-Item -LiteralPath $bodyFile -Force -ErrorAction SilentlyContinue
        }

        $createOutputText = ($createOutput | Out-String).Trim()
        $newIssueNumber = $null
        if ($createOutputText -match '/issues/(\d+)\s*$') {
            $newIssueNumber = $Matches[1]
        }
        if (-not $newIssueNumber) {
            Write-Log "Created a subtask for issue #${IssueNumber} but could not parse its issue number from output: $createOutputText" "ERROR"
            continue
        }

        $prevEAP = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $newIdOutput = gh api "repos/$Repo/issues/$newIssueNumber" -q .id 2>&1
            if ($LASTEXITCODE -ne 0) {
                Write-Log "Failed to fetch database id for new subtask #${newIssueNumber}: $($newIdOutput | Out-String)" "ERROR"
                continue
            }
            $newId = ($newIdOutput | Out-String).Trim()

            $linkOutput = gh api "repos/$Repo/issues/$IssueNumber/sub_issues" -F "sub_issue_id=$newId" 2>&1
            if ($LASTEXITCODE -ne 0) {
                Write-Log "Failed to link new subtask #${newIssueNumber} to parent #${IssueNumber}: $($linkOutput | Out-String)" "ERROR"
                continue
            }
        } finally {
            $ErrorActionPreference = $prevEAP
        }

        Write-Log "Created and linked subtask #$newIssueNumber for issue #$IssueNumber."
        $created += "#$newIssueNumber"
    }

    $updateList = @()
    if ($null -ne $Decision.subtasks.update) { $updateList = @(@($Decision.subtasks.update) | Where-Object { $null -ne $_ }) }
    foreach ($subtask in $updateList) {
        if ($null -eq $subtask.subtask_number -or $subtask.subtask_number -eq 0) {
            Write-Log "Skipping malformed update-subtask entry for issue #${IssueNumber}: missing subtask_number." "WARN"
            continue
        }
        $subtaskNumber = $subtask.subtask_number
        $bodyText = Format-SubtaskBody -ParentNumber $IssueNumber -Subtask $subtask
        $bodyFile = Join-Path ([System.IO.Path]::GetTempPath()) "architect-subtask-update-$([guid]::NewGuid()).md"
        try {
            $utf8NoBom = New-Object System.Text.UTF8Encoding $false
            [System.IO.File]::WriteAllText($bodyFile, $bodyText, $utf8NoBom)

            $prevEAP = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            try {
                $editOutput = gh issue edit $subtaskNumber --repo $Repo --body-file $bodyFile 2>&1
                if ($LASTEXITCODE -ne 0) {
                    Write-Log "Failed to update subtask #${subtaskNumber} for issue #${IssueNumber}: $($editOutput | Out-String)" "ERROR"
                    continue
                }
            } finally {
                $ErrorActionPreference = $prevEAP
            }
        } finally {
            Remove-Item -LiteralPath $bodyFile -Force -ErrorAction SilentlyContinue
        }

        Write-Log "Updated subtask #$subtaskNumber for issue #$IssueNumber."
        $updated += "#$subtaskNumber"
    }

    $closeList = @()
    if ($null -ne $Decision.subtasks.close) { $closeList = @(@($Decision.subtasks.close) | Where-Object { $null -ne $_ }) }
    foreach ($subtask in $closeList) {
        if ($null -eq $subtask.subtask_number -or $subtask.subtask_number -eq 0) {
            Write-Log "Skipping malformed close-subtask entry for issue #${IssueNumber}: missing subtask_number." "WARN"
            continue
        }
        $subtaskNumber = $subtask.subtask_number
        $reason = [string]$subtask.reason

        $prevEAP = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $closeOutput = gh issue close $subtaskNumber --repo $Repo --comment "Closed by Architect: $reason" 2>&1
            if ($LASTEXITCODE -ne 0) {
                Write-Log "Failed to close subtask #${subtaskNumber} for issue #${IssueNumber}: $($closeOutput | Out-String)" "ERROR"
                continue
            }
        } finally {
            $ErrorActionPreference = $prevEAP
        }

        Write-Log "Closed subtask #$subtaskNumber for issue #$IssueNumber ($reason)."
        $closed += "#$subtaskNumber"
    }

    $summaryLines = @("**Architect finished (mode: $Mode).**", "")
    if ($created.Count -gt 0) { $summaryLines += "Created: $($created -join ' ')" }
    if ($updated.Count -gt 0) { $summaryLines += "Updated: $($updated -join ' ')" }
    if ($closed.Count -gt 0) { $summaryLines += "Closed: $($closed -join ' ')" }
    $summaryLines += ""
    $summaryLines += "Sending the current subtask set to Three Amigos for batch review."
    $summaryBody = $summaryLines -join "`n"

    $summaryFile = Join-Path ([System.IO.Path]::GetTempPath()) "architect-summary-$([guid]::NewGuid()).md"
    try {
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($summaryFile, $summaryBody, $utf8NoBom)

        $prevEAP = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $commentOutput = gh issue comment $IssueNumber --repo $Repo --body-file $summaryFile 2>&1
            if ($LASTEXITCODE -ne 0) {
                Write-Log "Failed to post summary comment on issue #${IssueNumber}: $($commentOutput | Out-String)" "ERROR"
            }
        } finally {
            $ErrorActionPreference = $prevEAP
        }
    } finally {
        Remove-Item -LiteralPath $summaryFile -Force -ErrorAction SilentlyContinue
    }

    # Newly created subtasks start at status:pending-review (informational
    # only, nothing triggers on it) rather than status:review, since review
    # now happens once for the whole batch, triggered by the PARENT'S
    # relabel below -- not per-subtask. Same behavior as architect.yml.
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $labelOutput = gh issue edit $IssueNumber --repo $Repo --remove-label $TriggerLabel --add-label "status:review" 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Log "Failed to swap labels on issue #${IssueNumber} to status:review: $($labelOutput | Out-String)" "ERROR"
        }
    } finally {
        $ErrorActionPreference = $prevEAP
    }

    Write-Log "Issue #$IssueNumber PROCEED applied: created=$($created.Count) updated=$($updated.Count) closed=$($closed.Count)."
}

# --- Main ---
try {
    Write-Log "===== Architect run starting ====="

    Write-Log "Syncing local checkout to origin/main..."
    Push-Location $RepoRoot
    $prevEAP = $ErrorActionPreference
    try {
        # git writes routine, non-error status lines (e.g. "Already on 'main'",
        # per-file "M <path>" notes) to stderr. Under $ErrorActionPreference =
        # "Stop", capturing that via 2>&1 wraps each line in an ErrorRecord and
        # throws even on real success -- switch to "Continue" for these calls
        # and check $LASTEXITCODE ourselves instead of relying on the stream.
        $ErrorActionPreference = "Continue"

        git checkout main 2>&1 | ForEach-Object { Write-Log "git: $_" }
        if ($LASTEXITCODE -ne 0) { throw "git checkout main failed with exit code $LASTEXITCODE" }

        git fetch origin 2>&1 | ForEach-Object { Write-Log "git: $_" }
        if ($LASTEXITCODE -ne 0) { throw "git fetch origin failed with exit code $LASTEXITCODE" }

        git reset --hard origin/main 2>&1 | ForEach-Object { Write-Log "git: $_" }
        if ($LASTEXITCODE -ne 0) { throw "git reset --hard origin/main failed with exit code $LASTEXITCODE" }
    } finally {
        $ErrorActionPreference = $prevEAP
        Pop-Location
    }

    $PromptTemplates = @{}
    foreach ($mode in $PromptFileByMode.Keys) {
        $templatePath = Join-Path $PromptTemplateDir $PromptFileByMode[$mode]
        if (-not (Test-Path -LiteralPath $templatePath)) {
            Write-Log "Prompt template not found at $templatePath" "ERROR"
            exit 1
        }
        $PromptTemplates[$mode] = Get-Content -LiteralPath $templatePath -Raw
    }

    $qualifyingStories = Get-QualifyingStories

    if ($qualifyingStories.Count -eq 0) {
        Write-Log "Nothing to architect across labels: $($TriggerLabels -join ', '). Exiting without invoking claude.exe."
        Write-Log "===== Architect run complete (no-op) ====="
        exit 0
    }

    if (-not (Test-Path -LiteralPath $ClaudePath)) {
        Write-Log "claude.exe not found at $ClaudePath; cannot run judge step." "ERROR"
        exit 1
    }

    foreach ($story in ($qualifyingStories | Sort-Object Number)) {
        Write-Log "Processing issue #$($story.Number) '$($story.Title)' (mode=$($story.Mode), model=$($story.Model), trigger=$($story.TriggerLabel))..."

        $issueContext = Get-IssueContext -Number $story.Number
        $existingSubtasks = Get-ExistingSubtasks -ParentNumber $story.Number

        $decision = Invoke-ArchitectJudge -Mode $story.Mode -Model $story.Model -IssueContext $issueContext -ExistingSubtasks $existingSubtasks -PromptTemplates $PromptTemplates
        if ($null -eq $decision) {
            Write-Log "Judge failed to produce a usable decision for issue #$($story.Number); leaving it unprocessed for manual triage." "WARN"
            continue
        }

        Publish-ArchitectDecision -IssueNumber $story.Number -TriggerLabel $story.TriggerLabel -Mode $story.Mode -Decision $decision
    }

    Write-Log "===== Architect run complete ====="
    exit 0
} catch {
    Write-Log "Unhandled error in architect run: $_" "ERROR"
    exit 1
}
