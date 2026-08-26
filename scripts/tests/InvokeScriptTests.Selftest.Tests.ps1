<#
.SYNOPSIS
    Self-test assertions for Invoke-ScriptTests.ps1 across 2 scenarios:
      - Scenario 1: Isolated temp directory with single passing fixture (expects exit 0).
      - Scenario 2: Isolated temp directory with consolidated passing, failing, and
        exception fixtures (expects exit 1), consolidated to reduce child-process spawns.
    Proves that Invoke-ScriptTests.ps1 propagates non-zero exit codes when
    test fixtures fail and exits 0 when all test fixtures pass.
    Dot-sourced by Invoke-ScriptTests.ps1.
#>

$RunnerScript = Join-Path $PSScriptRoot "Invoke-ScriptTests.ps1" |
    Resolve-Path | Select-Object -ExpandProperty Path

function Invoke-HarnessChildProcess {
    param (
        [string]$TestDirectory
    )

    $pwshExe = if (Get-Command pwsh -ErrorAction SilentlyContinue) { "pwsh" } else { "powershell.exe" }

    function EscapeArg ([string]$s) { '"' + $s.Replace('"', '""') + '"' }

    $argParts = @(
        '-NoProfile', '-File', (EscapeArg $RunnerScript),
        '-TestDir',   (EscapeArg $TestDirectory)
    )
    $argString = $argParts -join ' '

    $proc = Start-Process -FilePath $pwshExe -ArgumentList $argString `
                -Wait -PassThru -NoNewWindow
    return $proc.ExitCode
}

function Invoke-SelftestScenario {
    param (
        [System.Collections.IDictionary]$Fixtures,
        [int]$ExpectedExitCode,
        [string]$TestName
    )

    $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) "selftest-$([guid]::NewGuid())"
    try {
        [System.IO.Directory]::CreateDirectory($tempDir) | Out-Null
        foreach ($key in $Fixtures.Keys) {
            $fixturePath = Join-Path $tempDir $key
            $content = $Fixtures[$key]
            [System.IO.File]::WriteAllText($fixturePath, $content, [System.Text.Encoding]::UTF8)
        }

        $exitCode = Invoke-HarnessChildProcess -TestDirectory $tempDir
        Assert-Equal -Actual $exitCode -Expected $ExpectedExitCode -TestName $TestName
    } finally {
        if (Test-Path $tempDir) {
            Remove-Item $tempDir -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}

# ---------------------------------------------------------------------------
# Scenario 1: Isolated temp directory with passing fixture exits 0
# ---------------------------------------------------------------------------
Write-Host "--- selftest: passing fixture directory ---"
Invoke-SelftestScenario `
    -Fixtures @{
        "SamplePass.Tests.ps1" = "Assert-True -Condition `$true -TestName `"selftest synthetic pass`"`nAssert-Equal -Actual 42 -Expected 42 -TestName `"selftest synthetic equality`""
    } `
    -ExpectedExitCode 0 `
    -TestName "selftest: passing fixture directory exits 0"

# ---------------------------------------------------------------------------
# Scenario 2: Isolated temp directory with consolidated passing, failing, and exception fixtures exits 1
# ---------------------------------------------------------------------------
Write-Host "--- selftest: consolidated failing and exception fixtures directory ---"
Invoke-SelftestScenario `
    -Fixtures @{
        "A_Pass.Tests.ps1"      = "Assert-True -Condition `$true -TestName `"selftest mixed pass`""
        "B_Fail.Tests.ps1"      = "Assert-True -Condition `$false -TestName `"selftest mixed fail`""
        "C_Exception.Tests.ps1" = "throw `"Synthetic unhandled exception`""
    } `
    -ExpectedExitCode 1 `
    -TestName "selftest: consolidated failing and exception fixtures directory exits 1"
