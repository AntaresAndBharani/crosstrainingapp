<#
.SYNOPSIS
    Configures anti-bill guardrails, Artifact Registry cleanup policy, Pub/Sub Billing Kill-Switch,
    automated 5-unit Cloud Billing Budget, Firebase Auth authorizedDomains, and deploys the
    CrossTraining Remote MCP Server to Google Cloud Run (virgymia-c2cc7).
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
gcloud config set account $Account --quiet
gcloud config set project $ProjectId --quiet
gcloud config set billing/quota_project $ProjectId --quiet

Write-Host "=== 2. Verifying Blaze Billing Status on $ProjectId ===" -ForegroundColor Cyan
$billingEnabled = gcloud billing projects describe $ProjectId --format="value(billingEnabled)" --quiet
if ($billingEnabled -ne "True") {
    Write-Warning "Billing is currently disabled (Spark Plan) on project '$ProjectId'."
    Write-Host @"
To complete deployment:
1. Open https://console.firebase.google.com/project/$ProjectId/overview (signed in as $Account)
2. Click 'Upgrade' -> select 'Blaze plan' and link your billing account.
3. Re-run this script: .\mcp-server\scripts\setup-gcp-and-deploy.ps1
"@ -ForegroundColor Yellow
    exit 1
}
$billingAccountFull = gcloud billing projects describe $ProjectId --format="value(billingAccountName)" --quiet
$billingAccountId = $billingAccountFull -replace "^billingAccounts/", ""

Write-Host "=== 3. Enabling Required Google Cloud APIs ===" -ForegroundColor Cyan
gcloud services enable `
    run.googleapis.com `
    cloudbuild.googleapis.com `
    artifactregistry.googleapis.com `
    cloudbilling.googleapis.com `
    billingbudgets.googleapis.com `
    pubsub.googleapis.com `
    secretmanager.googleapis.com `
    iam.googleapis.com `
    serviceusage.googleapis.com `
    --project=$ProjectId `
    --quiet

$projectNumber = gcloud projects describe $ProjectId --format="value(projectNumber)" --quiet
$runtimeSa = "$projectNumber-compute@developer.gserviceaccount.com"
$killSwitchSaName = "billing-killswitch"
$killSwitchSa = "$killSwitchSaName@$ProjectId.iam.gserviceaccount.com"
$jwtSecretName = "mcp-jwt-secret"

Write-Host "=== 3b. Granting Cloud Build & Runtime IAM Roles to $runtimeSa ===" -ForegroundColor Cyan
# The default compute SA is both the Cloud Build identity for `gcloud run deploy --source` and the
# MCP server runtime identity. Firebase ID-token verification (verifyIdToken without checkRevoked)
# uses Google's public signing keys and needs no IAM role, so roles/firebaseauth.admin is NOT granted
# to this internet-facing runtime identity.
$runtimeRoles = @(
    "roles/cloudbuild.builds.builder",
    "roles/storage.admin",
    "roles/artifactregistry.writer",
    "roles/logging.logWriter",
    "roles/datastore.user"
)
foreach ($role in $runtimeRoles) {
    gcloud projects add-iam-policy-binding $ProjectId `
        --member="serviceAccount:$runtimeSa" `
        --role=$role `
        --condition=None `
        --quiet | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Failed to grant $role to $runtimeSa." }
}

Write-Host "=== 4. Creating Pub/Sub Topic '$BudgetTopic' & Provisioning Service Agent ===" -ForegroundColor Cyan
$existingTopics = gcloud pubsub topics list --project=$ProjectId --format="value(name)" --quiet
if (-not ($existingTopics -match "/topics/$BudgetTopic$")) {
    gcloud pubsub topics create $BudgetTopic --project=$ProjectId --quiet
    if ($LASTEXITCODE -ne 0) { throw "Failed to create Pub/Sub topic '$BudgetTopic'." }
}

# Builds REST headers from a fresh gcloud access token. Content-Type is passed via -ContentType
# (not -Headers) because Windows PowerShell 5.1 rejects a Content-Type entry in -Headers.
function Get-GoogleApiHeaders {
    $token = gcloud auth print-access-token --quiet
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($token)) { throw "Failed to obtain a gcloud access token." }
    return @{
        Authorization         = "Bearer $token"
        "x-goog-user-project" = $ProjectId
    }
}

# Provision the Google-managed Pub/Sub service identity via Service Usage REST API (no gcloud beta component needed)
Invoke-RestMethod -Method Post `
    -Uri "https://serviceusage.googleapis.com/v1beta1/projects/$ProjectId/services/pubsub.googleapis.com:generateServiceIdentity" `
    -Headers (Get-GoogleApiHeaders) `
    -ContentType "application/json" `
    -Body "{}" | Out-Null

# Grant Cloud Billing Budget alert system service account permission to publish to this topic
gcloud pubsub topics add-iam-policy-binding $BudgetTopic `
    --project=$ProjectId `
    --member="serviceAccount:billing-budget-alert@system.gserviceaccount.com" `
    --role="roles/pubsub.publisher" `
    --quiet | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Failed to grant billing-budget-alert publisher access on '$BudgetTopic'." }

Write-Host "=== 5a. Creating least-privilege Kill-Switch service account (if not exists) ===" -ForegroundColor Cyan
$existingSas = gcloud iam service-accounts list --project=$ProjectId --format="value(email)" --quiet
if (-not ($existingSas -contains $killSwitchSa)) {
    gcloud iam service-accounts create $killSwitchSaName `
        --project=$ProjectId `
        --display-name="Billing Kill-Switch" `
        --quiet
}
# Project Billing Manager grants resourcemanager.projects.deleteBillingAssignment (needed to unlink billing).
gcloud projects add-iam-policy-binding $ProjectId `
    --member="serviceAccount:$killSwitchSa" `
    --role="roles/billing.projectManager" `
    --condition=None `
    --quiet | Out-Null

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
    --set-env-vars="GCP_PROJECT_ID=$ProjectId" `
    --quiet
if ($LASTEXITCODE -ne 0) { throw "Failed to deploy billing-killswitch to Cloud Run." }

Write-Host "=== 5c. Wiring Pub/Sub budget topic -> Kill-Switch (authenticated push subscription) ===" -ForegroundColor Cyan
$killSwitchUrl = gcloud run services describe "billing-killswitch" --region=$Region --project=$ProjectId --format="value(status.url)" --quiet
if ([string]::IsNullOrWhiteSpace($killSwitchUrl)) { throw "Could not resolve billing-killswitch URL." }

gcloud run services add-iam-policy-binding "billing-killswitch" `
    --region=$Region `
    --project=$ProjectId `
    --member="serviceAccount:$killSwitchSa" `
    --role="roles/run.invoker" `
    --quiet | Out-Null

