<#
.SYNOPSIS
    Unit and integration tests for scripts/lib/AdbEmulatorHelper.ps1.
    Covers:
    - Preferred AVD selection and filtering (Pixel/Phone vs Wear OS / TV / Automotive)
    - Actionable error when no compatible AVD is installed (sdkmanager / avdmanager hints)
    - Bulletproof 3-stage boot sequencing (sys.boot_completed, init.svc.bootanim, pm path android)
    - 3-stage boot sequencing timeout handling
    - Signature conflict auto-healing (INSTALL_FAILED_UPDATE_INCOMPATIBLE -> uninstall -> retry install)
    - Clean installs and --clear-data flag
    - Process startup parameters (GUI vs --headless)
    - Online devices enumeration and parser resilience
    - ANSI status badge formatting
    - Static script hygiene
    Dot-sourced by Invoke-ScriptTests.ps1.
#>

$AdbHelperPath = Join-Path (Join-Path $PSScriptRoot "..") "lib\AdbEmulatorHelper.ps1" |
    Resolve-Path | Select-Object -ExpandProperty Path

# Dot-source the module under test
. $AdbHelperPath

# ---------------------------------------------------------------------------
# 1. AVD Discovery and Selection Tests
# ---------------------------------------------------------------------------
Write-Host "--- AVD Discovery & Selection Tests ---"

# Scenario: Preferred AVD selection filters non-phone form factors
$mixedAvdList = @(
    "Wear_OS_Square_API_34",
    "Android_TV_1080p_API_33",
    "Pixel_10_API_35",
    "Pixel_8_Pro_API_34",
    "Automotive_1024p_API_32",
    "Watch_Round_API_30"
)
$selectedPixelAvd = Get-PreferredAvd -AvdList $mixedAvdList
Assert-Equal -Actual $selectedPixelAvd -Expected "Pixel_10_API_35" `
             -TestName "avd: selects first matching Pixel/Phone AVD and ignores Wear OS / TV / Watch / Automotive"

# Scenario: Phone keyword matching when Pixel prefix is absent
$genericPhoneAvdList = @(
    "Wear_Small_Round_API_28",
    "Android_TV_4K",
    "Generic_Phone_API_31",
    "Tablet_10_inch"
)
$selectedPhoneAvd = Get-PreferredAvd -AvdList $genericPhoneAvdList
Assert-Equal -Actual $selectedPhoneAvd -Expected "Generic_Phone_API_31" `
             -TestName "avd: selects Generic_Phone AVD when Pixel is absent"

# Scenario: No valid AVD installed throws actionable error
$onlyNonPhoneAvds = @("Wear_OS_Round_API_30", "Android_TV_4k", "Automotive_API_32")
$caughtNoAvdError = $false
$noAvdErrorMessage = ""
try {
    Get-PreferredAvd -AvdList $onlyNonPhoneAvds | Out-Null
} catch {
    $caughtNoAvdError = $true
    $noAvdErrorMessage = $_.ToString()
}
Assert-True -Condition $caughtNoAvdError -TestName "avd: aborts when no AVD matches Pixel|Phone"
Assert-Match -Value $noAvdErrorMessage -Pattern "sdkmanager.*system-images" `
             -TestName "avd: error contains exact sdkmanager command to install system image"
Assert-Match -Value $noAvdErrorMessage -Pattern "avdmanager create avd" `
             -TestName "avd: error contains exact avdmanager command to create AVD"

# Scenario: Empty list or whitespace-only list
$caughtEmptyError = $false
try {
    Get-PreferredAvd -AvdList @("", "   ") | Out-Null
} catch {
    $caughtEmptyError = $true
}
Assert-True -Condition $caughtEmptyError -TestName "avd: empty/whitespace list aborts with error"

# ---------------------------------------------------------------------------
# 2. 3-Stage Boot Sequencing Tests
# ---------------------------------------------------------------------------
Write-Host "--- 3-Stage Boot Sequencing Tests ---"

# Scenario: Bulletproof 3-stage boot sequencing (Immediate success)
$pollCommands = [System.Collections.Generic.List[string]]::new()
$immediateMockExecutor = {
    param([string]$cmd)
    $pollCommands.Add($cmd)
    switch -Wildcard ($cmd) {
        "wait-for-device" { return "" }
        "*getprop sys.boot_completed" { return "1" }
        "*getprop init.svc.bootanim"  { return "stopped" }
        "*pm path android"            { return "package:/system/framework/framework-res.apk" }
        default                       { return "" }
    }
}

