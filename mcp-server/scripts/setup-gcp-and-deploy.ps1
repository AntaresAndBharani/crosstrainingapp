<#
.SYNOPSIS
    Configures anti-bill guardrails, Artifact Registry cleanup policy, Pub/Sub Billing Kill-Switch,
    and deploys the CrossTraining Remote MCP Server to Google Cloud Run (virgymia-c2cc7).
#>
param(
    [string]$ProjectId = "virgymia-c2cc7",
    [string]$Account = "fractanomics@gmail.com",
    [string]$Region = "europe-west1",
    [string]$ServiceName = "crosstraining-mcp",
    [string]$BudgetTopic = "billing-killswitch-alerts"
)

$ErrorActionPreference = "Stop"

Write-Host "=== 1. Configuring gcloud Account & Project ===" -ForegroundColor Cyan
gcloud config set account $Account
gcloud config set project $ProjectId

Write-Host "=== 2. Verifying Blaze Billing Status on $ProjectId ===" -ForegroundColor Cyan
$billingEnabled = gcloud billing projects describe $ProjectId --format="value(billingEnabled)"
if ($billingEnabled -ne "True") {
    Write-Warning "Billing is currently disabled (Spark Plan) on project '$ProjectId'."
    Write-Host @"
To complete deployment:
1. Open https://console.firebase.google.com/project/$ProjectId/overview (signed in as $Account)
2. Click 'Upgrade' -> select 'Blaze plan' and link your billing account.
3. Open https://console.cloud.google.com/billing/budgets?project=$ProjectId
   Create a `$5.00 budget alert (20%, 50%, 80%, 100%) and connect it to Pub/Sub topic '$BudgetTopic'.
4. Re-run this script: .\mcp-server\scripts\setup-gcp-and-deploy.ps1
"@ -ForegroundColor Yellow
    exit 1
}

Write-Host "=== 3. Enabling Required Google Cloud APIs ===" -ForegroundColor Cyan
gcloud services enable `
    run.googleapis.com `
    cloudbuild.googleapis.com `
    artifactregistry.googleapis.com `
    cloudbilling.googleapis.com `
    pubsub.googleapis.com `
    --project=$ProjectId

Write-Host "=== 4. Creating Pub/Sub Topic '$BudgetTopic' (if not exists) ===" -ForegroundColor Cyan
$existingTopics = gcloud pubsub topics list --project=$ProjectId --format="value(name)"
if (-not ($existingTopics -match "/topics/$BudgetTopic$")) {
    gcloud pubsub topics create $BudgetTopic --project=$ProjectId
}

Write-Host "=== 5. Deploying Automated Pub/Sub Billing Kill-Switch (max-instances=1) ===" -ForegroundColor Cyan
$killSwitchDir = Join-Path $PSScriptRoot "..\billing-killswitch"
gcloud run deploy "billing-killswitch" `
    --source=$killSwitchDir `
    --region=$Region `
    --project=$ProjectId `
    --no-allow-unauthenticated `
    --max-instances=1 `
    --min-instances=0 `
    --cpu=1 `
    --memory=128Mi `
    --cpu-throttling `
    --timeout=15s `
    --set-env-vars="GCP_PROJECT_ID=$ProjectId"

Write-Host "=== 6. Deploying CrossTraining Remote MCP Server (Hard Cap: max-instances=1, 256Mi) ===" -ForegroundColor Cyan
$mcpDir = Join-Path $PSScriptRoot ".."
gcloud run deploy $ServiceName `
    --source=$mcpDir `
    --region=$Region `
    --project=$ProjectId `
    --allow-unauthenticated `
    --max-instances=1 `
    --min-instances=0 `
    --cpu=1 `
    --memory=256Mi `
    --cpu-throttling `
    --concurrency=20 `
    --timeout=30s `
    --set-env-vars="FIREBASE_PROJECT_ID=$ProjectId"

Write-Host "=== 7. Enforcing Artifact Registry 2-Image Cleanup Policy (Free Tier <= 500MB Guard) ===" -ForegroundColor Cyan
$policyFile = Join-Path $env:TEMP "ar-cleanup-policy.json"
@"
[
  {
    "name": "delete-old-images",
    "action": {"type": "Delete"},
    "mostRecentVersions": {
      "keepCount": 2
    }
  }
]
"@ | Set-Content -Path $policyFile -Encoding UTF8

gcloud artifacts repositories set-cleanup-policies cloud-run-source-deploy `
    --project=$ProjectId `
    --location=$Region `
    --policy=$policyFile `
    --no-dry-run

Remove-Item $policyFile -ErrorAction SilentlyContinue

$serviceUrl = gcloud run services describe $ServiceName --region=$Region --project=$ProjectId --format="value(status.url)"
Write-Host ""
Write-Host "✅ Deployment Complete!" -ForegroundColor Green
Write-Host "Remote MCP Endpoint URL : $serviceUrl/mcp" -ForegroundColor Green
Write-Host "Legacy SSE Endpoint URL : $serviceUrl/sse" -ForegroundColor Green
Write-Host "OAuth Discovery URL     : $serviceUrl/.well-known/oauth-authorization-server" -ForegroundColor Green
