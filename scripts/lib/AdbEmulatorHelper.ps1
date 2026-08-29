<#
.SYNOPSIS
    Android Emulator and ADB lifecycle helper functions.
.DESCRIPTION
    Provides Get-PreferredAvd, Wait-ForEmulatorBoot, and Deploy-And-Launch-App.
    Supports AVD discovery with Pixel|Phone regex, 3-stage boot lock verification,
    signature conflict auto-healing (INSTALL_FAILED_UPDATE_INCOMPATIBLE), and --clear-data handling.
#>

function Get-PreferredAvd {
    [CmdletBinding()]
    [OutputType([string])]
    param(
        [string[]]$AvdList
    )

    if (-not $PSBoundParameters.ContainsKey('AvdList')) {
        $emulatorCmd = Get-Command emulator -ErrorAction SilentlyContinue
        if ($null -eq $emulatorCmd) {
            $androidHome = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
            $emulatorPath = Join-Path $androidHome "emulator\emulator.exe"
            if (Test-Path $emulatorPath) {
                $emulatorCmd = $emulatorPath
            } else {
                $emulatorCmd = "emulator"
            }
        }
        try {
            $rawList = & $emulatorCmd -list-avds 2>$null
            if ($rawList) {
                $AvdList = @($rawList | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
            } else {
                $AvdList = @()
            }
        } catch {
            $AvdList = @()
        }
    }

    $matched = @()
    if ($null -ne $AvdList) {
        foreach ($avd in $AvdList) {
            $trimmed = "$avd".Trim()
            if ($trimmed -match '(?i)(Pixel|Phone)' -and $trimmed -notmatch '(?i)(Wear|TV|Auto)') {
                $matched += $trimmed
            }
        }
    }

    if ($matched.Count -gt 0) {
        return $matched[0]
    }

    $errorMsg = "No compatible Android Phone AVD found (matching regex 'Pixel|Phone').`n" +
               "Please create a compatible AVD with sdkmanager and avdmanager:`n" +
               "  sdkmanager `"system-images;android-35;google_apis_playstore;x86_64`"`n" +
               "  avdmanager create avd -n Pixel_10_API_35 -k `"system-images;android-35;google_apis_playstore;x86_64`" --device `"pixel_6`""
    throw $errorMsg
}

function Wait-ForEmulatorBoot {
    [CmdletBinding()]
    [OutputType([bool])]
    param(
        [int]$TimeoutSeconds = 120,
        [int]$PollIntervalSeconds = 2,
        [scriptblock]$AdbCommand = $null
    )

    $defaultAdb = {
        param([string]$CommandArgs)
        $adbExe = if (Get-Command adb -ErrorAction SilentlyContinue) { "adb" } else {
            $androidHome = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
            Join-Path $androidHome "platform-tools\adb.exe"
        }
        & $adbExe $CommandArgs.Split(' ')
    }

    $execAdb = if ($null -ne $AdbCommand) { $AdbCommand } else { $defaultAdb }

    $elapsed = 0
    while ($elapsed -lt $TimeoutSeconds) {
        $bootCompleted = ""
        $bootAnim = ""
        $pmPath = ""

        try {
            $bootCompleted = (& $execAdb "shell getprop sys.boot_completed") -join ""
            $bootAnim = (& $execAdb "shell getprop init.svc.bootanim") -join ""
            $pmPath = (& $execAdb "shell pm path android") -join ""
        } catch {}

        $isBootComplete = ($bootCompleted.Trim() -eq "1")
        $isAnimStopped = ($bootAnim.Trim() -eq "stopped")
        $isPmReady = ($pmPath.Trim() -match 'package:')

        if ($isBootComplete -and $isAnimStopped -and $isPmReady) {
            return $true
        }

        Start-Sleep -Seconds $PollIntervalSeconds
        $elapsed += $PollIntervalSeconds
    }

    throw "Emulator failed to complete 3-stage boot sequencing within $TimeoutSeconds seconds."
}

function Deploy-And-Launch-App {
    [CmdletBinding()]
    [OutputType([bool])]
    param(
        [Parameter(Mandatory = $true)]
        [string]$ApkPath,
        [switch]$ClearData,
        [string]$PackageName = "com.fractanomics.crosstraining",
        [string]$MainActivity = ".MainActivity",
        [scriptblock]$AdbCommand = $null
    )

    $defaultAdb = {
        param([string]$CommandArgs)
        $adbExe = if (Get-Command adb -ErrorAction SilentlyContinue) { "adb" } else {
            $androidHome = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
            Join-Path $androidHome "platform-tools\adb.exe"
        }
        & $adbExe $CommandArgs.Split(' ')
    }

    $execAdb = if ($null -ne $AdbCommand) { $AdbCommand } else { $defaultAdb }

    if (-not (Test-Path $ApkPath)) {
        throw "APK file not found at: $ApkPath"
    }

    # Install APK with -r -d (replace existing, allow version downgrade)
    $installOutput = ""
    try {
        $installOutput = (& $execAdb "install -r -d `"$ApkPath`"") -join "`n"
    } catch {
        $installOutput = "$_"
    }

    if ($installOutput -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
        Write-Host "Detected signature mismatch (INSTALL_FAILED_UPDATE_INCOMPATIBLE). Auto-healing via uninstall..." -ForegroundColor Yellow
        try {
            & $execAdb "uninstall $PackageName" | Out-Null
        } catch {}
        $retryOutput = (& $execAdb "install -r -d `"$ApkPath`"") -join "`n"
        if ($retryOutput -notmatch 'Success') {
            throw "Failed to install APK after auto-healing: $retryOutput"
        }
    } elseif ($installOutput -notmatch 'Success' -and -not [string]::IsNullOrWhiteSpace($installOutput)) {
        if ($installOutput -match 'Failure|Error') {
            throw "Failed to install APK: $installOutput"
        }
    }

    if ($ClearData) {
        Write-Host "Clearing application data for $PackageName..." -ForegroundColor Cyan
        try {
            & $execAdb "shell pm clear $PackageName" | Out-Null
        } catch {}
    }

    # Launch main activity
    try {
        & $execAdb "shell am start -n $PackageName/$MainActivity" | Out-Null
    } catch {}

    return $true
}