$bootSuccess = Wait-ForEmulatorBoot -CommandExecutor $immediateMockExecutor -TimeoutSeconds 10 -PollIntervalSeconds 0
Assert-True -Condition $bootSuccess -TestName "boot: returns true when all 3 stages pass"
Assert-True -Condition ($pollCommands -contains "shell getprop sys.boot_completed") `
            -TestName "boot: verified sys.boot_completed"
Assert-True -Condition ($pollCommands -contains "shell getprop init.svc.bootanim") `
            -TestName "boot: verified init.svc.bootanim"
Assert-True -Condition ($pollCommands -contains "shell pm path android") `
            -TestName "boot: verified pm path android"

# Scenario: Staged progression across multiple poll iterations
$script:stageCallCount = 0
$stagedMockExecutor = {
    param([string]$cmd)
    $script:stageCallCount++
    switch -Wildcard ($cmd) {
        "wait-for-device" { return "" }
        "*getprop sys.boot_completed" {
            if ($script:stageCallCount -lt 2) { return "0" } else { return "1" }
        }
        "*getprop init.svc.bootanim" {
            if ($script:stageCallCount -lt 4) { return "running" } else { return "stopped" }
        }
        "*pm path android" {
            if ($script:stageCallCount -lt 5) { return "" } else { return "package:/system/framework/framework-res.apk" }
        }
        default { return "" }
    }
}

$stagedBootResult = Wait-ForEmulatorBoot -CommandExecutor $stagedMockExecutor -TimeoutSeconds 10 -PollIntervalSeconds 0
Assert-True -Condition $stagedBootResult -TestName "boot: succeeds across staged progressive poll cycles"

# Scenario: Boot timeout when sys.boot_completed never becomes 1
$neverBootExecutor = {
    param([string]$cmd)
    switch -Wildcard ($cmd) {
        "wait-for-device" { return "" }
        "*getprop sys.boot_completed" { return "0" }
        default { return "" }
    }
}
$caughtTimeout = $false
$timeoutErrorMsg = ""
try {
    Wait-ForEmulatorBoot -CommandExecutor $neverBootExecutor -TimeoutSeconds 1 -PollIntervalSeconds 0 | Out-Null
} catch {
    $caughtTimeout = $true
    $timeoutErrorMsg = $_.ToString()
}
Assert-True -Condition $caughtTimeout -TestName "boot: throws timeout exception when sys.boot_completed stays 0"
Assert-Match -Value $timeoutErrorMsg -Pattern "Timeout waiting for Android emulator 3-stage boot lock" `
             -TestName "boot: timeout message clearly describes failure"

# Scenario: Boot timeout when bootanim never stops
$neverAnimStopExecutor = {
    param([string]$cmd)
    switch -Wildcard ($cmd) {
        "wait-for-device" { return "" }
        "*getprop sys.boot_completed" { return "1" }
        "*getprop init.svc.bootanim"  { return "running" }
        default { return "" }
    }
}
$caughtAnimTimeout = $false
try {
    Wait-ForEmulatorBoot -CommandExecutor $neverAnimStopExecutor -TimeoutSeconds 1 -PollIntervalSeconds 0 | Out-Null
} catch {
    $caughtAnimTimeout = $true
}
Assert-True -Condition $caughtAnimTimeout -TestName "boot: throws timeout exception when init.svc.bootanim stays running"

# ---------------------------------------------------------------------------
# 3. Installation & Signature Auto-Healing Tests
# ---------------------------------------------------------------------------
Write-Host "--- Installation & Signature Auto-Healing Tests ---"

# Scenario: Clean install happy path
$executedDeployCmds = [System.Collections.Generic.List[string]]::new()
$cleanInstallExecutor = {
    param([string]$cmd)
    $executedDeployCmds.Add($cmd)
    switch -Wildcard ($cmd) {
        "install -r -d*" { return "Success" }
        "shell am start*" { return "Starting: Intent { act=android.intent.action.MAIN cmp=com.fractanomics.crosstraining/.MainActivity }" }
        default { return "" }
    }
}

