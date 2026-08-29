<#
.SYNOPSIS
    Helper module for Android ADB and Emulator operations.
.DESCRIPTION
    Provides functions for AVD discovery, process management, 3-stage boot lock polling,
    and signature-conflict-safe APK deployment and app launching.
#>

function Format-StatusBadge {
    [CmdletBinding()]
    [OutputType([string])]
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet('INFO', 'AVD', 'FETCH', 'DEPLOY', 'SUCCESS', 'ERROR')]
        [string]$Badge,

        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    $colorMap = @{
        'INFO'    = '36' # Cyan
        'AVD'     = '33' # Yellow
        'FETCH'   = '35' # Magenta
        'DEPLOY'  = '34' # Blue
        'SUCCESS' = '32' # Green
        'ERROR'   = '31' # Red
    }

    $code = $colorMap[$Badge]
    $esc = [char]27
    return "${esc}[${code}m[${Badge}]${esc}[0m ${Message}"
}

function Write-StatusBadge {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet('INFO', 'AVD', 'FETCH', 'DEPLOY', 'SUCCESS', 'ERROR')]
        [string]$Badge,

        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    $formatted = Format-StatusBadge -Badge $Badge -Message $Message
    Write-Host $formatted
}

function Get-AdbCommand {
    [CmdletBinding()]
    [OutputType([string])]
    param()

    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($null -ne $cmd) {
        return $cmd.Source
    }

    $candidates = @()
    if ($env:ANDROID_HOME) {
        $candidates += Join-Path $env:ANDROID_HOME "platform-tools/adb.exe"
        $candidates += Join-Path $env:ANDROID_HOME "platform-tools/adb"
    }
    if ($env:ANDROID_SDK_ROOT) {
        $candidates += Join-Path $env:ANDROID_SDK_ROOT "platform-tools/adb.exe"
        $candidates += Join-Path $env:ANDROID_SDK_ROOT "platform-tools/adb"
    }
    if ($env:LOCALAPPDATA) {
        $candidates += Join-Path $env:LOCALAPPDATA "Android/Sdk/platform-tools/adb.exe"
    }

    foreach ($c in $candidates) {
        if ([System.IO.File]::Exists($c)) {
            return $c
        }
    }

    return "adb"
}

function Get-EmulatorCommand {
    [CmdletBinding()]
    [OutputType([string])]
    param()

    $cmd = Get-Command emulator -ErrorAction SilentlyContinue
    if ($null -ne $cmd) {
        return $cmd.Source
    }

    $candidates = @()
    if ($env:ANDROID_HOME) {
        $candidates += Join-Path $env:ANDROID_HOME "emulator/emulator.exe"
        $candidates += Join-Path $env:ANDROID_HOME "emulator/emulator"
    }
    if ($env:ANDROID_SDK_ROOT) {
        $candidates += Join-Path $env:ANDROID_SDK_ROOT "emulator/emulator.exe"
        $candidates += Join-Path $env:ANDROID_SDK_ROOT "emulator/emulator"
    }
    if ($env:LOCALAPPDATA) {
        $candidates += Join-Path $env:LOCALAPPDATA "Android/Sdk/emulator/emulator.exe"
    }

    foreach ($c in $candidates) {
        if ([System.IO.File]::Exists($c)) {
            return $c
        }
    }

    return "emulator"
}

function Get-OnlineDevices {
    [CmdletBinding()]
    [OutputType([string[]])]
    param(
        [string]$AdbExe = $null
    )

    if ([string]::IsNullOrWhiteSpace($AdbExe)) {
        $AdbExe = Get-AdbCommand
    }

    $devices = @()
    try {
        $output = & $AdbExe devices 2>$null
        foreach ($line in ($output -split "`r?`n")) {
            $trimmed = $line.Trim()
            if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("List of devices") -or $trimmed.StartsWith("*")) {
                continue
            }
            if ($trimmed -match '^([^\s]+)\s+device$') {
                $devices += $Matches[1]
            }
        }
    } catch {
        # Return empty list on adb query error
    }

    return $devices
}

function Get-PreferredAvd {
    [CmdletBinding()]
    [OutputType([string])]
    param(
        [string[]]$AvdList = $null,
        [string]$EmulatorExe = $null
    )

    if ($null -eq $AvdList) {
        if ([string]::IsNullOrWhiteSpace($EmulatorExe)) {
            $EmulatorExe = Get-EmulatorCommand
        }
        try {
            $rawList = & $EmulatorExe -list-avds 2>$null
            $AvdList = @($rawList -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
        } catch {
            $AvdList = @()
        }
    }

    $candidates = @()
    foreach ($avd in $AvdList) {
        $name = $avd.Trim()
        if ([string]::IsNullOrWhiteSpace($name)) { continue }
        # Exclude non-phone form factors
        if ($name -match 'Wear|TV|Watch|Automotive') {
            continue
        }
        # Require phone or Pixel
        if ($name -match 'Pixel|Phone') {
            $candidates += $name
        }
    }

    if ($candidates.Count -gt 0) {
        return $candidates[0]
    }

    $errorMsg = "No compatible phone AVD found (expected Pixel or Phone matching name). Please install and create one, for example:`n" +
               "sdkmanager `"system-images;android-35;google_apis_playstore;x86_64`"`n" +
               "avdmanager create avd -n Pixel_10_API_35 -k `"system-images;android-35;google_apis_playstore;x86_64`" --device `"pixel_6`""
    throw $errorMsg
}

function Start-AvdEmulator {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [string]$AvdName,

        [switch]$Headless,

        [string]$EmulatorExe = $null
    )

    if ([string]::IsNullOrWhiteSpace($EmulatorExe)) {
        $EmulatorExe = Get-EmulatorCommand
    }

    $argsList = @(
        '-avd', $AvdName,
        '-no-boot-anim',
        '-no-snapshot-save',
        '-gpu', 'host',
        '-netdelay', 'none',
        '-netspeed', 'full'
    )

    if ($Headless) {
        $argsList += '-no-window'
    }

    # Launch detached process so terminal Ctrl+C will not terminate the emulator
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $EmulatorExe
    $psi.Arguments = ($argsList -join ' ')
    $psi.UseShellExecute = $true
    $psi.CreateNoWindow = $Headless.IsPresent

    $proc = [System.Diagnostics.Process]::Start($psi)
    return $proc
}

