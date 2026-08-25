<#
.SYNOPSIS
    Shared assertion helpers and summarizer invoker for the script test harness.
    Dot-sourced by each *.Tests.ps1 file; do not execute directly.
#>

# ---------------------------------------------------------------------------
# Globals written by the runner before dot-sourcing this file
# $script:PassCount and $script:FailCount are owned by the runner.
# ---------------------------------------------------------------------------

$SummarizerScript = Join-Path (Join-Path $PSScriptRoot "..") "summarize-unit-tests.ps1" |
    Resolve-Path | Select-Object -ExpandProperty Path

# ---------------------------------------------------------------------------
# Child-process wrapper
# ---------------------------------------------------------------------------

function Invoke-SummarizerScript {
    <#
    .SYNOPSIS
        Runs summarize-unit-tests.ps1 as a child process and returns the
        exit code plus the markdown written to -OutFile.
    .PARAMETER ResultsDir
        Passed as -ResultsDir to the script.
    .PARAMETER PrNumber
        Passed as -PrNumber; defaults to "" so no gh call is made.
    .PARAMETER ArtifactName
        Passed as -ArtifactName when supplied.
    .PARAMETER WorkingDirectory
        Working directory for the child process. Defaults to a fresh temp dir.
    #>
    param (
        [string]$ResultsDir,
        [string]$PrNumber    = "",
        [string]$ArtifactName = $null,
        [string]$WorkingDirectory = $null
    )

    $tempOut = Join-Path ([System.IO.Path]::GetTempPath()) "summary-test-$([guid]::NewGuid()).md"

    # Clear env vars that affect output determinism
    $savedVars = @('GITHUB_WORKSPACE', 'GITHUB_STEP_SUMMARY', 'GITHUB_REPOSITORY')
    $savedValues = @{}
    foreach ($v in $savedVars) {
        $savedValues[$v] = [Environment]::GetEnvironmentVariable($v)
        [Environment]::SetEnvironmentVariable($v, $null)
    }

    # Choose working dir
    $tempWd = $null
    if ([string]::IsNullOrWhiteSpace($WorkingDirectory)) {
        $tempWd = Join-Path ([System.IO.Path]::GetTempPath()) "sut-wd-$([guid]::NewGuid())"
        [System.IO.Directory]::CreateDirectory($tempWd) | Out-Null
        $WorkingDirectory = $tempWd
    }

    $pwshExe = if (Get-Command pwsh -ErrorAction SilentlyContinue) { "pwsh" } else { "powershell.exe" }

    # Build the argument list as a single string so PS5 splatting doesn't eat empty values.
    function EscapeArg ([string]$s) { '"' + $s.Replace('"', '""') + '"' }

    $argParts = @(
        '-NoProfile', '-File', (EscapeArg $SummarizerScript),
        '-ResultsDir', (EscapeArg $ResultsDir),
        '-OutFile',    (EscapeArg $tempOut),
        '-PrNumber',   (EscapeArg $PrNumber)
    )
    if (-not [string]::IsNullOrEmpty($ArtifactName)) {
        $argParts += @('-ArtifactName', (EscapeArg $ArtifactName))
    }
    $argString = $argParts -join ' '

    $exitCode = 1
    $output   = ""
    try {
        $proc = Start-Process -FilePath $pwshExe -ArgumentList $argString `
                    -WorkingDirectory $WorkingDirectory `
                    -Wait -PassThru -NoNewWindow
        $exitCode = $proc.ExitCode

        if (Test-Path -Path $tempOut) {
            $rawOutput = [System.IO.File]::ReadAllText($tempOut, [System.Text.Encoding]::UTF8)
            $output = $rawOutput.Replace("`r`n", "`n")
        }
    } finally {
        Remove-Item $tempOut -Force -ErrorAction SilentlyContinue
        if ($tempWd -and (Test-Path $tempWd)) {
            Remove-Item $tempWd -Recurse -Force -ErrorAction SilentlyContinue
        }
        foreach ($v in $savedVars) {
            if ($null -ne $savedValues[$v]) {
                [Environment]::SetEnvironmentVariable($v, $savedValues[$v])
            }
        }
    }

    return [PSCustomObject]@{
        ExitCode = [int]$exitCode
        Output   = [string]$output
    }
}

# ---------------------------------------------------------------------------
# Assertion helpers  (write PASS/FAIL and update $script:PassCount/FailCount)
# ---------------------------------------------------------------------------

function Assert-True {
    param (
        [bool]  $Condition,
        [string]$TestName,
        [string]$FailureMessage = ""
    )
    if ($Condition) {
        Write-Host "  [PASS] $TestName" -ForegroundColor Green
        $script:PassCount++
    } else {
        Write-Host "  [FAIL] $TestName" -ForegroundColor Red
        if ($FailureMessage) {
            Write-Host "    $FailureMessage" -ForegroundColor DarkGray
        }
        $script:FailCount++
    }
}

