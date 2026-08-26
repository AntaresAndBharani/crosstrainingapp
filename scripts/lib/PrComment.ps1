<#
.SYNOPSIS
    Shared helper for publishing sticky PR evidence comments to GitHub PRs.
.DESCRIPTION
    Provides Publish-PrComment to find and update (PATCH) an existing sticky comment
    matching a given evidence marker, or create (POST) a new one if not found.
    Handles CLI presence checks, pagination, BOM-free UTF-8 temp file creation,
    cleanup in finally blocks, and non-fatal error handling.
#>

function Publish-PrComment {
    [CmdletBinding()]
    [Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSAvoidUsingWriteHost', '', Justification = 'Deliberate human-readable CI console output')]
    # Publish-PrComment is the single source of truth for -Repo fallback and normalization. Callers should pass -Repo through directly.
    param(
        [string]$Repo = $(if ($env:GITHUB_REPOSITORY) { $env:GITHUB_REPOSITORY } else { "AntaresAndBharani/crosstrainingapp" }),
        [string]$PrNumber,
        [string]$Marker,
        [string]$Body,
        [string]$TempFilePrefix = "pr-comment"
    )

    $ErrorActionPreference = 'Continue'

    if ([string]::IsNullOrWhiteSpace($Repo)) {
        $Repo = if ($env:GITHUB_REPOSITORY) { $env:GITHUB_REPOSITORY } else { "AntaresAndBharani/crosstrainingapp" }
    }

    if ([string]::IsNullOrWhiteSpace($TempFilePrefix)) {
        $TempFilePrefix = "pr-comment"
    }

    if ([string]::IsNullOrWhiteSpace($PrNumber) -or $PrNumber -notmatch '^\d+$') {
        $emDash = [char]0x2014
        Write-Host "No PR context $emDash skipping comment"
        return $false
    }

    if ($null -eq (Get-Command gh -ErrorAction SilentlyContinue)) {
        Write-Warning "GitHub CLI (gh) not found in PATH; skipping PR comment publishing."
        return $false
    }

    $tempBodyFile = Join-Path ([System.IO.Path]::GetTempPath()) "$TempFilePrefix-$([guid]::NewGuid()).md"

    try {
        try {
            $utf8NoBom = New-Object System.Text.UTF8Encoding $false
            [System.IO.File]::WriteAllText($tempBodyFile, $Body, $utf8NoBom)
        } catch {
            Write-Warning "Failed to write temp body file: $_"
            return $false
        }

        $commentsRaw = $null
        try {
            $commentsRaw = gh api "repos/$Repo/issues/$PrNumber/comments" --paginate
        } catch {
            Write-Warning "Failed to invoke gh api to list PR comments on PR #${PrNumber}: $_"
            return $false
        }

        if ($LASTEXITCODE -ne 0) {
            Write-Warning "Failed to list PR comments on PR #$PrNumber."
            return $false
        }

        $comments = @()
        if (-not [string]::IsNullOrWhiteSpace($commentsRaw)) {
            try {
                $parsed = $commentsRaw | ConvertFrom-Json
                if ($null -ne $parsed) {
                    foreach ($item in $parsed) {
                        if ($null -ne $item) {
                            $comments += $item
                        }
                    }
                }
            } catch {
                Write-Warning "Failed to parse comments JSON: $_"
                return $false
            }
        }

        $currentUser = $null
        try {
            $currentUser = gh api user --jq .login 2>$null
        } catch {
            $currentUser = $null
        }

        $targetComment = $comments | Where-Object {
            ($null -ne $_.body) -and
            ($_.body -is [string]) -and
            ($_.body.StartsWith($Marker, [System.StringComparison]::Ordinal)) -and
            ([string]::IsNullOrWhiteSpace($currentUser) -or (($null -ne $_.user) -and ($_.user.login -eq $currentUser)))
        } | Select-Object -Last 1

        if ($targetComment) {
            Write-Host "Updating existing PR comment on PR #$PrNumber (ID: $($targetComment.id))..."
            try {
                $null = gh api "repos/$Repo/issues/comments/$($targetComment.id)" -X PATCH --silent -F "body=@$tempBodyFile"
            } catch {
                Write-Warning "Failed to update comment on PR #${PrNumber}: $_"
                return $false
            }

            if ($LASTEXITCODE -ne 0) {
                Write-Warning "Failed to update comment on PR #$PrNumber."
                return $false
            }
        } else {
            Write-Host "Posting new PR comment on PR #$PrNumber..."
            try {
                $null = gh api "repos/$Repo/issues/$PrNumber/comments" -X POST --silent -F "body=@$tempBodyFile"
            } catch {
                Write-Warning "Failed to post comment on PR #${PrNumber}: $_"
                return $false
            }

            if ($LASTEXITCODE -ne 0) {
                Write-Warning "Failed to post comment on PR #$PrNumber."
                return $false
            }
        }

        Write-Host "Evidence posted successfully." -ForegroundColor Green
        return $true
    } catch {
        Write-Warning "Unexpected error in Publish-PrComment: $_"
        return $false
    } finally {
        Remove-Item -LiteralPath $tempBodyFile -Force -ErrorAction SilentlyContinue
    }
}
