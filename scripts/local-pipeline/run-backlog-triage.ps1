<#
.SYNOPSIS
    Local Windows Task Scheduler replacement for the Antigravity "Backlog
    Triage" scheduled task — Fetch -> Judge -> Act, judgment-only LLM call.

.DESCRIPTION
    Design: ws-setups/graph-engineering/docs/backlog-triage-node.md

    Runs the same per-label procedure as the old fully-agentic
    `.antigravity/tasks/backlog-triage.md`, but splits it so the LLM is
    only ever asked to do the one thing that genuinely needs judgment
    (clustering + story synthesis), while every deterministic step
    (listing issues, creating/commenting/closing them, syncing the
    checkout) runs as plain PowerShell/gh:

      1. Fetch  - `gh issue list` per label (never mixed).
      2. Gate   - if every label came back empty, exit 0 without ever
                  invoking agy.exe. A poll with nothing to do must cost
                  zero LLM tokens.
      3. Judge  - one short `agy.exe --print` call per non-empty label,
                  using the judgment-only prompt template at
                  `.antigravity/tasks/backlog-triage.md`.
      4. Act    - create one `type:user-story` issue per returned
                  cluster, then comment+close every absorbed source issue.

    Labels are always processed independently, one at a time, so a
    cluster/story never absorbs issues from more than one label.

.EXAMPLE
    .\scripts\local-pipeline\run-backlog-triage.ps1
#>
param(
    [string]$Repo = "AntaresAndBharani/crosstrainingapp",
    [string]$AgyPath = "C:\Users\rogal\AppData\Local\agy\bin\agy.exe",
    [string]$Model = "gemini-3.7-flash-high",
    [string[]]$Labels = @("tech-debt", "enhancement"),
    [string]$PromptTemplatePath = (Join-Path $PSScriptRoot "..\..\.antigravity\tasks\backlog-triage.md")
)

$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$LogDir = Join-Path $RepoRoot "logs\local-pipeline"
if (-not (Test-Path -LiteralPath $LogDir)) {
    New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
}
$LogFile = Join-Path $LogDir ("backlog-triage-{0}.log" -f (Get-Date -Format "yyyy-MM-dd"))

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

function Get-OpenIssuesForLabel {
    param([string]$Label)

    Write-Log "Fetching open issues for label '$Label'..."
    $raw = $null
    try {
        $raw = gh issue list --repo $Repo --label $Label --state open --json number,title,body 2>&1
    } catch {
        Write-Log "gh issue list threw for label '${Label}': $_" "ERROR"
        throw
    }
    if ($LASTEXITCODE -ne 0) {
        Write-Log "gh issue list exited $LASTEXITCODE for label '${Label}': $raw" "ERROR"
        throw "gh issue list failed for label '$Label'"
    }

    $issues = @()
    $rawText = ($raw | Out-String).Trim()
    if (-not [string]::IsNullOrWhiteSpace($rawText)) {
        try {
            $parsed = $rawText | ConvertFrom-Json -ErrorAction Stop
        } catch {
            Write-Log "Failed to parse gh issue list JSON for label '${Label}': $_. Raw: $rawText" "ERROR"
            throw
        }
        if ($null -ne $parsed) {
            foreach ($item in @($parsed)) {
                if ($null -ne $item) { $issues += $item }
            }
        }
    }

    Write-Log "Fetched $($issues.Count) open issue(s) for label '$Label'."
    return , $issues
}

function ConvertTo-JsonArray {
    # ConvertTo-Json collapses a 1-element array back to a bare object when
    # piped; force array bracket wrapping so the prompt always embeds a
    # JSON array even for a single fetched issue.
    param($InputObject)

    $json = ConvertTo-Json -InputObject $InputObject -Depth 6
    $trimmed = $json.TrimStart()
    if (@($InputObject).Count -le 1 -and -not $trimmed.StartsWith('[')) {
        $json = "[$json]"
    }
    return $json
}

function Invoke-BacklogJudge {
    param(
        [string]$Label,
        [array]$Issues,
        [string]$PromptTemplate
    )

    $issuesJson = ConvertTo-JsonArray -InputObject $Issues
    $prompt = $PromptTemplate.Replace('{{LABEL}}', $Label).Replace('{{ISSUES_JSON}}', $issuesJson)

    Write-Log "Invoking agy.exe (model=$Model) for label '$Label' ($($Issues.Count) issue(s) in prompt)..."
    $agyRaw = $null
    try {
        $agyRaw = & $AgyPath --model $Model --output-format json --print $prompt 2>&1
    } catch {
        Write-Log "agy.exe invocation threw for label '${Label}': $_" "ERROR"
        return @()
    }
    if ($LASTEXITCODE -ne 0) {
        Write-Log "agy.exe exited $LASTEXITCODE for label '${Label}'. Output: $($agyRaw | Out-String)" "ERROR"
        return @()
    }

    $agyRawText = ($agyRaw | Out-String).Trim()

    $envelope = $null
    try {
        $envelope = $agyRawText | ConvertFrom-Json -ErrorAction Stop
    } catch {
        Write-Log "Failed to parse agy.exe JSON envelope for label '${Label}': $_. Raw: $agyRawText" "ERROR"
        return @()
    }

    if ([string]::IsNullOrWhiteSpace($envelope.response)) {
        Write-Log "agy.exe envelope for label '$Label' had an empty 'response' field. status=$($envelope.status)" "ERROR"
        return @()
    }

    $responseText = $envelope.response.Trim()
    if ($responseText -match '(?s)^```(?:json)?\s*(.*?)\s*```$') {
        $responseText = $Matches[1].Trim()
    }

    Write-Log "Judge response for label '${Label}': $responseText"

    if ([string]::IsNullOrWhiteSpace($responseText)) {
        Write-Log "Judge returned an empty response for label '$Label'; treating as no clusters." "WARN"
        return @()
    }

    $clusters = $null
    try {
        $clusters = $responseText | ConvertFrom-Json -ErrorAction Stop
    } catch {
        Write-Log "Failed to parse cluster JSON from judge response for label '${Label}': $_. Response: $responseText" "ERROR"
        return @()
    }

    $clusters = @($clusters) | Where-Object { $null -ne $_ }
    return , $clusters
}

