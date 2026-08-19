param (
    [string]$SummaryPath,
    [string]$PrNumber = ""
)

$ErrorActionPreference = "Stop"

if (-not $PrNumber) {
    if ($null -ne (Get-Command gh -ErrorAction SilentlyContinue)) {
        try {
            $PrJson = gh pr view --json number 2>$null | ConvertFrom-Json
            if ($null -ne $PrJson -and $null -ne $PrJson.number) {
                $PrNumber = $PrJson.number
            }
        } catch {}
    }
}

if (-not $PrNumber) {
    Write-Host "No open PR found for this branch. Please push, open a PR, and run this script again." -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Path $SummaryPath)) {
    Write-Error "Summary file not found at $SummaryPath"
}

$Version = (Get-Item $SummaryPath).Directory.Name

$Summary = Get-Content $SummaryPath -Raw | ConvertFrom-Json

$CommentBody = "<!-- e2e-evidence -->`n### :test_tube: E2E Test Evidence`n`n"
$CommentBody += "| Flow | Status |`n|---|---|`n"

$AnyFailed = $false
foreach ($flow in $Summary) {
    $StatusIcon = if ($flow.passed) { "✅" } else { "❌" }
    $CommentBody += "| $($flow.flow) | $StatusIcon |`n"
    if (-not $flow.passed) { $AnyFailed = $true }
}

if ($AnyFailed) {
    $CommentBody += "`n**Status:** ❌ Some flows failed.`n"
} else {
    $CommentBody += "`n**Status:** ✅ All flows passed.`n"
}

$CommentBody += "`n[🔗 View Full HTML Report & Screenshots](https://github.com/AntaresAndBharani/virgymia-qa/releases/download/$Version/report.html)`n"

$Comments = gh pr view $PrNumber --json comments | ConvertFrom-Json
$ExistingComment = $Comments.comments | Where-Object { $_.body -match "<!-- e2e-evidence -->" } | Select-Object -Last 1

if ($ExistingComment) {
    Write-Host "Updating existing PR comment on PR #$PrNumber..."
    $CommentBody | gh pr comment $PrNumber --edit $ExistingComment.id -F -
} else {
    Write-Host "Posting new PR comment on PR #$PrNumber..."
    $CommentBody | gh pr comment $PrNumber -F -
}

Write-Host "Evidence posted successfully." -ForegroundColor Green
