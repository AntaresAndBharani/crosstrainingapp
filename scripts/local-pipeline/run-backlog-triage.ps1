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
    [string]$Model = "gemini-3.7-flash-medium",
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
    $rawText = ConvertTo-SafeString $raw
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

function ConvertTo-SafeString {
    # Found live building the Three Amigos + Dev & Test wrapper: `($x |
    # Out-String).Trim()` on a captured native-command output is NOT a
    # safe way to reassemble it into one string for JSON parsing. Out-String
    # runs the value through PowerShell's display-formatting subsystem
    # (the same one used for console output), which applies a line-wrap
    # width -- and that width is unreliable in a headless/non-interactive
    # process (varies by how the process happens to be spawned). Confirmed
    # live and reproducibly flaky: the exact same gh output, captured the
    # exact same way, parsed to 9 real objects most of the time and to one
    # corrupted object (fields concatenated together) some of the time,
    # with no code difference between runs. Avoid the formatting subsystem
    # entirely -- join array elements with a real newline instead.
    param($InputObject)
    if ($null -eq $InputObject) { return "" }
    if ($InputObject -is [array]) {
        return (($InputObject -join "`n")).Trim()
    }
    return ([string]$InputObject).Trim()
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

function ConvertTo-EscapedArgument {
    # Standard Win32/CommandLineToArgvW argument-quoting algorithm. Needed
    # because PowerShell 5.1's own native-command argument marshaling
    # (`& $exe --print $largeString`) mangles arguments containing embedded
    # double quotes / backslash sequences -- both routine in real issue
    # body text (paths, inline-code spans). ProcessStartInfo.ArgumentList
    # isn't available on this system's .NET Framework, so build the
    # pre-quoted command line by hand instead of relying on either.
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
        [string[]]$ArgumentStrings
    )

    $argLine = ($ArgumentStrings | ForEach-Object { ConvertTo-EscapedArgument $_ }) -join ' '

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $FilePath
    $psi.Arguments = $argLine
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false

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

function Invoke-BacklogJudge {
    param(
        [string]$Label,
        [array]$Issues,
        [string]$PromptTemplate
    )

    $issuesJson = ConvertTo-JsonArray -InputObject $Issues
    $prompt = $PromptTemplate.Replace('{{LABEL}}', $Label).Replace('{{ISSUES_JSON}}', $issuesJson)

    Write-Log "Invoking agy.exe (model=$Model) for label '$Label' ($($Issues.Count) issue(s) in prompt)..."
    $result = $null
    try {
        $result = Invoke-NativeProcess -FilePath $AgyPath -ArgumentStrings @("--model", $Model, "--output-format", "json", "--print", $prompt)
    } catch {
        Write-Log "agy.exe invocation threw for label '${Label}': $_" "ERROR"
        return @()
    }
    if ($result.ExitCode -ne 0) {
        Write-Log "agy.exe exited $($result.ExitCode) for label '${Label}'. StdOut: $($result.StdOut) StdErr: $($result.StdErr)" "ERROR"
        return @()
    }

    $agyRawText = $result.StdOut.Trim()

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
    # Non-anchored on purpose -- see run-architect.ps1 for why: a model can
    # add trailing prose after a complete, valid fenced JSON block despite
    # being told not to, and an end-anchored ($) match fails entirely in
    # that case. Extract the first fenced block wherever it appears.
    if ($responseText -match '(?s)```(?:json)?\s*(.*?)\s*```') {
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

    $createOutputText = ConvertTo-SafeString $createOutput
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
        # `gh` writes its own success confirmation (e.g. "Closed issue ...")
        # to stderr. Under the script-wide $ErrorActionPreference = "Stop",
        # capturing that via 2>&1 wraps it in an ErrorRecord and throws even
        # on real success -- same class of bug as the git-sync step above.
        # Switch to "Continue" for these calls and check $LASTEXITCODE
        # ourselves instead of relying on the stream/try-catch.
        $prevEAP = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
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
        } finally {
            $ErrorActionPreference = $prevEAP
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
