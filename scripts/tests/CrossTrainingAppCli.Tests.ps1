<#
.SYNOPSIS
    Unit and integration tests for CrossTraining App CLI router and helper modules.
    Tests AVD discovery, boot lock polling, SHA caching, and CLI argument routing.
    Dot-sourced by Invoke-ScriptTests.ps1.
#>

$CliScriptPath = Join-Path $PSScriptRoot "../crosstrainingapp.ps1" | Resolve-Path | Select-Object -ExpandProperty Path
$AdbHelperPath = Join-Path $PSScriptRoot "../lib/AdbEmulatorHelper.ps1" | Resolve-Path | Select-Object -ExpandProperty Path
$ArtifactHelperPath = Join-Path $PSScriptRoot "../lib/GitHubArtifactHelper.ps1" | Resolve-Path | Select-Object -ExpandProperty Path

# Dot-source helpers for unit testing
. $AdbHelperPath
. $ArtifactHelperPath

Write-Host "--- AVD Discovery & Selection Tests ---"

# Test 1: Preferred AVD filters out Wear OS and TV form factors
$mockAvds = @(
    "Wear_OS_Square_API_34",
    "Android_TV_1080p_API_33",
    "Pixel_10_API_35",
    "Pixel_8_Pro_API_34",
    "Automotive_1024p_API_32"
)
$selectedAvd = Get-PreferredAvd -AvdList $mockAvds
Assert-Equal -Actual $selectedAvd -Expected "Pixel_10_API_35" `
             -TestName "avd: selects first matching Pixel/Phone AVD and ignores Wear/TV/Automotive"

# Test 2: Phone AVD match
$mockAvds2 = @(
    "Wear_Small_Round",
    "Generic_Phone_API_31"
)
$selectedAvd2 = Get-PreferredAvd -AvdList $mockAvds2
Assert-Equal -Actual $selectedAvd2 -Expected "Generic_Phone_API_31" `
             -TestName "avd: selects Generic_Phone AVD when Pixel is absent"

# Test 3: No valid phone AVD throws actionable error
$mockAvdsOnlyWear = @("Wear_OS_Round_API_30", "Android_TV_4k")
$caughtError = $false
$errorMessage = ""
try {
    Get-PreferredAvd -AvdList $mockAvdsOnlyWear | Out-Null
} catch {
    $caughtError = $true
    $errorMessage = $_.ToString()
}
Assert-True -Condition $caughtError -TestName "avd: throws when no phone AVD exists"
Assert-Match -Value $errorMessage -Pattern "sdkmanager.*system-images" `
             -TestName "avd: error message includes sdkmanager installation hint"
Assert-Match -Value $errorMessage -Pattern "avdmanager create avd" `
             -TestName "avd: error message includes avdmanager creation command"

Write-Host "--- Status Badge ANSI Output Tests ---"

# Test 4: ANSI badges formatting with specific color codes
$infoOut = Format-StatusBadge -Badge 'INFO' -Message 'System ready'
Assert-Match -Value $infoOut -Pattern "\[36m\[INFO\]" -TestName "badge: INFO badge formatted with Cyan (36)"

$avdOut = Format-StatusBadge -Badge 'AVD' -Message 'AVD booted'
Assert-Match -Value $avdOut -Pattern "\[33m\[AVD\]" -TestName "badge: AVD badge formatted with Yellow (33)"

$fetchOut = Format-StatusBadge -Badge 'FETCH' -Message 'Downloading'
Assert-Match -Value $fetchOut -Pattern "\[35m\[FETCH\]" -TestName "badge: FETCH badge formatted with Magenta (35)"

$deployOut = Format-StatusBadge -Badge 'DEPLOY' -Message 'Installing'
Assert-Match -Value $deployOut -Pattern "\[34m\[DEPLOY\]" -TestName "badge: DEPLOY badge formatted with Blue (34)"

