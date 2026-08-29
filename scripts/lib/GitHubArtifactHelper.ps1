<#
.SYNOPSIS
    Shared helper for retrieving and SHA-caching main-branch APK artifacts from GitHub.
.DESCRIPTION
    Provides Get-LatestMainCommitSha and Get-LatestMainBuildApk to resolve the latest
    main-branch HEAD SHA, check local artifact cache (app/build/outputs/apk/ci-main),
    and retrieve/cache remote build artifacts from GitHub Actions CI or GitHub Releases.
#>

function Get-LatestMainCommitSha {
    [CmdletBinding()]
    [OutputType([string])]
    param(
        [string]$Repo = $(if ($env:GITHUB_REPOSITORY) { $env:GITHUB_REPOSITORY } else { "AntaresAndBharani/crosstrainingapp" }),
        [string]$Branch = "main"
    )

    $ErrorActionPreference = 'Continue'

    if ([string]::IsNullOrWhiteSpace($Repo)) {
        $Repo = if ($env:GITHUB_REPOSITORY) { $env:GITHUB_REPOSITORY } else { "AntaresAndBharani/crosstrainingapp" }
    }

    if ([string]::IsNullOrWhiteSpace($Branch)) {
        $Branch = "main"
    }

    if ($null -eq (Get-Command gh -ErrorAction SilentlyContinue)) {
        Write-Warning "GitHub CLI (gh) not found in PATH; cannot resolve latest commit SHA."
        return $null
    }

    try {
        $shaRaw = gh api "repos/$Repo/commits/$Branch" --jq .sha 2>$null
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($shaRaw)) {
            Write-Warning "Failed to fetch commit SHA for branch '$Branch' on repo '$Repo'."
            return $null
        }

        $trimmed = $shaRaw.Trim()
        if ($trimmed.Length -gt 7) {
            return $trimmed.Substring(0, 7)
        }
        return $trimmed
    } catch {
        Write-Warning "Error fetching commit SHA: $_"
        return $null
    }
}

function Get-LatestMainSha {
    [CmdletBinding()]
    [OutputType([string])]
    param(
        [string]$Repo = $(if ($env:GITHUB_REPOSITORY) { $env:GITHUB_REPOSITORY } else { "AntaresAndBharani/crosstrainingapp" }),
        [string]$Branch = "main"
    )
    return (Get-LatestMainCommitSha -Repo $Repo -Branch $Branch)
}

