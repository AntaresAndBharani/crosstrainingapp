<#
.SYNOPSIS
    Fixture-level markdown output assertions for summarize-unit-tests.ps1.
    Covers: pass, fail, empty, messy fixtures.
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
    $baseline = [System.IO.File]::ReadAllText($baselinePath, [System.Text.Encoding]::UTF8).Replace("`r`n", "`n")
    Assert-Equal -Actual $failResult.Output -Expected $baseline `
                 -TestName "fail: output matches committed baseline (fail-summary.md)"
} else {
    Write-Host "  [FAIL] fail: baseline file not found at '$baselinePath'" -ForegroundColor Red
    $script:FailCount++
}

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