function Publish-StoryFromCluster {
    param(
        [string]$Label,
        [pscustomobject]$Cluster
    )

    if ([string]::IsNullOrWhiteSpace($Cluster.story_title)) {
        Write-Log "Skipping malformed cluster for label '${Label}': missing story_title." "WARN"
        return
    }
    if ([string]::IsNullOrWhiteSpace($Cluster.story_body)) {
        Write-Log "Skipping cluster '$($Cluster.story_title)' for label '${Label}': missing story_body." "WARN"
        return
    }

    $absorbed = @()
    if ($null -ne $Cluster.absorbed_issue_numbers) {
        $absorbed = @($Cluster.absorbed_issue_numbers) | Where-Object { $null -ne $_ }
    }
    if ($absorbed.Count -eq 0) {
        Write-Log "Skipping cluster '$($Cluster.story_title)' for label '${Label}': no absorbed_issue_numbers." "WARN"
        return
    }

    Write-Log "Creating story issue '$($Cluster.story_title)' for label '$Label', absorbing issue(s) $($absorbed -join ', ')..."

    $bodyFile = Join-Path ([System.IO.Path]::GetTempPath()) "backlog-triage-story-$([guid]::NewGuid()).md"
    $createOutput = $null
    try {
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($bodyFile, $Cluster.story_body, $utf8NoBom)

        $createOutput = gh issue create --repo $Repo --title $Cluster.story_title --body-file $bodyFile --label "type:user-story,status:ready-for-architect,origin:backlog-triage" 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Log "Failed to create story issue '$($Cluster.story_title)' for label '${Label}': $($createOutput | Out-String)" "ERROR"
            return
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
        Write-Log "Created a story issue for label '$Label' but could not parse its issue number from output: $createOutputText" "ERROR"
        return
    }

    Write-Log "Created story issue #$newIssueNumber for label '$Label' ($createOutputText)."

    foreach ($sourceNumber in $absorbed) {
        $commentBody = "Closed as absorbed and consolidated into parent story #$newIssueNumber."
        try {
            $commentOutput = gh issue comment $sourceNumber --repo $Repo --body $commentBody 2>&1
            if ($LASTEXITCODE -ne 0) {
                Write-Log "Failed to comment on issue #${sourceNumber}: $($commentOutput | Out-String)" "ERROR"
                continue
            }

            $closeOutput = gh issue close $sourceNumber --repo $Repo 2>&1
            if ($LASTEXITCODE -ne 0) {
                Write-Log "Failed to close issue #${sourceNumber}: $($closeOutput | Out-String)" "ERROR"
                continue
            }

            Write-Log "Closed issue #$sourceNumber as absorbed into #$newIssueNumber."
        } catch {
            Write-Log "Error absorbing issue #${sourceNumber} into #${newIssueNumber}: $_" "ERROR"
        }
    }
}

# --- Main ---
try {
    Write-Log "===== Backlog triage run starting ====="

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

    if (-not (Test-Path -LiteralPath $PromptTemplatePath)) {
        Write-Log "Prompt template not found at $PromptTemplatePath" "ERROR"
        exit 1
    }
    $PromptTemplate = Get-Content -LiteralPath $PromptTemplatePath -Raw

    $IssuesByLabel = @{}
    $TotalOpenCount = 0
    foreach ($label in $Labels) {
        $IssuesByLabel[$label] = Get-OpenIssuesForLabel -Label $label
        $TotalOpenCount += $IssuesByLabel[$label].Count
    }

    if ($TotalOpenCount -eq 0) {
        Write-Log "Nothing to triage across labels: $($Labels -join ', '). Exiting without invoking agy.exe."
        Write-Log "===== Backlog triage run complete (no-op) ====="
        exit 0
    }

    if (-not (Test-Path -LiteralPath $AgyPath)) {
        Write-Log "agy.exe not found at $AgyPath; cannot run judge step." "ERROR"
        exit 1
    }

    foreach ($label in $Labels) {
        $issues = $IssuesByLabel[$label]
        if ($issues.Count -eq 0) {
            Write-Log "No open issues for label '$label'; skipping judge/act for this label."
            continue
        }

        $clusters = Invoke-BacklogJudge -Label $label -Issues $issues -PromptTemplate $PromptTemplate
        if ($clusters.Count -eq 0) {
            # The template requires every fetched issue to land in exactly one
            # cluster, so an empty result here (with issues present) means the
            # judge call failed or the model didn't follow instructions -- not
            # a normal "nothing to do" outcome.
            Write-Log "Judge returned no clusters for label '$label' despite $($issues.Count) open issue(s) -- expected every issue to land in a cluster. Check the judge response logged above." "WARN"
            continue
        }

        Write-Log "Judge returned $($clusters.Count) cluster(s) for label '$label'."
        foreach ($cluster in $clusters) {
            Publish-StoryFromCluster -Label $label -Cluster $cluster
        }
    }

    Write-Log "===== Backlog triage run complete ====="
    exit 0
} catch {
    Write-Log "Unhandled error in backlog triage run: $_" "ERROR"
    exit 1
}