# Allow the Pub/Sub service agent to mint OIDC tokens for the push identity.
gcloud iam service-accounts add-iam-policy-binding $killSwitchSa `
    --project=$ProjectId `
    --member="serviceAccount:service-$projectNumber@gcp-sa-pubsub.iam.gserviceaccount.com" `
    --role="roles/iam.serviceAccountTokenCreator" `
    --quiet | Out-Null

$pushSubscription = "$BudgetTopic-push"
$existingSubs = gcloud pubsub subscriptions list --project=$ProjectId --format="value(name)" --quiet
if (-not ($existingSubs -match "/subscriptions/$pushSubscription$")) {
    gcloud pubsub subscriptions create $pushSubscription `
        --project=$ProjectId `
        --topic=$BudgetTopic `
        --push-endpoint=$killSwitchUrl `
        --push-auth-service-account=$killSwitchSa `
        --ack-deadline=30 `
        --quiet
    if ($LASTEXITCODE -ne 0) { throw "Failed to create push subscription '$pushSubscription'." }
}

Write-Host "=== 5d. Ensuring 5-Cap Cloud Billing Budget Connected to Pub/Sub '$BudgetTopic' ===" -ForegroundColor Cyan
if ([string]::IsNullOrWhiteSpace($billingAccountId)) { throw "Could not resolve the billing account linked to '$ProjectId'." }
$currencyCode = gcloud billing accounts describe $billingAccountId --format="value(currencyCode)" --quiet
if ([string]::IsNullOrWhiteSpace($currencyCode)) { $currencyCode = "EUR" }
$budgetDisplayName = "CrossTraining MCP 5-Cap KillSwitch"
$existingBudgetsRaw = gcloud billing budgets list --billing-account=$billingAccountId --format="json" --quiet
if ($LASTEXITCODE -ne 0) { throw "Failed to list Cloud Billing budgets for billing account '$billingAccountId'." }
$existingBudgetsJson = ($existingBudgetsRaw | Out-String) | ConvertFrom-Json
$matchingBudget = $existingBudgetsJson | Where-Object { $_.displayName -eq $budgetDisplayName }
if (-not $matchingBudget) {
    gcloud billing budgets create `
        --billing-account=$billingAccountId `
        --display-name=$budgetDisplayName `
        --budget-amount="5$currencyCode" `
        --filter-projects="projects/$projectNumber" `
        --threshold-rule="percent=0.2" `
        --threshold-rule="percent=0.5" `
        --threshold-rule="percent=0.8" `
        --threshold-rule="percent=1.0" `
        --notifications-rule-pubsub-topic="projects/$ProjectId/topics/$BudgetTopic" `
        --quiet
    if ($LASTEXITCODE -ne 0) { throw "Failed to create Cloud Billing Budget '$budgetDisplayName' (5 $currencyCode)." }
    Write-Host "Created Cloud Billing Budget '$budgetDisplayName' (5 $currencyCode) wired to Pub/Sub '$BudgetTopic'." -ForegroundColor Green
} else {
    Write-Host "Cloud Billing Budget '$budgetDisplayName' already exists." -ForegroundColor Green
}

