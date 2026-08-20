<#
.SYNOPSIS
    Runs local end-to-end (E2E) UI test flows using Maestro on an Android emulator or physical device.

.DESCRIPTION
    1. Verifies/Starts the Android emulator (Pixel_10_API_35 by default).
    2. Builds/installs the latest APK onto the target device.
    3. Runs declarative Maestro test flows (e2e/flows/*.yaml).
    4. Generates an execution report.

.EXAMPLE
    .\scripts\run-e2e-tests.ps1
    .\scripts\run-e2e-tests.ps1 -Flow "e2e/flows/01_auth_flow.yaml"
    .\scripts\run-e2e-tests.ps1 -AvdName "Pixel_10_API_35" -SkipBuild
#>
param (
    [string]$Flow = "e2e/flows",
    [string]$AvdName = "Pixel_10_API_35",
    [switch]$SkipBuild,
    [switch]$Headless,
    [switch]$CaptureArtifacts,
    [string]$OutputRepo = "..\virgymia-qa",
    [string]$Version = "latest",
    [switch]$PushArtifacts
)

$ErrorActionPreference = "Stop"

if ($Version -eq "latest") {
    $PrNumber = ""
    if ($null -ne (Get-Command gh -ErrorAction SilentlyContinue)) {
        try {
            $PrJson = gh pr view --json number 2>$null | ConvertFrom-Json
            if ($null -ne $PrJson -and $null -ne $PrJson.number) {
                $PrNumber = $PrJson.number
            }
        } catch {}
    }
    if ($PrNumber) {
        $Version = "e2e-pr-$PrNumber"
    } else {
        $Branch = git rev-parse --abbrev-ref HEAD 2>$null
        if (-not $Branch) { $Branch = "local" }
        $BranchSafe = $Branch -replace '[^a-zA-Z0-9-]', '-'
        $Version = "e2e-$BranchSafe"
    }
}
Write-Host " [APP] CrossTraining App - Local E2E Test Suite" -ForegroundColor Cyan
Write-Host " 📱 CrossTraining App - Local E2E Test Suite" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. Locate ADB
$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (!(Test-Path $AdbPath)) {
    $AdbCmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($AdbCmd) { $AdbPath = $AdbCmd.Source } else {
        Write-Error "Could not find adb.exe in Android SDK. Please ensure Android SDK is installed."
    }
}

# 2. Locate Maestro
$MaestroPath = "$env:USERPROFILE\.maestro\bin\maestro.bat"
if (!(Test-Path $MaestroPath)) {
    $MaestroCmd = Get-Command maestro -ErrorAction SilentlyContinue
    if ($MaestroCmd) { $MaestroPath = $MaestroCmd.Source } else {
        Write-Error "Maestro CLI not found at '$MaestroPath'. Please install Maestro CLI."
    }
}

# 3. Check for running devices
Write-Host "`n[1/4] Checking connected Android devices..." -ForegroundColor Yellow
$Devices = & $AdbPath devices | Where-Object { $_ -match "\bdevice$" }
if (-not $Devices) {
    Write-Host "No active Android device found. Booting AVD '$AvdName'..." -ForegroundColor Yellow
    if (Get-Command android -ErrorAction SilentlyContinue) {
        android emulator start $AvdName
    } else {
        $EmuPath = "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe"
        if (Test-Path $EmuPath) {
            Start-Process -FilePath $EmuPath -ArgumentList "-avd", $AvdName, "-no-snapshot-load"
        }
    }
    Write-Host "Waiting for device to boot..." -ForegroundColor Yellow
    & $AdbPath wait-for-device
}

$OnlineDevice = (& $AdbPath devices | Where-Object { $_ -match "\bdevice$" } | Select-Object -First 1).Split("`t")[0]
Write-Host "Connected device: $OnlineDevice" -ForegroundColor Green

# 4. Build and Install APK
if (-not $SkipBuild) {
    Write-Host "`n[2/4] Building latest snapshot APK..." -ForegroundColor Yellow
    .\gradlew.bat assembleSnapshot -PsnapshotLabel=localtest --no-daemon
    
    if (!(Test-Path "local_test")) { New-Item -ItemType Directory -Path "local_test" | Out-Null }
    if (Test-Path "app\build\outputs\apk\snapshot\app-snapshot.apk") {
        Copy-Item -Path "app\build\outputs\apk\snapshot\app-snapshot.apk" -Destination "local_test\latest.apk" -Force
    }
}

Write-Host "`n[3/4] Installing latest APK to $OnlineDevice..." -ForegroundColor Yellow
if (Test-Path "local_test\latest.apk") {
    & $AdbPath -s $OnlineDevice install -r "local_test\latest.apk"
} else {
    Write-Warning "local_test\latest.apk not found. Skipping install step."
}

# 5. Run Maestro E2E Flows
Write-Host "`n[4/4] Executing Maestro E2E test flows: '$Flow'..." -ForegroundColor Yellow
$Env:MAESTRO_CLI_NO_ANALYTICS = "true"
$Env:MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED = "true"

if ($CaptureArtifacts) {
    $ReportDir = "$OutputRepo\screenshots\$Version"
    if (!(Test-Path $ReportDir)) { New-Item -ItemType Directory -Path $ReportDir -Force | Out-Null }
    
    Write-Host "Capturing artifacts and HTML report to $ReportDir..." -ForegroundColor Cyan
    & $MaestroPath test $Flow --format html --output "$ReportDir\report.html" 2>&1 | Tee-Object -Variable CapturedOutput
    $TestExitCode = $LASTEXITCODE
    $MaestroOutput = $CapturedOutput | Out-String
    $MaestroOutput = $MaestroOutput -replace '\x1B\[[0-9;]*[a-zA-Z]', ''

    # Parse Maestro 2.8.0 output for pass/fail. Validated regex against version 2.8.0.
    $SummaryFile = "$ReportDir\summary.json"
    $FlowSummaries = @()
    # Match lines like "Passed flow_name.yaml" or "o. flow.yaml"
    $check = [char]::ConvertFromUtf32(0x2705)
    $cross = [char]::ConvertFromUtf32(0x274C)
    $Regex = '(?m)^.*?\[?(Passed|Failed|{0}|{1})\]?\s+([a-zA-Z0-9_-]+)(?:\.yaml|\s*\()' -f $check, $cross
    
    $Matches = [regex]::Matches($MaestroOutput, $Regex)
    foreach ($Match in $Matches) {
        $StatusStr = $Match.Groups[1].Value
        $FlowName = $Match.Groups[2].Value
        $MatchStr = '^(Passed|{0})$' -f $check
        $IsPassed = ($StatusStr -match $MatchStr)
        $FlowSummaries += @{ flow = $FlowName; passed = $IsPassed }
    }
    # Validate parsed flow count against actual targeted flow files
    $TargetFiles = if (Test-Path $Flow -PathType Leaf) { @(Get-Item $Flow) } else { Get-ChildItem -Path $Flow -Filter "*.yaml" -Recurse }
    $TargetCount = @($TargetFiles).Count
    if ($TargetCount -gt 0 -and $FlowSummaries.Count -ne $TargetCount) {
        Write-Error "Parsed flow count ($($FlowSummaries.Count)) does not match targeted flow files ($TargetCount). Maestro output format may have changed."
    }

    $FlowSummaries | ConvertTo-Json -Depth 2 | Set-Content $SummaryFile -Encoding utf8

    $LatestDir = "docs\screenshots"
    if (!(Test-Path $LatestDir)) { New-Item -ItemType Directory -Path $LatestDir -Force | Out-Null }

    Write-Host "Syncing latest screenshots to main repo ($LatestDir)..." -ForegroundColor Cyan
    Get-ChildItem -Path . -Filter "*.png" | Copy-Item -Destination $LatestDir -Force

    Write-Host "Archiving screenshots to QA repo ($ReportDir)..." -ForegroundColor Cyan
    Get-ChildItem -Path . -Filter "*.png" | Move-Item -Destination $ReportDir -Force

    if ($PushArtifacts) {
        Write-Host "Uploading artifacts to virgymia-qa release $Version..." -ForegroundColor Cyan
        
        $ReleaseExists = $false
        try {
            $null = gh release view $Version --repo AntaresAndBharani/virgymia-qa 2>&1
            $ReleaseExists = $LASTEXITCODE -eq 0
        } catch {}

        $FilesToUpload = @("$ReportDir\report.html") + (Get-ChildItem -Path $ReportDir -Filter "*.png" | Select-Object -ExpandProperty FullName)

        if ($ReleaseExists) {
            gh release upload $Version $FilesToUpload --repo AntaresAndBharani/virgymia-qa --clobber
        } else {
            gh release create $Version $FilesToUpload --repo AntaresAndBharani/virgymia-qa --title "E2E Evidence $Version" --notes "Automated E2E screenshots and HTML report."
        }
    }
} else {
    & $MaestroPath test $Flow
    $TestExitCode = $LASTEXITCODE
}

Write-Host "`n==========================================" -ForegroundColor Cyan
if ($TestExitCode -eq 0) {
    Write-Host " [PASS] All local E2E test flows passed successfully!" -ForegroundColor Green
} else {
    Write-Host " [FAIL] E2E test flows encountered failures (Exit Code: `$TestExitCode)." -ForegroundColor Red
}
Write-Host "==========================================" -ForegroundColor Cyan
exit $TestExitCode



