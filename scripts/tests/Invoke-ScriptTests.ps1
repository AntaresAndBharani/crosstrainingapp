<#
.SYNOPSIS
    Dependency-free test runner for scripts/tests/*.Tests.ps1.
    Usage: pwsh -NoProfile -File ./scripts/tests/Invoke-ScriptTests.ps1
    Exit code: 0 = all tests passed, 1 = one or more tests failed.
#>
param ()

$script:PassCount = 0
$script:FailCount = 0

# Dot-source shared helpers (makes assertion functions and Invoke-SummarizerScript available)
. (Join-Path $PSScriptRoot "TestHelpers.ps1")

# Discover test files in the same directory
$testFiles = Get-ChildItem -Path $PSScriptRoot -Filter "*.Tests.ps1" -File |
    Sort-Object Name

if ($testFiles.Count -eq 0) {
    Write-Host "No *.Tests.ps1 files found in '$PSScriptRoot'." -ForegroundColor Yellow
    exit 0
}

Write-Host "Discovered $($testFiles.Count) test file(s):" -ForegroundColor Cyan
foreach ($f in $testFiles) { Write-Host "  $($f.Name)" }
Write-Host ""

foreach ($testFile in $testFiles) {
    Write-Host "=== $($testFile.Name) ===" -ForegroundColor Cyan
    try {
        . $testFile.FullName
    } catch {
        Write-Host "  [ERROR] Unhandled exception in $($testFile.Name): $_" -ForegroundColor Red
        $script:FailCount++
    }
    Write-Host ""
}

$colour = if ($script:FailCount -eq 0) { "Green" } else { "Red" }
Write-Host "=== Test Summary: $($script:PassCount) passed, $($script:FailCount) failed ===" -ForegroundColor $colour

if ($script:FailCount -gt 0) { exit 1 }
exit 0
