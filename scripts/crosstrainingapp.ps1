<#
.SYNOPSIS
    CrossTraining unified CLI for emulator provisioning, build caching, and deployment.
.DESCRIPTION
    Router for the 'emulator' subcommand and execution flags.
    Usage:
        pwsh ./scripts/crosstrainingapp.ps1 emulator --latest-main
        pwsh ./scripts/crosstrainingapp.ps1 emulator --local
        pwsh ./scripts/crosstrainingapp.ps1 emulator --headless
        pwsh ./scripts/crosstrainingapp.ps1 emulator --clear-data
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0, Mandatory = $false)]
    [string]$Command = "emulator",

    [Alias("latest-main")]
    [switch]$LatestMain,

    [switch]$Local,

    [switch]$Headless,

    [Alias("clear-data")]
    [switch]$ClearData
)

$ErrorActionPreference = 'Stop'

# Import helper libraries
$libDir = Join-Path $PSScriptRoot "lib"
. (Join-Path $libDir "AdbEmulatorHelper.ps1")
. (Join-Path $libDir "GitHubArtifactHelper.ps1")

function Write-StatusBadge {
    param(
        [ValidateSet("INFO", "AVD", "FETCH", "DEPLOY", "SUCCESS", "ERROR")]
        [string]$Badge,
        [string]$Message
    )
    $colorMap = @{
        "INFO"    = "Cyan"
        "AVD"     = "Yellow"
        "FETCH"   = "Magenta"
        "DEPLOY"  = "Blue"
        "SUCCESS" = "Green"
        "ERROR"   = "Red"
    }
    $c = $colorMap[$Badge]
    Write-Host "[$Badge] " -ForegroundColor $c -NoNewline
    Write-Host $Message
}

function Invoke-LocalBuild {
    Write-StatusBadge "DEPLOY" "Compiling locally with assembleDebug..."
    $isWin = [System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT -or [System.IO.Path]::DirectorySeparatorChar -eq '\'
    $gradlew = if ($isWin) { ".\gradlew.bat" } else { "./gradlew" }
    
    $buildArgs = @(":app:assembleDebug", "--build-cache", "--parallel")
    $proc = Start-Process -FilePath $gradlew -ArgumentList ($buildArgs -join " ") -Wait -PassThru -NoNewWindow
    if ($proc.ExitCode -ne 0) {
        throw "Local Gradle compilation failed with exit code $($proc.ExitCode)."
    }

    $localApk = "app/build/outputs/apk/debug/app-debug.apk"
    if (-not (Test-Path $localApk)) {
        throw "Build succeeded but APK not found at $localApk."
    }
    return (Resolve-Path $localApk).Path
}

if ($Command -ne "emulator") {
    Write-StatusBadge "ERROR" "Unknown command '$Command'. Supported commands: emulator"
    exit 1
}

Write-StatusBadge "INFO" "Starting CrossTrainingApp CLI runner..."

# 1. Determine APK to deploy
$apkToDeploy = $null

if ($Local) {
    Write-StatusBadge "INFO" "Local compilation mode requested (--local)."
    $apkToDeploy = Invoke-LocalBuild
} elseif ($LatestMain) {
    Write-StatusBadge "FETCH" "Fetching verified latest-main build artifact..."
    try {
        $apkToDeploy = Get-LatestMainBuildApk
    } catch {
        Write-StatusBadge "ERROR" "Failed to retrieve main artifact from GitHub: $_"
        Write-StatusBadge "DEPLOY" "Falling back to local Gradle compilation..."
        $apkToDeploy = Invoke-LocalBuild
    }
} else {
    Write-StatusBadge "FETCH" "No build source specified; defaulting to --latest-main..."
    try {
        $apkToDeploy = Get-LatestMainBuildApk
    } catch {
        Write-StatusBadge "ERROR" "Failed to retrieve main artifact: $_"
        Write-StatusBadge "DEPLOY" "Falling back to local compilation..."
        $apkToDeploy = Invoke-LocalBuild
    }
}

# 2. Check / Launch Emulator
Write-StatusBadge "AVD" "Checking online Android devices..."
$adbDevices = @()
try {
    $adbDevices = @(adb devices 2>$null | Where-Object { $_ -match 'device$' -and $_ -notmatch 'List of' })
} catch {}

if ($adbDevices.Count -eq 0) {
    Write-StatusBadge "AVD" "No running emulator found. Selecting preferred AVD..."
    $avdName = Get-PreferredAvd
    Write-StatusBadge "AVD" "Launching AVD: $avdName (Headless: $Headless)..."
    
    $emuArgs = @("-avd", $avdName, "-no-boot-anim", "-no-snapshot-save", "-gpu", "host", "-netdelay", "none", "-netspeed", "full")
    if ($Headless) {
        $emuArgs += "-no-window"
    }

    $emulatorCmd = if (Get-Command emulator -ErrorAction SilentlyContinue) { "emulator" } else { "emulator.exe" }
    Start-Process -FilePath $emulatorCmd -ArgumentList ($emuArgs -join " ")

    Write-StatusBadge "AVD" "Waiting for 3-stage boot sequencing lock..."
    Wait-ForEmulatorBoot
    Write-StatusBadge "AVD" "AVD boot completed and verified responsive."
} else {
    Write-StatusBadge "AVD" "Active Android device detected."
}

# 3. Deploy APK
Write-StatusBadge "DEPLOY" "Deploying APK ($apkToDeploy)..."
Deploy-And-Launch-App -ApkPath $apkToDeploy -ClearData:$ClearData

Write-StatusBadge "SUCCESS" "Application deployed and launched successfully!"
Write-Host ""
Write-Host "Logcat diagnostic monitoring hint:" -ForegroundColor Cyan
Write-Host "  adb logcat -s CrossTrainingApp:* TimerService:*" -ForegroundColor Yellow
Write-Host ""
