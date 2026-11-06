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
    secretmanager.googleapis.com `
    iam.googleapis.com `
    --project=$ProjectId

$projectNumber = gcloud projects describe $ProjectId --format="value(projectNumber)"
$runtimeSa = "$projectNumber-compute@developer.gserviceaccount.com"
$killSwitchSaName = "billing-killswitch"
$killSwitchSa = "$killSwitchSaName@$ProjectId.iam.gserviceaccount.com"
$jwtSecretName = "mcp-jwt-secret"

Write-Host "=== 4. Creating Pub/Sub Topic '$BudgetTopic' (if not exists) ===" -ForegroundColor Cyan
$existingTopics = gcloud pubsub topics list --project=$ProjectId --format="value(name)"
if (-not ($existingTopics -match "/topics/$BudgetTopic$")) {
    gcloud pubsub topics create $BudgetTopic --project=$ProjectId
}

Write-Host "=== 5a. Creating least-privilege Kill-Switch service account (if not exists) ===" -ForegroundColor Cyan
$existingSas = gcloud iam service-accounts list --project=$ProjectId --format="value(email)"
if (-not ($existingSas -contains $killSwitchSa)) {
    gcloud iam service-accounts create $killSwitchSaName --project=$ProjectId --display-name="Billing Kill-Switch"
}
# Project Billing Manager grants resourcemanager.projects.deleteBillingAssignment (needed to unlink billing).
gcloud projects add-iam-policy-binding $ProjectId `
    --member="serviceAccount:$killSwitchSa" `
    --role="roles/billing.projectManager" `
    --condition=None | Out-Null

Write-Host "=== 5b. Deploying Automated Pub/Sub Billing Kill-Switch (max-instances=1) ===" -ForegroundColor Cyan
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
    --service-account=$killSwitchSa `
    --set-env-vars="GCP_PROJECT_ID=$ProjectId"

Write-Host "=== 5c. Wiring Pub/Sub budget topic -> Kill-Switch (authenticated push subscription) ===" -ForegroundColor Cyan
$killSwitchUrl = gcloud run services describe "billing-killswitch" --region=$Region --project=$ProjectId --format="value(status.url)"
gcloud run services add-iam-policy-binding "billing-killswitch" `
    --region=$Region `
    --project=$ProjectId `
    --member="serviceAccount:$killSwitchSa" `
    --role="roles/run.invoker" | Out-Null
# Allow the Pub/Sub service agent to mint OIDC tokens for the push identity.
gcloud iam service-accounts add-iam-policy-binding $killSwitchSa `
    --project=$ProjectId `
    --member="serviceAccount:service-$projectNumber@gcp-sa-pubsub.iam.gserviceaccount.com" `
    --role="roles/iam.serviceAccountTokenCreator" | Out-Null
$pushSubscription = "$BudgetTopic-push"
$existingSubs = gcloud pubsub subscriptions list --project=$ProjectId --format="value(name)"
if (-not ($existingSubs -match "/subscriptions/$pushSubscription$")) {
    gcloud pubsub subscriptions create $pushSubscription `
        --project=$ProjectId `
        --topic=$BudgetTopic `
        --push-endpoint=$killSwitchUrl `
        --push-auth-service-account=$killSwitchSa `
        --ack-deadline=30
}

Write-Host "=== 5d. Ensuring MCP OAuth signing secret '$jwtSecretName' in Secret Manager ===" -ForegroundColor Cyan
$existingSecrets = gcloud secrets list --project=$ProjectId --format="value(name)"
if (-not ($existingSecrets -match "/secrets/$jwtSecretName$")) {
    $secretBytes = [byte[]]::new(48)
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($secretBytes)
    $secretFile = New-TemporaryFile
    try {
        [System.IO.File]::WriteAllText($secretFile.FullName, [Convert]::ToBase64String($secretBytes))
        gcloud secrets create $jwtSecretName `
            --project=$ProjectId `
            --replication-policy=automatic `
            --data-file=$secretFile.FullName
    }
    finally {
        Remove-Item $secretFile.FullName -Force -ErrorAction SilentlyContinue
    }
}
gcloud secrets add-iam-policy-binding $jwtSecretName `
    --project=$ProjectId `
    --member="serviceAccount:$runtimeSa" `
    --role="roles/secretmanager.secretAccessor" | Out-Null

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
    --service-account=$runtimeSa `
    --set-secrets="MCP_JWT_SECRET=${jwtSecretName}:latest" `
    --set-env-vars="FIREBASE_PROJECT_ID=$ProjectId"

Write-Host "=== 7. Enforcing Artifact Registry 2-Image Cleanup Policy (Free Tier <= 500MB Guard) ===" -ForegroundColor Cyan
$policyFile = Join-Path ([System.IO.Path]::GetTempPath()) "ar-cleanup-policy.json"
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
Write-Host ""
Write-Host "REQUIRED (manual, once): the kill-switch only fires if a Cloud Billing budget publishes to Pub/Sub." -ForegroundColor Yellow
Write-Host "  https://console.cloud.google.com/billing/budgets?project=$ProjectId" -ForegroundColor Yellow
Write-Host "  Create a `$5.00 budget (20/50/80/100%) and connect it to topic 'projects/$ProjectId/topics/$BudgetTopic'." -ForegroundColor Yellow