Write-Host "=== 5e. Ensuring MCP OAuth signing secret '$jwtSecretName' in Secret Manager ===" -ForegroundColor Cyan
$existingSecrets = gcloud secrets list --project=$ProjectId --format="value(name)" --quiet
if (-not ($existingSecrets -match "/secrets/$jwtSecretName$")) {
    $secretBytes = New-Object byte[] 48
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($secretBytes) } finally { $rng.Dispose() }
    $secretFile = [System.IO.Path]::GetTempFileName()
    try {
        [System.IO.File]::WriteAllText($secretFile, [Convert]::ToBase64String($secretBytes))
        gcloud secrets create $jwtSecretName `
            --project=$ProjectId `
            --replication-policy=automatic `
            --data-file=$secretFile `
            --quiet
        if ($LASTEXITCODE -ne 0) { throw "Failed to create Secret Manager secret '$jwtSecretName'." }
    }
    finally {
        Remove-Item $secretFile -Force -ErrorAction SilentlyContinue
    }
}
gcloud secrets add-iam-policy-binding $jwtSecretName `
    --project=$ProjectId `
    --member="serviceAccount:$runtimeSa" `
    --role="roles/secretmanager.secretAccessor" `
    --quiet | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Failed to grant secretAccessor on '$jwtSecretName' to $runtimeSa." }

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
    --set-env-vars="FIREBASE_PROJECT_ID=$ProjectId" `
    --quiet
if ($LASTEXITCODE -ne 0) { throw "Failed to deploy $ServiceName to Cloud Run." }

Write-Host "=== 7. Enforcing Artifact Registry 2-Image Cleanup Policy (Free Tier <= 500MB Guard) ===" -ForegroundColor Cyan
$policyFile = Join-Path ([System.IO.Path]::GetTempPath()) "ar-cleanup-policy.json"
$policyJson = @"
[
  {
    "name": "keep-2-most-recent",
    "action": {"type": "Keep"},
    "mostRecentVersions": {
      "keepCount": 2
    }
  },
  {
    "name": "delete-older-images",
    "action": {"type": "Delete"},
    "condition": {
      "tagState": "ANY"
    }
  }
]
"@
[System.IO.File]::WriteAllText($policyFile, $policyJson)

gcloud artifacts repositories set-cleanup-policies cloud-run-source-deploy `
    --project=$ProjectId `
    --location=$Region `
    --policy=$policyFile `
    --no-dry-run `
    --quiet
$cleanupExitCode = $LASTEXITCODE
Remove-Item $policyFile -ErrorAction SilentlyContinue
if ($cleanupExitCode -ne 0) { throw "Failed to apply Artifact Registry cleanup policy." }

$serviceUrl = gcloud run services describe $ServiceName --region=$Region --project=$ProjectId --format="value(status.url)" --quiet
if ([string]::IsNullOrWhiteSpace($serviceUrl)) { throw "Could not resolve $ServiceName URL." }
$serviceDomain = ([Uri]$serviceUrl).Host
$regionalDomain = "$ServiceName-$projectNumber.$Region.run.app"

Write-Host "=== 8. Registering Cloud Run Domains in Firebase Auth Authorized Domains ===" -ForegroundColor Cyan
# Refresh the token: the source builds above can outlive a token minted earlier in the run.
$apiHeaders = Get-GoogleApiHeaders
$cfg = Invoke-RestMethod -Uri "https://identitytoolkit.googleapis.com/admin/v2/projects/$ProjectId/config" -Headers $apiHeaders
$domains = [System.Collections.Generic.List[string]]::new()
foreach ($d in $cfg.authorizedDomains) { $domains.Add([string]$d) }
$changed = $false
foreach ($newDomain in @($serviceDomain, $regionalDomain)) {
    if (-not $domains.Contains($newDomain)) {
        $domains.Add($newDomain)
        $changed = $true
    }
}
if ($changed) {
    $patchBody = @{ authorizedDomains = $domains.ToArray() } | ConvertTo-Json
    Invoke-RestMethod -Method Patch `
        -Uri "https://identitytoolkit.googleapis.com/admin/v2/projects/$ProjectId/config?updateMask=authorizedDomains" `
        -Headers $apiHeaders `
        -ContentType "application/json" `
        -Body $patchBody | Out-Null
    Write-Host "Added Cloud Run domains to Firebase Auth authorizedDomains." -ForegroundColor Green
} else {
    Write-Host "Cloud Run domains already present in Firebase Auth authorizedDomains." -ForegroundColor Green
}

Write-Host ""
Write-Host "✅ Deployment Complete!" -ForegroundColor Green
Write-Host "Remote MCP Endpoint URL : $serviceUrl/mcp" -ForegroundColor Green
Write-Host "Legacy SSE Endpoint URL : $serviceUrl/sse" -ForegroundColor Green
Write-Host "OAuth Discovery URL     : $serviceUrl/.well-known/oauth-authorization-server" -ForegroundColor Green
