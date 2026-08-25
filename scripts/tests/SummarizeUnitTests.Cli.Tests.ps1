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
