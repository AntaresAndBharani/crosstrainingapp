<#
.SYNOPSIS
    Static source-text regression assertions for .agents/rules/local_test_apk.md
    and docs/local-testing.md.
    Verifies crosstrainingapp CLI usage, supported flags, SHA-based caching,
    AVD regex conventions, and 3-stage boot lock (#441, #446).
    Dot-sourced by Invoke-ScriptTests.ps1.
#>

$AgentRulePath = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "../../.agents/rules/local_test_apk.md")
)
$LocalTestingDocPath = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "../../docs/local-testing.md")
)

Write-Host "--- static hygiene: .agents/rules/local_test_apk.md ---"

Assert-True -Condition ([System.IO.File]::Exists($AgentRulePath)) `
            -TestName "static: local_test_apk.md exists"

$RuleText = [System.IO.File]::ReadAllText($AgentRulePath, [System.Text.Encoding]::UTF8)

# ---------------------------------------------------------------------------
# Agent Rule CLI Mandate & Scenarios (#441, #446)
# ---------------------------------------------------------------------------
Assert-True -Condition ($RuleText -match '\./scripts/crosstrainingapp\.ps1 emulator --latest-main') `
            -TestName "static: local_test_apk.md instructs running ./scripts/crosstrainingapp.ps1 emulator --latest-main"

Assert-True -Condition ($RuleText -match '(?i)(DO NOT|rather than|instead of).*(invoke|invoking|run|running).*(raw|direct).*(gradlew|adb)') `
            -TestName "static: local_test_apk.md prohibits raw gradlew or adb invocations directly"

Assert-True -Condition ($RuleText -match '--latest-main') `
            -TestName "static: local_test_apk.md documents --latest-main flag"

Assert-True -Condition ($RuleText -match '--local') `
            -TestName "static: local_test_apk.md documents --local flag"

Assert-True -Condition ($RuleText -match '--headless') `
            -TestName "static: local_test_apk.md documents --headless flag"

Assert-True -Condition ($RuleText -match '--clear-data') `
            -TestName "static: local_test_apk.md documents --clear-data flag"

Assert-True -Condition ($RuleText -match 'Pixel\|Phone') `
            -TestName "static: local_test_apk.md documents Pixel|Phone AVD regex convention"

Assert-True -Condition ($RuleText -match 'sys\.boot_completed' -and $RuleText -match 'init\.svc\.bootanim' -and $RuleText -match 'pm path android') `
            -TestName "static: local_test_apk.md documents 3-stage boot lock properties"

Assert-True -Condition ($RuleText -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') `
            -TestName "static: local_test_apk.md documents signature conflict auto-healing"

Assert-BalancedMarkdownDelimiters -Output $RuleText `
                                  -TestName "static: local_test_apk.md has balanced markdown delimiters"

Write-Host "--- static hygiene: docs/local-testing.md ---"

Assert-True -Condition ([System.IO.File]::Exists($LocalTestingDocPath)) `
            -TestName "static: local-testing.md exists"

$DocText = [System.IO.File]::ReadAllText($LocalTestingDocPath, [System.Text.Encoding]::UTF8)

# ---------------------------------------------------------------------------
# Documentation CLI Surface & Behavior (#441, #446)
# ---------------------------------------------------------------------------
Assert-True -Condition ($DocText -match 'crosstrainingapp\.ps1') `
            -TestName "static: local-testing.md references crosstrainingapp.ps1 CLI"

Assert-True -Condition ($DocText -match '--latest-main') `
            -TestName "static: local-testing.md documents --latest-main flag"

Assert-True -Condition ($DocText -match '--local') `
            -TestName "static: local-testing.md documents --local flag"

Assert-True -Condition ($DocText -match '--headless') `
            -TestName "static: local-testing.md documents --headless flag"

Assert-True -Condition ($DocText -match '--clear-data') `
            -TestName "static: local-testing.md documents --clear-data flag"

Assert-True -Condition ($DocText -match 'ci-main' -or $DocText -match 'app-debug-') `
            -TestName "static: local-testing.md documents SHA-based caching path/behavior"

Assert-True -Condition ($DocText -match 'Pixel\|Phone') `
            -TestName "static: local-testing.md documents Pixel|Phone AVD naming convention"

Assert-True -Condition ($DocText -match 'sys\.boot_completed' -and $DocText -match 'init\.svc\.bootanim' -and $DocText -match 'pm path android') `
            -TestName "static: local-testing.md documents 3-stage boot sequencing"

Assert-True -Condition ($DocText -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') `
            -TestName "static: local-testing.md documents signature conflict auto-healing"

Assert-True -Condition ($DocText -match '\[INFO\]' -and $DocText -match '\[AVD\]' -and $DocText -match '\[FETCH\]' -and $DocText -match '\[DEPLOY\]' -and $DocText -match '\[SUCCESS\]' -and $DocText -match '\[ERROR\]') `
            -TestName "static: local-testing.md documents ANSI status badge conventions"

Assert-True -Condition ($DocText -match 'adb logcat -s CrossTrainingApp:\* TimerService:\*') `
            -TestName "static: local-testing.md documents logcat monitoring hint"

Assert-BalancedMarkdownDelimiters -Output $DocText `
                                  -TestName "static: local-testing.md has balanced markdown delimiters"
