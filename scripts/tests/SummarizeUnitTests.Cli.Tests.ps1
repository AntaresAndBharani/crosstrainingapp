<#
.SYNOPSIS
    CLI parameter edge-case assertions for summarize-unit-tests.ps1.
    Covers: missing/empty/whitespace/non-existent -ResultsDir;
            empty and non-numeric -PrNumber.
    Dot-sourced by Invoke-ScriptTests.ps1.
#>

$FixtureRoot   = Join-Path (Join-Path $PSScriptRoot "..") "testdata"
$FixtureRoot   = Join-Path $FixtureRoot "unit-tests"
$NoResultsBody = "<!-- unit-test-evidence -->`n### :test_tube: Unit Test Results`n`nNo unit test results found.`n"

# ---------------------------------------------------------------------------
# Helper: a non-existent path that is guaranteed not to exist
# ---------------------------------------------------------------------------
function New-NonExistentPath {
    return Join-Path ([System.IO.Path]::GetTempPath()) "nonexistent-$([guid]::NewGuid())"
}

# ---------------------------------------------------------------------------
# -ResultsDir omitted (defaults to relative path from cwd; use fresh temp cwd
# so the relative default "app/build/test-results/testDebugUnitTest" doesn't
# exist there and we get the no-results body, identical to CI behaviour when
# no Gradle step has run yet).
# ---------------------------------------------------------------------------
Write-Host "--- -ResultsDir omitted (default relative path, fresh temp cwd) ---"
$omitResult = Invoke-SummarizerScript -ResultsDir "" -WorkingDirectory (
    [System.IO.Directory]::CreateDirectory(
        (Join-Path ([System.IO.Path]::GetTempPath()) "cli-wd-$([guid]::NewGuid())")
    ).FullName
)
Assert-Equal -Actual $omitResult.ExitCode -Expected 0 `
             -TestName "cli: -ResultsDir empty string exits 0"
Assert-Equal -Actual $omitResult.Output -Expected $NoResultsBody `
             -TestName "cli: -ResultsDir empty string produces no-results body"

# ---------------------------------------------------------------------------
# -ResultsDir whitespace-only
# ---------------------------------------------------------------------------
Write-Host "--- -ResultsDir whitespace ---"
$wsResult = Invoke-SummarizerScript -ResultsDir "   "
Assert-Equal -Actual $wsResult.ExitCode -Expected 0 `
             -TestName "cli: -ResultsDir whitespace exits 0"
Assert-Equal -Actual $wsResult.Output -Expected $NoResultsBody `
             -TestName "cli: -ResultsDir whitespace produces no-results body"

# ---------------------------------------------------------------------------
# -ResultsDir non-existent path
# ---------------------------------------------------------------------------
Write-Host "--- -ResultsDir non-existent ---"
$neResult = Invoke-SummarizerScript -ResultsDir (New-NonExistentPath)
Assert-Equal -Actual $neResult.ExitCode -Expected 0 `
             -TestName "cli: -ResultsDir non-existent exits 0"
Assert-Equal -Actual $neResult.Output -Expected $NoResultsBody `
             -TestName "cli: -ResultsDir non-existent produces no-results body"

# ---------------------------------------------------------------------------
# -PrNumber empty against pass fixture (normal table, no gh call)
# ---------------------------------------------------------------------------
Write-Host "--- -PrNumber empty (pass fixture) ---"
$prEmptyResult = Invoke-SummarizerScript -ResultsDir (Join-Path $FixtureRoot "pass") -PrNumber ""
Assert-Equal -Actual $prEmptyResult.ExitCode -Expected 0 `
             -TestName "cli: -PrNumber empty exits 0"
Assert-Match -Value $prEmptyResult.Output -Pattern '(?m)^\| 3 \| 3 \| 0 \| 0 \| \d+\.\d{2}s \|$' `
             -TestName "cli: -PrNumber empty produces normal results table"