$successOut = Format-StatusBadge -Badge 'SUCCESS' -Message 'Done'
Assert-Match -Value $successOut -Pattern "\[32m\[SUCCESS\]" -TestName "badge: SUCCESS badge formatted with Green (32)"

$errorOut = Format-StatusBadge -Badge 'ERROR' -Message 'Failed'
Assert-Match -Value $errorOut -Pattern "\[31m\[ERROR\]" -TestName "badge: ERROR badge formatted with Red (31)"

Write-Host "--- SHA-Cached Artifact Retrieval Tests ---"

# Test 5: Cached APK retrieval skips network download
$tempCacheDir = Join-Path ([System.IO.Path]::GetTempPath()) "apk-cache-test-$([guid]::NewGuid())"
[System.IO.Directory]::CreateDirectory($tempCacheDir) | Out-Null
try {
    $testSha = "a1b2c3d4e5f6"
    $cachedApkPath = Join-Path $tempCacheDir "app-debug-a1b2c3d.apk"
    [System.IO.File]::WriteAllText($cachedApkPath, "fake-apk-binary-content")

    $retrieved = Get-LatestMainBuildApk -CacheDir $tempCacheDir -ExplicitSha $testSha
    Assert-Equal -Actual $retrieved -Expected (Resolve-Path $cachedApkPath).Path `
                 -TestName "artifact: cached APK returns local path without network call"
} finally {
    Remove-Item -Path $tempCacheDir -Recurse -Force -ErrorAction SilentlyContinue
}

# Test 6: Missing SHA throws when offline/no gh token
$emptyCacheDir = Join-Path ([System.IO.Path]::GetTempPath()) "apk-cache-empty-$([guid]::NewGuid())"
[System.IO.Directory]::CreateDirectory($emptyCacheDir) | Out-Null
try {
    $caughtArtifactError = $false
    try {
        # Intentionally invalid repo to test graceful error throwing
        Get-LatestMainBuildApk -Repo "NonExistent/Repo-404-XYZ" -CacheDir $emptyCacheDir -ExplicitSha "deadbeef123" | Out-Null
    } catch {
        $caughtArtifactError = $true
    }
    Assert-True -Condition $caughtArtifactError `
                -TestName "artifact: missing APK download failure throws exception for caller fallback"
} finally {
    Remove-Item -Path $emptyCacheDir -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "--- CLI Router Invocation Tests ---"

# Test 7: CLI Help command
$pwshExe = if (Get-Command pwsh -ErrorAction SilentlyContinue) { "pwsh" } else { "powershell.exe" }

$helpOutRaw = & $pwshExe -NoProfile -File $CliScriptPath help 2>&1
$helpText = $helpOutRaw -join "`n"

Assert-Equal -Actual $LASTEXITCODE -Expected 0 -TestName "cli: 'help' subcommand exits 0"
Assert-Match -Value $helpText -Pattern "--latest-main" -TestName "cli: help output lists --latest-main"
Assert-Match -Value $helpText -Pattern "--local" -TestName "cli: help output lists --local"
Assert-Match -Value $helpText -Pattern "--headless" -TestName "cli: help output lists --headless"
Assert-Match -Value $helpText -Pattern "--clear-data" -TestName "cli: help output lists --clear-data"

# Test 8: Unknown subcommand exits 1 with error badge
$unknownOutRaw = & $pwshExe -NoProfile -File $CliScriptPath invalidcmd 2>&1
$unknownExit = $LASTEXITCODE
$unknownText = $unknownOutRaw -join "`n"

Assert-Equal -Actual $unknownExit -Expected 1 -TestName "cli: unknown subcommand exits 1"
Assert-Match -Value $unknownText -Pattern "\[31m\[ERROR\]" -TestName "cli: unknown subcommand prints [ERROR] ANSI code"
Assert-Match -Value $unknownText -Pattern "Unknown subcommand" -TestName "cli: unknown subcommand message present"
