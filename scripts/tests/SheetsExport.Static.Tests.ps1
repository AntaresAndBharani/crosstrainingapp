<#
.SYNOPSIS
    Static source-text and contract regression assertions for the Google Sheets export bridge:
    - mcp-server/scripts/setup-gcp-and-deploy.ps1
    - mcp-server/scripts/google-sheets-sync.gs
    Dot-sourced by Invoke-ScriptTests.ps1.
#>

$DeployScriptPath = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "../../mcp-server/scripts/setup-gcp-and-deploy.ps1")
)
$AppsScriptPath = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "../../mcp-server/scripts/google-sheets-sync.gs")
)

Write-Host "--- static hygiene: setup-gcp-and-deploy.ps1 & google-sheets-sync.gs ---"

# ---------------------------------------------------------------------------
# 1. Cloud Run Deploy Script (mcp-server/scripts/setup-gcp-and-deploy.ps1)
# ---------------------------------------------------------------------------
Assert-True -Condition ([System.IO.File]::Exists($DeployScriptPath)) `
            -TestName "static: setup-gcp-and-deploy.ps1 exists"

$DeployText = [System.IO.File]::ReadAllText($DeployScriptPath, [System.Text.Encoding]::UTF8)

Assert-True -Condition ($DeployText -match '\$SheetsExportUid\s*=' -and $DeployText -match '\$SheetsExportEnv\s*=') `
            -TestName "static: setup-gcp-and-deploy.ps1 defines optional -SheetsExportUid and -SheetsExportEnv params"

Assert-True -Condition ($DeployText -match 'sheets-export-token' -and $DeployText -match 'roles/secretmanager\.secretAccessor') `
            -TestName "static: setup-gcp-and-deploy.ps1 provisions sheets-export-token in Secret Manager with secretAccessor IAM"

Assert-True -Condition ($DeployText -match 'SHEETS_EXPORT_UID' -and $DeployText -match 'SHEETS_EXPORT_ENV' -and $DeployText -match 'gcloud run services describe') `
            -TestName "static: setup-gcp-and-deploy.ps1 preserves existing SHEETS_EXPORT_UID and SHEETS_EXPORT_ENV on redeploy"

Assert-True -Condition ($DeployText -match '--set-secrets="\$deploySecrets"' -and $DeployText -match '--set-env-vars="\$deployEnvVars"' -and $DeployText -match 'SHEETS_EXPORT_TOKEN=\$\{sheetsExportSecretName\}:latest') `
            -TestName "static: setup-gcp-and-deploy.ps1 passes quoted comma-separated secrets and env vars to gcloud run deploy"

Assert-True -Condition (-not ($DeployText -match 'Write-Host.*\$\w*hexToken')) `
            -TestName "static: setup-gcp-and-deploy.ps1 never prints the generated export token"

# ---------------------------------------------------------------------------
# 2. Google Apps Script Bridge (mcp-server/scripts/google-sheets-sync.gs)
# ---------------------------------------------------------------------------
Assert-True -Condition ([System.IO.File]::Exists($AppsScriptPath)) `
            -TestName "static: google-sheets-sync.gs exists"

$AppsText = [System.IO.File]::ReadAllText($AppsScriptPath, [System.Text.Encoding]::UTF8)

Assert-True -Condition ($AppsText -match 'PropertiesService\.getScriptProperties\(\)' -and $AppsText -match 'EXPORT_URL' -and $AppsText -match 'EXPORT_TOKEN') `
            -TestName "static: google-sheets-sync.gs reads EXPORT_URL and EXPORT_TOKEN from ScriptProperties"

Assert-True -Condition ($AppsText -match 'Authorization:\s*"Bearer "\s*\+\s*token') `
            -TestName "static: google-sheets-sync.gs sends Bearer token in Authorization header"

Assert-True -Condition ($AppsText -match 'sheet\.clearContents\(\)' -and (-not ($AppsText -match 'deleteSheet\('))) `
            -TestName "static: google-sheets-sync.gs clears sheet contents in place without deleting tabs"

$ExpectedTabs = @("Dashboard", "Weights", "Sessions_Sets", "PRs", "Cycles", "Cycle_Goals", "Daily_Logs")
$AllTabsPresent = $true
foreach ($tab in $ExpectedTabs) {
    if (-not ($AppsText -match [regex]::Escape("""$tab"""))) {
        $AllTabsPresent = $false
    }
}
Assert-True -Condition $AllTabsPresent `
            -TestName "static: google-sheets-sync.gs configures Dashboard and all 6 exported data tabs"

Assert-True -Condition ($AppsText -match 'installDailySyncTrigger' -and $AppsText -match 'syncCrossTrainingData') `
            -TestName "static: google-sheets-sync.gs provides manual sync and daily auto-sync trigger setup"

Assert-True -Condition (-not ($AppsText -match '[0-9a-f]{64}')) `
            -TestName "static: google-sheets-sync.gs contains no hardcoded 64-char secret tokens"