function Wait-ForEmulatorBoot {
    [CmdletBinding()]
    [OutputType([bool])]
    param(
        [string]$AdbExe = $null,
        [int]$TimeoutSeconds = 120,
        [int]$PollIntervalSeconds = 2
    )

    if ([string]::IsNullOrWhiteSpace($AdbExe)) {
        $AdbExe = Get-AdbCommand
    }

    $startTime = [System.DateTime]::UtcNow
    $timeout = [System.TimeSpan]::FromSeconds($TimeoutSeconds)

    # Wait for adb server/device recognition
    try {
        & $AdbExe wait-for-device 2>$null
    } catch {}

    while (([System.DateTime]::UtcNow - $startTime) -lt $timeout) {
        $bootCompleted = $false
        $animStopped = $false
        $pmReady = $false

        try {
            $propBoot = & $AdbExe shell getprop sys.boot_completed 2>$null
            if ($null -ne $propBoot -and $propBoot.Trim() -eq '1') {
                $bootCompleted = $true
            }
        } catch {}

        if ($bootCompleted) {
            try {
                $propAnim = & $AdbExe shell getprop init.svc.bootanim 2>$null
                if ($null -ne $propAnim -and $propAnim.Trim() -eq 'stopped') {
                    $animStopped = $true
                }
            } catch {}
        }

        if ($bootCompleted -and $animStopped) {
            try {
                $pmPath = & $AdbExe shell pm path android 2>$null
                if ($LASTEXITCODE -eq 0 -and $null -ne $pmPath -and $pmPath.Trim().StartsWith('package:')) {
                    $pmReady = $true
                }
            } catch {}
        }

        if ($bootCompleted -and $animStopped -and $pmReady) {
            return $true
        }

        Start-Sleep -Seconds $PollIntervalSeconds
    }

    throw "Timeout waiting for Android emulator 3-stage boot lock after $TimeoutSeconds seconds."
}

function Deploy-And-Launch-App {
    [CmdletBinding()]
    [OutputType([bool])]
    param(
        [Parameter(Mandatory = $true)]
        [string]$ApkPath,

        [string]$PackageName = "com.fractanomics.crosstraining",
        [string]$MainActivity = "com.fractanomics.crosstraining/.MainActivity",
        [switch]$ClearData,
        [string]$AdbExe = $null
    )

    if ([string]::IsNullOrWhiteSpace($AdbExe)) {
        $AdbExe = Get-AdbCommand
    }

    if (-not [System.IO.File]::Exists($ApkPath)) {
        throw "APK file not found at path: $ApkPath"
    }

    # Step 1: Attempt install with downgrade / reinstall flags
    $installOutput = & $AdbExe install -r -d "$ApkPath" 2>&1
    $installText = $installOutput -join "`n"

    # Step 2: Signature conflict auto-healing
    if ($installText -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|INSTALL_FAILED_SHARED_USER_INCOMPATIBLE|signatures do not match') {
        Write-StatusBadge 'AVD' "Detected signature mismatch (INSTALL_FAILED_UPDATE_INCOMPATIBLE). Uninstalling existing package for clean install..."
        & $AdbExe uninstall $PackageName 2>&1 | Out-Null
        $retryOutput = & $AdbExe install -r -d "$ApkPath" 2>&1
        $retryText = $retryOutput -join "`n"
        if ($retryText -notmatch 'Success') {
            throw "Failed to install APK after clean retry: $retryText"
        }
    } elseif ($installText -notmatch 'Success' -and $LASTEXITCODE -ne 0) {
        throw "Failed to install APK: $installText"
    }

    # Step 3: Clear data if requested
    if ($ClearData) {
        Write-StatusBadge 'AVD' "Clearing application data (--clear-data)..."
        & $AdbExe shell pm clear $PackageName 2>&1 | Out-Null
    }

    # Step 4: Start MainActivity
    Write-StatusBadge 'DEPLOY' "Launching $MainActivity..."
    $launchOutput = & $AdbExe shell am start -n $MainActivity 2>&1
    $launchText = $launchOutput -join "`n"
    if ($launchText -match 'Error|Exception' -and $launchText -notmatch 'Warning: Activity not started') {
        Write-StatusBadge 'ERROR' "Warning during app launch: $launchText"
    }

    return $true
}