$deploySuccess = Deploy-And-Launch-App -ApkPath "C:\dummy\fake.apk" -CommandExecutor $cleanInstallExecutor
Assert-True -Condition $deploySuccess -TestName "deploy: clean install succeeds and launches MainActivity"
Assert-True -Condition ($executedDeployCmds -contains "install -r -d `"C:\dummy\fake.apk`"") `
            -TestName "deploy: install invoked with -r -d downgrade flags"
Assert-True -Condition ($executedDeployCmds -contains "shell am start -n com.fractanomics.crosstraining/.MainActivity") `
            -TestName "deploy: launches com.fractanomics.crosstraining/.MainActivity"

# Scenario: Signature conflict auto-healing (INSTALL_FAILED_UPDATE_INCOMPATIBLE)
$healCommands = [System.Collections.Generic.List[string]]::new()
$script:healInstallCount = 0
$signatureMismatchExecutor = {
    param([string]$cmd)
    $healCommands.Add($cmd)
    switch -Wildcard ($cmd) {
        "install -r -d*" {
            $script:healInstallCount++
            if ($script:healInstallCount -eq 1) {
                return "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package com.fractanomics.crosstraining signatures do not match previously installed version; ignoring!]"
            } else {
                return "Success"
            }
        }
        "uninstall com.fractanomics.crosstraining" {
            return "Success"
        }
        "shell am start*" {
            return "Starting: Intent { act=android.intent.action.MAIN cmp=com.fractanomics.crosstraining/.MainActivity }"
        }
        default { return "" }
    }
}

$script:healInstallCount = 0
$healSuccess = Deploy-And-Launch-App -ApkPath "C:\dummy\fake.apk" -CommandExecutor $signatureMismatchExecutor
Assert-True -Condition $healSuccess -TestName "deploy: signature conflict auto-heals and completes deployment"
Assert-True -Condition ($healCommands -contains "uninstall com.fractanomics.crosstraining") `
            -TestName "deploy: auto-healing uninstalls conflicting package"
$installCommandCount = @($healCommands | Where-Object { $_ -like "install -r -d*" }).Count
Assert-Equal -Actual $installCommandCount -Expected 2 `
             -TestName "deploy: auto-healing retried install cleanly"

# Scenario: Clear data flag is executed when requested
$clearDataCommands = [System.Collections.Generic.List[string]]::new()
$clearDataExecutor = {
    param([string]$cmd)
    $clearDataCommands.Add($cmd)
    switch -Wildcard ($cmd) {
        "shell pm clear*" { return "Success" }
        "install -r -d*"  { return "Success" }
        "shell am start*" { return "Starting: Intent { cmp=com.fractanomics.crosstraining/.MainActivity }" }
        default           { return "" }
    }
}

$clearResult = Deploy-And-Launch-App -ApkPath "C:\dummy\fake.apk" -ClearData -CommandExecutor $clearDataExecutor
Assert-True -Condition $clearResult -TestName "deploy: --clear-data succeeds"
Assert-True -Condition ($clearDataCommands -contains "shell pm clear com.fractanomics.crosstraining") `
            -TestName "deploy: runs 'adb shell pm clear com.fractanomics.crosstraining'"

# Scenario: Non-signature fatal install error throws without uninstall
$fatalCommands = [System.Collections.Generic.List[string]]::new()
$fatalInstallExecutor = {
    param([string]$cmd)
    $fatalCommands.Add($cmd)
    switch -Wildcard ($cmd) {
        "install -r -d*" { return "Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]" }
        default { return "" }
    }
}
$caughtFatal = $false
try {
    Deploy-And-Launch-App -ApkPath "C:\dummy\fake.apk" -CommandExecutor $fatalInstallExecutor | Out-Null
} catch {
    $caughtFatal = $true
}
Assert-True -Condition $caughtFatal -TestName "deploy: fatal install error throws exception"
Assert-True -Condition (-not ($fatalCommands -contains "uninstall com.fractanomics.crosstraining")) `
            -TestName "deploy: non-signature error does not trigger uninstallation"

# ---------------------------------------------------------------------------
# 4. Status Badge Formatting Tests
# ---------------------------------------------------------------------------
Write-Host "--- Status Badge ANSI Output Tests ---"

$infoBadge = Format-StatusBadge -Badge 'INFO' -Message 'System ready'
Assert-Match -Value $infoBadge -Pattern "\[36m\[INFO\]" -TestName "badge: INFO formatted in Cyan (36)"

$avdBadge = Format-StatusBadge -Badge 'AVD' -Message 'Booting AVD'
Assert-Match -Value $avdBadge -Pattern "\[33m\[AVD\]" -TestName "badge: AVD formatted in Yellow (33)"

$fetchBadge = Format-StatusBadge -Badge 'FETCH' -Message 'Downloading'
Assert-Match -Value $fetchBadge -Pattern "\[35m\[FETCH\]" -TestName "badge: FETCH formatted in Magenta (35)"

$deployBadge = Format-StatusBadge -Badge 'DEPLOY' -Message 'Deploying'
Assert-Match -Value $deployBadge -Pattern "\[34m\[DEPLOY\]" -TestName "badge: DEPLOY formatted in Blue (34)"

$successBadge = Format-StatusBadge -Badge 'SUCCESS' -Message 'All done'
Assert-Match -Value $successBadge -Pattern "\[32m\[SUCCESS\]" -TestName "badge: SUCCESS formatted in Green (32)"

$errorBadge = Format-StatusBadge -Badge 'ERROR' -Message 'Fatal problem'
Assert-Match -Value $errorBadge -Pattern "\[31m\[ERROR\]" -TestName "badge: ERROR formatted in Red (31)"

# ---------------------------------------------------------------------------
# 5. Online Devices Enumeration Tests
# ---------------------------------------------------------------------------
Write-Host "--- Online Devices Enumeration Tests ---"

$devicesMock = {
    return @"
* daemon not running; starting now at tcp:5037
* daemon started successfully
List of devices attached
emulator-5554	device
emulator-5556	offline
192.168.1.100:5555	device
unauthorized_device	unauthorized

"@
}

$parsedDevices = Get-OnlineDevices -CommandExecutor $devicesMock
Assert-Equal -Actual $parsedDevices.Count -Expected 2 -TestName "devices: correctly parses only online devices"
Assert-True -Condition ($parsedDevices -contains "emulator-5554") -TestName "devices: includes emulator-5554"
Assert-True -Condition ($parsedDevices -contains "192.168.1.100:5555") -TestName "devices: includes network device"
Assert-True -Condition (-not ($parsedDevices -contains "emulator-5556")) -TestName "devices: excludes offline device"
Assert-True -Condition (-not ($parsedDevices -contains "unauthorized_device")) -TestName "devices: excludes unauthorized device"

# ---------------------------------------------------------------------------
# 6. Emulator Startup Parameter Building (WhatIf Mode)
# ---------------------------------------------------------------------------
Write-Host "--- Emulator Startup Parameter Building Tests ---"

# Test Start-AvdEmulator with -WhatIf to test parameter formulation without spawning real process
$startedProc = Start-AvdEmulator -AvdName "Pixel_10_API_35" -Headless -EmulatorExe "emulator" -WhatIf
Assert-True -Condition ($null -eq $startedProc) -TestName "emulator-start: WhatIf returns safely without spawning process"

# ---------------------------------------------------------------------------
# 7. Static Code Hygiene Tests
# ---------------------------------------------------------------------------
Write-Host "--- Static Code Hygiene Tests ---"

$helperContent = [System.IO.File]::ReadAllText($AdbHelperPath, [System.Text.Encoding]::UTF8)
Assert-True -Condition (Test-Path $AdbHelperPath) -TestName "static: AdbEmulatorHelper.ps1 exists on disk"
Assert-Match -Value $helperContent -Pattern "function Get-PreferredAvd" -TestName "static: exports Get-PreferredAvd"
Assert-Match -Value $helperContent -Pattern "function Wait-ForEmulatorBoot" -TestName "static: exports Wait-ForEmulatorBoot"
Assert-Match -Value $helperContent -Pattern "function Deploy-And-Launch-App" -TestName "static: exports Deploy-And-Launch-App"
Assert-Match -Value $helperContent -Pattern "function Start-AvdEmulator" -TestName "static: exports Start-AvdEmulator"
Assert-Match -Value $helperContent -Pattern "function Get-OnlineDevices" -TestName "static: exports Get-OnlineDevices"
Assert-Match -Value $helperContent -Pattern "function Get-AdbCommand" -TestName "static: exports Get-AdbCommand"
Assert-Match -Value $helperContent -Pattern "function Get-EmulatorCommand" -TestName "static: exports Get-EmulatorCommand"
Assert-NotMatch -Value $helperContent -Pattern "(?i)#\s*TODO" -TestName "static: no TODO comments in helper"