function Get-LatestMainBuildApk {
    [CmdletBinding()]
    [OutputType([string])]
    [Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSAvoidUsingWriteHost', '', Justification = 'Deliberate human-readable CLI console output')]
    param(
        [string]$Repo = $(if ($env:GITHUB_REPOSITORY) { $env:GITHUB_REPOSITORY } else { "AntaresAndBharani/crosstrainingapp" }),
        [string]$Branch = "main",
        [string]$CacheDir = "app/build/outputs/apk/ci-main",
        [Alias("ExplicitSha")]
        [string]$Sha = $null,
        [Alias("ForceDownload")]
        [switch]$Force
    )

    $ErrorActionPreference = 'Continue'

    if ([string]::IsNullOrWhiteSpace($Repo)) {
        $Repo = if ($env:GITHUB_REPOSITORY) { $env:GITHUB_REPOSITORY } else { "AntaresAndBharani/crosstrainingapp" }
    }

    if ([string]::IsNullOrWhiteSpace($Branch)) {
        $Branch = "main"
    }

    if ([string]::IsNullOrWhiteSpace($CacheDir)) {
        $CacheDir = "app/build/outputs/apk/ci-main"
    }

    # Resolve commit SHA if not explicitly provided
    $resolvedSha = $Sha
    if ([string]::IsNullOrWhiteSpace($resolvedSha)) {
        $resolvedSha = Get-LatestMainCommitSha -Repo $Repo -Branch $Branch
        if ([string]::IsNullOrWhiteSpace($resolvedSha)) {
            Write-Warning "Could not determine latest commit SHA for '$Branch' on '$Repo'."
            return $null
        }
    } else {
        $resolvedSha = $resolvedSha.Trim()
        if ($resolvedSha.Length -gt 7) {
            $resolvedSha = $resolvedSha.Substring(0, 7)
        }
    }

    # Resolve target cache directory and cached APK path
    $resolvedCacheDir = if ([System.IO.Path]::IsPathRooted($CacheDir)) {
        $CacheDir
    } else {
        Join-Path (Get-Location) $CacheDir
    }

    $expectedFileName = "app-debug-$resolvedSha.apk"
    $cachedApkPath = Join-Path $resolvedCacheDir $expectedFileName

    # Check local cache first (unless -Force is specified)
    if (-not $Force -and (Test-Path -LiteralPath $cachedApkPath -PathType Leaf)) {
        Write-Host "Found cached APK for SHA $resolvedSha at '$cachedApkPath' (skipping network download)." -ForegroundColor Green
        return [System.IO.Path]::GetFullPath($cachedApkPath)
    }

    # If cache miss or -Force: retrieve from GitHub
    if ($null -eq (Get-Command gh -ErrorAction SilentlyContinue)) {
        Write-Warning "GitHub CLI (gh) not found in PATH; cannot download remote build artifact."
        return $null
    }

    if (-not (Test-Path -LiteralPath $resolvedCacheDir)) {
        try {
            [System.IO.Directory]::CreateDirectory($resolvedCacheDir) | Out-Null
        } catch {
            Write-Warning "Failed to create cache directory '$resolvedCacheDir': $_"
            return $null
        }
    }

    $tempDownloadDir = Join-Path ([System.IO.Path]::GetTempPath()) "apk-artifact-$([guid]::NewGuid())"
    try {
        [System.IO.Directory]::CreateDirectory($tempDownloadDir) | Out-Null

        $downloadSucceeded = $false

        # Strategy 1: Attempt to download GitHub Actions CI artifact matching commit SHA
        try {
            $runsRaw = gh api "repos/$Repo/actions/runs?branch=$Branch&status=success&per_page=10" 2>$null
            if ($LASTEXITCODE -eq 0 -and (-not [string]::IsNullOrWhiteSpace($runsRaw))) {
                $runsData = $runsRaw | ConvertFrom-Json
                if ($null -ne $runsData -and $null -ne $runsData.workflow_runs) {
                    $matchingRun = $runsData.workflow_runs | Where-Object {
                        $null -ne $_.head_sha -and $_.head_sha.StartsWith($resolvedSha, [System.StringComparison]::OrdinalIgnoreCase)
                    } | Select-Object -First 1

                    if ($matchingRun) {
                        Write-Host "Found matching CI run $($matchingRun.id) for SHA $resolvedSha. Downloading artifact..." -ForegroundColor Cyan
                        $null = gh run download $matchingRun.id --repo $Repo --dir $tempDownloadDir 2>$null
                        if ($LASTEXITCODE -eq 0) {
                            $downloadSucceeded = $true
                        }
                    }
                }
            }
        } catch {
            Write-Warning "Failed querying CI artifacts: $_"
        }

        # Strategy 2: Fallback to GitHub Releases if CI artifact download didn't find/download APK
        if (-not $downloadSucceeded) {
            try {
                Write-Host "Checking GitHub Releases for SHA $resolvedSha / latest release..." -ForegroundColor Cyan
                $null = gh release download --repo $Repo --dir $tempDownloadDir --pattern "*.apk" 2>$null
                if ($LASTEXITCODE -eq 0) {
                    $downloadSucceeded = $true
                }
            } catch {
                Write-Warning "Failed querying GitHub Releases: $_"
            }
        }

        # Look for any APK in downloaded files
        $foundApk = Get-ChildItem -Path $tempDownloadDir -Filter "*.apk" -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($null -ne $foundApk) {
            Copy-Item -LiteralPath $foundApk.FullName -Destination $cachedApkPath -Force
            Write-Host "Successfully retrieved and cached APK at '$cachedApkPath'." -ForegroundColor Green
            return [System.IO.Path]::GetFullPath($cachedApkPath)
        } else {
            Write-Warning "No APK artifact found in download for SHA $resolvedSha."
            return $null
        }
    } catch {
        Write-Warning "Failed to retrieve main-branch APK: $_"
        return $null
    } finally {
        if (Test-Path -LiteralPath $tempDownloadDir) {
            Remove-Item -LiteralPath $tempDownloadDir -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}

