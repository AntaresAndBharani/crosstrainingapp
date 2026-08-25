param (
    [string]$SummaryPath,
    [string]$PrNumber = "",
    [string]$Version = "",
    [string]$Repo = $(if ($env:GITHUB_REPOSITORY) { $env:GITHUB_REPOSITORY } else { "AntaresAndBharani/crosstrainingapp" })
)

. (Join-Path $PSScriptRoot 'lib/PrComment.ps1')

if ([string]::IsNullOrWhiteSpace($Repo)) {
    $Repo = if ($env:GITHUB_REPOSITORY) { $env:GITHUB_REPOSITORY } else { "AntaresAndBharani/crosstrainingapp" }
}

if ($PrNumber) {
    if ($PrNumber -notmatch '^\d+$') {
        Write-Host "Invalid PR number '$PrNumber'. PR number must be numeric." -ForegroundColor Yellow
        exit 1
    }
} else {
    if ($null -ne (Get-Command gh -ErrorAction SilentlyContinue)) {
        try {
            $PrJson = gh pr view --json number 2>$null | ConvertFrom-Json
            if ($null -ne $PrJson -and $null -ne $PrJson.number) {
                $PrNumber = "$($PrJson.number)"
            }
        } catch {}
    }
}

if (-not $PrNumber) {
    Write-Host "No open PR found for this branch. Please push, open a PR, and run this script again." -ForegroundColor Yellow
    exit 1
}

if ([string]::IsNullOrWhiteSpace($SummaryPath) -or -not (Test-Path -LiteralPath $SummaryPath -PathType Leaf)) {
    Write-Host "Summary file not found at $SummaryPath" -ForegroundColor Yellow
    exit 1
}

if (-not $Version) {
    $Version = (Get-Item -LiteralPath $SummaryPath).Directory.Name
}

# Check if release actually exists to avoid dead links
try {
    $null = gh release view $Version --repo AntaresAndBharani/virgymia-qa 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Not found" }
} catch {
    Write-Host "Release $Version not found on virgymia-qa. Please ensure you ran the tests with -PushArtifacts." -ForegroundColor Yellow
    exit 1
}

$Summary = $null
try {
    $Summary = Get-Content -LiteralPath $SummaryPath -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
} catch {
    Write-Host "Failed to read or parse summary JSON at ${SummaryPath}: $_" -ForegroundColor Yellow
    exit 1
}

$Flows = @($Summary | Where-Object { $null -ne $_ -and -not [string]::IsNullOrWhiteSpace($_.flow) })
if ($Flows.Count -eq 0) {
    Write-Host "Summary at $SummaryPath contained no flow results." -ForegroundColor Yellow
    exit 1
}

$CommentBody = "<!-- e2e-evidence -->`n### :test_tube: E2E Test Evidence`n`n"
$CommentBody += "| Flow | Status |`n|---|---|`n"

$AnyFailed = $false
foreach ($flow in $Flows) {
    $check = [char]::ConvertFromUtf32(0x2705)
    $cross = [char]::ConvertFromUtf32(0x274C)
    $StatusIcon = if ($flow.passed) { $check } else { $cross }
    
    $StatusCell = $StatusIcon
    if (-not $flow.passed -and $flow.screenshot) {
        $ScreenshotUrl = "https://github.com/AntaresAndBharani/virgymia-qa/releases/download/$Version/$($flow.screenshot)"
        $camera = [char]::ConvertFromUtf32(0x1F4F8)
        $StatusCell += " [$($camera) Failure Screenshot]($ScreenshotUrl)"
    }

    $CommentBody += "| $($flow.flow) | $StatusCell |`n"
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

Publish-PrComment -Repo $Repo -PrNumber $PrNumber -Marker '<!-- e2e-evidence -->' -Body $CommentBody -TempFilePrefix 'e2e-evidence-body'
exit 0
