param (
    [string]$SummaryPath,
    [string]$PrNumber = "",
    [string]$Version = ""
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

if (-not $Version) {
    $Version = (Get-Item $SummaryPath).Directory.Name
}

# Check if release actually exists to avoid dead links
try {
    $null = gh release view $Version --repo AntaresAndBharani/virgymia-qa 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Not found" }
} catch {
    Write-Error "Release $Version not found on virgymia-qa. Please ensure you ran the tests with -PushArtifacts."
}

$Summary = Get-Content $SummaryPath -Raw | ConvertFrom-Json

$CommentBody = "<!-- e2e-evidence -->`n### :test_tube: E2E Test Evidence`n`n"
$CommentBody += "| Flow | Status |`n|---|---|`n"

$AnyFailed = $false
foreach ($flow in $Summary) {
    $check = [char]::ConvertFromUtf32(0x2705)
    $cross = [char]::ConvertFromUtf32(0x274C)
    $StatusIcon = if ($flow.passed) { $check } else { $cross }
    $CommentBody += "| $($flow.flow) | $StatusIcon |`n"
    if (-not $flow.passed) { $AnyFailed = $true }
}

if ($AnyFailed) {
    $cross = [char]::ConvertFromUtf32(0x274C)
    $CommentBody += "`n**Status:** $($cross) Some flows failed.`n"
} else {
    $check = [char]::ConvertFromUtf32(0x2705)
    $CommentBody += "`n**Status:** $($check) All flows passed.`n"
}

$link = [char]::ConvertFromUtf32(0x1F517)
$CommentBody += "`n[$($link) View Full HTML Report & Screenshots](https://github.com/AntaresAndBharani/virgymia-qa/releases/download/$Version/report.html)`n"

$RestComments = gh api repos/AntaresAndBharani/crosstrainingapp/issues/$PrNumber/comments | ConvertFrom-Json
$RestComment = $RestComments | Where-Object { $_.body -match "<!-- e2e-evidence -->" } | Select-Object -Last 1


if ($RestComment) {
    Write-Host "Updating existing PR comment on PR #$PrNumber (ID: $($RestComment.id))..."
    $CommentBody | gh api "repos/AntaresAndBharani/crosstrainingapp/issues/comments/$($RestComment.id)" -X PATCH -F body=@-
    if ($LASTEXITCODE -ne 0) { Write-Error "Failed to update comment." }
} else {
    Write-Host "Posting new PR comment on PR #$PrNumber..."
    $CommentBody | gh api "repos/AntaresAndBharani/crosstrainingapp/issues/$PrNumber/comments" -X POST -F body=@-
    if ($LASTEXITCODE -ne 0) { Write-Error "Failed to post comment." }
}

Write-Host "Evidence posted successfully." -ForegroundColor Green
