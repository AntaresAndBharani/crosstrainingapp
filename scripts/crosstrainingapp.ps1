<#
.SYNOPSIS
    CrossTraining App CLI router.
.DESCRIPTION
    Unified developer and autonomous agent CLI for provisioning Android Virtual Devices (AVDs),
    retrieving verified main-branch CI builds, and deploying APKs locally or headless.
.EXAMPLE
    pwsh ./scripts/crosstrainingapp.ps1 emulator --latest-main
.EXAMPLE
    pwsh ./scripts/crosstrainingapp.ps1 emulator --local --headless
#>

[CmdletBinding()]
param(
    [Parameter(Position = 0, Mandatory = $false)]
    [string]$Subcommand = 'emulator',

    [Alias('latest-main')]
    [switch]$LatestMain,

    [switch]$Local,

    [switch]$Headless,

    [Alias('clear-data')]
    [switch]$ClearData
)

# Parse additional double-dash or single-dash arguments passed via $args
$extraArgs = @($args)
if ($Subcommand.StartsWith('-')) {
    $extraArgs = @($Subcommand) + $extraArgs
    $Subcommand = 'emulator'
}

foreach ($arg in $extraArgs) {
    switch ($arg) {
        '--latest-main' { $LatestMain = $true }
        '-latest-main'  { $LatestMain = $true }
        '--local'       { $Local = $true }
        '-local'        { $Local = $true }
        '--headless'    { $Headless = $true }
        '-headless'     { $Headless = $true }
        '--clear-data'  { $ClearData = $true }
        '-clear-data'   { $ClearData = $true }
        'emulator'      { $Subcommand = 'emulator' }
        'help'          { $Subcommand = 'help' }
        '--help'        { $Subcommand = 'help' }
        '-h'            { $Subcommand = 'help' }
    }
}

# Dot-source helper modules from scripts/lib
$libDir = Join-Path $PSScriptRoot "lib"
. (Join-Path $libDir "AdbEmulatorHelper.ps1")
. (Join-Path $libDir "GitHubArtifactHelper.ps1")

function Show-Help {
    Write-StatusBadge 'INFO' "CrossTraining CLI tool"
    Write-Host ""
    Write-Host "Usage: crosstrainingapp.ps1 <subcommand> [flags]"
    Write-Host ""
    Write-Host "Subcommands:"
    Write-Host "  emulator         Provision AVD, retrieve/build APK, and deploy to device"
    Write-Host "  help             Show this help message"
    Write-Host ""
    Write-Host "Flags for 'emulator':"
    Write-Host "  --latest-main    Deploy latest main-branch CI/Release build (default mode)"
    Write-Host "  --local          Compile locally via Gradle (:app:assembleDebug) before deployment"
    Write-Host "  --headless       Launch emulator with -no-window (GUI disabled)"
    Write-Host "  --clear-data     Clear application data upon install via 'adb shell pm clear'"
}

if ($Subcommand -eq 'help') {
    Show-Help
    exit 0
}

if ($Subcommand -ne 'emulator') {
    Write-StatusBadge 'ERROR' "Unknown subcommand '$Subcommand'. Run with 'help' for usage."
    exit 1
}

# ---------------------------------------------------------------------------
# Emulator Subcommand Execution Flow
# ---------------------------------------------------------------------------
Write-StatusBadge 'INFO' "Starting CrossTraining emulator workflow..."

# 1. Device check / provisioning
$onlineDevices = Get-OnlineDevices
if ($onlineDevices.Count -eq 0) {
    Write-StatusBadge 'AVD' "No online Android device detected."
    try {
        $avd = Get-PreferredAvd
    } catch {
        Write-StatusBadge 'ERROR' "$_"
        exit 1
    }

    $headlessStr = if ($Headless) { " (headless mode: -no-window)" } else { "" }
    Write-StatusBadge 'AVD' "Booting preferred phone AVD '$avd'$headlessStr..."
    
    try {
        $null = Start-AvdEmulator -AvdName $avd -Headless:$Headless
    } catch {
        Write-StatusBadge 'ERROR' "Failed to start emulator: $_"
        exit 1
    }

    Write-StatusBadge 'AVD' "Waiting for 3-stage boot lock (sys.boot_completed, bootanim stopped, pm ready)..."
    try {
        $null = Wait-ForEmulatorBoot
    } catch {
        Write-StatusBadge 'ERROR' "Boot lock timeout or failure: $_"
        exit 1
    }

    Write-StatusBadge 'AVD' "Android device online and boot sequence complete."
} else {
    Write-StatusBadge 'AVD' "Using online Android device: $($onlineDevices -join ', ')"
}