function Assert-Equal {
    param (
        $Actual,
        $Expected,
        [string]$TestName
    )
    $normActual   = if ($Actual   -is [string]) { $Actual.Replace("`r`n", "`n")   } else { $Actual }
    $normExpected = if ($Expected -is [string]) { $Expected.Replace("`r`n", "`n") } else { $Expected }

    $match = if ($normActual -is [string] -and $normExpected -is [string]) {
        $normActual -ceq $normExpected
    } else {
        $normActual -eq $normExpected
    }

    if ($match) {
        Write-Host "  [PASS] $TestName" -ForegroundColor Green
        $script:PassCount++
    } else {
        Write-Host "  [FAIL] $TestName" -ForegroundColor Red
        $preview = if ($normActual -is [string] -and $normActual.Length -gt 200) {
            $normActual.Substring(0, 200) + "..."
        } else { "$normActual" }
        Write-Host "    Expected: $normExpected" -ForegroundColor DarkGray
        Write-Host "    Actual:   $preview"      -ForegroundColor DarkGray
        $script:FailCount++
    }
}

function Assert-Match {
    param (
        [string]$Value,
        [string]$Pattern,
        [string]$TestName
    )
    if ($Value -match $Pattern) {
        Write-Host "  [PASS] $TestName" -ForegroundColor Green
        $script:PassCount++
    } else {
        Write-Host "  [FAIL] $TestName - pattern not found: $Pattern" -ForegroundColor Red
        $script:FailCount++
    }
}

function Assert-NotMatch {
    param (
        [string]$Value,
        [string]$Pattern,
        [string]$TestName
    )
    if ($Value -notmatch $Pattern) {
        Write-Host "  [PASS] $TestName" -ForegroundColor Green
        $script:PassCount++
    } else {
        Write-Host "  [FAIL] $TestName - forbidden pattern found: $Pattern" -ForegroundColor Red
        $script:FailCount++
    }
}

function Assert-BalancedMarkdownDelimiters {
    <#
    .SYNOPSIS
        Walks the output line-by-line verifying:
        - Fenced code blocks (lines that are purely 3+ backticks) are opened
          and closed with matching run lengths.
        - Outside fenced blocks, every inline code span opened by a run of N
          backticks is closed by another run of exactly N backticks on the
          same line (CommonMark rule: backtick runs are paired greedily by
          equal length).
        - At end-of-file the fence state is closed.
    #>
    param (
        [string]$Text,
        [string]$TestName
    )

    $lines   = $Text -split "`n"
    $inFence = $false
    $fenceLen = 0
    $issues  = [System.Collections.Generic.List[string]]::new()
    $lineNo  = 0

    foreach ($line in $lines) {
        $lineNo++
        $trimmed = $line.Trim()

        # Detect fence toggle: line whose trimmed form is purely 3+ backticks
        if ($trimmed -match '^(`{3,})$') {
            $run = $Matches[1].Length
            if (-not $inFence) {
                $inFence  = $true
                $fenceLen = $run
            } elseif ($run -eq $fenceLen) {
                $inFence  = $false
                $fenceLen = 0
            }
            # mismatched closing run length keeps the fence open; EOF check surfaces it
            continue
        }

        # Inside a fenced block: no inline-span checks needed
        if ($inFence) { continue }

        # Outside fenced blocks: check that every inline backtick span is closed.
        # Algorithm: scan runs of backticks left-to-right; when not in a span,
        # a run of length N opens a span that closes on the next run of length N.
        $runs = [regex]::Matches($line, '`+')
        $spanOpen = $null   # length of the currently-open inline span, or $null

        foreach ($run in $runs) {
            $rl = $run.Length
            if ($null -eq $spanOpen) {
                $spanOpen = $rl   # open a new span
            } elseif ($rl -eq $spanOpen) {
                $spanOpen = $null  # close the span
            }
            # Different length while in a span: treat as literal content per CommonMark
        }

        if ($null -ne $spanOpen) {
            $msg = "Line ${lineNo}: unclosed inline code span (opened with $spanOpen backtick(s)) -- '$line'"
            $issues.Add($msg)
        }
    }

    if ($inFence) {
        $issues.Add("EOF reached with an unclosed fenced block (opened at fence-length $fenceLen)")
    }

    if ($issues.Count -eq 0) {
        Write-Host "  [PASS] $TestName" -ForegroundColor Green
        $script:PassCount++
    } else {
        Write-Host "  [FAIL] $TestName" -ForegroundColor Red
        foreach ($issue in $issues) {
            Write-Host "    $issue" -ForegroundColor DarkGray
        }
        $script:FailCount++
    }
}