# ---------------------------------------------------------------------------
# -PrNumber non-numeric against pass fixture (normal table, no gh call)
# ---------------------------------------------------------------------------
Write-Host "--- -PrNumber non-numeric (pass fixture) ---"
$prAlphaResult = Invoke-SummarizerScript -ResultsDir (Join-Path $FixtureRoot "pass") -PrNumber "abc"
Assert-Equal -Actual $prAlphaResult.ExitCode -Expected 0 `
             -TestName "cli: -PrNumber 'abc' exits 0"
Assert-Match -Value $prAlphaResult.Output -Pattern '(?m)^\| 3 \| 3 \| 0 \| 0 \| \d+\.\d{2}s \|$' `
             -TestName "cli: -PrNumber 'abc' produces normal results table"

# ---------------------------------------------------------------------------
# -ArtifactName variations with truncating failure set
# ---------------------------------------------------------------------------
Write-Host "--- -ArtifactName variations (truncation) ---"
$truncDir = Join-Path ([System.IO.Path]::GetTempPath()) "sut-trunc-$([guid]::NewGuid())"
[System.IO.Directory]::CreateDirectory($truncDir) | Out-Null
try {
    $sb = [System.Text.StringBuilder]::new()
    [void]$sb.Append('<?xml version="1.0" encoding="UTF-8"?><testsuite name="com.example.TruncTest" tests="200" failures="200" errors="0" skipped="0" time="5.0">')
    for ($i = 0; $i -lt 200; $i++) {
        [void]$sb.Append("<testcase name=`"test$i`" classname=`"com.example.TruncTest`" time=`"0.01`"><failure message=`"failure message $i`" type=`"java.lang.AssertionError`">")
        [void]$sb.Append(([string]::new('x', 350)))
        [void]$sb.Append("`nat com.example.TruncTest.test$i(TruncTest.kt:10)`n</failure></testcase>")
    }
    [void]$sb.Append('</testsuite>')
    [System.IO.File]::WriteAllText((Join-Path $truncDir "TEST-com.example.TruncTest.xml"), $sb.ToString(), [System.Text.Encoding]::UTF8)

    # Named
    $namedRes = Invoke-SummarizerScript -ResultsDir $truncDir -ArtifactName "unit-test-report-pr-abc1234"
    Assert-Equal -Actual $namedRes.ExitCode -Expected 0 `
                 -TestName "cli: -ArtifactName named exits 0"
    Assert-True  -Condition ($namedRes.Output.Contains('> Additional failures truncated. See full test report in the `unit-test-report-pr-abc1234` artifact.')) `
                 -TestName "cli: -ArtifactName named contains backticked artifact name"

    # Named explicitly matching fallback string
    $explicitFallbackRes = Invoke-SummarizerScript -ResultsDir $truncDir -ArtifactName "unit test report"
    Assert-Equal -Actual $explicitFallbackRes.ExitCode -Expected 0 `
                 -TestName "cli: -ArtifactName equal to fallback string exits 0"
    Assert-True  -Condition ($explicitFallbackRes.Output.Contains('> Additional failures truncated. See full test report in the `unit test report` artifact.')) `
                 -TestName "cli: -ArtifactName equal to fallback string contains backticked artifact name"

    # Named with embedded backtick
    $btRes = Invoke-SummarizerScript -ResultsDir $truncDir -ArtifactName 'unit-test-report-pr-abc`1234'
    Assert-Equal -Actual $btRes.ExitCode -Expected 0 `
                 -TestName "cli: -ArtifactName with backtick exits 0"
    Assert-True  -Condition ($btRes.Output.Contains('> Additional failures truncated. See full test report in the ``unit-test-report-pr-abc`1234`` artifact.')) `
                 -TestName "cli: -ArtifactName with backtick contains double-backticked artifact name"
    Assert-BalancedMarkdownDelimiters -Text $btRes.Output `
                                      -TestName "cli: -ArtifactName with backtick has balanced markdown delimiters"

    # Named with leading/trailing backticks
    $leadBtRes = Invoke-SummarizerScript -ResultsDir $truncDir -ArtifactName '`unit-test-report-pr-abc1234`'
    Assert-Equal -Actual $leadBtRes.ExitCode -Expected 0 `
                 -TestName "cli: -ArtifactName with leading backticks exits 0"
    Assert-True  -Condition ($leadBtRes.Output.Contains('> Additional failures truncated. See full test report in the `` `unit-test-report-pr-abc1234` `` artifact.')) `
                 -TestName "cli: -ArtifactName with leading backticks contains padded backticked artifact name"
    Assert-BalancedMarkdownDelimiters -Text $leadBtRes.Output `
                                      -TestName "cli: -ArtifactName with leading backticks has balanced markdown delimiters"

    # Default (omitted)
    $defRes = Invoke-SummarizerScript -ResultsDir $truncDir
    Assert-Equal -Actual $defRes.ExitCode -Expected 0 `
                 -TestName "cli: -ArtifactName omitted exits 0"
    Assert-True  -Condition ($defRes.Output.Contains('> Additional failures truncated. See full test report in the unit test report artifact.')) `
                 -TestName "cli: -ArtifactName omitted contains generic fallback note"

    # Explicit empty string
    $emptyArtRes = Invoke-SummarizerScript -ResultsDir $truncDir -ArtifactName ""
    Assert-Equal -Actual $emptyArtRes.ExitCode -Expected 0 `
                 -TestName "cli: -ArtifactName empty string exits 0"
    Assert-True  -Condition ($emptyArtRes.Output.Contains('> Additional failures truncated. See full test report in the unit test report artifact.')) `
                 -TestName "cli: -ArtifactName empty string contains generic fallback note"

    # Whitespace
    $wsArtRes = Invoke-SummarizerScript -ResultsDir $truncDir -ArtifactName "   "
    Assert-Equal -Actual $wsArtRes.ExitCode -Expected 0 `
                 -TestName "cli: -ArtifactName whitespace exits 0"
    Assert-True  -Condition ($wsArtRes.Output.Contains('> Additional failures truncated. See full test report in the unit test report artifact.')) `
                 -TestName "cli: -ArtifactName whitespace contains generic fallback note"
} finally {
    if (Test-Path -Path $truncDir) {
        Remove-Item $truncDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# ---------------------------------------------------------------------------
# -Repo fallback behavior and pass-through to Publish-PrComment
# ---------------------------------------------------------------------------
Write-Host "--- -Repo fallback behavior (pass fixture with mock gh) ---"
$mockGhDir = Join-Path ([System.IO.Path]::GetTempPath()) "sut-ghmock-$([guid]::NewGuid())"
[System.IO.Directory]::CreateDirectory($mockGhDir) | Out-Null
$mockGhLog = Join-Path ([System.IO.Path]::GetTempPath()) "sut-ghlog-$([guid]::NewGuid()).log"

try {
    # Create mock gh executable for Windows (.cmd) and Unix (sh)
    $cmdContent = "@echo off`r`necho %* >> `"%GH_MOCK_LOG%`"`r`nif `"%~1`"==`"user`" (echo bot-user & exit /b 0)`r`nif `"%~1`"==`"api`" (echo [] & exit /b 0)`r`nexit /b 0`r`n"
    [System.IO.File]::WriteAllText((Join-Path $mockGhDir "gh.cmd"), $cmdContent, [System.Text.Encoding]::ASCII)
    [System.IO.File]::WriteAllText((Join-Path $mockGhDir "gh.bat"), $cmdContent, [System.Text.Encoding]::ASCII)

    $shContent = "#!/bin/sh`necho `"`$@`" >> `"`$GH_MOCK_LOG`"`nif [ `"`$1`" = `"user`" ]; then echo `"bot-user`"; exit 0; fi`nif [ `"`$1`" = `"api`" ]; then echo `"[]`"; exit 0; fi`nexit 0`n"
    $shFile = Join-Path $mockGhDir "gh"
    [System.IO.File]::WriteAllText($shFile, $shContent, [System.Text.Encoding]::UTF8)

    if ($PSVersionTable.PSVersion.Major -ge 6 -and -not [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([System.Runtime.InteropServices.OSPlatform]::Windows)) {
        chmod +x $shFile 2>$null
    }

    $pathSep = [System.IO.Path]::PathSeparator
    $baseEnv = @{
        GH_MOCK_LOG = $mockGhLog
        PATH        = "$mockGhDir$pathSep$($env:PATH)"
    }

    # Case 1: -Repo omitted and GITHUB_REPOSITORY unset -> AntaresAndBharani/crosstrainingapp
    if (Test-Path $mockGhLog) { Remove-Item $mockGhLog -Force }
    $res1 = Invoke-SummarizerScript -ResultsDir (Join-Path $FixtureRoot "pass") -PrNumber "123" -Environment $baseEnv
    Assert-Equal -Actual $res1.ExitCode -Expected 0 `
                 -TestName "cli: -Repo omitted with -PrNumber exits 0"
    $log1 = if (Test-Path $mockGhLog) { [System.IO.File]::ReadAllText($mockGhLog) } else { "" }
    Assert-True  -Condition ($log1.Contains('repos/AntaresAndBharani/crosstrainingapp/issues/123/comments')) `
                 -TestName "cli: -Repo omitted falls back to AntaresAndBharani/crosstrainingapp"

    # Case 2: -Repo empty string with GITHUB_REPOSITORY set -> GITHUB_REPOSITORY
    if (Test-Path $mockGhLog) { Remove-Item $mockGhLog -Force }
    $envWithRepo = @{
        GH_MOCK_LOG       = $mockGhLog
        PATH              = "$mockGhDir$pathSep$($env:PATH)"
        GITHUB_REPOSITORY = "CustomOrg/custom-repo"
    }
    $res2 = Invoke-SummarizerScript -ResultsDir (Join-Path $FixtureRoot "pass") -PrNumber "123" -Repo "" -Environment $envWithRepo
    Assert-Equal -Actual $res2.ExitCode -Expected 0 `
                 -TestName "cli: -Repo empty with GITHUB_REPOSITORY exits 0"
    $log2 = if (Test-Path $mockGhLog) { [System.IO.File]::ReadAllText($mockGhLog) } else { "" }
    Assert-True  -Condition ($log2.Contains('repos/CustomOrg/custom-repo/issues/123/comments')) `
                 -TestName "cli: -Repo empty falls back to GITHUB_REPOSITORY"

    # Case 3: -Repo explicit -> passed through unchanged
    if (Test-Path $mockGhLog) { Remove-Item $mockGhLog -Force }
    $res3 = Invoke-SummarizerScript -ResultsDir (Join-Path $FixtureRoot "pass") -PrNumber "123" -Repo "ExplicitOrg/explicit-repo" -Environment $baseEnv
    Assert-Equal -Actual $res3.ExitCode -Expected 0 `
                 -TestName "cli: -Repo explicit exits 0"
    $log3 = if (Test-Path $mockGhLog) { [System.IO.File]::ReadAllText($mockGhLog) } else { "" }
    Assert-True  -Condition ($log3.Contains('repos/ExplicitOrg/explicit-repo/issues/123/comments')) `
                 -TestName "cli: -Repo explicit passed through to gh api"
} finally {
    if (Test-Path $mockGhDir) {
        Remove-Item $mockGhDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path $mockGhLog) {
        Remove-Item $mockGhLog -Force -ErrorAction SilentlyContinue
    }
}

# ---------------------------------------------------------------------------
# -Environment restoration (unconditional restoration in finally block)
# ---------------------------------------------------------------------------
Write-Host "--- -Environment restoration ---"
$testUnsetVar = "TEST_UNSET_VAR_$([guid]::NewGuid().ToString('N'))"
[Environment]::SetEnvironmentVariable($testUnsetVar, $null)

$testPresetVar = "TEST_PRESET_VAR_$([guid]::NewGuid().ToString('N'))"
[Environment]::SetEnvironmentVariable($testPresetVar, "initial_value")

try {
    $envTestResult = Invoke-SummarizerScript -ResultsDir (Join-Path $FixtureRoot "pass") `
        -Environment @{
            $testUnsetVar  = 'temporary_unset_val'
            $testPresetVar = 'overridden_val'
        }
    Assert-Equal -Actual $envTestResult.ExitCode -Expected 0 `
                 -TestName "cli: -Environment restoration call exits 0"
    Assert-Equal -Actual ([Environment]::GetEnvironmentVariable($testUnsetVar)) -Expected $null `
                 -TestName "cli: originally-unset env var is restored to `$null after Invoke-SummarizerScript"
    Assert-Equal -Actual ([Environment]::GetEnvironmentVariable($testPresetVar)) -Expected "initial_value" `
                 -TestName "cli: pre-set env var is restored to initial value after Invoke-SummarizerScript"
} finally {
    [Environment]::SetEnvironmentVariable($testUnsetVar, $null)
    [Environment]::SetEnvironmentVariable($testPresetVar, $null)
}


