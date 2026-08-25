<#
.SYNOPSIS
    Fixture-level markdown output assertions for summarize-unit-tests.ps1.
    Covers: pass, fail, empty, messy, longmessage fixtures.
    Dot-sourced by Invoke-ScriptTests.ps1.
#>

$FixtureRoot    = Join-Path (Join-Path $PSScriptRoot "..") "testdata"
$FixtureRoot    = Join-Path $FixtureRoot "unit-tests"
$ExpectedDir    = Join-Path $FixtureRoot "expected"
$NoResultsBody  = "<!-- unit-test-evidence -->`n### :test_tube: Unit Test Results`n`nNo unit test results found.`n"

# ---------------------------------------------------------------------------
# Pass fixture
# ---------------------------------------------------------------------------
Write-Host "--- pass fixture ---"
$passResult = Invoke-SummarizerScript -ResultsDir (Join-Path $FixtureRoot "pass")
Assert-Equal  -Actual $passResult.ExitCode -Expected 0 `
              -TestName "pass: exits 0"
Assert-True   -Condition ($passResult.Output.StartsWith("<!-- unit-test-evidence -->")) `
              -TestName "pass: starts with evidence marker"
Assert-Match  -Value $passResult.Output -Pattern '(?m)^\| 3 \| 3 \| 0 \| 0 \| \d+\.\d{2}s \|$' `
              -TestName "pass: counts row 3/3/0/0"
$check = [char]::ConvertFromUtf32(0x2705)
Assert-True   -Condition ($passResult.Output.Contains("**Status:** $check All 3 tests passed.")) `
              -TestName "pass: status line contains checkmark and 'All 3 tests passed.'"
Assert-True   -Condition (-not ($passResult.Output.Contains("#### Failures"))) `
              -TestName "pass: no Failures section"
Assert-NotMatch -Value $passResult.Output -Pattern '(?i)/home/runner/work|[a-z]:\\a\\' `
                -TestName "pass: no runner path prefixes"
Assert-BalancedMarkdownDelimiters -Text $passResult.Output `
                                  -TestName "pass: balanced markdown delimiters"

# ---------------------------------------------------------------------------
# Fail fixture — exact baseline comparison
# ---------------------------------------------------------------------------
Write-Host "--- fail fixture ---"
$failResult = Invoke-SummarizerScript -ResultsDir (Join-Path $FixtureRoot "fail")
Assert-Equal  -Actual $failResult.ExitCode -Expected 0 `
              -TestName "fail: exits 0"

