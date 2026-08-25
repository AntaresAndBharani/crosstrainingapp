<#
.SYNOPSIS
    Self-test assertions for Invoke-ScriptTests.ps1.
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

# ---------------------------------------------------------------------------
# Scenario 1: Isolated temp directory with passing fixture exits 0
# ---------------------------------------------------------------------------
Write-Host "--- selftest: passing fixture directory ---"
$tempPassDir = Join-Path ([System.IO.Path]::GetTempPath()) "selftest-pass-$([guid]::NewGuid())"
try {
    [System.IO.Directory]::CreateDirectory($tempPassDir) | Out-Null
    $passFixture = Join-Path $tempPassDir "SamplePass.Tests.ps1"
    $passContent = "Assert-True -Condition `$true -TestName `"selftest synthetic pass`"`nAssert-Equal -Actual 42 -Expected 42 -TestName `"selftest synthetic equality`""
    [System.IO.File]::WriteAllText($passFixture, $passContent, [System.Text.Encoding]::UTF8)

    $exitCode = Invoke-HarnessChildProcess -TestDirectory $tempPassDir
    Assert-Equal -Actual $exitCode -Expected 0 `
                 -TestName "selftest: passing fixture directory exits 0"
} finally {
    if (Test-Path $tempPassDir) {
        Remove-Item $tempPassDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# ---------------------------------------------------------------------------
# Scenario 2: Isolated temp directory with failing fixture exits 1
# ---------------------------------------------------------------------------
Write-Host "--- selftest: failing fixture directory ---"
$tempFailDir = Join-Path ([System.IO.Path]::GetTempPath()) "selftest-fail-$([guid]::NewGuid())"
try {
    [System.IO.Directory]::CreateDirectory($tempFailDir) | Out-Null
    $failFixture = Join-Path $tempFailDir "SampleFail.Tests.ps1"
    $failContent = "Assert-True -Condition `$false -TestName `"selftest synthetic failure`""
    [System.IO.File]::WriteAllText($failFixture, $failContent, [System.Text.Encoding]::UTF8)

    $exitCode = Invoke-HarnessChildProcess -TestDirectory $tempFailDir
    Assert-Equal -Actual $exitCode -Expected 1 `
                 -TestName "selftest: failing fixture directory exits 1"
} finally {
    if (Test-Path $tempFailDir) {
        Remove-Item $tempFailDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# ---------------------------------------------------------------------------
# Scenario 3: Isolated temp directory with mixed passing and failing fixtures exits 1
# ---------------------------------------------------------------------------
Write-Host "--- selftest: mixed fixtures directory ---"
$tempMixedDir = Join-Path ([System.IO.Path]::GetTempPath()) "selftest-mixed-$([guid]::NewGuid())"
try {
    [System.IO.Directory]::CreateDirectory($tempMixedDir) | Out-Null
    $mixedPassFixture = Join-Path $tempMixedDir "A_Pass.Tests.ps1"
    $mixedPassContent = "Assert-True -Condition `$true -TestName `"selftest mixed pass`""
    [System.IO.File]::WriteAllText($mixedPassFixture, $mixedPassContent, [System.Text.Encoding]::UTF8)

    $mixedFailFixture = Join-Path $tempMixedDir "B_Fail.Tests.ps1"
    $mixedFailContent = "Assert-True -Condition `$false -TestName `"selftest mixed fail`""
    [System.IO.File]::WriteAllText($mixedFailFixture, $mixedFailContent, [System.Text.Encoding]::UTF8)

    $exitCode = Invoke-HarnessChildProcess -TestDirectory $tempMixedDir
    Assert-Equal -Actual $exitCode -Expected 1 `
                 -TestName "selftest: mixed fixtures directory exits 1"
} finally {
    if (Test-Path $tempMixedDir) {
        Remove-Item $tempMixedDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# ---------------------------------------------------------------------------
# Scenario 4: Isolated temp directory with unhandled script exception exits 1
# ---------------------------------------------------------------------------
Write-Host "--- selftest: exception fixture directory ---"
$tempExDir = Join-Path ([System.IO.Path]::GetTempPath()) "selftest-ex-$([guid]::NewGuid())"
try {
    [System.IO.Directory]::CreateDirectory($tempExDir) | Out-Null
    $exFixture = Join-Path $tempExDir "SampleException.Tests.ps1"
    $exContent = "throw `"Synthetic unhandled exception`""
    [System.IO.File]::WriteAllText($exFixture, $exContent, [System.Text.Encoding]::UTF8)

    $exitCode = Invoke-HarnessChildProcess -TestDirectory $tempExDir
    Assert-Equal -Actual $exitCode -Expected 1 `
                 -TestName "selftest: exception fixture directory exits 1"
} finally {
    if (Test-Path $tempExDir) {
        Remove-Item $tempExDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}
