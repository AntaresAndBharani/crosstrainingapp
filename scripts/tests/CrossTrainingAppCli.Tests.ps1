<#
.SYNOPSIS
    Unit and integration tests for crosstrainingapp CLI, AdbEmulatorHelper,
    and GitHubArtifactHelper (#441, #445).
    Dot-sourced by Invoke-ScriptTests.ps1.
#>

$ScriptRoot = Join-Path $PSScriptRoot ".."
$LibDir = Join-Path $ScriptRoot "lib"
$CliScriptPath = Join-Path $ScriptRoot "crosstrainingapp.ps1"
$AdbHelperPath = Join-Path $LibDir "AdbEmulatorHelper.ps1"
$ArtifactHelperPath = Join-Path $LibDir "GitHubArtifactHelper.ps1"

# Dot-source helper modules under test
. $AdbHelperPath
. $ArtifactHelperPath

Write-Host "--- AVD discovery regex & form factor filtering (#445 Scenario 1) ---"

# Test 1: Mixed list selects Pixel entry first
$mockAvdsMixed = @("Wear_OS_Round_API_34", "Android_TV_1080p", "Pixel_10_API_35", "Desktop_Auto")
$selectedAvd = Get-PreferredAvd -AvdList $mockAvdsMixed
Assert-Equal -Actual $selectedAvd -Expected "Pixel_10_API_35" `
             -TestName "avd: selects Pixel entry from mixed AVD list"

# Test 2: Generic Phone entry selected when no Pixel present
$mockAvdsPhone = @("Wear_OS_Small_Square", "Medium_Phone_API_34", "Android_TV_4K")
$selectedPhone = Get-PreferredAvd -AvdList $mockAvdsPhone
Assert-Equal -Actual $selectedPhone -Expected "Medium_Phone_API_34" `
             -TestName "avd: selects generic Phone entry when no Pixel present"

# Test 3: Ignores Wear OS, TV, and Auto entries
$mockAvdsNonPhone = @("Wear_OS_Round", "Android_TV_720p", "Automotive_1024p")
$noMatchThrew = $false
$noMatchMsg = ""
try {
    Get-PreferredAvd -AvdList $mockAvdsNonPhone
} catch {
    $noMatchThrew = $true
    $noMatchMsg = "$_"
}
Assert-True -Condition $noMatchThrew `
            -TestName "avd: throws when only Wear OS / TV entries exist"
Assert-True -Condition ($noMatchMsg -match 'sdkmanager' -and $noMatchMsg -match 'avdmanager') `
            -TestName "avd: error message includes sdkmanager and avdmanager installation instructions"

# Test 4: Empty list throws with instructions
$emptyListThrew = $false
$emptyListMsg = ""
try {
    Get-PreferredAvd -AvdList @()
} catch {
    $emptyListThrew = $true
    $emptyListMsg = "$_"
}
Assert-True -Condition $emptyListThrew `
            -TestName "avd: throws on empty AVD list"
Assert-True -Condition ($emptyListMsg -match 'Pixel\|Phone') `
            -TestName "avd: error message references Pixel|Phone regex requirement"


Write-Host "--- SHA-caching logic (#445 Scenario 2) ---"

$tempCacheDir = Join-Path ([System.IO.Path]::GetTempPath()) "ci-cache-test-$([guid]::NewGuid())"
try {
    [System.IO.Directory]::CreateDirectory($tempCacheDir) | Out-Null
    $mockSha = "a1b2c3d4e5f6"
    $cachedApkName = "app-debug-$mockSha.apk"
    $cachedApkPath = Join-Path $tempCacheDir $cachedApkName

    # Test 1: Cache Miss triggers download action
    $script:downloadInvoked = $false
    $script:downloadTargetFile = $null
    $script:downloadSha = $null

    $mockDownload = {
        param($TargetFile, $Sha, $Repo)
        $script:downloadInvoked = $true
        $script:downloadTargetFile = $TargetFile
        $script:downloadSha = $Sha
        [System.IO.File]::WriteAllText($TargetFile, "MOCK_APK_CONTENT")
    }

    $retrievedMissPath = Get-LatestMainBuildApk -CacheDir $tempCacheDir -HeadSha $mockSha -DownloadAction $mockDownload
    Assert-True -Condition $script:downloadInvoked `
                -TestName "cache: cache miss invokes download action"
    Assert-Equal -Actual $script:downloadSha -Expected $mockSha `
                 -TestName "cache: download action receives matching HEAD SHA"
    Assert-True -Condition (Test-Path $retrievedMissPath) `
                -TestName "cache: retrieved APK exists after download"

    # Test 2: Cache Hit skips download action
    $script:downloadInvokedOnHit = $false
    $mockDownloadHit = {
        param($TargetFile, $Sha, $Repo)
        $script:downloadInvokedOnHit = $true
    }

    $retrievedHitPath = Get-LatestMainBuildApk -CacheDir $tempCacheDir -HeadSha $mockSha -DownloadAction $mockDownloadHit
    Assert-True -Condition (-not $script:downloadInvokedOnHit) `
                -TestName "cache: cache hit skips network download action"
    Assert-Equal -Actual $retrievedHitPath -Expected ([System.IO.Path]::GetFullPath($cachedApkPath)) `
                 -TestName "cache: cache hit returns cached file path"
} finally {
    if (Test-Path $tempCacheDir) {
        Remove-Item $tempCacheDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}


Write-Host "--- 3-Stage Boot Sequencing & Signature Auto-Healing (#443, #445) ---"

# Test 1: 3-Stage Boot Sequencing queries all three properties
$script:queriedCommands = [System.Collections.Generic.List[string]]::new()
$mockAdbBoot = {
    param([string]$CommandArgs)
    $script:queriedCommands.Add($CommandArgs)
    if ($CommandArgs -match 'sys\.boot_completed') { return "1" }
    if ($CommandArgs -match 'init\.svc\.bootanim') { return "stopped" }
    if ($CommandArgs -match 'pm path android') { return "package:/system/framework/framework-res.apk" }
    return ""
}

$bootSuccess = Wait-ForEmulatorBoot -TimeoutSeconds 5 -PollIntervalSeconds 1 -AdbCommand $mockAdbBoot
Assert-True -Condition $bootSuccess `
            -TestName "boot: Wait-ForEmulatorBoot succeeds when all 3 stages report ready"
Assert-True -Condition ($script:queriedCommands -contains "shell getprop sys.boot_completed") `
            -TestName "boot: verifies sys.boot_completed property"
Assert-True -Condition ($script:queriedCommands -contains "shell getprop init.svc.bootanim") `
            -TestName "boot: verifies init.svc.bootanim property"
Assert-True -Condition ($script:queriedCommands -contains "shell pm path android") `
            -TestName "boot: verifies Package Manager android package availability"

# Test 2: Signature Conflict Auto-Healing (INSTALL_FAILED_UPDATE_INCOMPATIBLE)
$tempApkDir = Join-Path ([System.IO.Path]::GetTempPath()) "apk-deploy-test-$([guid]::NewGuid())"
try {
    [System.IO.Directory]::CreateDirectory($tempApkDir) | Out-Null
    $dummyApk = Join-Path $tempApkDir "test.apk"
    [System.IO.File]::WriteAllText($dummyApk, "DUMMY_APK")

    $script:adbCalls = [System.Collections.Generic.List[string]]::new()
    $script:installAttempts = 0

    $mockAdbDeploy = {
        param([string]$CommandArgs)
        $script:adbCalls.Add($CommandArgs)
        if ($CommandArgs -match '^install -r -d') {
            $script:installAttempts++
            if ($script:installAttempts -eq 1) {
                return "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package signatures do not match]"
            } else {
                return "Success"
            }
        }
        if ($CommandArgs -match '^uninstall') {
            return "Success"
        }
        if ($CommandArgs -match '^shell pm clear') {
            return "Success"
        }
        return ""
    }

    $deployResult = Deploy-And-Launch-App -ApkPath $dummyApk -ClearData -AdbCommand $mockAdbDeploy
    Assert-True -Condition $deployResult `
                -TestName "deploy: Deploy-And-Launch-App succeeds after auto-healing signature mismatch"
    Assert-True -Condition ($script:adbCalls -contains "uninstall com.fractanomics.crosstraining") `
                -TestName "deploy: uninstalls conflicting package on signature mismatch"
    Assert-True -Condition ($script:adbCalls -contains "shell pm clear com.fractanomics.crosstraining") `
                -TestName "deploy: executes pm clear when -ClearData is specified"
    Assert-Equal -Actual $script:installAttempts -Expected 2 `
                 -TestName "deploy: retries installation after uninstall"
} finally {
    if (Test-Path $tempApkDir) {
        Remove-Item $tempApkDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}


Write-Host "--- CLI argument parsing & router static hygiene (#445 Scenario 3) ---"

Assert-True -Condition ([System.IO.File]::Exists($CliScriptPath)) `
            -TestName "cli: crosstrainingapp.ps1 script exists"

$cliText = [System.IO.File]::ReadAllText($CliScriptPath, [System.Text.Encoding]::UTF8)

Assert-True -Condition ($cliText -match '(?i)latest-main') `
            -TestName "cli: supports --latest-main flag"

Assert-True -Condition ($cliText -match '(?i)local') `
            -TestName "cli: supports --local flag"

Assert-True -Condition ($cliText -match '(?i)headless') `
            -TestName "cli: supports --headless flag"

Assert-True -Condition ($cliText -match '(?i)clear-data') `
            -TestName "cli: supports --clear-data flag"

Assert-True -Condition ($cliText -match '(?i)assembleDebug') `
            -TestName "cli: routes local compilation to assembleDebug"

Assert-True -Condition ($cliText -match '-no-window') `
            -TestName "cli: passes -no-window when headless mode is active"

Assert-True -Condition ($cliText -match 'adb logcat -s CrossTrainingApp:\* TimerService:\*') `
            -TestName "cli: outputs logcat diagnostic monitoring hint"

Assert-True -Condition ($cliText -match 'INFO' -and $cliText -match 'AVD' -and $cliText -match 'FETCH' -and $cliText -match 'DEPLOY' -and $cliText -match 'SUCCESS' -and $cliText -match 'ERROR') `
            -TestName "cli: implements ANSI status badge formatting"

# Test child-process invocation with invalid subcommand
$pwshExe = if (Get-Command pwsh -ErrorAction SilentlyContinue) { "pwsh" } else { "powershell.exe" }
$invalidProc = Start-Process -FilePath $pwshExe -ArgumentList "-NoProfile -File `"$CliScriptPath`" invalid-subcommand" -Wait -PassThru -NoNewWindow
Assert-Equal -Actual $invalidProc.ExitCode -Expected 1 `
             -TestName "cli: invalid subcommand exits with code 1"