# 2. Resolving APK (Local compilation or Latest-Main retrieval with fallback)
$apkPath = $null
$isWindowsOs = ($env:OS -match 'Windows') -or ($IsWindows -eq $true)
$gradlewCmd = if ($isWindowsOs) { ".\gradlew.bat" } else { "./gradlew" }

if ($Local) {
    Write-StatusBadge 'DEPLOY' "Compiling debug APK locally via Gradle (:app:assembleDebug --build-cache --parallel)..."
    & $gradlewCmd :app:assembleDebug --build-cache --parallel
    if ($LASTEXITCODE -ne 0) {
        Write-StatusBadge 'ERROR' "Gradle compilation failed with exit code $LASTEXITCODE."
        exit $LASTEXITCODE
    }

    $debugApk = Join-Path $PSScriptRoot "../app/build/outputs/apk/debug/app-debug.apk"
    if (Test-Path $debugApk) {
        $apkPath = [System.IO.Path]::GetFullPath($debugApk)
    } else {
        # Search recursively in outputs
        $found = Get-ChildItem -Path (Join-Path $PSScriptRoot "../app/build/outputs/apk") -Filter "*.apk" -Recurse -File | Select-Object -First 1
        if ($found) {
            $apkPath = $found.FullName
        } else {
            Write-StatusBadge 'ERROR' "Could not find assembled APK in app/build/outputs/apk/"
            exit 1
        }
    }
} else {
    Write-StatusBadge 'FETCH' "Retrieving latest verified build APK via GitHubArtifactHelper..."
    try {
        $apkPath = Get-LatestMainBuildApk
        if ([string]::IsNullOrWhiteSpace($apkPath)) {
            throw "Artifact retrieval returned empty or null path."
        }
        Write-StatusBadge 'FETCH' "Successfully resolved APK: $apkPath"
    } catch {
        Write-StatusBadge 'ERROR' "Artifact retrieval failed: $_"
        Write-StatusBadge 'DEPLOY' "Gracefully falling back to compiling locally via $gradlewCmd :app:assembleDebug..."
        & $gradlewCmd :app:assembleDebug --build-cache --parallel
        if ($LASTEXITCODE -ne 0) {
            Write-StatusBadge 'ERROR' "Fallback Gradle compilation failed with exit code $LASTEXITCODE."
            exit $LASTEXITCODE
        }
        $debugApk = Join-Path $PSScriptRoot "../app/build/outputs/apk/debug/app-debug.apk"
        if (Test-Path $debugApk) {
            $apkPath = [System.IO.Path]::GetFullPath($debugApk)
        } else {
            $found = Get-ChildItem -Path (Join-Path $PSScriptRoot "../app/build/outputs/apk") -Filter "*.apk" -Recurse -File | Select-Object -First 1
            if ($found) {
                $apkPath = $found.FullName
            } else {
                Write-StatusBadge 'ERROR' "Could not find assembled APK in app/build/outputs/apk/"
                exit 1
            }
        }
    }
}

# 3. Deployment and launch
Write-StatusBadge 'DEPLOY' "Deploying APK '$apkPath'..."
try {
    $null = Deploy-And-Launch-App -ApkPath $apkPath -ClearData:$ClearData
} catch {
    Write-StatusBadge 'ERROR' "Deployment failed: $_"
    exit 1
}

Write-StatusBadge 'SUCCESS' "CrossTraining application deployed and started successfully."
Write-StatusBadge 'INFO' "Logcat monitor hint: adb logcat -s CrossTrainingApp:* TimerService:*"
exit 0