$baselinePath = Join-Path $ExpectedDir "fail-summary.md"
if (Test-Path $baselinePath) {
    $baseline = ConvertTo-LfLineEnding ([System.IO.File]::ReadAllText($baselinePath, [System.Text.Encoding]::UTF8))
    Assert-Equal -Actual $failResult.Output -Expected $baseline `
                 -TestName "fail: output matches committed baseline (fail-summary.md)"
} else {
    Write-Host "  [FAIL] fail: baseline file not found at '$baselinePath'" -ForegroundColor Red
    $script:FailCount++
}

$cross = [char]::ConvertFromUtf32(0x274C)
Assert-True   -Condition ($failResult.Output.Contains("**Status:** $cross 1 test failed.")) `
              -TestName "fail: status line contains cross and singular '1 test failed.'"
$statusIdx = $failResult.Output.IndexOf("**Status:** $cross 1 test failed.")
$failHeadingIdx = $failResult.Output.IndexOf("#### Failures")
Assert-True   -Condition ($statusIdx -ge 0 -and $failHeadingIdx -ge 0 -and $statusIdx -lt $failHeadingIdx) `
              -TestName "fail: status line precedes Failures heading"
Assert-True   -Condition ($failResult.Output.Contains("#### Failures")) `
              -TestName "fail: contains Failures section"
Assert-True   -Condition ($failResult.Output.Contains('**Message:** `expected:<1> but was:<2>`')) `
              -TestName "fail: failure message is not entity-escaped"
Assert-NotMatch -Value $failResult.Output -Pattern '(?i)/home/runner/work|[a-z]:\\a\\' `
                -TestName "fail: no runner path prefixes"
Assert-True   -Condition ($failResult.Output.Contains("app/src/test/java/com/example/RedTest.kt:42")) `
              -TestName "fail: sanitized relative path present"
Assert-BalancedMarkdownDelimiters -Text $failResult.Output `
                                  -TestName "fail: balanced markdown delimiters"

# ---------------------------------------------------------------------------
# Empty fixture (zero-byte XML)
# ---------------------------------------------------------------------------
Write-Host "--- empty fixture ---"
$emptyResult = Invoke-SummarizerScript -ResultsDir (Join-Path $FixtureRoot "empty")
Assert-Equal -Actual $emptyResult.ExitCode -Expected 0 `
             -TestName "empty: exits 0"
Assert-Equal -Actual $emptyResult.Output -Expected $NoResultsBody `
             -TestName "empty: produces no-results body"

# ---------------------------------------------------------------------------
# Messy fixture — XML entities, backticks, multi-line, absolute paths
# ---------------------------------------------------------------------------
Write-Host "--- messy fixture ---"
$messyResult = Invoke-SummarizerScript -ResultsDir (Join-Path $FixtureRoot "messy")
Assert-Equal  -Actual $messyResult.ExitCode -Expected 0 `
              -TestName "messy: exits 0"

Assert-True   -Condition ($messyResult.Output.Contains("**Status:** $cross 5 tests failed.")) `
              -TestName "messy: status line contains cross and plural '5 tests failed.'"
$messyStatusIdx = $messyResult.Output.IndexOf("**Status:** $cross 5 tests failed.")
$messyFailHeadingIdx = $messyResult.Output.IndexOf("#### Failures")
Assert-True   -Condition ($messyStatusIdx -ge 0 -and $messyFailHeadingIdx -ge 0 -and $messyStatusIdx -lt $messyFailHeadingIdx) `
              -TestName "messy: status line precedes Failures heading"

# Literal angle-bracket content must NOT be entity-escaped
Assert-True   -Condition ($messyResult.Output.Contains("expected:<b> but was:<i>")) `
              -TestName "messy: angle-bracket content not entity-escaped"
Assert-NotMatch -Value $messyResult.Output -Pattern '&lt;|&gt;|&amp;' `
                -TestName "messy: no HTML entities in output"

# Bold/italic/ampersand/symbols literal string
Assert-True   -Condition ($messyResult.Output.Contains("*bold* _italic_ __underline__ **strong** & <symbols>")) `
              -TestName "messy: formatting symbols and ampersand not escaped"

# No runner paths
Assert-NotMatch -Value $messyResult.Output -Pattern '(?i)/home/runner/work|[a-z]:\\a\\' `
                -TestName "messy: no runner path prefixes"

# Balanced delimiters (the messy fixture contains ``` triple and `single` backticks)
Assert-BalancedMarkdownDelimiters -Text $messyResult.Output `
                                  -TestName "messy: balanced markdown delimiters"

# ---------------------------------------------------------------------------
# Long message fixture — message capped at 40 lines + truncation indicator
# ---------------------------------------------------------------------------
Write-Host "--- longmessage fixture ---"
$longResult = Invoke-SummarizerScript -ResultsDir (Join-Path $FixtureRoot "longmessage")
Assert-Equal  -Actual $longResult.ExitCode -Expected 0 `
              -TestName "longmessage: exits 0"
Assert-True   -Condition ($longResult.Output.Contains("#### Failures")) `
              -TestName "longmessage: contains Failures section"
Assert-True   -Condition ($longResult.Output.Contains("... (truncated)")) `
              -TestName "longmessage: truncation indicator present"
Assert-NotMatch -Value $longResult.Output -Pattern '(?i)/home/runner/work|[a-z]:\\a\\' `
                -TestName "longmessage: no runner path prefixes"
Assert-True   -Condition ($longResult.Output.Contains("app/src/test/java/com/example/LongMessageTest.kt:10")) `
              -TestName "longmessage: sanitized relative path present in message"
Assert-BalancedMarkdownDelimiters -Text $longResult.Output `
                                  -TestName "longmessage: balanced markdown delimiters"

