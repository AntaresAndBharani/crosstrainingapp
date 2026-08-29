<#
.SYNOPSIS
    Helper module for downloading and caching GitHub Actions / Release build artifacts.
.DESCRIPTION
    Provides Get-LatestMainBuildApk to retrieve SHA-cached main-branch APK builds,
    avoiding redundant network transfers and downloading on demand.
#>

function Get-LatestMainSha {
    [CmdletBinding()]
    [OutputType([string])]
    param(
        [string]$Repo = "AntaresAndBharani/crosstrainingapp"
    )

    $sha = $null
    try {
        $gitOut = & git rev-parse origin/main 2>$null
        if ($LASTEXITCODE -eq 0 -and (-not [string]::IsNullOrWhiteSpace($gitOut))) {
            $sha = $gitOut.Trim()
        }
    } catch {}

    if ([string]::IsNullOrWhiteSpace($sha)) {
        try {
            $gitOut = & git rev-parse main 2>$null
            if ($LASTEXITCODE -eq 0 -and (-not [string]::IsNullOrWhiteSpace($gitOut))) {
                $sha = $gitOut.Trim()
            }
        } catch {}
    }

    if ([string]::IsNullOrWhiteSpace($sha)) {
        try {
            $ghOut = & gh api "repos/$Repo/commits/main" -q .sha 2>$null
            if ($LASTEXITCODE -eq 0 -and (-not [string]::IsNullOrWhiteSpace($ghOut))) {
                $sha = $ghOut.Trim()
            }
        } catch {}
    }

    return $sha
}

function Get-LatestMainBuildApk {
    [CmdletBinding()]
    [OutputType([string])]
    param(
        [string]$Repo = $(if ($env:GITHUB_REPOSITORY) { $env:GITHUB_REPOSITORY } else { "AntaresAndBharani/crosstrainingapp" }),
        [string]$CacheDir = "app/build/outputs/apk/ci-main",
        [string]$ExplicitSha = $null,
        [switch]$ForceDownload
    )

    $sha = if (-not [string]::IsNullOrWhiteSpace($ExplicitSha)) {
        $ExplicitSha.Trim()
    } else {
        Get-LatestMainSha -Repo $Repo
    }

    if ([string]::IsNullOrWhiteSpace($sha)) {
        throw "Failed to resolve latest main commit SHA for repository '$Repo'."
    }

    $shortSha = if ($sha.Length -gt 7) { $sha.Substring(0, 7) } else { $sha }

    # Resolve CacheDir path
    $targetDir = $CacheDir
    if (-not [System.IO.Path]::IsPathRooted($targetDir)) {
        $targetDir = Join-Path $PWD $CacheDir
    }

    $expectedFiles = @(
        (Join-Path $targetDir "app-debug-$sha.apk"),
        (Join-Path $targetDir "app-debug-$shortSha.apk")
    )

    if (-not $ForceDownload) {
        foreach ($f in $expectedFiles) {
            if ([System.IO.File]::Exists($f)) {
                return [System.IO.Path]::GetFullPath($f)
            }
        }
        if ([System.IO.Directory]::Exists($targetDir)) {
            $matching = Get-ChildItem -Path $targetDir -Filter "*$shortSha*.apk" -File -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($null -ne $matching) {
                return $matching.FullName
            }
        }
    }

    # Ensure target directory exists
    if (-not [System.IO.Directory]::Exists($targetDir)) {
        [System.IO.Directory]::CreateDirectory($targetDir) | Out-Null
    }

    # Verify GitHub CLI availability
    if ($null -eq (Get-Command gh -ErrorAction SilentlyContinue)) {
        throw "GitHub CLI ('gh') is not installed or not found on PATH. Cannot retrieve CI artifacts."
    }

    # Attempt to download via gh release / run artifact
    $downloadSuccess = $false

    # Try 1: gh run download
    try {
        $runs = gh run list --repo $Repo --branch main --workflow build.yml --status success --limit 1 --json databaseId 2>$null | ConvertFrom-Json
        if ($runs -and $runs.Count -gt 0) {
            $runId = $runs[0].databaseId
            & gh run download $runId --repo $Repo --dir $targetDir 2>&1 | Out-Null
            if ($LASTEXITCODE -eq 0) {
                $downloadSuccess = $true
            }
        }
    } catch {}

    # Try 2: gh release download
    if (-not $downloadSuccess) {
        try {
            & gh release download --repo $Repo --dir $targetDir --pattern "*.apk" --clobber 2>&1 | Out-Null
            if ($LASTEXITCODE -eq 0) {
                $downloadSuccess = $true
            }
        } catch {}
    }

    if (-not $downloadSuccess) {
        throw "Failed to download artifact or release from GitHub for repository '$Repo'."
    }

    # Locate downloaded APK
    $downloadedApk = Get-ChildItem -Path $targetDir -Filter "*.apk" -Recurse -File | Select-Object -First 1
    if ($null -eq $downloadedApk) {
        throw "No APK file found in downloaded artifact directory '$targetDir'."
    }

    # Cache with standard naming
    $cachedPath = Join-Path $targetDir "app-debug-$shortSha.apk"
    if ($downloadedApk.FullName -ne $cachedPath) {
        Copy-Item -Path $downloadedApk.FullName -Destination $cachedPath -Force
    }

    return [System.IO.Path]::GetFullPath($cachedPath)
}
