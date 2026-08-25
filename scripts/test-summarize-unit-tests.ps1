param ()

$ErrorActionPreference = "Stop"

$script:PassCount = 0
$script:FailCount = 0

$ScriptPath = Join-Path $PSScriptRoot "summarize-unit-tests.ps1"
if (-not (Test-Path -Path $ScriptPath)) {
    Write-Error "Could not locate summarizer script at '$ScriptPath'."
    exit 1
}

function ConvertTo-LfLineEnding ([string]$text) {
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

        $cmdArgs = @('-NoProfile', '-File', $ScriptPath, '-ResultsDir', $ResultsDir, '-OutFile', $tempOut)
        if ($PrNumber) {
            $cmdArgs += @('-PrNumber', $PrNumber)
        }
        if ($ArtifactName) {
            $cmdArgs += @('-ArtifactName', $ArtifactName)
        }

        $null = & $pwsh @cmdArgs
        $exitCode = $LASTEXITCODE

        if (Test-Path -Path $tempOut) {
            $rawOutput = [System.IO.File]::ReadAllText($tempOut, [System.Text.Encoding]::UTF8)
            $output = ConvertTo-LfLineEnding $rawOutput
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
        ExitCode = [int]$exitCode
        Output   = [string]$output
    }
}

function Assert-Equal {
    param (
        $Actual,
        $Expected,
        [string]$ScenarioName
    )

    $normActual = if ($Actual -is [string]) { ConvertTo-LfLineEnding $Actual } else { $Actual }
    $normExpected = if ($Expected -is [string]) { ConvertTo-LfLineEnding $Expected } else { $Expected }

    $isMatch = if ($normActual -is [string] -and $normExpected -is [string]) {
        $normActual -ceq $normExpected
    } else {
        $normActual -eq $normExpected
    }

    if ($isMatch) {
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

# Scenario 4: Passing unit test results fixture
Write-Host "`nScenario 4: Passing unit test results fixture"
$passFixtureDir = Join-Path $PSScriptRoot "testdata/unit-tests/pass"
$result4 = Invoke-Summarizer -ResultsDir $passFixtureDir
Assert-Equal -Actual $result4.ExitCode -Expected 0 -ScenarioName "Pass fixture exits with 0"
Assert-True -Condition ($result4.Output.StartsWith("<!-- unit-test-evidence -->")) `
    -ScenarioName "Pass fixture output starts with evidence marker" `
    -FailureMessage "Output did not start with <!-- unit-test-evidence -->"
Assert-True -Condition ($result4.Output -match '(?m)^\| 3 \| 3 \| 0 \| 0 \| \d+\.\d{2}s \|$') `
    -ScenarioName "Pass fixture counts row matches | 3 | 3 | 0 | 0 | with formatted elapsed" `
    -FailureMessage "Counts row with 3/3/0/0 and formatted elapsed time not found in output"
Assert-True -Condition (-not ($result4.Output.Contains("#### Failures"))) `
    -ScenarioName "Pass fixture has no failures section" `
    -FailureMessage "Output unexpectedly contained '#### Failures'"

# Scenario 5: Failing unit test results fixture
Write-Host "`nScenario 5: Failing unit test results fixture"
$failFixtureDir = Join-Path $PSScriptRoot "testdata/unit-tests/fail"
$result5 = Invoke-Summarizer -ResultsDir $failFixtureDir
Assert-Equal -Actual $result5.ExitCode -Expected 0 -ScenarioName "Fail fixture exits with 0"
Assert-True -Condition ($result5.Output -match '(?m)^\| 3 \| 1 \| 1 \| 1 \| \d+\.\d{2}s \|$') `
    -ScenarioName "Fail fixture counts row matches | 3 | 1 | 1 | 1 | with formatted elapsed" `
    -FailureMessage "Counts row with 3/1/1/1 and formatted elapsed time not found in output"
Assert-True -Condition ($result5.Output.Contains("#### Failures")) `
    -ScenarioName "Fail fixture contains failures section header" `
    -FailureMessage "Output did not contain '#### Failures'"
Assert-True -Condition ($result5.Output.Contains("**com.example.RedTest > testFailingMethod**")) `
    -ScenarioName "Fail fixture contains failing test name header" `
    -FailureMessage "Output did not contain '**com.example.RedTest > testFailingMethod**'"
Assert-True -Condition ($result5.Output.Contains('**Message:** `expected:<1> but was:<2>`')) `
    -ScenarioName "Fail fixture contains failure assertion message" `
    -FailureMessage "Output did not contain expected failure message"
Assert-True -Condition ($result5.Output -match '(?s)<details>\s*<summary>Stack Trace</summary>\s*```.*?\tat org\.junit\.Assert\.fail\(Assert\.java:89\).*?```\s*</details>') `
    -ScenarioName "Fail fixture stack trace details block preserves tab indentation" `
    -FailureMessage "Stack trace details block with tab-indented stack frames not found"

# Scenario 6: Path sanitization negative assertion
Write-Host "`nScenario 6: Path-sanitization negative assertion"
Assert-True -Condition (-not ($result5.Output.Contains("/home/runner/work/"))) `
    -ScenarioName "Fail fixture output does not contain runner path /home/runner/work/" `
    -FailureMessage "Output leaked runner path prefix /home/runner/work/"
Assert-True -Condition ($result5.Output.Contains("app/src/test/java/com/example/RedTest.kt:42")) `
    -ScenarioName "Fail fixture output contains sanitized relative path" `
    -FailureMessage "Output did not contain sanitized relative path app/src/test/java/com/example/RedTest.kt:42"

# Summary
Write-Host "`nTest Summary: $($script:PassCount) passed, $($script:FailCount) failed." -ForegroundColor $(if ($script:FailCount -eq 0) { "Green" } else { "Red" })

if ($script:FailCount -gt 0) {
    exit 1
}

exit 0
