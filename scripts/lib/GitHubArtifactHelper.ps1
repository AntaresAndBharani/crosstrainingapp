<#
.SYNOPSIS
    GitHub Actions / Releases artifact retrieval and SHA-caching helper.
.DESCRIPTION
    Provides Get-LatestMainBuildApk to retrieve verified builds by origin/main HEAD SHA.
    Skips download when APK is already present in the local cache directory.
#>

function Get-LatestMainBuildApk {
    [CmdletBinding()]
    [OutputType([string])]
    param(
        [string]$CacheDir = "app/build/outputs/apk/ci-main",
        [string]$Repo = "AntaresAndBharani/crosstrainingapp",
        [string]$HeadSha = $null,
        [scriptblock]$DownloadAction = $null
    )

    if ([string]::IsNullOrWhiteSpace($HeadSha)) {
        try {
            $gitSha = git rev-parse origin/main 2>$null
            if ($LASTEXITCODE -eq 0 -and (-not [string]::IsNullOrWhiteSpace($gitSha))) {
                $HeadSha = $gitSha.Trim()
            }
        } catch {}

        if ([string]::IsNullOrWhiteSpace($HeadSha)) {
            try {
                $gitHead = git rev-parse HEAD 2>$null
                if ($LASTEXITCODE -eq 0 -and (-not [string]::IsNullOrWhiteSpace($gitHead))) {
                    $HeadSha = $gitHead.Trim()
                }
            } catch {}
        }
    }

    if ([string]::IsNullOrWhiteSpace($HeadSha)) {
        $HeadSha = "latest"
    }

    if (-not (Test-Path $CacheDir)) {
        [System.IO.Directory]::CreateDirectory($CacheDir) | Out-Null
    }

    $apkFileName = "app-debug-$HeadSha.apk"
    $cachedApkPath = Join-Path $CacheDir $apkFileName
    $resolvedPath = [System.IO.Path]::GetFullPath($cachedApkPath)

    if (Test-Path $resolvedPath) {
        Write-Host "Found cached artifact at $resolvedPath. Skipping network download." -ForegroundColor Green
        return $resolvedPath
    }

    Write-Host "Cached APK not found for SHA $HeadSha. Downloading from GitHub..." -ForegroundColor Magenta
    if ($null -ne $DownloadAction) {
        & $DownloadAction -TargetFile $resolvedPath -Sha $HeadSha -Repo $Repo
    } else {
        if ($null -eq (Get-Command gh -ErrorAction SilentlyContinue)) {
            throw "GitHub CLI (gh) not found in PATH; unable to download artifact."
        }

        try {
            $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) "apk-dl-$([guid]::NewGuid())"
            [System.IO.Directory]::CreateDirectory($tempDir) | Out-Null
            gh release download --repo $Repo --pattern "*.apk" --dir $tempDir 2>$null
            $downloadedApk = Get-ChildItem -Path $tempDir -Filter "*.apk" | Select-Object -First 1
            if ($downloadedApk) {
                Copy-Item -Path $downloadedApk.FullName -Destination $resolvedPath -Force
                Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
            } else {
                Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
                throw "No APK found in release for $Repo."
            }
        } catch {
            throw "Failed to download artifact from GitHub: $_"
        }
    }

    if (Test-Path $resolvedPath) {
        return $resolvedPath
    }

    throw "Artifact download completed but expected file was not created: $resolvedPath"
}
