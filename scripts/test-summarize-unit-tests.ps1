param ()

$ErrorActionPreference = "Stop"

$script:PassCount = 0
$script:FailCount = 0

$ScriptPath = Join-Path $PSScriptRoot "summarize-unit-tests.ps1"
if (-not (Test-Path -Path $ScriptPath)) {
    Write-Error "Could not locate summarizer script at '$ScriptPath'."
    exit 1
}

function Normalize-LineEndings ([string]$text) {
    if ($null -eq $text) { return "" }
    return $text.Replace("`r`n", "`n")
}

function Invoke-Summarizer {
    param (
        [string]$ResultsDir,
        [string]$ArtifactName = "unit test report",
        [string]$PrNumber = ""
    )

    $tempOut = Join-Path ([System.IO.Path]::GetTempPath()) "summary-test-$([guid]::NewGuid()).md"

    $origStepSummary = $env:GITHUB_STEP_SUMMARY
    $origWorkspace = $env:GITHUB_WORKSPACE

    $exitCode = 1
    $output = ""

    try {
        Remove-Item env:GITHUB_STEP_SUMMARY -ErrorAction SilentlyContinue
        Remove-Item env:GITHUB_WORKSPACE -ErrorAction SilentlyContinue

        $pwsh = if (Get-Command pwsh -ErrorAction SilentlyContinue) { "pwsh" } else { "powershell" }

        & $pwsh -NoProfile -File $ScriptPath -ResultsDir $ResultsDir -OutFile $tempOut -PrNumber '""' -ArtifactName $ArtifactName
        $exitCode = $LASTEXITCODE

        if (Test-Path -Path $tempOut) {
            $output = [System.IO.File]::ReadAllText($tempOut, [System.Text.Encoding]::UTF8)
        }
    } finally {
        if (Test-Path -Path $tempOut) {
            Remove-Item $tempOut -Force -ErrorAction SilentlyContinue
        }

        if ($null -ne $origStepSummary) {
            $env:GITHUB_STEP_SUMMARY = $origStepSummary
        } else {
            Remove-Item env:GITHUB_STEP_SUMMARY -ErrorAction SilentlyContinue
        }

        if ($null -ne $origWorkspace) {
            $env:GITHUB_WORKSPACE = $origWorkspace
        } else {
            Remove-Item env:GITHUB_WORKSPACE -ErrorAction SilentlyContinue
        }
    }

    return [PSCustomObject]@{
        ExitCode = $exitCode
        Output   = $output
    }
}

function Assert-Equal {
    param (
        $Actual,
        $Expected,
        [string]$ScenarioName
    )

    $normActual = if ($Actual -is [string]) { Normalize-LineEndings $Actual } else { $Actual }
    $normExpected = if ($Expected -is [string]) { Normalize-LineEndings $Expected } else { $Expected }

    if ($normActual -eq $normExpected) {
        Write-Host "  [PASS] $ScenarioName" -ForegroundColor Green
        $script:PassCount++
    } else {
        Write-Host "  [FAIL] $ScenarioName" -ForegroundColor Red
        Write-Host "    Expected: $normExpected" -ForegroundColor DarkGray
        Write-Host "    Actual:   $normActual" -ForegroundColor DarkGray
        $script:FailCount++
    }
}

function Assert-True {
    param (
        [bool]$Condition,
        [string]$ScenarioName,
        [string]$FailureMessage = ""
    )

    if ($Condition) {
        Write-Host "  [PASS] $ScenarioName" -ForegroundColor Green
        $script:PassCount++
    } else {
        Write-Host "  [FAIL] $ScenarioName" -ForegroundColor Red
        if ($FailureMessage) {
            Write-Host "    $FailureMessage" -ForegroundColor DarkGray
        }
        $script:FailCount++
    }
}

$ExpectedNoResults = "<!-- unit-test-evidence -->`n### :test_tube: Unit Test Results`n`nNo unit test results found.`n"

Write-Host "Running summarize-unit-tests regression test harness..." -ForegroundColor Cyan

# Scenario 1: Non-existent results directory
Write-Host "`nScenario 1: Non-existent results directory"
$nonExistentDir = Join-Path ([System.IO.Path]::GetTempPath()) "nonexistent-dir-$([guid]::NewGuid())"
$result1 = Invoke-Summarizer -ResultsDir $nonExistentDir
Assert-Equal -Actual $result1.ExitCode -Expected 0 -ScenarioName "Non-existent dir exits with 0"
Assert-Equal -Actual $result1.Output -Expected $ExpectedNoResults -ScenarioName "Non-existent dir produces standard no-results markdown"

# Scenario 2: Empty directory created at runtime
Write-Host "`nScenario 2: Empty directory created at runtime"
$tempEmptyDir = Join-Path ([System.IO.Path]::GetTempPath()) "empty-unit-tests-$([guid]::NewGuid())"
[System.IO.Directory]::CreateDirectory($tempEmptyDir) | Out-Null
try {
    $result2 = Invoke-Summarizer -ResultsDir $tempEmptyDir
    Assert-Equal -Actual $result2.ExitCode -Expected 0 -ScenarioName "Empty runtime dir exits with 0"
    Assert-Equal -Actual $result2.Output -Expected $ExpectedNoResults -ScenarioName "Empty runtime dir produces standard no-results markdown"
} finally {
    if (Test-Path -Path $tempEmptyDir) {
        Remove-Item $tempEmptyDir -Force -Recurse -ErrorAction SilentlyContinue
    }
}

# Scenario 3: Empty fixture directory with zero-byte XML
Write-Host "`nScenario 3: Empty fixture directory (zero-byte XML)"
$emptyFixtureDir = Join-Path $PSScriptRoot "testdata/unit-tests/empty"
$result3 = Invoke-Summarizer -ResultsDir $emptyFixtureDir
Assert-Equal -Actual $result3.ExitCode -Expected 0 -ScenarioName "Zero-byte XML fixture exits with 0"
Assert-Equal -Actual $result3.Output -Expected $ExpectedNoResults -ScenarioName "Zero-byte XML fixture produces standard no-results markdown"

# Summary
Write-Host "`nTest Summary: $($script:PassCount) passed, $($script:FailCount) failed." -ForegroundColor $(if ($script:FailCount -eq 0) { "Green" } else { "Red" })

if ($script:FailCount -gt 0) {
    exit 1
}

exit 0
